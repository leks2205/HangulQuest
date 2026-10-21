package io.github.leks2205.hangulquest.learn.research;

import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.learn.fsrs.FsrsParams;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.leks2205.hangulquest.learn.research.ResearchFixture.SALT;
import static io.github.leks2205.hangulquest.learn.research.ResearchFixture.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentsTest {
	@TempDir
	Path dir;

	private final Experiments real = Experiments.load();

	// ------------------------------------------------------------------ the shipped file

	@Test
	void theShippedFileDefinesTheThreeExperiments() {
		Experiments.Experiment retention = real.get(Experiments.RETENTION);
		assertEquals(List.of("0.85", "0.90"), retention.arms());
		assertTrue(retention.active());
		assertEquals("word", retention.unit());
		Experiments.Experiment productive = real.get(Experiments.PRODUCTIVE);
		assertEquals(List.of("3d", "7d"), productive.arms());
		assertTrue(productive.active());
		Experiments.Experiment enactment = real.get(Experiments.ENACTMENT);
		assertEquals(List.of("card-first", "quest-first"), enactment.arms());
		assertFalse(enactment.active(), "enactment is only defined, it needs content work");
		assertNull(real.activeArm(SALT, Experiments.ENACTMENT, "concept:stone"));
		assertNotEquals(null, real.arm(SALT, Experiments.ENACTMENT, "concept:stone"), "its arm still exists");
	}

	// ------------------------------------------------------------------ assignment

	@Test
	void theArmOfAWordIsTheSameEveryTimeAndNeedsNoStorage() {
		Experiments again = Experiments.load();
		int zero = 0;
		for (int i = 0; i < 4000; i++) {
			String id = "concept:word" + i;
			String arm = real.arm("salt", Experiments.RETENTION, id);
			assertEquals(arm, real.arm("salt", Experiments.RETENTION, id));
			assertEquals(arm, again.arm("salt", Experiments.RETENTION, id), "a fresh instance agrees");
			if (arm.equals("0.85")) {
				zero++;
			}
		}
		assertTrue(zero > 1800 && zero < 2200, "the arms split about evenly, got " + zero + " of 4000");
	}

	@Test
	void theExperimentsAssignIndependentlyAndTheSaltMatters() {
		int both = 0;
		int differ = 0;
		for (int i = 0; i < 4000; i++) {
			String id = "concept:word" + i;
			if (real.arm("salt", Experiments.RETENTION, id).equals("0.85")
					&& real.arm("salt", Experiments.PRODUCTIVE, id).equals("3d")) {
				both++;
			}
			if (!real.arm("salt", Experiments.RETENTION, id).equals(real.arm("other", Experiments.RETENTION, id))) {
				differ++;
			}
		}
		assertTrue(both > 850 && both < 1150, "about a quarter in both first arms, got " + both);
		assertTrue(differ > 1500, "another salt reshuffles the words, only " + differ + " moved");
	}

	@Test
	void anUnknownOrInactiveExperimentGivesNoActiveArm() {
		assertNull(real.arm(SALT, "nope", "concept:stone"));
		assertNull(real.activeArm(SALT, "nope", "concept:stone"));
		Experiments off = Experiments.of(List.of(new Experiments.Experiment("retention", List.of("0.85", "0.90"), false, "word")));
		assertNull(off.activeArm(SALT, "retention", "concept:stone"));
		assertTrue(off.retention(SALT, "concept:stone").isEmpty());
		assertNull(off.tag(SALT, "concept:stone", Set.of()));
		assertEquals(Experiments.DEFAULT_UNLOCK_DAYS, Experiments.none().productiveUnlockDays(SALT, "concept:stone"));
	}

	@Test
	void theTagListsTheActiveArmsInFileOrder() {
		String id = "concept:stone";
		String tag = real.tag(SALT, id, Set.of());
		assertEquals("retention:" + real.arm(SALT, "retention", id) + ",productive:" + real.arm(SALT, "productive", id), tag);
		assertEquals("productive:" + real.arm(SALT, "productive", id), real.tag(SALT, id, Set.of(Experiments.RETENTION)));
	}

	// ------------------------------------------------------------------ in the log

	@Test
	void everyLogLineOfAWordCarriesItsArms() {
		ResearchFixture fixture = new ResearchFixture(dir, 20);
		fixture.learn.enableResearch(real);
		String id = fixture.ids.get(3);
		fixture.learn.answer(id, true, T0);
		fixture.learn.expose(fixture.catalog.byId(id).orElseThrow(), T0 + 1000, null);
		fixture.learn.answerSelfGraded(id, 3, "card:self", T0 + 2000);
		fixture.learn.reviewLog().flush();

		String expected = real.tag(SALT, id, Set.of());
		List<ReviewLog.Event> events = ReviewLog.readAll(dir);
		assertEquals(3, events.size());
		for (ReviewLog.Event event : events) {
			assertEquals(expected, event.arm());
		}
		assertTrue(expected.matches("retention:0\\.(85|90),productive:(3|7)d"), expected);
	}

	@Test
	void withoutTheSwitchTheArmFieldStaysEmpty() {
		ResearchFixture fixture = new ResearchFixture(dir, 20);
		fixture.learn.answer(fixture.ids.get(0), true, T0);
		fixture.learn.reviewLog().flush();
		assertNull(ReviewLog.readAll(dir).get(0).arm());
	}

	// ------------------------------------------------------------------ the retention arm

	private static String idWithRetention(ResearchFixture fixture, Experiments experiments, String arm) {
		return fixture.firstWhere(id -> arm.equals(experiments.activeArm(SALT, Experiments.RETENTION, id)));
	}

	@Test
	void theRetentionArmChangesTheIntervals() {
		ResearchFixture fixture = new ResearchFixture(dir, 60);
		fixture.learn.enableResearch(real);
		String low = idWithRetention(fixture, real, "0.85");
		String high = idWithRetention(fixture, real, "0.90");
		// the same history for both words, built up to a long interval
		for (String id : List.of(low, high)) {
			fixture.learn.answer(id, true, T0);
			fixture.learn.answer(id, true, T0 + 11 * Srs.MINUTE);
		}
		long now = T0 + 3 * Srs.DAY;
		CardState beforeLow = fixture.learn.data().words.get(low).fsrs.copy();
		CardState beforeHigh = fixture.learn.data().words.get(high).fsrs.copy();
		assertEquals(beforeLow.stability, beforeHigh.stability, 1e-9, "the same memory going in");
		fixture.learn.answer(low, true, now);
		fixture.learn.answer(high, true, now);

		WordProgress a = fixture.learn.data().words.get(low);
		WordProgress b = fixture.learn.data().words.get(high);
		Fsrs at85 = new Fsrs(FsrsParams.defaults().withDesiredRetention(0.85));
		Fsrs at90 = new Fsrs(FsrsParams.defaults().withDesiredRetention(0.90));
		assertEquals(at85.review(beforeLow, 3, now), a.fsrs, "0.85 word follows the 0.85 scheduler");
		assertEquals(at90.review(beforeHigh, 3, now), b.fsrs, "0.90 word follows the 0.90 scheduler");
		long days85 = (a.fsrs.due - now) / Srs.DAY;
		long days90 = (b.fsrs.due - now) / Srs.DAY;
		assertTrue(days85 > days90 * 1.25, "lower retention, longer gap: " + days85 + " vs " + days90);
		assertEquals(a.fsrs.stability, b.fsrs.stability, 1e-9, "only the interval differs, not the memory");
		assertEquals(a.fsrs.due, a.dueAt, "the old due field follows");
	}

	@Test
	void aPlayerWhoPicksTheirOwnRetentionIsLeftAlone() {
		ResearchFixture fixture = new ResearchFixture(dir, 60);
		fixture.learn.enableResearch(real);
		fixture.learn.configure(0.92, 10);
		String low = idWithRetention(fixture, real, "0.85");
		assertEquals(0.92, fixture.learn.fsrsFor(low).params().desiredRetention(), 1e-9);
		fixture.learn.answer(low, true, T0);
		fixture.learn.reviewLog().flush();
		String arm = ReviewLog.readAll(dir).get(0).arm();
		assertFalse(arm.contains("retention"), "the log does not claim an arm that did not apply: " + arm);
		assertTrue(arm.startsWith("productive:"));
	}

	@Test
	void theDefaultSettingLeavesTheArmInCharge() {
		ResearchFixture fixture = new ResearchFixture(dir, 60);
		fixture.learn.enableResearch(real);
		fixture.learn.configure(FsrsParams.DEFAULT_RETENTION, 10);
		assertEquals(0.85, fixture.learn.fsrsFor(idWithRetention(fixture, real, "0.85")).params().desiredRetention(), 1e-9);
		assertEquals(0.90, fixture.learn.fsrsFor(idWithRetention(fixture, real, "0.90")).params().desiredRetention(), 1e-9);
	}

	@Test
	void replayingTheLogWithPerWordSchedulersGivesTheLiveCards() {
		ResearchFixture fixture = new ResearchFixture(dir, 60);
		fixture.learn.enableResearch(real);
		String low = idWithRetention(fixture, real, "0.85");
		String high = idWithRetention(fixture, real, "0.90");
		for (String id : List.of(low, high)) {
			fixture.learn.answer(id, true, T0);
			fixture.learn.answer(id, true, T0 + 11 * Srs.MINUTE);
			fixture.learn.answer(id, true, T0 + 3 * Srs.DAY);
			fixture.learn.answer(id, true, T0 + 30 * Srs.DAY);
		}
		fixture.learn.reviewLog().flush();
		Map<String, CardState> perWord = ReviewLog.replay(dir, fixture.learn::fsrsFor, null);
		for (String id : List.of(low, high)) {
			assertEquals(fixture.learn.data().words.get(id).fsrs, perWord.get(id), id);
		}
		Map<String, CardState> plain = ReviewLog.replay(dir, fixture.learn.fsrs(), null);
		assertNotEquals(plain.get(low), perWord.get(low), "a single scheduler would have got the 0.85 word wrong");
	}

	// ------------------------------------------------------------------ the productive arm

	@Test
	void theProductiveArmSetsWhenTilesAndSelfGradedCardsUnlock() {
		ResearchFixture fixture = new ResearchFixture(dir, 60);
		fixture.learn.enableResearch(real);
		String early = fixture.firstWhere(id -> "3d".equals(real.activeArm(SALT, Experiments.PRODUCTIVE, id)));
		String late = fixture.firstWhere(id -> "7d".equals(real.activeArm(SALT, Experiments.PRODUCTIVE, id)));
		assertEquals(3.0, fixture.learn.productiveUnlockDays(early));
		assertEquals(7.0, fixture.learn.productiveUnlockDays(late));

		WordProgress progress = new WordProgress();
		progress.fsrs = new CardState();
		progress.fsrs.state = CardState.State.REVIEW;
		progress.fsrs.stability = 5;
		assertTrue(CardFactory.productiveUnlocked(progress, fixture.learn.productiveUnlockDays(early)));
		assertFalse(CardFactory.productiveUnlocked(progress, fixture.learn.productiveUnlockDays(late)));
		progress.fsrs.stability = 8;
		assertTrue(CardFactory.productiveUnlocked(progress, fixture.learn.productiveUnlockDays(late)));
		assertTrue(CardFactory.productiveUnlocked(progress), "the old entry point still means three days");
	}

	@Test
	void withoutExperimentsEveryWordUnlocksAtThreeDays() {
		ResearchFixture fixture = new ResearchFixture(dir, 20);
		Set<Double> seen = new HashSet<>();
		for (String id : fixture.ids) {
			seen.add(fixture.learn.productiveUnlockDays(id));
		}
		assertEquals(Set.of(3.0), seen);
	}
}
