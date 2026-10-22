package io.github.leks2205.hangulquest.story;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading and checking story files: a bad file is reported and never stops the others. */
class StoryParserTest {
	private static byte[] json(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	private static final String GOOD = """
			{"id": "house_first", "chapter": "house", "level": 1, "titleKo": "나의 첫 집",
			 "unlock": {"trails": ["holzpfad"], "minWords": 8},
			 "paragraphs": ["아침이에요.", "집을 지어요."],
			 "words": ["concept:house"], "grammar": ["L1.eoyo"]}""";

	@Test
	void aGoodFileBecomesAStoryWithItsKeys() {
		StoryParser.Parsed parsed = StoryParser.parse("house", "house_first", json(GOOD));
		assertTrue(parsed.problems().isEmpty(), parsed.problems().toString());
		Story story = parsed.story();
		assertEquals("house", story.chapter());
		assertEquals(1, story.level());
		assertEquals(List.of("holzpfad"), story.unlock().trails());
		assertEquals(8, story.unlock().minWords());
		assertEquals(2, story.paragraphs().size());
		assertEquals("hangulquest.story.house_first.title", story.titleKey());
		assertEquals("hangulquest.story.house_first.p2", story.paragraphKey(1));
		assertEquals(10, story.syllables(), "아침이에요 (5) + 집을 지어요 (5); spaces and the period do not count");
	}

	@Test
	void theIdMustBeTheFileNameAndTheChapterTheFolder() {
		StoryParser.Parsed parsed = StoryParser.parse("field", "other", json(GOOD));
		assertEquals(2, parsed.problems().size(), parsed.problems().toString());
		assertTrue(parsed.problems().get(0).contains("must be the file name"));
		assertTrue(parsed.problems().get(1).contains("must be the folder name"));
	}

	@Test
	void aMissingOrBrokenPartIsAProblemNotAnException() {
		assertNull(StoryParser.parse("house", "x", json("{ nope")).story());
		assertNull(StoryParser.parse("house", "x", json("[1, 2]")).story());
		StoryParser.Parsed bare = StoryParser.parse("house", "x", json("{\"id\": \"x\", \"chapter\": \"house\"}"));
		assertNotNull(bare.story());
		assertTrue(bare.problems().stream().anyMatch(problem -> problem.contains("no paragraphs")));
		assertTrue(bare.problems().stream().anyMatch(problem -> problem.contains("level")));
		assertTrue(bare.problems().stream().anyMatch(problem -> problem.contains("no Korean title")));
	}

	@Test
	void anIdThatWouldBreakALanguageKeyIsRefused() {
		StoryParser.Parsed parsed = StoryParser.parse("house", "House.First",
				json(GOOD.replace("house_first", "House.First")));
		assertTrue(parsed.problems().stream().anyMatch(problem -> problem.contains("may only use")));
	}

	@Test
	void theValidatorChecksChapterTrailsGrammarWordsAndKeys() {
		Story story = StoryParser.parse("house", "house_first", json(GOOD)).story();
		StoryValidator.Env good = new StoryValidator.Env(Set.of("house"), Set.of("holzpfad"), Set.of("L1.eoyo"),
				id -> id.equals("concept:house"),
				key -> key.equals("hangulquest.story.house_first.title") || key.startsWith("hangulquest.story.house_first.p"));
		assertEquals(List.of(), StoryValidator.validate(story, good));

		StoryValidator.Env bad = new StoryValidator.Env(Set.of("field"), Set.of("nachtpfad"), Set.of("L2.seyo"),
				id -> false, key -> false);
		List<String> problems = StoryValidator.validate(story, bad);
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("unknown chapter")));
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("unknown trail holzpfad")));
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("unknown grammar point L1.eoyo")));
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("unknown word concept:house")));
		assertTrue(problems.stream().anyMatch(problem -> problem.contains("missing language key hangulquest.story.house_first.p2")));
	}

	@Test
	void aWordListedTwiceIsAProblem() {
		Story story = StoryParser.parse("house", "house_first",
				json(GOOD.replace("[\"concept:house\"]", "[\"concept:house\", \"concept:house\"]"))).story();
		assertTrue(StoryValidator.validate(story, StoryValidator.Env.none()).stream()
				.anyMatch(problem -> problem.contains("listed twice")));
	}

	private static final String GLOSSED = """
			{"id": "house_first", "chapter": "house", "level": 1, "titleKo": "나의 첫 집",
			 "unlock": {"trails": [], "minWords": 0},
			 "paragraphs": ["집을 지어요."],
			 "words": ["concept:house", "concept:build"], "grammar": [], "glossed": ["concept:build"]}""";

	@Test
	void glossedWordsAreReadAndAnOldFileHasNone() {
		assertEquals(List.of("concept:build"), StoryParser.parse("house", "house_first", json(GLOSSED)).story().glossed());
		assertTrue(StoryParser.parse("house", "house_first", json(GOOD)).story().glossed().isEmpty());
	}

	private static StoryValidator.Env wordsExist() {
		return new StoryValidator.Env(null, null, null, id -> id.startsWith("concept:"), null);
	}

	@Test
	void glossedWordsMustExistBeInTheWordListAndBeAtMostThree() {
		Story base = StoryParser.parse("house", "house_first", json(GLOSSED)).story();
		assertTrue(StoryValidator.validate(base, wordsExist()).isEmpty());

		Story unknown = new Story(base.id(), base.chapter(), base.level(), base.titleKo(), base.unlock(), base.paragraphs(),
				List.of("concept:house", "gone"), base.grammar(), List.of("gone"));
		assertTrue(StoryValidator.validate(unknown, wordsExist()).stream().anyMatch(p -> p.contains("glossed") && p.contains("gone")),
				"a glossed id the catalog does not have");

		Story outside = new Story(base.id(), base.chapter(), base.level(), base.titleKo(), base.unlock(), base.paragraphs(),
				List.of("concept:house"), base.grammar(), List.of("concept:build"));
		assertTrue(StoryValidator.validate(outside, wordsExist()).stream().anyMatch(p -> p.contains("not in the words list")));

		List<String> four = List.of("concept:a", "concept:b", "concept:c", "concept:d");
		Story many = new Story(base.id(), base.chapter(), base.level(), base.titleKo(), base.unlock(), base.paragraphs(),
				four, base.grammar(), four);
		assertTrue(StoryValidator.validate(many, wordsExist()).stream().anyMatch(p -> p.contains("at most 3")));

		Story twice = new Story(base.id(), base.chapter(), base.level(), base.titleKo(), base.unlock(), base.paragraphs(),
				List.of("concept:build"), base.grammar(), List.of("concept:build", "concept:build"));
		assertTrue(StoryValidator.validate(twice, wordsExist()).stream().anyMatch(p -> p.contains("twice")));
	}
}
