package io.github.leks2205.hangulquest.learn.fsrs;

import java.util.Arrays;

/**
 * Everything that tunes the scheduler. Immutable; the {@code with...} methods return changed copies.
 * The default weights are the FSRS-6 defaults of py-fsrs 6.3.2 ({@code fsrs/scheduler.py}).
 */
public final class FsrsParams {
	public static final int WEIGHTS = 21;
	public static final double MIN_RETENTION = 0.80;
	public static final double MAX_RETENTION = 0.95;
	public static final double DEFAULT_RETENTION = 0.88;
	public static final int DEFAULT_MAX_INTERVAL_DAYS = 365;

	public static final double STABILITY_MIN = 0.001;
	public static final double MIN_DIFFICULTY = 1.0;
	public static final double MAX_DIFFICULTY = 10.0;

	private static final double[] DEFAULT_WEIGHTS = {
			0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
			1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
	};
	/** The parameter bounds py-fsrs validates against. */
	private static final double[] LOWER = {
			STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, 1.0, 0.001, 0.001, 0.001, 0.0, 0.0,
			0.001, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
	};
	private static final double[] UPPER = {
			100.0, 100.0, 100.0, 100.0, 10.0, 4.0, 4.0, 0.75, 4.5, 0.8, 3.5, 5.0, 0.25, 0.9, 4.0, 1.0, 6.0,
			2.0, 2.0, 0.8, 0.8,
	};

	private final double[] weights;
	private final double desiredRetention;
	private final int maximumIntervalDays;
	private final long[] learningStepsMs;
	private final long[] relearningStepsMs;
	private final boolean fuzz;

	private FsrsParams(double[] weights, double desiredRetention, int maximumIntervalDays,
					   long[] learningStepsMs, long[] relearningStepsMs, boolean fuzz) {
		if (weights.length != WEIGHTS) {
			throw new IllegalArgumentException("FSRS-6 needs " + WEIGHTS + " weights, got " + weights.length);
		}
		for (int i = 0; i < WEIGHTS; i++) {
			if (!(weights[i] >= LOWER[i] && weights[i] <= UPPER[i])) {
				throw new IllegalArgumentException("weight " + i + " = " + weights[i] + " is out of bounds");
			}
		}
		this.weights = weights.clone();
		this.desiredRetention = Math.max(MIN_RETENTION, Math.min(MAX_RETENTION, desiredRetention));
		this.maximumIntervalDays = Math.max(1, maximumIntervalDays);
		this.learningStepsMs = learningStepsMs.clone();
		this.relearningStepsMs = relearningStepsMs.clone();
		this.fuzz = fuzz;
	}

	public static FsrsParams defaults() {
		return new FsrsParams(DEFAULT_WEIGHTS, DEFAULT_RETENTION, DEFAULT_MAX_INTERVAL_DAYS,
				new long[] {60_000L, 600_000L}, new long[] {600_000L}, true);
	}

	/** Desired retention is kept inside 0.80 to 0.95. */
	public FsrsParams withDesiredRetention(double retention) {
		return new FsrsParams(weights, retention, maximumIntervalDays, learningStepsMs, relearningStepsMs, fuzz);
	}

	public FsrsParams withWeights(double[] changed) {
		return new FsrsParams(changed, desiredRetention, maximumIntervalDays, learningStepsMs, relearningStepsMs, fuzz);
	}

	public FsrsParams withMaximumIntervalDays(int days) {
		return new FsrsParams(weights, desiredRetention, days, learningStepsMs, relearningStepsMs, fuzz);
	}

	public FsrsParams withFuzz(boolean on) {
		return new FsrsParams(weights, desiredRetention, maximumIntervalDays, learningStepsMs, relearningStepsMs, on);
	}

	public FsrsParams withSteps(long[] learningMs, long[] relearningMs) {
		return new FsrsParams(weights, desiredRetention, maximumIntervalDays, learningMs, relearningMs, fuzz);
	}

	public double w(int index) {
		return weights[index];
	}

	public double[] weights() {
		return weights.clone();
	}

	public double desiredRetention() {
		return desiredRetention;
	}

	public int maximumIntervalDays() {
		return maximumIntervalDays;
	}

	public long[] learningStepsMs() {
		return learningStepsMs.clone();
	}

	public long[] relearningStepsMs() {
		return relearningStepsMs.clone();
	}

	public boolean fuzz() {
		return fuzz;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FsrsParams p && Arrays.equals(weights, p.weights)
				&& desiredRetention == p.desiredRetention && maximumIntervalDays == p.maximumIntervalDays
				&& Arrays.equals(learningStepsMs, p.learningStepsMs)
				&& Arrays.equals(relearningStepsMs, p.relearningStepsMs) && fuzz == p.fuzz;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(weights) * 31 + Double.hashCode(desiredRetention);
	}
}
