package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import io.github.leks2205.hangulquest.dialogue.DialogueParser;
import io.github.leks2205.hangulquest.dialogue.DialogueValidator;
import io.github.leks2205.hangulquest.dialogue.LineLevels;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestFixtures;
import io.github.leks2205.hangulquest.villager.RequestParser;
import io.github.leks2205.hangulquest.villager.RequestValidator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks a folder of drafted villager conversations and requests before they are copied into the mod: every rule
 * the game and the content tests apply, plus the naming scheme of content/briefs/VILLAGERS.md. Prints one line per
 * problem ({@code file: node: message}), writes the lines as a sentence dump for the Korean linter and exits 1 when
 * anything is wrong. Run through {@code python3 tools/validate_dialogue_drafts.py <dir>}, which adds the linter.
 *
 * <p>Folder layout: {@code dialogue/*.json} and {@code requests/*.json} (or any *.json: a file with {@code nodes} is
 * a conversation, one with {@code lines} a request), {@code lang/en_us.json} and {@code lang/de_de.json} with the
 * glosses, and optionally {@code _lexicon.json} with new words ({@code {"words": [...]}} as in the shipped one).
 */
public final class DraftValidator {
	/** One problem: the file (relative to the folder), the node or part, the message. */
	public record Problem(String file, String where, String message) {
		@Override
		public String toString() {
			return file + ": " + where + ": " + message;
		}
	}

	/** A line of the sentence dump: id, level, source, Korean, goal (SentenceDumpTest's columns). */
	public record Row(String id, int level, String source, String ko, String goal) {
	}

	public record Result(List<Problem> problems, List<Row> rows, int dialogues, int requests) {
	}

	private static final Pattern PLAIN = Pattern.compile("[가-힣 ,.!?]+");
	private static final Pattern INTRO_ID = Pattern.compile("vt_([a-z]+)_([a-z]+)_intro");
	private static final Pattern TALK_ID = Pattern.compile("vt_([a-z]+)_([a-z]+)_(\\d\\d)");
	private static final Pattern MILESTONE_ID = Pattern.compile("vt_([a-z]+)_ms(3|6|10)");
	private static final Pattern BANMAL_ID = Pattern.compile("vt_([a-z]+)_banmal");
	private static final Pattern REQUEST_ID = Pattern.compile("req_([a-z]+)_(\\d\\d)");

	private final Path dir;
	private final Catalog catalog = KoreanFixtures.catalog();
	private final List<Problem> problems = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private Map<String, String> english;
	private Map<String, String> german;
	private DialogueLexicon lexicon;

	private DraftValidator(Path dir) {
		this.dir = dir;
	}

	public static void main(String[] args) {
		if (args.length < 1 || args[0].isBlank()) {
			System.err.println("usage: DraftValidator <folder of drafts> [sentences.tsv]");
			System.exit(2);
		}
		Path dir = Path.of(args[0]).toAbsolutePath();
		if (!Files.isDirectory(dir)) {
			System.err.println(dir + " is not a folder");
			System.exit(2);
		}
		Result result = check(dir);
		if (args.length > 1) {
			write(result.rows(), Path.of(args[1]));
		}
		System.exit(report(result, System.out) ? 0 : 1);
	}

	/** Prints the problems and a summary; true when there are none. */
	static boolean report(Result result, PrintStream out) {
		result.problems().forEach(out::println);
		out.println("drafts: " + result.dialogues() + " conversations, " + result.requests() + " requests, "
				+ result.rows().size() + " lines, " + result.problems().size() + " problems");
		return result.problems().isEmpty();
	}

	public static Result check(Path dir) {
		DraftValidator validator = new DraftValidator(dir);
		return validator.run();
	}

	private Result run() {
		english = new HashMap<>(DialogueFixtures.lang("en_us"));
		german = new HashMap<>(DialogueFixtures.lang("de_de"));
		english.putAll(draftLang("en_us"));
		german.putAll(draftLang("de_de"));
		lexicon = lexicon();
		int dialogues = 0;
		int requests = 0;
		for (Path file : jsonFiles()) {
			String rel = dir.relativize(file).toString().replace('\\', '/');
			String name = file.getFileName().toString();
			String id = name.substring(0, name.length() - ".json".length());
			byte[] bytes;
			JsonObject root;
			try {
				bytes = Files.readAllBytes(file);
				root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (IOException | RuntimeException broken) {
				problems.add(new Problem(rel, "file", "not a JSON object (" + broken.getMessage() + ")"));
				continue;
			}
			if (root.has("nodes")) {
				dialogues++;
				checkDialogue(rel, id, bytes);
			} else if (root.has("lines")) {
				requests++;
				checkRequest(rel, id, bytes);
			} else {
				problems.add(new Problem(rel, "file", "neither a conversation (nodes) nor a request (lines)"));
			}
		}
		if (dialogues + requests == 0) {
			problems.add(new Problem(".", "folder", "no drafted conversation or request found"));
		}
		return new Result(List.copyOf(problems), List.copyOf(rows), dialogues, requests);
	}

	// ---------------------------------------------------------------- conversations

	private void checkDialogue(String rel, String id, byte[] bytes) {
		DialogueParser.Parsed parsed = DialogueParser.parse(id, bytes);
		parsed.problems().forEach(problem -> problems.add(new Problem(rel, "parse", problem)));
		Dialogue dialogue = parsed.dialogue();
		if (dialogue == null) {
			return;
		}
		// the glosses are checked below, in both languages
		DialogueValidator.Env env = new DialogueValidator.Env(DialogueFixtures.trailIds(), DialogueFixtures.grammarIds(),
				word -> catalog.byId(word).isPresent(), null);
		for (String problem : DialogueValidator.validate(dialogue, env)) {
			problems.add(new Problem(rel, nodeOf(problem, dialogue.id()), problem));
		}
		checkDialogueName(rel, dialogue);
		if (dialogue.level() < 1 || dialogue.level() > 6) {
			problems.add(new Problem(rel, "level", "level must be 1 to 6"));
		}
		for (Dialogue.Node node : dialogue.nodes().values()) {
			String where = node.id();
			List<String> glossKeys = new ArrayList<>(List.of(dialogue.lineKey(node.id())));
			if (node.typed() != null) {
				glossKeys.add(dialogue.promptKey(node.id()));
			}
			for (int i = 0; i < node.choices().size(); i++) {
				glossKeys.add(dialogue.choiceKey(node.id(), i));
				if (node.grammar() != null) {
					glossKeys.add(dialogue.choiceNoteKey(node.id(), i));
				}
			}
			for (String key : glossKeys) {
				gloss(rel, where, key);
			}
			if (node.selfIntro()) {
				String intro = english.getOrDefault(dialogue.lineKey(node.id()), "");
				if (!intro.contains("%s") || !german.getOrDefault(dialogue.lineKey(node.id()), "").contains("%s")) {
					problems.add(new Problem(rel, where, "the gloss of the selfIntro line needs %s for the name "
							+ "(\"I'm %s.\", \"Ich bin %s.\")"));
				}
			}
			String file = "draft:" + rel + "#nodes." + node.id();
			String rowId = "dialogue." + dialogue.id() + "." + node.id();
			if (!node.selfIntro()) {
				line(rel, where, node.ko(), dialogue.level(), rowId, file + ".ko",
						"Dialogue " + node.speaker().id() + " " + node.speechLevel().id());
			}
			if (node.koBanmal() != null) {
				line(rel, where + ".ko_banmal", node.koBanmal(), dialogue.level(), rowId + ".banmal",
						file + ".ko_banmal", "Dialogue " + node.speaker().id() + " banmal");
			}
			for (int i = 0; i < node.choices().size(); i++) {
				Dialogue.Choice choice = node.choices().get(i);
				String grade = choice.correct() == null ? "ungraded" : choice.correct() ? "right" : "wrong";
				line(rel, where + ".c" + (i + 1), choice.ko(), dialogue.level(), rowId + ".c" + (i + 1),
						file + ".choices[" + i + "].ko", "Dialogue player " + choice.speechLevel().id() + " " + grade);
			}
		}
	}

	/** The naming scheme of the villager conversations (content/briefs/VILLAGERS.md). */
	private void checkDialogueName(String rel, Dialogue dialogue) {
		Dialogue.Trigger trigger = dialogue.trigger();
		if (!trigger.villagerOnly()) {
			return;
		}
		if (trigger.personalities().size() != 1) {
			problems.add(new Problem(rel, "trigger", "a drafted villager conversation names exactly one personality"));
			return;
		}
		String personality = trigger.personalities().get(0).id();
		String region = trigger.regions().isEmpty() ? "gen" : trigger.regions().size() == 1
				? trigger.regions().get(0).id() : null;
		String id = dialogue.id();
		String expected = switch (trigger.kind()) {
			case INTRO -> "vt_" + personality + "_" + region + "_intro";
			case SMALLTALK -> "vt_" + personality + "_" + region + "_NN";
			case MILESTONE -> "vt_" + personality + "_ms" + trigger.friendship();
			case BANMAL_OFFER -> "vt_" + personality + "_banmal";
			case REQUEST -> "a request file";
		};
		boolean ok = switch (trigger.kind()) {
			case INTRO -> region != null && INTRO_ID.matcher(id).matches() && id.equals(expected);
			case SMALLTALK -> region != null && TALK_ID.matcher(id).matches()
					&& id.startsWith("vt_" + personality + "_" + region + "_");
			case MILESTONE -> MILESTONE_ID.matcher(id).matches() && id.equals(expected);
			case BANMAL_OFFER -> BANMAL_ID.matcher(id).matches() && id.equals(expected);
			case REQUEST -> false;
		};
		if (region == null) {
			problems.add(new Problem(rel, "trigger", "a drafted villager conversation names one region or none (gen)"));
		} else if (!ok) {
			problems.add(new Problem(rel, "id", "the id \"" + id + "\" should be " + expected));
		}
	}

	// ---------------------------------------------------------------- requests

	private void checkRequest(String rel, String id, byte[] bytes) {
		RequestParser.Parsed parsed = RequestParser.parse(id, bytes);
		parsed.problems().forEach(problem -> problems.add(new Problem(rel, "parse", problem)));
		Request request = parsed.request();
		if (request == null || request.kind() == null) {
			return;
		}
		RequestValidator.Env env = new RequestValidator.Env(catalog, word -> catalog.byId(word).isPresent(), null,
				RequestFixtures.itemExists());
		for (String problem : RequestValidator.validate(request, env)) {
			problems.add(new Problem(rel, nodeOf(problem, request.id()), problem));
		}
		var matcher = REQUEST_ID.matcher(request.id());
		if (!matcher.matches() || request.regions().stream().noneMatch(region -> region.id().equals(matcher.group(1)))) {
			problems.add(new Problem(rel, "id", "the id \"" + request.id() + "\" should be req_<region>_NN with one of "
					+ request.regions().stream().map(Region::id).toList()));
		}
		List<String> glossKeys = new ArrayList<>(List.of(request.titleKey(), request.answerKey(true), request.answerKey(false)));
		for (Personality personality : request.offeredBy()) {
			for (Request.Part part : Request.Part.values()) {
				glossKeys.add(request.lineKey(personality, part));
			}
		}
		for (String key : glossKeys) {
			gloss(rel, key.substring(key.lastIndexOf('.') + 1), key);
		}
		if (request.yes() == null || request.no() == null) {
			return;
		}
		for (RequestFixtures.Spoken spoken : RequestFixtures.lines(request)) {
			line(rel, spoken.node(), spoken.ko(), request.level(), "dialogue." + request.id() + "." + spoken.node(),
					"draft:" + rel + "#" + spoken.pointer(), "Dialogue " + spoken.speaker() + " " + spoken.level()
							+ (spoken.speaker().equals("player") ? " ungraded" : ""));
		}
	}

	// ---------------------------------------------------------------- shared checks

	/** The Korean form, the vocabulary and the grammar level of one line; adds it to the sentence dump. */
	private void line(String rel, String where, String ko, int level, String rowId, String source, String goal) {
		if (ko == null || ko.isBlank()) {
			return;
		}
		rows.add(new Row(rowId, level, source, ko, goal));
		if (!PLAIN.matcher(ko).matches() || !(ko.endsWith(".") || ko.endsWith("!") || ko.endsWith("?"))) {
			problems.add(new Problem(rel, where, "\"" + ko + "\": only Hangul, spaces and , . ! ?, ending in . ! or ?"));
		}
		for (Lang lang : List.of(Lang.EN, Lang.DE)) {
			DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(catalog, lexicon, earlyWords(), lang);
			List<DialogueCoverage.Token> tokens = DialogueCoverage.analyze(ko, vocabulary);
			for (DialogueCoverage.Token token : tokens) {
				if (lang == Lang.EN && token.state() == DialogueCoverage.State.UNRESOLVED) {
					problems.add(new Problem(rel, where, "'" + token.text() + "' in \"" + ko + "\" matches no word of the"
							+ " catalog or the dialogue lexicon (add it to _lexicon.json, or a particle/ending the"
							+ " lexicon knows)"));
				}
				if (token.state() == DialogueCoverage.State.UNKNOWN && (token.gloss() == null || token.gloss().isBlank())) {
					problems.add(new Problem(rel, where, "'" + token.text() + "' has no " + (lang == Lang.EN ? "English" : "German") + " gloss"));
				}
			}
			if (lang == Lang.EN && !DialogueCoverage.meetsRule(tokens)) {
				problems.add(new Problem(rel, where, "\"" + ko + "\" has " + DialogueCoverage.unknownWords(tokens)
						+ " words a beginner does not know; at most " + DialogueCoverage.MAX_UNKNOWN));
			}
		}
		for (String problem : LineLevels.problems(where, ko, level)) {
			problems.add(new Problem(rel, where, problem.substring(where.length() + 2)));
		}
	}

	private void gloss(String rel, String where, String key) {
		if (english.getOrDefault(key, "").isBlank()) {
			problems.add(new Problem(rel, where, "no English gloss " + key + " (lang/en_us.json)"));
		}
		if (german.getOrDefault(key, "").isBlank()) {
			problems.add(new Problem(rel, where, "no German gloss " + key + " (lang/de_de.json)"));
		}
	}

	/** What a player who has done the first two levels knows: the curated words of tiers 1 and 2 (as the content tests). */
	private Predicate<String> earlyWords() {
		return id -> catalog.byId(id).filter(word -> word.conceptId != null && word.tier <= 2).isPresent();
	}

	/** "id.node: message" → node; anything else → "file". */
	private static String nodeOf(String problem, String id) {
		if (problem.startsWith(id + ".")) {
			int colon = problem.indexOf(':');
			return colon > 0 ? problem.substring(id.length() + 1, colon) : "file";
		}
		return problem.startsWith("no gloss text for ") ? "gloss" : "file";
	}

	private List<Path> jsonFiles() {
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.filter(path -> !path.getFileName().toString().startsWith("_"))
					.filter(path -> !dir.relativize(path).toString().replace('\\', '/').startsWith("lang/"))
					.sorted().toList();
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	private Map<String, String> draftLang(String code) {
		Path file = dir.resolve("lang").resolve(code + ".json");
		Map<String, String> out = new LinkedHashMap<>();
		if (!Files.isRegularFile(file)) {
			problems.add(new Problem("lang/" + code + ".json", "file", "missing: the glosses of the drafts"));
			return out;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (!entry.getKey().startsWith("hangulquest.dialogue.") && !entry.getKey().startsWith("hangulquest.request.")) {
					problems.add(new Problem("lang/" + code + ".json", entry.getKey(),
							"only hangulquest.dialogue.* and hangulquest.request.* keys belong in the drafts"));
				}
				out.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException | RuntimeException broken) {
			problems.add(new Problem("lang/" + code + ".json", "file", "not a flat JSON object of strings"));
		}
		return out;
	}

	/** The shipped dialogue lexicon with the drafted new words. */
	private DialogueLexicon lexicon() {
		try {
			JsonObject shipped = JsonParser.parseString(Files.readString(DialogueFixtures.DIR.resolve("_lexicon.json"),
					StandardCharsets.UTF_8)).getAsJsonObject();
			Set<String> ids = new java.util.HashSet<>();
			Set<String> forms = new java.util.HashSet<>();
			for (JsonElement word : shipped.getAsJsonArray("words")) {
				ids.add(word.getAsJsonObject().get("id").getAsString());
				word.getAsJsonObject().getAsJsonArray("forms").forEach(form -> forms.add(form.getAsString()));
			}
			Path draft = dir.resolve("_lexicon.json");
			if (Files.isRegularFile(draft)) {
				JsonArray added = JsonParser.parseString(Files.readString(draft, StandardCharsets.UTF_8)).getAsJsonObject()
						.getAsJsonArray("words");
				for (JsonElement element : added) {
					JsonObject word = element.getAsJsonObject();
					String id = word.has("id") ? word.get("id").getAsString() : "";
					if (!id.matches("[a-z][a-z0-9_]*") || !ids.add(id)) {
						problems.add(new Problem("_lexicon.json", id, "a new word needs a new id of a-z, 0-9 and _"));
					}
					if (!word.has("forms") || word.getAsJsonArray("forms").isEmpty()) {
						problems.add(new Problem("_lexicon.json", id, "forms: the stems the word appears with"));
					} else {
						for (JsonElement form : word.getAsJsonArray("forms")) {
							if (!form.getAsString().matches("[가-힣]+")) {
								problems.add(new Problem("_lexicon.json", id, "form \"" + form.getAsString()
										+ "\" is not plain Hangul"));
							} else if (!forms.add(form.getAsString())) {
								problems.add(new Problem("_lexicon.json", id, "form \"" + form.getAsString()
										+ "\" is already in the lexicon"));
							}
						}
					}
					for (String gloss : List.of("en", "de")) {
						if (!word.has(gloss) || word.get(gloss).getAsString().isBlank()) {
							problems.add(new Problem("_lexicon.json", id, "needs an \"" + gloss + "\" gloss"));
						}
					}
					shipped.getAsJsonArray("words").add(word);
				}
			}
			return DialogueLexicon.parse(shipped.toString());
		} catch (IOException | RuntimeException broken) {
			problems.add(new Problem("_lexicon.json", "file", "unreadable: " + broken.getMessage()));
			return DialogueFixtures.lexicon();
		}
	}

	static void write(List<Row> rows, Path target) {
		StringBuilder out = new StringBuilder("id\tlevel\tsource\tko\tgoal\n");
		for (Row row : rows) {
			out.append(clean(row.id())).append('\t').append(row.level()).append('\t').append(clean(row.source()))
					.append('\t').append(clean(row.ko())).append('\t').append(clean(row.goal())).append('\n');
		}
		try {
			Files.createDirectories(target.toAbsolutePath().getParent());
			Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	private static String clean(String value) {
		return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}
