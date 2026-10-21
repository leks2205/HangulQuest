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
			lines.add(node.ko());
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
	void theThreeConversationsHaveTheirOwnTriggers() {
		DialogueTrigger.Click click = new DialogueTrigger.Click(true, true, true, false, false);
		String farmer = "minecraft:farmer";
		String librarian = "minecraft:librarian";
		var set = io.github.leks2205.hangulquest.dialogue.DialogueSet.of(dialogues);
		assertEquals("greetings", pick(set, click, farmer, false));
		assertEquals("library", pick(set, click, librarian, false));
		assertEquals("child", pick(set, click, "minecraft:none", true));
		assertEquals("child", pick(set, click, farmer, true), "a baby never gets an adult conversation");
		assertEquals("", pick(set, click, "minecraft:mason", false), "no conversation for a mason yet");
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
				Speaker.TRADER, "entity.minecraft.wandering_trader", Speaker.GOLEM, "entity.minecraft.iron_golem");
		keys.forEach((speaker, key) -> assertEquals(names.getAsJsonArray(key).get(0).getAsString(), speaker.ko(),
				speaker + " should carry the vanilla name"));
	}

	@Test
	void everySpeakerKeepsItsSpeechLevelInEveryLine() {
		int shown = 0;
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				assertEquals(node.speaker().level(), node.speechLevel(), dialogue.id() + "." + node.id());
				var levels = SpeechLevel.ofLine(node.ko());
				assertTrue(levels.size() <= 1, dialogue.id() + "." + node.id() + " mixes " + levels);
				if (levels.contains(node.speaker().level())) {
					shown++;
				}
			}
		}
		assertTrue(shown >= 8, "too few lines show their speech level in the ending: " + shown);
	}

	@Test
	void theChildTalksBanmalAndTheLibrarianHapsyo() {
		for (Dialogue dialogue : dialogues) {
			for (Dialogue.Node node : dialogue.nodes().values()) {
				if (node.speaker() == Speaker.CHILD) {
					assertEquals(SpeechLevel.BANMAL, node.speechLevel(), node.id());
				}
				if (node.speaker() == Speaker.LIBRARIAN) {
					assertEquals(SpeechLevel.HAPSYO, node.speechLevel(), node.id());
				}
			}
		}
		assertTrue(dialogues.stream().anyMatch(d -> d.nodes().values().stream().anyMatch(n -> n.speaker() == Speaker.CHILD)));
		assertTrue(dialogues.stream().anyMatch(d -> d.nodes().values().stream().anyMatch(n -> n.speaker() == Speaker.LIBRARIAN)));
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
		assertEquals(2, tasks, "one task for the child and one for the librarian");
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
