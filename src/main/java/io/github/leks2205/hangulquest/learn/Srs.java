package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;

/**
 * Scheduling for word review and quest sidequests. It used to be a Leitner ladder; the schedule is now
 * FSRS-6 ({@link Fsrs}) and this class keeps the old entry points: the level the journal shows, and the
 * cards that old levels and "known" words start from.
 */
public final class Srs {
	public static final long MINUTE = 60_000L;
	public static final long HOUR = 60 * MINUTE;
	public static final long DAY = 24 * HOUR;
	/** Highest display level (journal dots). */
	public static final int MAX_LEVEL = 8;
	/** Stability in days that a Leitner level starts from (levels 0 to 8); schema v3 to v4 uses the same table. */
	public static final double[] LEVEL_STABILITY = {0.1, 0.2, 0.5, 1, 2, 4, 8, 16, 35};
	/** A word marked known by hand starts here. */
	public static final double KNOWN_STABILITY = 30;
	public static final double START_DIFFICULTY = 5;

	private static final Fsrs DEFAULT = Fsrs.defaults();

	private Srs() {
	}

	/** The scheduler with default settings. */
	public static Fsrs fsrs() {
		return DEFAULT;
	}

	/** One review with the default scheduler. */
	public static CardState review(CardState card, int grade, long now) {
		return DEFAULT.review(card, grade, now);
	}

	/** The display level for a stability: the last level whose start stability has been reached. */
	public static int levelFor(double stability) {
		int level = 0;
		for (int i = 1; i <= MAX_LEVEL; i++) {
			if (stability >= LEVEL_STABILITY[i] - 1e-9) {
				level = i;
			}
		}
		return level;
	}

	/** A review-state card for an old level (or a Lute status), due after its natural interval. */
	public static CardState seed(int level, long now, Fsrs fsrs) {
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		return seedWithStability(LEVEL_STABILITY[clamped], now, fsrs);
	}

	/** The card of a word the player says they know. */
	public static CardState seedKnown(long now, Fsrs fsrs) {
		return seedWithStability(KNOWN_STABILITY, now, fsrs);
	}

	/** A review-state card with the given stability, due after its natural interval. */
	public static CardState seedWithStability(double stability, long now, Fsrs fsrs) {
		CardState card = new CardState();
		card.state = CardState.State.REVIEW;
		card.stability = stability;
		card.difficulty = START_DIFFICULTY;
		card.lastReview = now;
		card.due = now + Math.round(fsrs.intervalDays(stability) * DAY);
		return card;
	}
}
