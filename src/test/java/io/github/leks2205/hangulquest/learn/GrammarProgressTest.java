package io.github.leks2205.hangulquest.learn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarProgressTest {
	private static ReviewLog.Event event(long t, String item, Integer grade, String ctx) {
		return new ReviewLog.Event(t, item, ReviewKind.GRAMMAR, grade == null ? ReviewMode.EXPOSURE : ReviewMode.MC3,
				grade, -1, -1, ctx);
	}

	// ------------------------------------------------------------------ knowledge tracing

	@Test
	void theFirstRightAnswerMovesTheEstimateFromThePrior() {
		// seen = .1*.9 / (.1*.9 + .9*.25) = .285714; learned on this attempt: + (1-.285714)*.15
		assertEquals(0.1, GrammarProgress.P_L0);
		assertEquals(0.392857, GrammarProgress.update(0.1, true), 1e-6);
	}

	@Test
	void aWrongAnswerLowersItButTheLearningStepKeepsItAboveZero() {
		// seen = .1*.1 / (.1*.1 + .9*.75) = .0145985; + (1-.0145985)*.15
		assertEquals(0.162409, GrammarProgress.update(0.1, false), 1e-6);
		assertTrue(GrammarProgress.update(0.5, false) < 0.5);
		assertTrue(GrammarProgress.update(0.5, false) > 0);
	}

	@Test
	void rightAnswersClimbToMasteredAndWrongOnesPullBack() {
		GrammarProgress progress = new GrammarProgress();
		double last = progress.pL("L2.seyo");
		assertEquals(0.1, last);
		int answers = 0;
		while (!progress.mastered("L2.seyo")) {
			progress.outcome("L2.seyo", true);
			answers++;
			assertTrue(progress.pL("L2.seyo") > last, "every right answer raises p(L)");
			last = progress.pL("L2.seyo");
			assertTrue(answers < 20, "never mastered");
		}
		assertEquals(4, answers, "four right answers in a row master a construction");
		double mastered = progress.pL("L2.seyo");
		progress.outcome("L2.seyo", false);
		assertTrue(progress.pL("L2.seyo") < mastered);
		assertEquals(5, progress.answers("L2.seyo"));
	}

	@Test
	void constructionsAreTrackedApart() {
		GrammarProgress progress = new GrammarProgress();
		progress.outcome("L1.eoyo", true);
		assertEquals(0.1, progress.pL("L1.obj_eul"));
		assertFalse(progress.met("L1.obj_eul"));
		assertTrue(progress.met("L1.eoyo"), "answering a construction means it has been met");
	}

	// ------------------------------------------------------------------ steps, cards and the level

	@Test
	void aConstructionIsMetByAStepOrByItsCard() {
		GrammarProgress progress = new GrammarProgress();
		assertFalse(progress.met("L1.eoyo"));
		progress.cardShown("L1.eoyo");
		assertTrue(progress.met("L1.eoyo"));
		assertTrue(progress.carded("L1.eoyo"));
		assertEquals(0, progress.steps("L1.eoyo"));
		progress.step("L1.obj_eul");
		progress.step("L1.obj_eul");
		assertFalse(progress.carded("L1.obj_eul"), "a step is not a card");
		assertEquals(2, progress.steps("L1.obj_eul"));
		assertEquals(Set.of("L1.eoyo", "L1.obj_eul"), progress.met());
	}

	@Test
	void theGrammarLevelIsTheHighestLevelWithEnoughSteps() {
		GrammarProgress progress = new GrammarProgress();
		assertEquals(0, progress.level(3));
		progress.ladderStep(1);
		progress.ladderStep(1);
		assertEquals(0, progress.level(3), "two steps are not enough");
		progress.ladderStep(1);
		assertEquals(1, progress.level(3));
		for (int i = 0; i < 3; i++) {
			progress.ladderStep(4);
		}
		assertEquals(4, progress.level(3));
		assertEquals(4, progress.level(1));
		assertEquals(0, progress.level(4), "a larger gate asks for more steps");
		assertEquals(3, progress.stepsAtLevel(4));
	}

	@Test
	void unlockingEverythingIsForToolsAndTests() {
		GrammarProgress progress = new GrammarProgress();
		progress.unlockAll();
		assertEquals(6, progress.level(3));
	}

	@Test
	void onlyConstructionIdsCountAsGrammar() {
		assertTrue(GrammarProgress.isConstruction("L2.seyo"));
		assertTrue(GrammarProgress.isConstruction("L1.loc_wi_e"));
		assertFalse(GrammarProgress.isConstruction("concept:stone"));
		assertFalse(GrammarProgress.isConstruction("ladder.3"));
		assertFalse(GrammarProgress.isConstruction(null));
	}

	// ------------------------------------------------------------------ derived from the review log

	@Test
	void theLogTellsTheSameStory() {
		List<ReviewLog.Event> log = new ArrayList<>(List.of(
				event(1, "L1.eoyo", null, "card"),
				event(2, "L1.eoyo", null, "trail/s1"),
				event(2, GrammarProgress.LADDER + 1, null, "trail/s1"),
				event(3, "L1.eoyo", null, "trail/s2"),
				event(3, GrammarProgress.LADDER + 1, null, "trail/s2"),
				event(4, "L1.eoyo", 3, "card:cloze"),
				event(5, "L1.eoyo", 1, "card:cloze"),
				new ReviewLog.Event(6, "concept:stone", ReviewKind.WORD, ReviewMode.MC4, 3, 1, 1, "quiz")));
		Collections.shuffle(log, new java.util.Random(7)); // order in the file does not matter, time does
		GrammarProgress progress = GrammarProgress.fromEvents(log);
		assertEquals(2, progress.steps("L1.eoyo"), "the card line is not a step");
		assertEquals(2, progress.answers("L1.eoyo"));
		assertEquals(2, progress.stepsAtLevel(1));
		double expected = GrammarProgress.update(GrammarProgress.update(0.1, true), false);
		assertEquals(expected, progress.pL("L1.eoyo"), 1e-12);
		assertFalse(progress.met("concept:stone"), "a word is not a construction");
	}

	@Test
	void aResetLineStartsOver() {
		GrammarProgress progress = GrammarProgress.fromEvents(List.of(
				event(1, "L1.eoyo", null, "trail/s1"),
				event(1, GrammarProgress.LADDER + 1, null, "trail/s1"),
				event(2, "L1.eoyo", 3, "card:cloze"),
				event(3, GrammarProgress.RESET, null, "reset"),
				event(4, "L1.obj_eul", null, "trail/s9")));
		assertFalse(progress.met("L1.eoyo"));
		assertEquals(0.1, progress.pL("L1.eoyo"));
		assertEquals(0, progress.stepsAtLevel(1));
		assertTrue(progress.met("L1.obj_eul"));
	}

	@Test
	void linesFromANewerVersionAreIgnored() {
		GrammarProgress progress = GrammarProgress.fromEvents(List.of(
				event(1, GrammarProgress.LADDER + "x", null, "t"), event(2, "something.else", null, "t")));
		assertEquals(0, progress.level(1));
		assertTrue(progress.met().isEmpty());
	}
}
