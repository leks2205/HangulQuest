package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a real quiz answer may move the schedule; encounters and practice only count. */
class LearningServiceTest {
	private static final String STONE = "concept:stone";
	private static final long T0 = 1_000_000L;

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LoggerFactory.getLogger("learning-test"));
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		learn = new LearningService(store.data(), store, catalog);
	}

	private WordProgress progress() {
		return learn.data().words.get(STONE);
	}

	@Test
	void exposeIntroducesAWordWithoutScheduling() {
		learn.expose(catalog.byId(STONE).orElseThrow(), T0);

		WordProgress progress = progress();
		assertNotNull(progress);
		assertEquals(0, progress.level);
		assertEquals(T0, progress.dueAt, "a new word is due at once so it can be recalled later");
		assertEquals(1, progress.seen);
		assertEquals(0, progress.correct);
		assertEquals(T0, progress.lastSeenAt);
		assertEquals(1, learn.todaySeen());
		assertEquals(0, learn.totalReviews());
	}

	@Test
	void exposeNeverChangesLevelDueOrKnown() {
		learn.answer(STONE, true, T0);
		learn.answer(STONE, true, T0);
		WordProgress progress = progress();
		int level = progress.level;
		long dueAt = progress.dueAt;
		CardState card = progress.fsrs.copy();

		for (int i = 0; i < 5; i++) {
			learn.expose(catalog.byId(STONE).orElseThrow(), T0 + 1000L * (i + 1));
		}

		assertEquals(level, progress.level);
		assertEquals(dueAt, progress.dueAt);
		assertEquals(card, progress.fsrs, "encounters leave the FSRS card alone");
		assertEquals(7, progress.seen);
		assertEquals(2, progress.correct);
		assertEquals(2, learn.totalReviews());
		assertEquals(T0 + 5000L, progress.lastSeenAt);
	}

	@Test
	void practiceOnlyCounts() {
		learn.answer(STONE, true, T0);
		WordProgress progress = progress();
		int level = progress.level;
		long dueAt = progress.dueAt;

		learn.practice(STONE, true, T0 + 10);
		learn.practice(STONE, false, T0 + 20);

		assertEquals(level, progress.level);
		assertEquals(dueAt, progress.dueAt);
		assertEquals(3, progress.seen);
		assertEquals(2, progress.correct);
		assertEquals(1, progress.wrong);
		assertEquals(1, learn.totalReviews(), "practice is not a review");
	}

	@Test
	void practiceOnAnUnknownWordIntroducesItAtLevelZero() {
		learn.practice(STONE, true, T0);

		assertEquals(0, progress().level);
		assertEquals(T0, progress().dueAt);
	}

	@Test
	void answerNeverClearsKnown() {
		learn.markKnown(STONE, true, T0);
		assertTrue(learn.isKnown(STONE));

		learn.answer(STONE, true, T0 + 1);
		assertTrue(learn.isKnown(STONE), "a correct answer keeps the word known");
		learn.answer(STONE, false, T0 + 2);
		assertTrue(learn.isKnown(STONE), "a wrong answer keeps the word known");

		learn.markKnown(STONE, false, T0 + 3);
		assertFalse(learn.isKnown(STONE), "only markKnown clears it");
	}

	@Test
	void answerMovesTheSchedule() {
		learn.answer(STONE, true, T0);
		assertEquals(CardState.State.LEARNING, progress().fsrs.state);
		assertEquals(T0 + 10 * Srs.MINUTE, progress().dueAt, "the second learning step is ten minutes away");
		assertEquals(progress().fsrs.due, progress().dueAt, "the old field mirrors the card");
		assertEquals(Srs.levelFor(progress().fsrs.stability), progress().level);

		learn.answer(STONE, false, T0 + 5);
		assertEquals(CardState.State.LEARNING, progress().fsrs.state);
		assertEquals(0, progress().fsrs.step, "a miss goes back to the first step");
		assertEquals(T0 + 5 + Srs.MINUTE, progress().dueAt);
		assertEquals(2, learn.totalReviews());
	}

	@Test
	void theThreeDayCapIsGone() {
		long t = T0;
		for (int i = 0; i < 8; i++) {
			learn.answer(STONE, true, t);
			t = progress().dueAt;
		}
		assertTrue(progress().fsrs.due - progress().fsrs.lastReview > 30 * Srs.DAY);
		assertTrue(progress().fsrs.due - progress().fsrs.lastReview <= 365 * Srs.DAY, "capped at a year");
	}

	@Test
	void masteredMeansMarkedKnownOrDurablyKnown() {
		learn.answer(STONE, true, T0);
		learn.answer(STONE, true, T0 + 1);
		assertEquals(0, learn.mastered(), "a couple of right answers do not make a word durably known");

		learn.markKnown("concept:torch", true, T0);
		assertEquals(1, learn.mastered());
		assertEquals(Srs.MAX_LEVEL, learn.level("concept:torch"));
		assertEquals(30, learn.data().words.get("concept:torch").fsrs.stability, 0, "known seeds the stability");
	}
}
