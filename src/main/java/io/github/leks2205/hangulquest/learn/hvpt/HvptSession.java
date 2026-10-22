package io.github.leks2205.hangulquest.learn.hvpt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * One run of trials, without any screen: the screen draws {@link #current()} and calls {@link #answer}.
 * A trial is heard, answered, then shown with its feedback and the replay of what was heard (and of what was
 * picked, when that was wrong); {@link #next()} moves on. Every answer goes to the sink at once, the score when
 * the last trial is done. Leaving early records the answers given, but no finished run.
 */
public final class HvptSession {
	/** Game ticks between two sounds of a replay; a clip is well under a second long. */
	public static final int REPLAY_GAP_TICKS = 24;
	private static final long NOT_HEARD = -1;

	public enum State { LISTENING, FEEDBACK, DONE }

	/** What an answer turned out to be: the form heard, the form picked and whether they match. */
	public record Feedback(boolean correct, String heard, String picked) {
	}

	private final HvptPhase phase;
	private final List<HvptTrial> trials;
	private final HvptSounds sounds;
	private final HvptSink sink;
	private final LongSupplier clock;
	private final Deque<String> queue = new ArrayDeque<>();
	private final Map<String, HvptResult.Tally> tallies = new LinkedHashMap<>();
	private int index;
	private int correct;
	private int answered;
	private int gap;
	private State state;
	private Feedback feedback;
	private long heardAt = NOT_HEARD;
	private boolean finishedReported;

	public HvptSession(HvptPhase phase, List<HvptTrial> trials, HvptSounds sounds, HvptSink sink, LongSupplier clock) {
		this.phase = phase;
		this.trials = List.copyOf(trials);
		this.sounds = sounds;
		this.sink = sink;
		this.clock = clock;
		this.state = this.trials.isEmpty() ? State.DONE : State.LISTENING;
	}

	public HvptPhase phase() {
		return phase;
	}

	public State state() {
		return state;
	}

	public int size() {
		return trials.size();
	}

	/** Zero-based position of the trial on screen. */
	public int index() {
		return index;
	}

	public int answered() {
		return answered;
	}

	public int correct() {
		return correct;
	}

	/** The trial on screen; null once the run is done. */
	public HvptTrial current() {
		return state == State.DONE ? null : trials.get(index);
	}

	public Feedback feedback() {
		return feedback;
	}

	/** Plays the sound of the trial (the first time it starts the answer clock). Returns false when it is missing. */
	public boolean listen() {
		HvptTrial trial = current();
		if (trial == null || state != State.LISTENING) {
			return false;
		}
		if (heardAt == NOT_HEARD) {
			heardAt = clock.getAsLong();
		}
		return start(trial.targetEvent());
	}

	/** Answers with the option at this position. Ignored unless a trial is waiting for an answer. */
	public Feedback answer(int option) {
		HvptTrial trial = current();
		if (trial == null || state != State.LISTENING || option < 0 || option >= trial.options().size()) {
			return null;
		}
		long now = clock.getAsLong();
		String picked = trial.options().get(option);
		boolean right = picked.equals(trial.target());
		long latency = heardAt == NOT_HEARD ? -1 : now - heardAt;
		feedback = new Feedback(right, trial.target(), picked);
		answered++;
		correct += right ? 1 : 0;
		HvptResult.add(tallies, trial.contrast(), right);
		state = State.FEEDBACK;
		sink.answered(phase, trial, picked, right, latency, now);
		replay();
		return feedback;
	}

	/**
	 * Plays what was heard again, then what was picked when it differs: both sounds, one after the other, in the
	 * voice of the trial. The screen's tick spaces them.
	 */
	public void replay() {
		HvptTrial trial = current();
		if (trial == null || feedback == null) {
			return;
		}
		queue.clear();
		gap = 0;
		queue.add(trial.event(feedback.heard()));
		if (!feedback.correct()) {
			queue.add(trial.event(feedback.picked()));
		}
		tick();
	}

	/** Plays the next queued sound once its gap has passed. Call once per game tick. */
	public void tick() {
		if (gap > 0) {
			gap--;
			return;
		}
		String event = queue.poll();
		if (event != null) {
			start(event);
			gap = REPLAY_GAP_TICKS;
		}
	}

	/** Plays one form of the current trial, for a click on its button during feedback. */
	public boolean playForm(String form) {
		HvptTrial trial = current();
		if (trial == null || state != State.FEEDBACK || !trial.options().contains(form)) {
			return false;
		}
		queue.clear();
		return start(trial.event(form));
	}

	/** From feedback to the next trial, or to the end of the run. */
	public void next() {
		if (state != State.FEEDBACK) {
			return;
		}
		queue.clear();
		feedback = null;
		heardAt = NOT_HEARD;
		index++;
		if (index >= trials.size()) {
			state = State.DONE;
			report();
		} else {
			state = State.LISTENING;
		}
	}

	/** The score so far; the whole run's once {@link #state()} is DONE. */
	public HvptResult result() {
		return new HvptResult(phase, answered, correct, tallies);
	}

	private void report() {
		if (!finishedReported) {
			finishedReported = true;
			sink.finished(result(), clock.getAsLong());
		}
	}

	private boolean start(String event) {
		if (!sounds.has(event)) {
			return false;
		}
		sounds.play(event);
		return true;
	}

	/** The trials in order, for tests and the preview. */
	public List<HvptTrial> trials() {
		return new ArrayList<>(trials);
	}
}
