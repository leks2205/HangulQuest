package io.github.leks2205.hangulquest.learn;

/** Leitner-style scheduling shared by word review and quest sidequests. */
public final class Srs {
	public static final long MINUTE = 60_000L;
	public static final long HOUR = 60 * MINUTE;
	public static final long DAY = 24 * HOUR;
	public static final int MAX_LEVEL = 6;

	/** Delay before the word is due again, by level (0 = fresh). */
	private static final long[] INTERVALS = {
			10 * MINUTE,
			5 * MINUTE,
			30 * MINUTE,
			2 * HOUR,
			8 * HOUR,
			DAY,
			3 * DAY,
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

	/** Practice (hint sidequests, ambient cards) never demotes a word. */
	public static int onPractice(int level, boolean correct) {
		return correct ? Math.min(MAX_LEVEL, Math.max(0, level) + 1) : Math.max(0, level);
	}
}
