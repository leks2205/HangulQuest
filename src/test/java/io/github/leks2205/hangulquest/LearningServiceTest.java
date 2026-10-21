package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
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
		assertEquals(2, level);

		for (int i = 0; i < 5; i++) {
			learn.expose(catalog.byId(STONE).orElseThrow(), T0 + 1000L * (i + 1));
		}

		assertEquals(level, progress.level);
		assertEquals(dueAt, progress.dueAt);
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
		assertEquals(1, progress().level);
		assertEquals(T0 + Srs.intervalMs(1), progress().dueAt);

		learn.answer(STONE, false, T0 + 5);
		assertEquals(0, progress().level);
		assertEquals(Srs.retryAt(T0 + 5), progress().dueAt);
		assertEquals(2, learn.totalReviews());
	}

	@Test
	void masteredMeansKnownOrLevelSix() {
		learn.answer(STONE, true, T0);
		for (int i = 0; i < Srs.MASTERED_LEVEL - 2; i++) {
			learn.answer(STONE, true, T0);
		}
		assertEquals(Srs.MASTERED_LEVEL - 1, progress().level);
		assertEquals(0, learn.mastered(), "level 5 is no longer mastered");

		learn.answer(STONE, true, T0);
		assertEquals(Srs.MASTERED_LEVEL, progress().level);
		assertEquals(1, learn.mastered());

		learn.markKnown("concept:torch", true, T0);
		assertEquals(2, learn.mastered());
		assertEquals(Srs.MAX_LEVEL, learn.level("concept:torch"));
	}
}
