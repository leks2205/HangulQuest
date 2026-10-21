package io.github.leks2205.hangulquest.learn;

/**
 * When a word counts as "durably known" (successive relearning): a right unaided recall on at least
 * three different study days and a memory stability of at least 21 days. Only recall modes count
 * ({@link ReviewMode#recall()}); guesses among two or three options and hinted quest steps never do.
 * A word the player marked as known by hand counts as mastered too, but is not "durably known" until
 * it has earned it.
 */
public final class Mastery {
	public static final int RECALL_DAYS = 3;
	public static final double STABILITY_DAYS = 21;

	private Mastery() {
	}

	/** Recall on enough different study days and a stable memory. */
	public static boolean durablyKnown(WordProgress progress) {
		return progress != null && progress.fsrs != null
				&& ExposureLedger.recallDays(progress) >= RECALL_DAYS
				&& progress.fsrs.stability >= STABILITY_DAYS;
	}

	/** What the journal and the achievements count: durably known, or marked known by hand. */
	public static boolean mastered(WordProgress progress) {
		return progress != null && (progress.known || durablyKnown(progress));
	}

	public static int count(ProgressData data) {
		int count = 0;
		for (WordProgress progress : data.words.values()) {
			if (mastered(progress)) {
				count++;
			}
		}
		return count;
	}
}
