package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Durably known" means recall on three different study days and a stability of 21 days. */
class MasteryTest {
	private static final String STONE = "concept:stone";
	private static final long DAY = Srs.DAY;
	/** 2026-06-01T12:00Z, noon so a few hours either way stay on the same study day. */
	private static final long D0 = Instant.parse("2026-06-01T12:00:00Z").toEpochMilli();

	@TempDir
	Path tempDir;

	private LearningService learn;
	private Catalog catalog;

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LoggerFactory.getLogger("mastery-test"));
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		catalog = Catalog.ofConcepts(List.of(concept));
		learn = new LearningService(store.data(), store, catalog, Clock.fixed(Instant.ofEpochMilli(D0), ZoneOffset.UTC));
	}

	private WordProgress stone() {
		return learn.data().words.get(STONE);
	}

	/** A word that is already stable enough, so only the study days are in question. */
	private void stableStone() {
		learn.introduce(catalog.byId(STONE).orElseThrow(), D0);
		stone().fsrs = Srs.seed(8, D0, learn.fsrs()); // 35 days
		stone().fsrs.due = D0;
	}

	@Test
	void threeSessionsOnDifferentDaysAndStabilityMakeAWordDurablyKnown() {
		stableStone();
		learn.answerMc(STONE, 4, true, 900, D0);
		assertFalse(Mastery.durablyKnown(stone()), "one day");
		learn.answerMc(STONE, 4, true, 900, D0 + 30 * Srs.MINUTE);
		learn.answerMc(STONE, 4, true, 900, D0 + 2 * Srs.HOUR);
		assertEquals(1, ExposureLedger.recallDays(stone()), "three answers on the same day are one session");
		assertFalse(Mastery.durablyKnown(stone()));

		learn.answerMc(STONE, 4, true, 900, D0 + DAY);
		assertFalse(Mastery.durablyKnown(stone()), "two days");
		assertEquals(0, learn.mastered());

		learn.answerMc(STONE, 4, true, 900, D0 + 2 * DAY);
		assertEquals(3, ExposureLedger.recallDays(stone()));
		assertTrue(stone().fsrs.stability >= Mastery.STABILITY_DAYS);
		assertTrue(Mastery.durablyKnown(stone()));
		assertEquals(1, learn.mastered());
	}

	@Test
	void theStudyDayTurnsOverAtFourInTheMorning() {
		stableStone();
		long evening = Instant.parse("2026-06-01T23:30:00Z").toEpochMilli();
		long afterMidnight = Instant.parse("2026-06-02T01:30:00Z").toEpochMilli();
		long nextMorning = Instant.parse("2026-06-02T05:00:00Z").toEpochMilli();
		learn.answerMc(STONE, 4, true, 900, evening);
		learn.answerMc(STONE, 4, true, 900, afterMidnight);
		assertEquals(1, ExposureLedger.recallDays(stone()), "23:30 and 01:30 are one study day");
		learn.answerMc(STONE, 4, true, 900, nextMorning);
		assertEquals(2, ExposureLedger.recallDays(stone()));
		assertEquals(List.of("2026-06-01", "2026-06-02"), stone().ledger.recallDays);
		assertEquals(Streak.dayKey(LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMidnight), ZoneOffset.UTC)),
				"2026-06-01");
	}

	@Test
	void lowStabilityIsNotEnoughEvenWithThreeDays() {
		learn.introduce(catalog.byId(STONE).orElseThrow(), D0);
		for (int day = 0; day < 3; day++) {
			learn.answerMc(STONE, 4, true, 900, D0 + day * DAY);
		}
		assertEquals(3, ExposureLedger.recallDays(stone()));
		assertTrue(stone().fsrs.stability < Mastery.STABILITY_DAYS, "stability " + stone().fsrs.stability);
		assertFalse(Mastery.durablyKnown(stone()));
	}

	@Test
	void guessesAmongTwoOrThreeOptionsNeverCount() {
		stableStone();
		CardState before = stone().fsrs.copy();
		for (int day = 0; day < 6; day++) {
			learn.answerMc(STONE, day % 2 == 0 ? 3 : 2, true, 900, D0 + day * DAY);
			learn.practice(STONE, 3, true, D0 + day * DAY + 1000);
		}
		assertEquals(0, ExposureLedger.recallDays(stone()));
		assertFalse(Mastery.durablyKnown(stone()));
		assertEquals(before, stone().fsrs);
	}

	@Test
	void hintedQuestStepsAndExposuresNeverCount() {
		stableStone();
		for (int day = 0; day < 5; day++) {
			long now = D0 + day * DAY;
			stone().fsrs.due = now;
			learn.questStep(catalog.byId(STONE).orElseThrow(), now, false, true, ExposureLedger.Context.NONE, "s");
			learn.expose(catalog.byId(STONE).orElseThrow(), now + 5);
		}
		assertEquals(0, ExposureLedger.recallDays(stone()));
		assertFalse(Mastery.durablyKnown(stone()));
	}

	@Test
	void unaidedQuestStepsTypedTileAndSelfGradedAnswersDoCount() {
		stableStone();
		long now = D0;
		stone().fsrs.due = now;
		learn.questStep(catalog.byId(STONE).orElseThrow(), now, false, false, ExposureLedger.Context.NONE, "s");
		assertEquals(1, ExposureLedger.recallDays(stone()), "unaided quest step");
		learn.answerGraded(STONE, Grader.typed(0), 2000, "typed", D0 + DAY);
		learn.answerGraded(STONE, Grader.tile(0), 2000, "tile", D0 + 2 * DAY);
		assertEquals(3, ExposureLedger.recallDays(stone()));
		assertTrue(Mastery.durablyKnown(stone()));

		stableStone();
		stone().ledger = null;
		learn.answerSelfGraded(STONE, 3, "self", D0 + 5 * DAY);
		assertEquals(1, ExposureLedger.recallDays(stone()), "self-graded");
	}

	@Test
	void aWrongAnswerDoesNotAddADay() {
		stableStone();
		learn.answerMc(STONE, 4, false, 900, D0);
		assertEquals(0, ExposureLedger.recallDays(stone()));
		assertEquals(1, ExposureLedger.retrievals(stone()), "but it was a retrieval attempt");
	}

	@Test
	void aWordMarkedKnownCountsAsMasteredButIsNotDurablyKnown() {
		learn.markKnown(STONE, true, D0);
		assertEquals(1, learn.mastered());
		assertTrue(Mastery.mastered(stone()));
		assertFalse(Mastery.durablyKnown(stone()), "no recall yet: probes can still check it");
		assertEquals(30, stone().fsrs.stability, 0);
	}

	@Test
	void theJournalAndTheAchievementUseMastery() {
		stableStone();
		for (int day = 0; day < 3; day++) {
			learn.answerMc(STONE, 4, true, 900, D0 + day * DAY);
		}
		assertEquals(Mastery.count(learn.data()), learn.mastered());
		assertEquals(1, learn.mastered());
	}
}
