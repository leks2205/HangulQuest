package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * The single place that turns what the player did into an FSRS grade. Everything else asks here.
 *
 * <pre>
 * 4-option MC, right, quick (under twice the rolling median)  Good
 * 4-option MC, right, slow                                    Hard
 * 4-option MC, wrong                                          Again
 * 2- or 3-option MC                                           logged, schedule unchanged
 * quest step on a due word, no hint                           Good
 * quest step on a due word, hint used                         Hard
 * quest step, assisted (creative/spectator) or word not due   exposure only
 * self-graded card                                            as chosen
 * typed or tile answer, one jamo off                          Hard (further off: Again)
 * </pre>
 *
 * <p>The instance only holds the player's rolling median of right-answer latencies.
 */
public final class Grader {
	/** What an outcome means: the mode to log and the grade, null when nothing is graded. */
	public record Graded(ReviewMode mode, Integer grade) {
		/** True when the FSRS card moves. */
		public boolean schedules() {
			return grade != null && mode.schedules();
		}

		/** The answer was right (Hard or better). */
		public boolean correct() {
			return grade != null && grade > Fsrs.AGAIN;
		}

		/** A right answer that counts as unaided recall for {@link Mastery}. */
		public boolean recall() {
			return correct() && mode.recall();
		}
	}

	public static final Graded EXPOSURE = new Graded(ReviewMode.EXPOSURE, null);
	/** Used until the player has enough answers for a median. */
	public static final long DEFAULT_MEDIAN_MS = 4000;
	public static final int MIN_SAMPLES = 5;
	public static final int WINDOW = 25;
	/** Slower than this many medians counts as a slow answer. */
	public static final double SLOW_FACTOR = 2.0;
	/** Passed when the answer time is not known; such an answer counts as quick. */
	public static final long UNKNOWN_LATENCY = -1;

	private final Deque<Long> latencies = new ArrayDeque<>();

	/** A multiple-choice answer. Right answers feed the rolling median after they are graded. */
	public synchronized Graded multipleChoice(int options, boolean correct, long latencyMs) {
		ReviewMode mode = ReviewMode.multipleChoice(options);
		if (!mode.schedules()) {
			return new Graded(mode, correct ? Fsrs.GOOD : Fsrs.AGAIN);
		}
		if (!correct) {
			return new Graded(mode, Fsrs.AGAIN);
		}
		boolean slow = latencyMs >= 0 && latencyMs >= SLOW_FACTOR * medianMs();
		if (latencyMs >= 0) {
			latencies.addLast(latencyMs);
			while (latencies.size() > WINDOW) {
				latencies.removeFirst();
			}
		}
		return new Graded(mode, slow ? Fsrs.HARD : Fsrs.GOOD);
	}

	/** The rolling median of right MC4 answers, or the default while there are too few. */
	public synchronized long medianMs() {
		if (latencies.size() < MIN_SAMPLES) {
			return DEFAULT_MEDIAN_MS;
		}
		long[] sorted = latencies.stream().mapToLong(Long::longValue).toArray();
		Arrays.sort(sorted);
		int mid = sorted.length / 2;
		return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
	}

	/**
	 * A quest step that taught a word which was due. Assisted completions teach but prove nothing.
	 * Whether the word was due is decided by the caller.
	 */
	public static Graded quest(boolean assisted, boolean hinted) {
		if (assisted) {
			return EXPOSURE;
		}
		return hinted ? new Graded(ReviewMode.QUEST_HINTED, Fsrs.HARD) : new Graded(ReviewMode.QUEST_UNAIDED, Fsrs.GOOD);
	}

	/** A card the player graded themselves, 1 to 4. */
	public static Graded selfGraded(int chosen) {
		return new Graded(ReviewMode.SELF, Math.max(Fsrs.AGAIN, Math.min(Fsrs.EASY, chosen)));
	}

	/** A typed answer, by how many jamo it is off: exact Good, one off Hard, more Again. */
	public static Graded typed(int jamoOff) {
		return new Graded(ReviewMode.TYPED, byDistance(jamoOff));
	}

	/** An assembled syllable-tile answer, graded like a typed one. */
	public static Graded tile(int jamoOff) {
		return new Graded(ReviewMode.TILE, byDistance(jamoOff));
	}

	/** A held-out probe: logged with its result, never moves the schedule. */
	public static Graded probe(boolean correct) {
		return new Graded(ReviewMode.PROBE, correct ? Fsrs.GOOD : Fsrs.AGAIN);
	}

	private static int byDistance(int jamoOff) {
		return jamoOff <= 0 ? Fsrs.GOOD : jamoOff == 1 ? Fsrs.HARD : Fsrs.AGAIN;
	}
}
