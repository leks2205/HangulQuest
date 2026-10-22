package io.github.leks2205.hangulquest.learn.fsrs;

import java.util.SplittableRandom;

/**
 * The FSRS-6 scheduler, ported from py-fsrs 6.3.2 ({@code fsrs/scheduler.py}, open-spaced-repetition).
 * Pure: the same card, grade and time always give the same result, which is what lets the review log
 * rebuild the card states ({@code ReviewLog.replay}). Even the interval fuzz is seeded from the card and
 * the review time, never from a global random source.
 *
 * <p>Grades are 1 Again, 2 Hard, 3 Good, 4 Easy.
 */
public final class Fsrs {
	public static final int AGAIN = 1;
	public static final int HARD = 2;
	public static final int GOOD = 3;
	public static final int EASY = 4;
	public static final long DAY_MS = 86_400_000L;

	private static final double[][] FUZZ_RANGES = {
			{2.5, 7.0, 0.15},
			{7.0, 20.0, 0.10},
			{20.0, Double.POSITIVE_INFINITY, 0.05},
	};

	private final FsrsParams params;
	private final double decay;
	private final double factor;

	public Fsrs(FsrsParams params) {
		this.params = params;
		this.decay = -params.w(20);
		this.factor = Math.pow(0.9, 1.0 / decay) - 1;
	}

	public static Fsrs defaults() {
		return new Fsrs(FsrsParams.defaults());
	}

	public FsrsParams params() {
		return params;
	}

	/** Probability of recall now. Whole elapsed days, like the reference. 0 for a card never reviewed. */
	public double retrievability(CardState card, long now) {
		if (card.isNew() || card.lastReview <= 0 || card.stability <= 0) {
			return 0;
		}
		long elapsedDays = Math.max(0, Math.floorDiv(now - card.lastReview, DAY_MS));
		return Math.pow(1 + factor * elapsedDays / card.stability, decay);
	}

	/** Days until recall probability falls to the desired retention; not rounded. */
	public double intervalDays(double stability) {
		return stability / factor * (Math.pow(params.desiredRetention(), 1.0 / decay) - 1);
	}

	/** Returns the card after one review; the argument is not changed. */
	public CardState review(CardState previous, int grade, long now) {
		if (grade < AGAIN || grade > EASY) {
			throw new IllegalArgumentException("grade must be 1 to 4: " + grade);
		}
		CardState card = previous.copy();
		boolean fresh = card.isNew();
		long sinceLastDays = card.lastReview > 0 ? Math.floorDiv(now - card.lastReview, DAY_MS) : Long.MAX_VALUE;
		boolean sameDay = !fresh && sinceLastDays < 1;
		CardState.State before = fresh ? CardState.State.LEARNING : card.state;
		long intervalMs;

		switch (before) {
			case LEARNING -> {
				if (fresh) {
					card.stability = initialStability(grade);
					card.difficulty = initialDifficulty(grade, true);
					card.step = 0;
				} else {
					updateMemory(card, grade, now, sameDay);
				}
				intervalMs = stepOrGraduate(card, grade, params.learningStepsMs(), CardState.State.LEARNING);
			}
			case REVIEW -> {
				updateMemory(card, grade, now, sameDay);
				if (grade == AGAIN) {
					card.lapses++;
				}
				long[] relearning = params.relearningStepsMs();
				if (grade == AGAIN && relearning.length > 0) {
					card.state = CardState.State.RELEARNING;
					card.step = 0;
					intervalMs = relearning[0];
				} else {
					card.state = CardState.State.REVIEW;
					intervalMs = nextIntervalDays(card.stability) * DAY_MS;
				}
			}
			case RELEARNING -> {
				updateMemory(card, grade, now, sameDay);
				intervalMs = stepOrGraduate(card, grade, params.relearningStepsMs(), CardState.State.RELEARNING);
			}
			default -> throw new IllegalStateException("unknown card state " + before);
		}

		if (params.fuzz() && card.state == CardState.State.REVIEW) {
			intervalMs = fuzzed(intervalMs, seed(card.stability, now));
		}
		card.reps++;
		card.lastReview = now;
		card.due = now + intervalMs;
		return card;
	}

	/** Stability and difficulty after a review of a card that already has a memory. */
	private void updateMemory(CardState card, int grade, long now, boolean sameDay) {
		if (sameDay) {
			card.stability = shortTermStability(card.stability, grade);
		} else {
			card.stability = nextStability(card.difficulty, card.stability, retrievability(card, now), grade);
		}
		card.difficulty = nextDifficulty(card.difficulty, grade);
	}

	/** The learning and relearning branches share one step machine. Sets state and step, returns the delay. */
	private long stepOrGraduate(CardState card, int grade, long[] steps, CardState.State inState) {
		if (steps.length == 0 || (card.step >= steps.length && grade != AGAIN)) {
			return graduate(card);
		}
		switch (grade) {
			case AGAIN -> {
				card.step = 0;
				card.state = inState;
				return steps[0];
			}
			case HARD -> {
				card.state = inState;
				if (card.step == 0 && steps.length == 1) {
					return Math.round(steps[0] * 1.5);
				}
				if (card.step == 0) {
					return (steps[0] + steps[1]) / 2;
				}
				return steps[card.step];
			}
			case GOOD -> {
				if (card.step + 1 == steps.length) {
					return graduate(card);
				}
				card.step++;
				card.state = inState;
				return steps[card.step];
			}
			default -> {
				return graduate(card);
			}
		}
	}

	private long graduate(CardState card) {
		card.state = CardState.State.REVIEW;
		card.step = 0;
		return nextIntervalDays(card.stability) * DAY_MS;
	}

	/** Whole days, at least 1, at most the maximum interval. */
	public long nextIntervalDays(double stability) {
		long days = (long) Math.rint(intervalDays(stability));
		return Math.min(Math.max(days, 1), params.maximumIntervalDays());
	}

	private double initialStability(int grade) {
		return Math.max(params.w(grade - 1), FsrsParams.STABILITY_MIN);
	}

	private double initialDifficulty(int grade, boolean clamp) {
		double value = params.w(4) - Math.exp(params.w(5) * (grade - 1)) + 1;
		return clamp ? clampDifficulty(value) : value;
	}

	private static double clampDifficulty(double value) {
		return Math.min(Math.max(value, FsrsParams.MIN_DIFFICULTY), FsrsParams.MAX_DIFFICULTY);
	}

	private static double clampStability(double value) {
		return Math.max(value, FsrsParams.STABILITY_MIN);
	}

	private double shortTermStability(double stability, int grade) {
		double increase = Math.exp(params.w(17) * (grade - 3 + params.w(18))) * Math.pow(stability, -params.w(19));
		if (grade >= HARD) {
			increase = Math.max(increase, 1.0);
		}
		return clampStability(stability * increase);
	}

	private double nextDifficulty(double difficulty, int grade) {
		double delta = -(params.w(6) * (grade - 3));
		double damped = difficulty + (10.0 - difficulty) * delta / 9.0;
		double reverted = params.w(7) * initialDifficulty(EASY, false) + (1 - params.w(7)) * damped;
		return clampDifficulty(reverted);
	}

	private double nextStability(double difficulty, double stability, double retrievability, int grade) {
		double next;
		if (grade == AGAIN) {
			double longTerm = params.w(11) * Math.pow(difficulty, -params.w(12))
					* (Math.pow(stability + 1, params.w(13)) - 1) * Math.exp((1 - retrievability) * params.w(14));
			double shortTerm = stability / Math.exp(params.w(17) * params.w(18));
			next = Math.min(longTerm, shortTerm);
		} else {
			double hardPenalty = grade == HARD ? params.w(15) : 1;
			double easyBonus = grade == EASY ? params.w(16) : 1;
			next = stability * (1 + Math.exp(params.w(8)) * (11 - difficulty) * Math.pow(stability, -params.w(9))
					* (Math.exp((1 - retrievability) * params.w(10)) - 1) * hardPenalty * easyBonus);
		}
		return clampStability(next);
	}

	private long fuzzed(long intervalMs, long seed) {
		long days = intervalMs / DAY_MS;
		if (days < 2.5) {
			return intervalMs;
		}
		double delta = 1.0;
		for (double[] range : FUZZ_RANGES) {
			delta += range[2] * Math.max(Math.min((double) days, range[1]) - range[0], 0.0);
		}
		long max = Math.min((long) Math.rint(days + delta), params.maximumIntervalDays());
		long min = Math.max(2, (long) Math.rint(days - delta));
		min = Math.min(min, max);
		double pick = new SplittableRandom(seed).nextDouble() * (max - min + 1) + min;
		return Math.min((long) Math.rint(pick), params.maximumIntervalDays()) * DAY_MS;
	}

	private static long seed(double stability, long now) {
		return Double.doubleToLongBits(stability) * 0x9E3779B97F4A7C15L ^ now;
	}
}
