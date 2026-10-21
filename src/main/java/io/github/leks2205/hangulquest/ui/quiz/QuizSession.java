package io.github.leks2205.hangulquest.ui.quiz;

import io.github.leks2205.hangulquest.ui.quiz.cards.Card;

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

	/**
	 * One question about a word. A plain multiple-choice quiz has options and a correct index; a quiz
	 * with a {@code card} is driven by the card instead and its options may be empty.
	 */
	public record Quiz(String wordId, List<String> options, int correctIndex, Card card) {
		public Quiz {
			options = List.copyOf(options);
		}

		public Quiz(String wordId, List<String> options, int correctIndex) {
			this(wordId, options, correctIndex, null);
		}

		/** A quiz that is played on this card. */
		public static Quiz of(String wordId, Card card) {
			return new Quiz(wordId, List.of(), -1, card);
		}
	}

	public record Result(String wordId, boolean correct) {
	}

	/** Where a real answer goes; the production implementation is {@code LearningService.answer}. */
	@FunctionalInterface
	public interface Recorder {
		void record(String wordId, boolean correct);
	}

	/** Where a card's first attempt goes; the production implementation records it with the learning service. */
	@FunctionalInterface
	public interface CardRecorder {
		void record(String wordId, Card.Answer answer, long latencyMs);
	}

	private State state = State.IDLE;
	private Quiz quiz;
	private long promptUntil;
	private long activeSince;

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
		activeSince = System.currentTimeMillis();
	}

	/** When the quiz became answerable (epoch ms); the answer time is measured from here. */
	public long activeSince() {
		return activeSince;
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
		activeSince = System.currentTimeMillis();
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

	/**
	 * The card counterpart of {@link #answer(int, Recorder)}: records the first attempt on the open card,
	 * with the time since the quiz became answerable. The card carries on (retry, feedback) on its own
	 * and is never recorded again.
	 */
	public Optional<Result> answer(Card.Answer answer, long now, CardRecorder recorder) {
		if (state != State.ACTIVE || quiz == null || quiz.card() == null || answer == null) {
			return Optional.empty();
		}
		Result result = new Result(quiz.wordId(), answer.correct());
		long latencyMs = Math.max(0, now - activeSince);
		clear();
		recorder.record(result.wordId(), answer, latencyMs);
		return Optional.of(result);
	}

	private void clear() {
		state = State.IDLE;
		quiz = null;
		promptUntil = 0;
	}
}
