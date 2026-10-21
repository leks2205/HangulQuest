package io.github.leks2205.hangulquest.learn;

/** Leitner-style scheduling shared by word review and quest sidequests. */
public final class Srs {
	public static final long MINUTE = 60_000L;
	public static final long HOUR = 60 * MINUTE;
	public static final long DAY = 24 * HOUR;
	public static final int MAX_LEVEL = 8;
	/** From this level on a word counts as mastered; saved levels 0-6 from older versions stay valid. */
	public static final int MASTERED_LEVEL = 6;

	/** Delay before the word is due again, by level (0 = fresh). Strictly increasing. */
	private static final long[] INTERVALS = {
			10 * MINUTE,
			HOUR,
			8 * HOUR,
			DAY,
			3 * DAY,
			7 * DAY,
			16 * DAY,
			35 * DAY,
			80 * DAY,
	};

	private Srs() {
	}

	public static long intervalMs(int level) {
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		return INTERVALS[clamped];
	}

	public static long dueAt(int level, long now) {
		return now + intervalMs(level);
	}

	public static int onCorrect(int level) {
		return Math.min(MAX_LEVEL, Math.max(0, level) + 1);
	}

	public static int onWrong(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level) - 1);
	}

	public static long retryAt(long now) {
		return now + 2 * MINUTE;
	}
}
