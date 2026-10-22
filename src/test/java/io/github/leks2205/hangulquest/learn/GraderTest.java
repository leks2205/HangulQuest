package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One test per row of the grading table. */
class GraderTest {
	private static final long QUICK = 1500;

	@Test
	void mc4CorrectAndQuickIsGood() {
		Grader.Graded graded = new Grader().multipleChoice(4, true, QUICK);
		assertEquals(ReviewMode.MC4, graded.mode());
		assertEquals(Fsrs.GOOD, graded.grade());
		assertTrue(graded.schedules());
		assertTrue(graded.recall());
	}

	@Test
	void mc4CorrectButSlowIsHard() {
		Grader grader = new Grader();
		assertEquals(Fsrs.HARD, grader.multipleChoice(4, true, 2 * Grader.DEFAULT_MEDIAN_MS).grade());
		assertEquals(Fsrs.GOOD, grader.multipleChoice(4, true, 2 * Grader.DEFAULT_MEDIAN_MS - 1).grade());
		Grader.Graded slow = new Grader().multipleChoice(4, true, 60_000);
		assertEquals(Fsrs.HARD, slow.grade());
		assertTrue(slow.correct(), "Hard is still a right answer");
		assertTrue(slow.schedules());
	}

	@Test
	void mc4WrongIsAgain() {
		Grader.Graded graded = new Grader().multipleChoice(4, false, QUICK);
		assertEquals(Fsrs.AGAIN, graded.grade());
		assertFalse(graded.correct());
		assertFalse(graded.recall());
		assertTrue(graded.schedules());
	}

	@Test
	void twoAndThreeOptionsAreLoggedButNeverSchedule() {
		for (int options : new int[] {2, 3}) {
			for (boolean correct : new boolean[] {true, false}) {
				Grader.Graded graded = new Grader().multipleChoice(options, correct, QUICK);
				assertEquals(options == 3 ? ReviewMode.MC3 : ReviewMode.MC2, graded.mode());
				assertFalse(graded.schedules(), options + " options, correct=" + correct);
				assertFalse(graded.recall(), "a guess is not recall");
				assertEquals(correct, graded.correct());
			}
		}
	}

	@Test
	void theThresholdFollowsThePlayersOwnRollingMedian() {
		Grader grader = new Grader();
		for (int i = 0; i < 10; i++) {
			grader.multipleChoice(4, true, 1000);
		}
		assertEquals(1000, grader.medianMs());
		assertEquals(Fsrs.HARD, grader.multipleChoice(4, true, 2000).grade(), "twice the median is slow");
		assertEquals(Fsrs.GOOD, grader.multipleChoice(4, true, 1500).grade());

		Grader patient = new Grader();
		for (int i = 0; i < 10; i++) {
			patient.multipleChoice(4, true, 9000);
		}
		assertEquals(Fsrs.GOOD, patient.multipleChoice(4, true, 12_000).grade(), "a slow player is not punished");
	}

	@Test
	void theMedianNeedsEnoughAnswersAndIgnoresWrongOnesAndGuesses() {
		Grader grader = new Grader();
		assertEquals(Grader.DEFAULT_MEDIAN_MS, grader.medianMs());
		for (int i = 0; i < Grader.MIN_SAMPLES - 1; i++) {
			grader.multipleChoice(4, true, 100);
		}
		assertEquals(Grader.DEFAULT_MEDIAN_MS, grader.medianMs(), "four answers are too few");
		grader.multipleChoice(4, false, 100_000);
		grader.multipleChoice(3, true, 100_000);
		grader.multipleChoice(2, true, 100_000);
		assertEquals(Grader.DEFAULT_MEDIAN_MS, grader.medianMs(), "wrong answers and guesses do not count");
		grader.multipleChoice(4, true, 100);
		assertEquals(100, grader.medianMs());
	}

	@Test
	void anUnknownAnswerTimeCountsAsQuick() {
		Grader grader = new Grader();
		assertEquals(Fsrs.GOOD, grader.multipleChoice(4, true, Grader.UNKNOWN_LATENCY).grade());
		assertEquals(Grader.DEFAULT_MEDIAN_MS, grader.medianMs(), "and is not remembered");
	}

	@Test
	void questStepUnaidedIsGoodHintedIsHardAssistedIsNothing() {
		Grader.Graded unaided = Grader.quest(false, false);
		assertEquals(ReviewMode.QUEST_UNAIDED, unaided.mode());
		assertEquals(Fsrs.GOOD, unaided.grade());
		assertTrue(unaided.recall());

		Grader.Graded hinted = Grader.quest(false, true);
		assertEquals(ReviewMode.QUEST_HINTED, hinted.mode());
		assertEquals(Fsrs.HARD, hinted.grade());
		assertTrue(hinted.schedules());
		assertFalse(hinted.recall(), "a hinted step is not unaided recall");

		for (boolean hint : new boolean[] {false, true}) {
			Grader.Graded assisted = Grader.quest(true, hint);
			assertEquals(ReviewMode.EXPOSURE, assisted.mode());
			assertNull(assisted.grade());
			assertFalse(assisted.schedules());
		}
	}

	@Test
	void aSelfGradedCardIsAsChosen() {
		for (int chosen = 1; chosen <= 4; chosen++) {
			Grader.Graded graded = Grader.selfGraded(chosen);
			assertEquals(ReviewMode.SELF, graded.mode());
			assertEquals(chosen, graded.grade());
			assertTrue(graded.schedules());
		}
		assertEquals(Fsrs.EASY, Grader.selfGraded(9).grade());
		assertEquals(Fsrs.AGAIN, Grader.selfGraded(-1).grade());
	}

	@Test
	void typedAndTileAnswersWithinOneJamoAreHard() {
		assertEquals(Fsrs.GOOD, Grader.typed(0).grade());
		assertEquals(Fsrs.HARD, Grader.typed(1).grade());
		assertEquals(Fsrs.AGAIN, Grader.typed(2).grade());
		assertEquals(Fsrs.HARD, Grader.tile(1).grade());
		assertEquals(ReviewMode.TILE, Grader.tile(0).mode());
		assertTrue(Grader.typed(1).recall());
	}

	@Test
	void exposureAndProbesAreNeverGradedForTheSchedule() {
		assertFalse(Grader.EXPOSURE.schedules());
		assertNull(Grader.EXPOSURE.grade());
		assertFalse(Grader.probe(true).schedules());
		assertFalse(Grader.probe(false).schedules());
	}

	@Test
	void onlyTheFiveRecallModesCountAsRecall() {
		for (ReviewMode mode : ReviewMode.values()) {
			boolean expected = switch (mode) {
				case MC4, TYPED, TILE, SELF, QUEST_UNAIDED -> true;
				default -> false;
			};
			assertEquals(expected, mode.recall(), mode.name());
		}
	}
}
