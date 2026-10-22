package io.github.leks2205.hangulquest.learn.hvpt;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;

import java.util.Locale;
import java.util.Map;

/**
 * Keeps what a run produced: one review log line per answer (mode HVPT, so it never touches a schedule), and in
 * the progress file the small per-contrast tallies and the score of each finished run.
 *
 * <p>Training answers add to the lifetime tally of their contrast; the tests do not, they only leave their
 * session record, so the pre- and post-test stay comparable.
 */
public final class HvptRecorder implements HvptSink {
	/** Finished runs older than this many are dropped, oldest training runs first; the tests are always kept. */
	static final int MAX_SESSIONS = 200;

	private final ProgressData data;
	private final ReviewLog log;
	private final Runnable markDirty;

	public HvptRecorder(ProgressData data, ReviewLog log, Runnable markDirty) {
		this.data = data;
		this.log = log;
		this.markDirty = markDirty;
	}

	@Override
	public void answered(HvptPhase phase, HvptTrial trial, String picked, boolean correct, long latencyMs, long now) {
		log.append(new ReviewLog.Event(now, trial.stimulus(), ReviewKind.HVPT, ReviewMode.HVPT,
				correct ? Fsrs.GOOD : Fsrs.AGAIN, latencyMs, -1,
				"hvpt:" + phase.name().toLowerCase(Locale.ROOT) + ":" + trial.voice() + ":" + picked));
		ProgressData.HvptData hvpt = data.hvpt;
		if (hvpt.startedAt == 0) {
			hvpt.startedAt = now;
		}
		if (phase == HvptPhase.TRAIN) {
			ProgressData.HvptTally tally = hvpt.contrasts.computeIfAbsent(trial.contrast(), id -> new ProgressData.HvptTally());
			tally.trials++;
			tally.correct += correct ? 1 : 0;
		}
		markDirty.run();
	}

	@Override
	public void finished(HvptResult result, long now) {
		long key = now;
		while (data.hvpt.sessions.containsKey(Long.toString(key))) {
			key++; // two runs never share an entry, even with a clock that stands still
		}
		ProgressData.HvptSession session = new ProgressData.HvptSession();
		session.t = key;
		session.phase = result.phase().name();
		session.trials = result.trials();
		session.correct = result.correct();
		result.byContrast().forEach((id, tally) -> {
			ProgressData.HvptTally out = new ProgressData.HvptTally();
			out.trials = tally.trials();
			out.correct = tally.correct();
			session.byContrast.put(id, out);
		});
		data.hvpt.sessions.put(Long.toString(key), session);
		trim(data.hvpt.sessions);
		markDirty.run();
	}

	private static void trim(Map<String, ProgressData.HvptSession> sessions) {
		var iterator = sessions.entrySet().iterator();
		while (sessions.size() > MAX_SESSIONS && iterator.hasNext()) {
			if (HvptPhase.TRAIN.name().equals(iterator.next().getValue().phase)) {
				iterator.remove();
			}
		}
	}
}
