package io.github.leks2205.hangulquest.ui.quiz;

import java.util.List;
import java.util.Optional;

/**
 * The state machine of one review quiz, free of any Minecraft types so it can be tested.
 *
 * <p>An automatic quiz is only <em>offered</em> ({@link State#PROMPT}): the HUD shows "press the key"
 * for {@link #PROMPT_MS} and the prompt then vanishes without recording anything. The quiz becomes
 * answerable ({@link State#ACTIVE}) only when the player opens it, which happens in the quiz screen.
 * A review is recorded in exactly one place, {@link #answer}; timing out and dismissing never record.
 */
public final class QuizSession {
	/** How long an automatic quiz waits for the player before it is dropped. */
	public static final long PROMPT_MS = 20_000L;

	public enum State { IDLE, PROMPT, ACTIVE }

	/** One multiple-choice question about a word. */
	public record Quiz(String wordId, List<String> options, int correctIndex) {
		public Quiz {
			options = List.copyOf(options);
		}
	}

	public record Result(String wordId, boolean correct) {
	}

	/** Where a real answer goes; the production implementation is {@code LearningService.answer}. */
	@FunctionalInterface
	public interface Recorder {
		void record(String wordId, boolean correct);
	}

	private State state = State.IDLE;
	private Quiz quiz;
	private long promptUntil;

	public State state() {
		return state;
	}

	public Quiz quiz() {
		return state == State.IDLE ? null : quiz;
	}

	/** An automatic quiz: shown as a prompt only. Ignored while another quiz is pending. */
	public boolean offer(Quiz offered, long now) {
		if (offered == null || state != State.IDLE) {
			return false;
		}
		quiz = offered;
		promptUntil = now + PROMPT_MS;
		state = State.PROMPT;
		return true;
	}

	/** A quiz the player asked for (review key, journal): answerable at once. */
	public void begin(Quiz requested) {
		if (requested == null) {
			return;
		}
		quiz = requested;
		state = State.ACTIVE;
	}

	/** Drops an expired prompt. Records nothing. Returns true when a prompt was dropped. */
	public boolean tick(long now) {
		if (state == State.PROMPT && now >= promptUntil) {
			clear();
			return true;
		}
		return false;
	}

	public boolean promptVisible(long now) {
		return state == State.PROMPT && now < promptUntil;
	}

	/** Time left before the prompt disappears; 0 when there is none. */
	public long promptRemainingMs(long now) {
		return promptVisible(now) ? promptUntil - now : 0;
	}

	/** Turns a still-visible prompt into the answerable quiz. */
	public boolean open(long now) {
		if (!promptVisible(now)) {
			return false;
		}
		state = State.ACTIVE;
		return true;
	}

	/** Esc or leaving the screen: the quiz is dropped and nothing is recorded. */
	public void dismiss() {
		clear();
	}

	/** The only way a review gets recorded. Ignored unless the quiz is open; bad indexes are ignored. */
	public Optional<Result> answer(int index, Recorder recorder) {
		if (state != State.ACTIVE || index < 0 || index >= quiz.options().size()) {
			return Optional.empty();
		}
		Result result = new Result(quiz.wordId(), index == quiz.correctIndex());
		clear();
		recorder.record(result.wordId(), result.correct());
		return Optional.of(result);
	}

	private void clear() {
		state = State.IDLE;
		quiz = null;
		promptUntil = 0;
	}
}
