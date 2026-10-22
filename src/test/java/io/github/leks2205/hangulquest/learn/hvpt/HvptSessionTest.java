package io.github.leks2205.hangulquest.learn.hvpt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HvptSessionTest {
	private static final class Speaker implements HvptSounds {
		final Set<String> missing;
		final List<String> played = new ArrayList<>();

		Speaker(String... missing) {
			this.missing = Set.of(missing);
		}

		@Override
		public boolean has(String event) {
			return !missing.contains(event);
		}

		@Override
		public void play(String event) {
			played.add(event);
		}
	}

	private static final class Notes implements HvptSink {
		final List<String> answers = new ArrayList<>();
		final List<HvptResult> results = new ArrayList<>();
		final List<Long> latencies = new ArrayList<>();

		@Override
		public void answered(HvptPhase phase, HvptTrial trial, String picked, boolean correct, long latencyMs, long now) {
			answers.add(phase + ":" + trial.stimulus() + ":" + trial.voice() + ":" + picked + ":" + correct);
			latencies.add(latencyMs);
		}

		@Override
		public void finished(HvptResult result, long now) {
			results.add(result);
		}
	}

	private static final HvptTrial T1 = new HvptTrial("dt", "dt_w1", "t", "male1", List.of("l", "t", "a"));
	private static final HvptTrial T2 = new HvptTrial("ss", "ss_w1", "l", "sohee", List.of("l", "t"));
	private long now = 1_000;

	private HvptSession session(Speaker speaker, Notes notes, HvptTrial... trials) {
		return new HvptSession(HvptPhase.TRAIN, List.of(trials), speaker, notes, () -> now);
	}

	@Test
	void aRightAnswerScoresAndReplaysWhatWasHeard() {
		Speaker speaker = new Speaker();
		Notes notes = new Notes();
		HvptSession session = session(speaker, notes, T1, T2);
		session.listen();
		assertEquals(List.of("hvpt.dt_w1.t.male1"), speaker.played);

		now += 1_500;
		HvptSession.Feedback feedback = session.answer(1);
		assertTrue(feedback.correct());
		assertEquals(1, session.correct());
		assertEquals(List.of("hvpt.dt_w1.t.male1", "hvpt.dt_w1.t.male1"), speaker.played, "the heard sound plays again");
		assertEquals(List.of("TRAIN:dt_w1.t:male1:t:true"), notes.answers);
		assertEquals(List.of(1_500L), notes.latencies);
	}

	@Test
	void aWrongAnswerReplaysBothSoundsInOrder() {
		Speaker speaker = new Speaker();
		Notes notes = new Notes();
		HvptSession session = session(speaker, notes, T1);
		session.listen();
		session.answer(2); // picked the aspirated one, heard the tense one
		assertFalse(session.feedback().correct());
		for (int i = 0; i < 2 * HvptSession.REPLAY_GAP_TICKS; i++) {
			session.tick();
		}
		assertEquals(List.of("hvpt.dt_w1.t.male1", "hvpt.dt_w1.t.male1", "hvpt.dt_w1.a.male1"), speaker.played);
		assertEquals(List.of("TRAIN:dt_w1.t:male1:a:false"), notes.answers);
	}

	@Test
	void theSecondSoundWaitsForTheFirst() {
		Speaker speaker = new Speaker();
		HvptSession session = session(speaker, new Notes(), T1);
		session.listen();
		session.answer(0);
		int before = speaker.played.size();
		for (int i = 0; i < HvptSession.REPLAY_GAP_TICKS - 1; i++) {
			session.tick();
		}
		assertEquals(before, speaker.played.size(), "nothing is played over the clip that is still running");
	}

	@Test
	void aTrialIsAnsweredOnlyOnce() {
		Notes notes = new Notes();
		HvptSession session = session(new Speaker(), notes, T1, T2);
		session.answer(1);
		assertNull(session.answer(0), "a second press during the feedback does nothing");
		assertEquals(1, notes.answers.size());
		assertEquals(1, session.answered());
	}

	@Test
	void anOptionThatDoesNotExistIsIgnored() {
		HvptSession session = session(new Speaker(), new Notes(), T2);
		assertNull(session.answer(2));
		assertNull(session.answer(-1));
		assertEquals(HvptSession.State.LISTENING, session.state());
	}

	@Test
	void theRunEndsWithTheScorePerContrast() {
		Notes notes = new Notes();
		HvptSession session = session(new Speaker(), notes, T1, T2);
		session.answer(1); // right
		session.next();
		assertEquals(HvptSession.State.LISTENING, session.state());
		assertTrue(notes.results.isEmpty(), "no result before the last trial is done");
		session.answer(1); // wrong: ss heard l
		session.next();

		assertEquals(HvptSession.State.DONE, session.state());
		assertEquals(1, notes.results.size());
		HvptResult result = notes.results.get(0);
		assertEquals(2, result.trials());
		assertEquals(1, result.correct());
		assertEquals(0.5, result.accuracy(), 0);
		assertEquals(new HvptResult.Tally(1, 1), result.byContrast().get("dt"));
		assertEquals(new HvptResult.Tally(1, 0), result.byContrast().get("ss"));
		assertNull(session.current());
	}

	@Test
	void theResultIsReportedOnce() {
		Notes notes = new Notes();
		HvptSession session = session(new Speaker(), notes, T2);
		session.answer(0);
		session.next();
		session.next();
		assertEquals(1, notes.results.size());
	}

	@Test
	void aMissingSoundIsSilentNotAnError() {
		Speaker speaker = new Speaker("hvpt.dt_w1.t.male1");
		HvptSession session = session(speaker, new Notes(), T1);
		assertFalse(session.listen());
		assertTrue(speaker.played.isEmpty());
		assertEquals(HvptSession.State.LISTENING, session.state());
	}

	@Test
	void duringTheFeedbackAFormCanBeHeardOnItsOwn() {
		Speaker speaker = new Speaker();
		HvptSession session = session(speaker, new Notes(), T1);
		assertFalse(session.playForm("l"), "not while a question is open");
		session.answer(0);
		speaker.played.clear();
		assertTrue(session.playForm("a"));
		assertEquals(List.of("hvpt.dt_w1.a.male1"), speaker.played);
	}

	@Test
	void anEmptyRunIsDoneAtOnce() {
		HvptSession session = session(new Speaker(), new Notes());
		assertEquals(HvptSession.State.DONE, session.state());
	}

	@Test
	void leavingEarlyReportsAnswersButNoFinishedRun() {
		Notes notes = new Notes();
		HvptSession session = session(new Speaker(), notes, T1, T2);
		session.answer(1);
		assertEquals(1, notes.answers.size());
		assertTrue(notes.results.isEmpty());
		assertEquals(1, session.result().trials(), "the score so far is available");
	}
}
