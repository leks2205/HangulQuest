package io.github.leks2205.hangulquest.learn.fsrs;

/**
 * The FSRS memory state of one card, as it is saved in progress.json. Times are epoch milliseconds,
 * stability is in days. {@link State#NEW} is a card that has never been reviewed (py-fsrs calls this
 * Learning with no stability).
 */
public final class CardState {
	public enum State { NEW, LEARNING, REVIEW, RELEARNING }

	public double stability;
	public double difficulty;
	public State state = State.NEW;
	/** Position in the learning or relearning steps. */
	public int step;
	public int reps;
	public int lapses;
	public long lastReview;
	public long due;

	public CardState() {
	}

	public CardState copy() {
		CardState copy = new CardState();
		copy.stability = stability;
		copy.difficulty = difficulty;
		copy.state = state;
		copy.step = step;
		copy.reps = reps;
		copy.lapses = lapses;
		copy.lastReview = lastReview;
		copy.due = due;
		return copy;
	}

	public boolean isNew() {
		return state == State.NEW;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CardState c && Double.compare(stability, c.stability) == 0
				&& Double.compare(difficulty, c.difficulty) == 0 && state == c.state && step == c.step
				&& reps == c.reps && lapses == c.lapses && lastReview == c.lastReview && due == c.due;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(stability) * 31 + Long.hashCode(due);
	}

	@Override
	public String toString() {
		return "CardState[" + state + " step=" + step + " s=" + stability + " d=" + difficulty
				+ " reps=" + reps + " lapses=" + lapses + " last=" + lastReview + " due=" + due + "]";
	}
}
