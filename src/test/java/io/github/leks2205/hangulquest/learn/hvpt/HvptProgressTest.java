package io.github.leks2205.hangulquest.learn.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HvptProgressTest {
	private static final long DAY = 86_400_000L;
	private static final long START = Instant.parse("2026-10-01T09:00:00Z").toEpochMilli();
	private final HvptSet set = HvptSet.shared();

	private static void add(ProgressData.HvptData data, long t, HvptPhase phase, int trials, int correct, String contrast,
							int contrastTrials, int contrastCorrect) {
		ProgressData.HvptSession session = new ProgressData.HvptSession();
		session.t = t;
		session.phase = phase.name();
		session.trials = trials;
		session.correct = correct;
		ProgressData.HvptTally tally = new ProgressData.HvptTally();
		tally.trials = contrastTrials;
		tally.correct = contrastCorrect;
		session.byContrast.put(contrast, tally);
		data.sessions.put(Long.toString(t), session);
	}

	private HvptProgress at(ProgressData.HvptData data, long now) {
		return HvptProgress.of(data, set, now, ZoneOffset.UTC);
	}

	@Test
	void aNewPlayerStartsWithThePreTest() {
		HvptProgress progress = at(new ProgressData.HvptData(), START);
		assertFalse(progress.preDone());
		assertEquals(HvptPhase.PRE, progress.next());
		assertEquals(0, progress.trainingRuns());
		assertFalse(progress.postReady());
		assertNull(progress.preAccuracy());
		assertTrue(progress.lines().stream().allMatch(line -> line.pre() == null && line.post() == null));
	}

	@Test
	void afterThePreTestItIsTrainingUntilThePostTestIsReady() {
		ProgressData.HvptData data = new ProgressData.HvptData();
		add(data, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		HvptProgress early = at(data, START + DAY);
		assertTrue(early.preDone());
		assertEquals(HvptPhase.TRAIN, early.next());
		assertEquals(0.5, early.preAccuracy(), 1e-9);
		assertEquals(0.4, early.lines().get(0).pre(), 1e-9);
		assertEquals(set.params().minSessions(), early.runsUntilPost());
		assertEquals(set.params().weeks() * 7 - 1, early.daysUntilPost());
	}

	@Test
	void thePostTestNeedsEnoughRunsAndEnoughTime() {
		ProgressData.HvptData data = new ProgressData.HvptData();
		add(data, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		for (int i = 0; i < set.params().minSessions(); i++) {
			add(data, START + (i + 1) * DAY, HvptPhase.TRAIN, 60, 40, "gk", 10, 6);
		}
		assertFalse(at(data, START + 10 * DAY).postReady(), "enough runs, too few days");
		assertTrue(at(data, START + 22 * DAY).postReady());

		ProgressData.HvptData fewRuns = new ProgressData.HvptData();
		add(fewRuns, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		add(fewRuns, START + DAY, HvptPhase.TRAIN, 60, 40, "gk", 10, 6);
		assertFalse(at(fewRuns, START + 30 * DAY).postReady(), "enough days, too few runs");
	}

	@Test
	void theGainComparesTheFirstPreWithTheLastPost() {
		ProgressData.HvptData data = new ProgressData.HvptData();
		add(data, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		add(data, START + 5 * DAY, HvptPhase.PRE, 30, 24, "gk", 5, 5);
		add(data, START + 25 * DAY, HvptPhase.POST, 30, 27, "gk", 5, 4);
		HvptProgress progress = at(data, START + 26 * DAY);
		assertTrue(progress.postDone());
		assertEquals(0.5, progress.preAccuracy(), 1e-9, "the first pre-test is the baseline");
		assertEquals(0.9, progress.postAccuracy(), 1e-9);
		assertEquals(0.4, progress.gain(), 1e-9);
		assertEquals(0.8, progress.lines().get(0).post(), 1e-9);
	}

	@Test
	void latelyPoolsTheLastThreeTrainingRuns() {
		ProgressData.HvptData data = new ProgressData.HvptData();
		add(data, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		add(data, START + DAY, HvptPhase.TRAIN, 60, 6, "gk", 10, 0);
		add(data, START + 2 * DAY, HvptPhase.TRAIN, 60, 30, "gk", 10, 5);
		add(data, START + 3 * DAY, HvptPhase.TRAIN, 60, 30, "gk", 10, 5);
		add(data, START + 4 * DAY, HvptPhase.TRAIN, 60, 60, "gk", 10, 10);
		HvptProgress progress = at(data, START + 4 * DAY + 3_600_000L);
		assertEquals(4, progress.trainingRuns());
		assertEquals(120.0 / 180.0, progress.recentAccuracy(), 1e-9, "the weak first run no longer counts");
		assertEquals(20.0 / 30.0, progress.lines().get(0).recent(), 1e-9);
		assertTrue(progress.trainedToday());
		assertFalse(at(data, START + 6 * DAY).trainedToday());
	}

	@Test
	void theDayCountStartsAtThePreTest() {
		ProgressData.HvptData data = new ProgressData.HvptData();
		add(data, START, HvptPhase.PRE, 30, 15, "gk", 5, 2);
		assertEquals(0, at(data, START + 3_600_000L).daysSinceStart());
		assertEquals(9, at(data, START + 9 * DAY).daysSinceStart());
	}

	@Test
	void aMissingRecordReadsAsNeverPlayed() {
		HvptProgress progress = HvptProgress.of(null, set, START, ZoneOffset.UTC);
		assertEquals(HvptPhase.PRE, progress.next());
		assertEquals(set.contrasts().size(), progress.lines().size());
	}
}
