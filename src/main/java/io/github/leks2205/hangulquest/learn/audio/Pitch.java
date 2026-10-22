package io.github.leks2205.hangulquest.learn.audio;

import java.util.Arrays;

/**
 * Fundamental frequency (F0) by the YIN method (de Cheveigné and Kawahara, 2002): a difference function, its
 * cumulative mean normalisation, the first dip under an absolute threshold, and a parabola through it for
 * sub-sample accuracy. Pure arithmetic on samples, so it runs and is tested without a client. It is the base of a
 * pitch view for the sound forge: the cue that separates the Korean three-way stop contrast is the pitch the vowel
 * starts on (low after plain and tense stops, high after aspirated ones).
 */
public final class Pitch {
	/** Lowest and highest pitch looked for, in Hz; covers a deep male to a bright female voice. */
	public static final double MIN_HZ = 70;
	public static final double MAX_HZ = 400;
	/** A dip in the normalised difference below this counts as a clear period. */
	public static final double THRESHOLD = 0.15;
	/** Returned for a frame with no clear pitch: silence, noise, a consonant burst. */
	public static final double UNVOICED = 0;

	private Pitch() {
	}

	/** The F0 of one frame in Hz, or {@link #UNVOICED}. The frame must hold at least two periods of the lowest pitch. */
	public static double estimate(float[] frame, int sampleRate) {
		int maxLag = (int) Math.ceil(sampleRate / MIN_HZ);
		int minLag = Math.max(2, (int) Math.floor(sampleRate / MAX_HZ));
		int window = frame.length - maxLag;
		if (window < maxLag / 2 || maxLag + 2 >= frame.length) {
			return UNVOICED;
		}
		double[] diff = new double[maxLag + 1];
		for (int lag = 1; lag <= maxLag; lag++) {
			double sum = 0;
			for (int i = 0; i < window; i++) {
				double delta = frame[i] - frame[i + lag];
				sum += delta * delta;
			}
			diff[lag] = sum;
		}
		double[] normalised = new double[maxLag + 1];
		normalised[0] = 1;
		double running = 0;
		for (int lag = 1; lag <= maxLag; lag++) {
			running += diff[lag];
			normalised[lag] = running == 0 ? 1 : diff[lag] * lag / running;
		}
		int best = -1;
		for (int lag = minLag; lag < maxLag; lag++) {
			if (normalised[lag] < THRESHOLD) {
				while (lag + 1 < maxLag && normalised[lag + 1] < normalised[lag]) {
					lag++;
				}
				best = lag;
				break;
			}
		}
		if (best < 0) {
			return UNVOICED;
		}
		return sampleRate / refine(normalised, best);
	}

	/** Parabolic interpolation around the dip, for a lag between two samples. */
	private static double refine(double[] values, int lag) {
		if (lag <= 1 || lag + 1 >= values.length) {
			return lag;
		}
		double before = values[lag - 1];
		double at = values[lag];
		double after = values[lag + 1];
		double denominator = before - 2 * at + after;
		return denominator == 0 ? lag : lag + 0.5 * (before - after) / denominator;
	}

	/**
	 * One F0 value per hop over a whole clip ({@link #UNVOICED} where there is none). A frame of 40 ms and a hop of
	 * 10 ms follow a voice well.
	 */
	public static double[] contour(float[] samples, int sampleRate) {
		int frame = sampleRate * 40 / 1000;
		int hop = Math.max(1, sampleRate * 10 / 1000);
		if (samples.length < frame) {
			return new double[0];
		}
		int count = 1 + (samples.length - frame) / hop;
		double[] f0 = new double[count];
		for (int i = 0; i < count; i++) {
			f0[i] = estimate(Arrays.copyOfRange(samples, i * hop, i * hop + frame), sampleRate);
		}
		return f0;
	}

	/** The mean F0 over the first {@code count} voiced frames of a contour, 0 when there are none: where the vowel starts. */
	public static double onset(double[] contour, int count) {
		double sum = 0;
		int used = 0;
		for (double value : contour) {
			if (value > UNVOICED && used < count) {
				sum += value;
				used++;
			}
		}
		return used == 0 ? UNVOICED : sum / used;
	}
}
