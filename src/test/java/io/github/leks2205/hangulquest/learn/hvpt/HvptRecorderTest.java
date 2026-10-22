package io.github.leks2205.hangulquest.learn.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HvptRecorderTest {
	private static final Clock UTC = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
	private static final HvptTrial TRIAL = new HvptTrial("bp", "bp_w1", "t", "female2", List.of("l", "t", "a"));

	@TempDir
	Path dir;

	@Test
	void everyAnswerIsALogLineInTheNewModeThatNeverSchedules() {
		ProgressData data = new ProgressData();
		AtomicInteger dirty = new AtomicInteger();
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			HvptRecorder recorder = new HvptRecorder(data, log, dirty::incrementAndGet);
			recorder.answered(HvptPhase.TRAIN, TRIAL, "t", true, 1800, 1_000);
			recorder.answered(HvptPhase.PRE, TRIAL, "a", false, 2200, 2_000);
			log.flush();
		}
		List<ReviewLog.Event> events = ReviewLog.readAll(dir);
		assertEquals(2, events.size());
		ReviewLog.Event first = events.get(0);
		assertEquals(ReviewKind.HVPT, first.kind());
		assertEquals(ReviewMode.HVPT, first.mode());
		assertFalse(first.mode().schedules());
		assertFalse(first.mode().recall());
		assertEquals("bp_w1.t", first.item());
		assertEquals(Fsrs.GOOD, first.grade());
		assertEquals(1800, first.latencyMs());
		assertEquals("hvpt:train:female2:t", first.ctx());
		assertEquals(Fsrs.AGAIN, events.get(1).grade());
		assertEquals("hvpt:pre:female2:a", events.get(1).ctx());
		assertEquals(2, dirty.get());
	}

	@Test
	void theLogIsNotUsedToRebuildWordCards() {
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			new HvptRecorder(new ProgressData(), log, () -> { }).answered(HvptPhase.TRAIN, TRIAL, "t", true, 1, 1_000);
			log.flush();
		}
		assertTrue(ReviewLog.replay(dir, Fsrs.defaults(), null).isEmpty(), "a sound is not a word");
	}

	@Test
	void trainingAddsToTheLifetimeTalliesButTestsDoNot() {
		ProgressData data = new ProgressData();
		try (ReviewLog log = ReviewLog.disabled()) {
			HvptRecorder recorder = new HvptRecorder(data, log, () -> { });
			recorder.answered(HvptPhase.TRAIN, TRIAL, "t", true, 1, 1_000);
			recorder.answered(HvptPhase.TRAIN, TRIAL, "l", false, 1, 2_000);
			recorder.answered(HvptPhase.PRE, TRIAL, "t", true, 1, 3_000);
		}
		ProgressData.HvptTally tally = data.hvpt.contrasts.get("bp");
		assertEquals(2, tally.trials);
		assertEquals(1, tally.correct);
		assertEquals(1_000, data.hvpt.startedAt);
	}

	@Test
	void aFinishedRunLeavesItsScore() {
		ProgressData data = new ProgressData();
		HvptRecorder recorder = new HvptRecorder(data, ReviewLog.disabled(), () -> { });
		recorder.finished(new HvptResult(HvptPhase.POST, 30, 24,
				Map.of("gk", new HvptResult.Tally(5, 4), "vo", new HvptResult.Tally(5, 5))), 5_000);
		ProgressData.HvptSession session = data.hvpt.sessions.get("5000");
		assertEquals("POST", session.phase);
		assertEquals(30, session.trials);
		assertEquals(24, session.correct);
		assertEquals(4, session.byContrast.get("gk").correct);
		assertNull(data.hvpt.contrasts.get("gk"), "a test is not training");
	}

	@Test
	void oldTrainingRunsAreTrimmedButTestsAreKept() {
		ProgressData data = new ProgressData();
		HvptRecorder recorder = new HvptRecorder(data, ReviewLog.disabled(), () -> { });
		recorder.finished(new HvptResult(HvptPhase.PRE, 30, 10, Map.of()), 1);
		for (int i = 0; i < HvptRecorder.MAX_SESSIONS + 20; i++) {
			recorder.finished(new HvptResult(HvptPhase.TRAIN, 60, 40, Map.of()), 100 + i);
		}
		assertEquals(HvptRecorder.MAX_SESSIONS, data.hvpt.sessions.size());
		assertTrue(data.hvpt.sessions.containsKey("1"), "the pre-test is never trimmed");
		assertFalse(data.hvpt.sessions.containsKey("100"), "the oldest training run goes first");
	}
}
