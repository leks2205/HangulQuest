package io.github.leks2205.hangulquest.learn.hvpt;

import java.util.LinkedHashMap;
import java.util.Map;

/** The score of a finished run: overall and per contrast. */
public record HvptResult(HvptPhase phase, int trials, int correct, Map<String, Tally> byContrast) {
	public HvptResult {
		byContrast = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byContrast));
	}

	/** Answers and right answers; the unit of every per-contrast figure. */
	public record Tally(int trials, int correct) {
		/** Share of right answers, 0 for nothing answered yet. */
		public double accuracy() {
			return trials == 0 ? 0 : (double) correct / trials;
		}
	}

	public double accuracy() {
		return trials == 0 ? 0 : (double) correct / trials;
	}

	/** Adds one answer to a running tally map. */
	public static void add(Map<String, Tally> tallies, String contrast, boolean right) {
		tallies.merge(contrast, new Tally(1, right ? 1 : 0),
				(a, b) -> new Tally(a.trials() + b.trials(), a.correct() + b.correct()));
	}
}
