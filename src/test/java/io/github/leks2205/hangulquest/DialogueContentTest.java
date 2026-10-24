package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import io.github.leks2205.hangulquest.dialogue.DialogueParser;
import io.github.leks2205.hangulquest.dialogue.DialogueTrigger;
import io.github.leks2205.hangulquest.dialogue.DialogueValidator;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import io.github.leks2205.hangulquest.dialogue.Speaker;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped villager conversations: graph, trails, Korean, speech levels, glosses and coverage. */
class DialogueContentTest {
	private final Catalog catalog = KoreanFixtures.catalog();
	private final List<Dialogue> dialogues = DialogueFixtures.shipped();
	private final Map<String, String> english = DialogueFixtures.lang("en_us");
	private final Map<String, String> german = DialogueFixtures.lang("de_de");

	/** What a player who has done the first two levels knows: the curated words of tiers 1 and 2. */
	private final Predicate<String> earlyWords = id -> catalog.byId(id)
			.filter(word -> word.conceptId != null && word.tier <= 2).isPresent();

	private List<String> koreanLines(Dialogue dialogue) {
		List<String> lines = new ArrayList<>();
		for (Dialogue.Node node : dialogue.nodes().values()) {
			if (!node.selfIntro()) {
				lines.add(node.ko());
			}
			if (node.koBanmal() != null) {
				lines.add(node.koBanmal());
			}
			node.choices().forEach(choice -> lines.add(choice.ko()));
		}
		return lines;
	}

	@Test
	void everyShippedDialogueParsesAndPassesTheGameChecks() {
		assertTrue(dialogues.size() >= 3, "expected the farmer, child and librarian dialogues");
		DialogueValidator.Env env = DialogueFixtures.env(catalog);
		List<String> problems = new ArrayList<>();
		for (Dialogue dialogue : dialogues) {
			problems.addAll(DialogueValidator.validate(dialogue, env));
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyProfessionHasItsOwnConversation() {
		DialogueTrigger.Click click = new DialogueTrigger.Click(true, true, true, false, false);
		var set = io.github.leks2205.hangulquest.dialogue.DialogueSet.of(dialogues);
		assertEquals("library", pick(set, click, "minecraft:librarian", false));
		assertEquals("fishing", pick(set, click, "minecraft:fisherman", false));
		assertEquals("smithy", pick(set, click, "minecraft:weaponsmith", false));
		assertEquals("smithy", pick(set, click, "minecraft:toolsmith", false));
		assertEquals("maps", pick(set, click, "minecraft:cartographer", false));
		assertEquals("cleric", pick(set, click, "minecraft:cleric", false));
		assertEquals("elder", pick(set, click, "minecraft:none", false));
		assertEquals("child", pick(set, click, "minecraft:none", true));
		assertEquals("child", pick(set, click, "minecraft:farmer", true), "a baby never gets an adult conversation");
		assertEquals("", pick(set, click, "minecraft:mason", false), "no conversation for a mason yet");
		assertEquals("", pick(set, click, "minecraft:armorer", false), "the armorer is not a smith");
	}

	@Test
	void farmersTalkEitherOfTheirTwoConversationsAndKeepTheirOwn() {
		DialogueTrigger.Click click = new DialogueTrigger.Click(true, true, true, false, false);
		var set = io.github.leks2205.hangulquest.dialogue.DialogueSet.of(dialogues);
		java.util.Set<String> seen = new java.util.TreeSet<>();
		for (int seed = 0; seed < 6; seed++) {
			String id = DialogueTrigger.select(set, click,
					new DialogueTrigger.Target("minecraft:villager", "minecraft:farmer", false, seed)).orElseThrow().id();
			seen.add(id);
			assertEquals(id, DialogueTrigger.select(set, click,
					new DialogueTrigger.Target("minecraft:villager", "minecraft:farmer", false, seed)).orElseThrow().id());
		}
		assertEquals(java.util.Set.of("farm", "greetings"), seen);
	}

	@Test
	void theProfessionConversationsAreShortAndWithinTheirLevels() {
		java.util.Set<String> professionTalks = java.util.Set.of("farm", "fishing", "smithy", "maps", "cleric", "library",
				"elder");
		int checked = 0;
		for (Dialogue dialogue : dialogues) {
			if (!professionTalks.contains(dialogue.id())) {
				continue;
			}
			checked++;
			assertTrue(dialogue.nodes().size() >= 3 && dialogue.nodes().size() <= 6,
					dialogue.id() + " has " + dialogue.nodes().size() + " nodes");
			assertTrue(dialogue.level() >= 1 && dialogue.level() <= 3, dialogue.id() + " level " + dialogue.level());
			assertTrue(dialogue.nodes().values().stream().anyMatch(node -> node.choices().size() >= 2),
					dialogue.id() + " has no choice");
		}
		assertEquals(professionTalks.size(), checked);
	}

	private static String pick(io.github.leks2205.hangulquest.dialogue.DialogueSet set, DialogueTrigger.Click click,
							   String profession, boolean baby) {
		Optional<Dialogue> found = DialogueTrigger.select(set, click,
				new DialogueTrigger.Target("minecraft:villager", profession, baby));
		return found.map(Dialogue::id).orElse("");
	}

	@Test
	void speakerNamesAreTheVanillaKoreanNames() throws IOException {
		JsonObject names = JsonParser.parseString(Files.readString(
				Path.of("src/main/resources/assets/hangulquest/data/names.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		Map<Speaker, String> keys = Map.of(
				Speaker.VILLAGER, "entity.minecraft.villager", Speaker.FARMER, "entity.minecraft.villager.farmer",
				Speaker.LIBRARIAN, "entity.minecraft.villager.librarian",
				Speaker.FISHERMAN, "entity.minecraft.villager.fisherman",
				Speaker.CARTOGRAPHER, "entity.minecraft.villager.cartographer",
				Speaker.CLERIC, "entity.minecraft.villager.cleric",
				Speaker.TRADER, "entity.minecraft.wandering_trader", Speaker.GOLEM, "entity.minecraft.iron_golem");
		keys.forEach((speaker, key) -> assertEquals(names.getAsJsonArray(key).get(0).getAsString(), speaker.ko(),
				speaker + " should carry the vanilla name"));
	}

	@Test
	void everySpeakerKeepsItsSpeechLevelInEveryLine() {
		int shown = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				assertEquals(DialogueValidator.expectedLevel(dialogue, node), node.speechLevel(),
						dialogue.id() + "." + node.id());
				var levels = SpeechLevel.ofLine(node.ko());
				assertTrue(levels.size() <= 1, dialogue.id() + "." + node.id() + " mixes " + levels);
				if (levels.contains(node.speechLevel())) {
					shown++;
				}
			}
		}
		assertTrue(shown >= 8, "too few lines show their speech level in the ending: " + shown);
	}

	@Test
	void theChildTalksBanmalAndTheLibrarianAndTheElderHapsyo() {
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (node.speaker() == Speaker.CHILD) {
					assertEquals(SpeechLevel.BANMAL, node.speechLevel(), node.id());
				}
				if (node.speaker() == Speaker.LIBRARIAN || node.speaker() == Speaker.ELDER) {
					assertEquals(SpeechLevel.HAPSYO, node.speechLevel(), node.id());
				}
			}
		}
		assertTrue(dialogues.stream().anyMatch(d -> d.nodes().values().stream().anyMatch(n -> n.speaker() == Speaker.CHILD)));
		assertTrue(dialogues.stream().anyMatch(d -> d.nodes().values().stream().anyMatch(n -> n.speaker() == Speaker.LIBRARIAN)));
		assertTrue(dialogues.stream().anyMatch(d -> d.nodes().values().stream().anyMatch(n -> n.speaker() == Speaker.ELDER)));
	}

	@Test
	void theSpeechLevelTasksPickTheLevelThatFitsTheListener() {
		int tasks = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (node.grammar() == null) {
					continue;
				}
				tasks++;
				List<Dialogue.Choice> right = node.choices().stream().filter(c -> Boolean.TRUE.equals(c.correct())).toList();
				assertEquals(1, right.size(), node.id() + " needs exactly one right form");
				SpeechLevel wanted = node.speaker() == Speaker.CHILD ? SpeechLevel.BANMAL : SpeechLevel.HAPSYO;
				assertEquals(wanted, right.get(0).speechLevel(), node.id());
				assertTrue(node.choices().stream().anyMatch(c -> c.wrong() && c.speechLevel() != wanted), node.id());
				assertEquals(node.speaker() == Speaker.CHILD ? "L6.banmal" : "L5.hapsyo", node.grammar());
			}
		}
		assertEquals(4, tasks, "two tasks for the child, one for the librarian and one for the elder");
	}

	@Test
	void theElderTaskGradesTheFormalAnswerAgainstPoliteAndCasual() {
		Dialogue elder = dialogues.stream().filter(d -> d.id().equals("elder")).findFirst().orElseThrow();
		Dialogue.Node ask = elder.nodes().get("ask");
		assertEquals(Speaker.ELDER, ask.speaker());
		assertEquals("L5.hapsyo", ask.grammar());
		assertEquals(java.util.Set.of(SpeechLevel.HAEYO, SpeechLevel.BANMAL), ask.choices().stream()
				.filter(Dialogue.Choice::wrong).map(Dialogue.Choice::speechLevel).collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void everySpeakerHasANameInEveryLanguage() {
		for (String name : List.of("en_us", "de_de", "fr_fr", "es_es")) {
			Map<String, String> table = DialogueFixtures.lang(name);
			for (Speaker speaker : Speaker.values()) {
				assertTrue(!table.getOrDefault("hangulquest.ui.dialogue.speaker." + speaker.id(), "").isBlank(),
						name + " lacks the name of " + speaker.id());
			}
		}
	}

	@Test
	void everyDialogueLineIsInTheReviewRegistry() throws IOException {
		Map<String, String> registered = new java.util.HashMap<>();
		for (String line : Files.readAllLines(Path.of("content/korean/lines.jsonl"), StandardCharsets.UTF_8)) {
			JsonObject record = JsonParser.parseString(line).getAsJsonObject();
			String id = record.get("id").getAsString();
			if (id.startsWith("dialogue.")) {
				registered.put(id, record.get("ko").getAsString());
			}
		}
		List<String> wrong = new ArrayList<>();
		int expected = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				String id = "dialogue." + dialogue.id() + "." + node.id();
				if (!node.selfIntro()) {
					expected++;
					if (!node.ko().equals(registered.get(id))) {
						wrong.add(id);
					}
				}
				if (node.koBanmal() != null) {
					expected++;
					if (!node.koBanmal().equals(registered.get(id + ".banmal"))) {
						wrong.add(id + ".banmal");
					}
				}
				for (int i = 0; i < node.choices().size(); i++) {
					expected++;
					if (!node.choices().get(i).ko().equals(registered.get(id + ".c" + (i + 1)))) {
						wrong.add(id + ".c" + (i + 1));
					}
				}
			}
		}
		for (Request request : RequestFixtures.shipped()) {
			for (RequestFixtures.Spoken line : RequestFixtures.lines(request)) {
				expected++;
				String id = "dialogue." + request.id() + "." + line.node();
				if (!line.ko().equals(registered.get(id))) {
					wrong.add(id);
				}
			}
		}
		assertTrue(wrong.isEmpty(), "not in content/korean/lines.jsonl (run tools/review/export_lines.py): " + wrong);
		assertEquals(expected, registered.size(), "the registry holds dialogue lines no file has");
	}

	@Test
	void everyLineHasAnEnglishAndAGermanGloss() {
		List<String> missing = new ArrayList<>();
		for (Dialogue dialogue : dialogues) {
			List<String> keys = new ArrayList<>();
			for (Dialogue.Node node : dialogue.nodes().values()) {
				keys.add(dialogue.lineKey(node.id()));
				if (node.typed() != null) {
					keys.add(dialogue.promptKey(node.id()));
				}
				for (int i = 0; i < node.choices().size(); i++) {
					keys.add(dialogue.choiceKey(node.id(), i));
					if (node.grammar() != null) {
						keys.add(dialogue.choiceNoteKey(node.id(), i));
					}
				}
			}
			for (String key : keys) {
				for (Map<String, String> table : List.of(english, german)) {
					if (table.getOrDefault(key, "").isBlank()) {
						missing.add(key + (table == english ? " (en)" : " (de)"));
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "missing glosses: " + missing);
	}

	@Test
	void everyDialogueLanguageKeyBelongsToALine() {
		List<String> stray = new ArrayList<>();
		List<String> known = new ArrayList<>();
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				known.add(dialogue.lineKey(node.id()));
				known.add(dialogue.promptKey(node.id()));
				for (int i = 0; i < node.choices().size(); i++) {
					known.add(dialogue.choiceKey(node.id(), i));
					known.add(dialogue.choiceNoteKey(node.id(), i));
				}
			}
		}
		for (Request request : RequestFixtures.shipped()) {
			for (RequestFixtures.Spoken line : RequestFixtures.lines(request)) {
				known.add(Dialogue.KEY_PREFIX + request.id() + "." + line.node().replace(".banmal", ""));
			}
		}
		for (String key : english.keySet()) {
			if (key.startsWith(Dialogue.KEY_PREFIX) && !known.contains(key)) {
				stray.add(key);
			}
		}
		assertTrue(stray.isEmpty(), "language keys without a line: " + stray);
	}

	@Test
	void everyLineHasAtMostOneWordThePlayerDoesNotKnow() {
		DialogueLexicon lexicon = DialogueFixtures.lexicon();
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(catalog, lexicon, earlyWords, Lang.EN);
		List<String> broken = new ArrayList<>();
		int checked = 0;
		for (Dialogue dialogue : dialogues) {
			for (String line : koreanLines(dialogue)) {
				List<DialogueCoverage.Token> tokens = DialogueCoverage.analyze(line, vocabulary);
				checked++;
				for (DialogueCoverage.Token token : tokens) {
					if (token.state() == DialogueCoverage.State.UNRESOLVED) {
						broken.add(dialogue.id() + ": '" + token.text() + "' in \"" + line + "\" matches no word");
					}
				}
				if (!DialogueCoverage.meetsRule(tokens)) {
					broken.add(dialogue.id() + ": \"" + line + "\" has " + DialogueCoverage.unknownWords(tokens)
							+ " new words");
				}
			}
		}
		assertTrue(checked >= 18, "checked only " + checked + " lines");
		assertTrue(broken.isEmpty(), String.join("\n", broken));
	}

	@Test
	void everyNewWordCanBeExplainedOnHoverInBothLanguages() {
		DialogueLexicon lexicon = DialogueFixtures.lexicon();
		int unknown = 0;
		for (Lang lang : List.of(Lang.EN, Lang.DE)) {
			DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(catalog, lexicon, earlyWords, lang);
			for (Dialogue dialogue : dialogues) {
				for (String line : koreanLines(dialogue)) {
					for (DialogueCoverage.Token token : DialogueCoverage.analyze(line, vocabulary)) {
						if (token.state() == DialogueCoverage.State.UNKNOWN) {
							unknown++;
							assertTrue(token.gloss() != null && !token.gloss().isBlank(), token.text() + " has no gloss");
						}
					}
				}
			}
		}
		assertTrue(unknown >= 8, "expected a few new words to gloss, got " + unknown);
	}

	@Test
	void everyKoreanLineIsPlainHangulWithPunctuation() {
		for (Dialogue dialogue : dialogues) {
			for (String line : koreanLines(dialogue)) {
				assertTrue(line.matches("[가-힣 ,.!?]+"), "unexpected character in \"" + line + "\"");
				assertTrue(line.endsWith(".") || line.endsWith("!") || line.endsWith("?"), line);
			}
		}
	}

	@Test
	void theParserRejectsAFileWhoseIdIsNotItsName() {
		DialogueParser.Parsed parsed = DialogueParser.parse("other", "{\"id\":\"greetings\",\"nodes\":{}}"
				.getBytes(StandardCharsets.UTF_8));
		assertTrue(parsed.problems().stream().anyMatch(problem -> problem.contains("file name")), parsed.problems().toString());
	}
}
