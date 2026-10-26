package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.Dialogue;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import io.github.leks2205.hangulquest.dialogue.DialogueParser;
import io.github.leks2205.hangulquest.dialogue.DialogueSet;
import io.github.leks2205.hangulquest.dialogue.DialogueValidator;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import io.github.leks2205.hangulquest.dialogue.Speaker;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.villager.ConversationSelector;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestFixtures;
import io.github.leks2205.hangulquest.villager.VillagerProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The shipped villager conversations: graph, trails, Korean, speech levels, glosses and coverage. */
class DialogueContentTest {
	private static final String VILLAGER_TYPE = "minecraft:villager";

	/**
	 * The six profession conversations of every personality. The file suffix is the number in
	 * {@code vt_<personality>_gen_11} ... {@code _16}; the smith file serves both smith professions.
	 */
	private static final Map<Integer, List<String>> PROFESSION_FILES = Map.of(
			11, List.of("minecraft:farmer"),
			12, List.of("minecraft:librarian"),
			13, List.of("minecraft:fisherman"),
			14, List.of("minecraft:weaponsmith", "minecraft:toolsmith"),
			15, List.of("minecraft:cartographer"),
			16, List.of("minecraft:cleric"));

	/**
	 * Glosses of the nodes the previous lazy plains introduction had. Its replacement uses different node ids;
	 * the language files still carry these six keys, so the test allows exactly them until their owner removes
	 * them. Every other key without a line is still an error.
	 */
	private static final Set<String> RETIRED_INTRO_KEYS = Set.of(
);

	private final Catalog catalog = KoreanFixtures.catalog();
	private final List<Dialogue> dialogues = DialogueFixtures.shipped();
	private final DialogueSet set = DialogueSet.of(dialogues);
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

	/** What the villager says for this personality, profession and age, with nothing else competing for the click. */
	private Dialogue talk(Personality personality, String profession, boolean baby, int playerLevel) {
		VillagerProfile profile = new VillagerProfile(
				new UUID(personality.ordinal() + 1L, profession == null ? 0L : profession.hashCode()).toString(),
				"테스트", personality.gender(), personality, personality.voice());
		profile.introduced = true;
		profile.baby = baby;
		ConversationSelector.Situation situation = new ConversationSelector.Situation(profile, VILLAGER_TYPE,
				profession, baby, playerLevel, 1, null, false, false, null);
		ConversationSelector.Choice choice = ConversationSelector.select(set, situation)
				.orElseThrow(() -> new AssertionError("no conversation for " + personality + " / " + profession));
		assertInstanceOf(ConversationSelector.Talk.class, choice);
		return ((ConversationSelector.Talk) choice).dialogue();
	}

	@Test
	void everyProfessionHasItsOwnConversation() {
		for (Personality personality : Personality.values()) {
			for (Map.Entry<Integer, List<String>> file : PROFESSION_FILES.entrySet()) {
				String expected = "vt_" + personality.id() + "_gen_" + file.getKey();
				for (String profession : file.getValue()) {
					assertEquals(expected, talk(personality, profession, false, 6).id(),
							personality + " " + profession);
				}
			}
		}
	}

	@Test
	void aBabyGetsABabyConversationAndNeverAnAdultOne() {
		for (Personality personality : Personality.values()) {
			assertEquals("vt_" + personality.id() + "_gen_91",
					talk(personality, "minecraft:farmer", true, 1).id(),
					personality + " should start with its first baby talk");
			Dialogue later = talk(personality, "minecraft:farmer", true, 2);
			assertTrue(Set.of("vt_" + personality.id() + "_gen_91", "vt_" + personality.id() + "_gen_92")
					.contains(later.id()), personality + " got " + later.id() + " instead of a baby talk");
			assertTrue(Boolean.TRUE.equals(later.trigger().baby()), later.id());
		}
	}

	@Test
	void aConversationForOnePersonalityIsNeverPickedForAnother() {
		for (Dialogue dialogue : dialogues) {
			if (dialogue.trigger().personalities().isEmpty()) {
				continue;
			}
			String profession = dialogue.trigger().profession();
			if (profession == null && !dialogue.trigger().professions().isEmpty()) {
				profession = dialogue.trigger().professions().get(0);
			}
			boolean baby = Boolean.TRUE.equals(dialogue.trigger().baby());
			for (Personality personality : Personality.values()) {
				if (dialogue.trigger().personalities().contains(personality)) {
					continue;
				}
				Dialogue chosen = talk(personality, profession, baby, 6);
				assertNotEquals(dialogue.id(), chosen.id(),
						dialogue.id() + " was picked for " + personality);
				assertTrue(chosen.trigger().allows(personality),
						chosen.id() + " is not written for " + personality);
			}
		}
	}

	@Test
	void theProfessionConversationsAreShortAndWithinTheirLevels() {
		int checked = 0;
		for (Dialogue dialogue : dialogues) {
			if (!dialogue.trigger().namesProfession() || Boolean.TRUE.equals(dialogue.trigger().baby())) {
				continue;
			}
			checked++;
			// The personality profession talks are longer than the old seven: five to eight nodes.
			assertTrue(dialogue.nodes().size() >= 3 && dialogue.nodes().size() <= 8,
					dialogue.id() + " has " + dialogue.nodes().size() + " nodes");
			assertTrue(dialogue.level() >= 1 && dialogue.level() <= 3, dialogue.id() + " level " + dialogue.level());
			assertTrue(dialogue.nodes().values().stream().anyMatch(node -> node.choices().size() >= 2),
					dialogue.id() + " has no choice");
		}
		// Every personality/family pair exists once per region set: the standard villages, the taiga (Busan) and
		// the savanna (Jeju) dialect versions.
		assertEquals(Personality.values().length * PROFESSION_FILES.size() * 3, checked,
				"one profession conversation per personality, profession family and region set");
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
	void childrenTalkBanmalAndAdultVillagersKeepTheirPersonalityRegister() {
		int children = 0;
		int adults = 0;
		for (Dialogue dialogue : dialogues) {
			boolean baby = Boolean.TRUE.equals(dialogue.trigger().baby());
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (node.speaker() == Speaker.CHILD) {
					children++;
					assertTrue(baby, dialogue.id() + "." + node.id() + " is a child in an adult conversation");
					assertEquals(SpeechLevel.BANMAL, node.speechLevel(), dialogue.id() + "." + node.id());
				} else if (node.speaker() == Speaker.VILLAGER) {
					adults++;
					assertFalse(baby, dialogue.id() + "." + node.id() + " is an adult in a baby conversation");
					assertEquals(DialogueValidator.expectedLevel(dialogue, node), node.speechLevel(),
							dialogue.id() + "." + node.id());
				}
			}
		}
		assertTrue(children > 0, "no child speaks in the shipped conversations");
		assertTrue(adults > 0, "no adult villager speaks in the shipped conversations");
	}

	@Test
	void theSpeechLevelTasksPickTheLevelThatFitsTheSpeaker() {
		int banmalTasks = 0;
		int politeTasks = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (node.grammar() == null) {
					continue;
				}
				List<Dialogue.Choice> right = node.choices().stream()
						.filter(c -> Boolean.TRUE.equals(c.correct())).toList();
				assertFalse(right.isEmpty(), node.id() + " needs at least one right form");
				switch (node.grammar()) {
					case "L6.banmal" -> {
						banmalTasks++;
						assertEquals(Speaker.CHILD, node.speaker(), node.id());
						assertTrue(right.stream().allMatch(c -> c.speechLevel() == SpeechLevel.BANMAL), node.id());
						assertTrue(node.choices().stream().filter(Dialogue.Choice::wrong)
								.allMatch(c -> c.speechLevel() != SpeechLevel.BANMAL), node.id());
					}
					case "L1.eoyo" -> {
						politeTasks++;
						assertEquals(Speaker.VILLAGER, node.speaker(), node.id());
						assertTrue(right.stream().allMatch(c -> c.speechLevel() == SpeechLevel.HAEYO), node.id());
						assertTrue(node.choices().stream().filter(Dialogue.Choice::wrong)
								.allMatch(c -> c.speechLevel() != SpeechLevel.HAEYO), node.id());
					}
					default -> fail(dialogue.id() + "." + node.id() + " has unexpected grammar " + node.grammar());
				}
			}
		}
		assertTrue(banmalTasks > 0, "no 반말 task for the children");
		assertTrue(politeTasks > 0, "no 해요체 task for an adult villager");
	}

	@Test
	void theAdultPoliteTaskGradesTheAnswerAgainstCasual() {
		int checked = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (!"L1.eoyo".equals(node.grammar())) {
					continue;
				}
				checked++;
				assertEquals(Speaker.VILLAGER, node.speaker(), node.id());
				assertEquals(Set.of(SpeechLevel.HAEYO), node.choices().stream()
						.filter(c -> Boolean.TRUE.equals(c.correct()))
						.map(Dialogue.Choice::speechLevel).collect(Collectors.toSet()), node.id());
				assertEquals(Set.of(SpeechLevel.BANMAL), node.choices().stream()
						.filter(Dialogue.Choice::wrong)
						.map(Dialogue.Choice::speechLevel).collect(Collectors.toSet()), node.id());
			}
		}
		assertTrue(checked > 0, "the adult librarian conversations lost their polite task");
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
			if (key.startsWith(Dialogue.KEY_PREFIX) && !known.contains(key) && !RETIRED_INTRO_KEYS.contains(key)) {
				stray.add(key);
			}
		}
		assertTrue(stray.isEmpty(), "language keys without a line: " + stray);
	}

	@Test
	void everyLineHasAtMostOneWordThePlayerDoesNotKnow() {
		DialogueLexicon lexicon = DialogueFixtures.lexicon();
		DialogueCoverage.Vocabulary base = DialogueCoverage.vocabulary(catalog, lexicon, earlyWords, Lang.EN);
		DialogueCoverage.Vocabulary vocabulary = base;
		List<String> broken = new ArrayList<>();
		int checked = 0;
		for (Dialogue dialogue : dialogues) {
			// The short profession and baby talks keep the strict one-new-word rule. The longer personality
			// smalltalk written for levels 1 to 6 may carry two new words in a line.
			boolean teachingTalk = dialogue.trigger().namesProfession()
					|| Boolean.TRUE.equals(dialogue.trigger().baby());
			int allowed = DialogueCoverage.MAX_UNKNOWN;
			for (String line : koreanLines(dialogue)) {
				List<DialogueCoverage.Token> tokens = DialogueCoverage.analyze(line, vocabulary);
				checked++;
				for (DialogueCoverage.Token token : tokens) {
					if (token.state() == DialogueCoverage.State.UNRESOLVED) {
						broken.add(dialogue.id() + ": '" + token.text() + "' in \"" + line + "\" matches no word");
					}
				}
				if (DialogueCoverage.unknownWords(tokens) > allowed) {
					broken.add(dialogue.id() + ": \"" + line + "\" has " + DialogueCoverage.unknownWords(tokens)
							+ " new words, allowed " + allowed);
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
	void everyLineStaysWithinTheGrammarOfItsLevel() {
		List<String> problems = new ArrayList<>();
		for (Dialogue dialogue : dialogues) {
			for (String line : koreanLines(dialogue)) {
				problems.addAll(io.github.leks2205.hangulquest.dialogue.LineLevels.problems(dialogue.id(), line,
						dialogue.level()));
			}
		}
		for (Request request : RequestFixtures.shipped()) {
			for (RequestFixtures.Spoken line : RequestFixtures.lines(request)) {
				problems.addAll(io.github.leks2205.hangulquest.dialogue.LineLevels.problems(request.id() + "." + line.node(),
						line.ko(), request.level()));
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
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
