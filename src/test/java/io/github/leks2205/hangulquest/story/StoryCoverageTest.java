package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 98% rule: when a story opens for this player, and what keeps it closed. */
class StoryCoverageTest {
	private static Concept concept(String id, String ko, String en) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.en = en;
		concept.de = en;
		concept.category = "BLOCKS";
		return concept;
	}

	private static final Catalog CATALOG = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stone"),
			concept("torch", "횃불", "Torch"), concept("door", "문", "Door")));

	private static StoryVocabulary vocabulary(Set<String> known) {
		return StoryVocabulary.of(CATALOG, StoryLexicon.EMPTY, known::contains, Lang.EN);
	}

	private static Story story(int minWords, String... paragraphs) {
		return new Story("test", "house", 1, "시험", new Story.Unlock(List.of("holzpfad", "nachtpfad"), minWords),
				List.of(paragraphs), List.of(), List.of());
	}

	/** A paragraph of {@code known} known words and {@code unknown} unknown ones. */
	private static String text(int known, int unknown) {
		List<String> words = new ArrayList<>();
		for (int i = 0; i < known; i++) {
			words.add("돌을");
		}
		for (int i = 0; i < unknown; i++) {
			words.add("횃불을");
		}
		return String.join(" ", words);
	}

	@Test
	void theThresholdIsExactlyNinetyEightPercentOfTheRunningWords() {
		assertTrue(new StoryCoverage.Coverage(49, 50).meets(), "49 of 50 is 98%");
		assertFalse(new StoryCoverage.Coverage(48, 50).meets());
		assertTrue(new StoryCoverage.Coverage(98, 100).meets());
		assertFalse(new StoryCoverage.Coverage(97, 100).meets());
		assertFalse(new StoryCoverage.Coverage(97, 99).meets(), "97 of 99 is 97.98%, not 98%");
		assertTrue(new StoryCoverage.Coverage(50, 50).meets());
	}

	@Test
	void thePercentIsRoundedDownSoNinetySevenPointNineNeverShowsAsNinetyEight() {
		assertEquals(97, new StoryCoverage.Coverage(97, 99).percent());
		assertEquals(97, new StoryCoverage.Coverage(48, 49).percent());
		assertEquals(98, new StoryCoverage.Coverage(49, 50).percent());
		assertEquals(100, new StoryCoverage.Coverage(7, 7).percent());
		assertEquals(0, new StoryCoverage.Coverage(0, 12).percent());
	}

	@Test
	void anEmptyTextIsCovered() {
		assertTrue(new StoryCoverage.Coverage(0, 0).meets());
		assertEquals(100, new StoryCoverage.Coverage(0, 0).percent());
	}

	@Test
	void coverageCountsEveryRunningWordNotEveryDistinctOne() {
		// "돌을" ten times and one unknown 횃불을: the repeated known word weighs ten times
		StoryCoverage.Status status = StoryCoverage.status(story(0, text(10, 1)), vocabulary(Set.of("concept:stone")),
				trail -> true, 99);
		assertEquals(10, status.coverage().known());
		assertEquals(11, status.coverage().total());
		assertEquals(90, status.coverage().percent());
		assertFalse(status.open());
	}

	@Test
	void oneUnknownWordInFiftyKeepsAStoryOpenAndInTwentyFiveItDoesNot() {
		Set<String> stone = Set.of("concept:stone");
		assertTrue(StoryCoverage.status(story(0, text(49, 1)), vocabulary(stone), trail -> true, 0).open());
		assertFalse(StoryCoverage.status(story(0, text(24, 1)), vocabulary(stone), trail -> true, 0).open());
	}

	@Test
	void theSameStoryOpensOrNotDependingOnWhatThePlayerKnows() {
		Story story = story(0, text(10, 10));
		assertFalse(StoryCoverage.status(story, vocabulary(Set.of()), trail -> true, 0).open());
		assertFalse(StoryCoverage.status(story, vocabulary(Set.of("concept:stone")), trail -> true, 0).open());
		assertTrue(StoryCoverage.status(story, vocabulary(Set.of("concept:stone", "concept:torch")), trail -> true, 0).open());
	}

	@Test
	void anEojeolThatMatchesNothingCountsAsUnknown() {
		StoryCoverage.Status status = StoryCoverage.status(story(0, text(49, 0) + " 용암을"), vocabulary(Set.of("concept:stone")),
				trail -> true, 0);
		assertEquals(49, status.coverage().known());
		assertEquals(50, status.coverage().total());
		assertEquals(1, status.newWords().size());
		assertEquals("용암을", status.newWords().get(0).text());
	}

	@Test
	void aStoryNeedsItsTrailsAndEnoughWordsAsWellAsTheCoverage() {
		Story story = story(10, text(5, 0));
		StoryVocabulary known = vocabulary(Set.of("concept:stone"));
		assertTrue(StoryCoverage.status(story, known, trail -> true, 10).open());

		StoryCoverage.Status oneTrailMissing = StoryCoverage.status(story, known, trail -> trail.equals("holzpfad"), 10);
		assertFalse(oneTrailMissing.open());
		assertEquals(List.of("nachtpfad"), oneTrailMissing.missingTrails());
		assertTrue(oneTrailMissing.coverage().meets(), "the coverage is not what blocks it");

		StoryCoverage.Status fewWords = StoryCoverage.status(story, known, trail -> true, 9);
		assertFalse(fewWords.open());
		assertFalse(fewWords.wordsEnough());
		assertEquals(9, fewWords.wordsMet());
	}

	@Test
	void theWordsThatKeepAStoryClosedAreListedOnceInReadingOrder() {
		Story story = story(0, "횃불을 돌을 횃불이 문을", "문이 돌이");
		StoryCoverage.Status status = StoryCoverage.status(story, vocabulary(Set.of("concept:stone")), trail -> true, 0);
		assertEquals(List.of("concept:torch", "concept:door"), status.newWords().stream().map(StoryText.Token::id).toList());
	}

	@Test
	void everyParagraphIsAnalysedForTheReader() {
		StoryCoverage.Status status = StoryCoverage.status(story(0, "돌을 놓아요.", "횃불이 있어요."), vocabulary(Set.of()),
				trail -> true, 0);
		assertEquals(2, status.paragraphs().size());
		assertEquals(2, status.paragraphs().get(0).size());
	}
}
