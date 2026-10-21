package io.github.leks2205.hangulquest.util.hangul;

import io.github.leks2205.hangulquest.learn.Grader;

import java.util.List;

/**
 * Grades a typed answer: the jamo-level diff, turned into a {@link Grader.Graded} by the grader's own
 * rule (exact Good, one jamo off Hard, further off Again). This is the entry point for cards.
 */
public final class TypedAnswer {
	/** The grade to log and the diff to show when the answer was not exact. */
	public record Result(Grader.Graded graded, JamoDiff diff) {
		public boolean exact() {
			return diff.exact();
		}
	}

	private TypedAnswer() {
	}

	public static Result grade(String expected, String typed) {
		JamoDiff diff = JamoDiff.of(expected, typed);
		return new Result(Grader.typed(diff.distance()), diff);
	}

	/** Several spellings are right (a word with a synonym): the closest one decides. */
	public static Result grade(List<String> accepted, String typed) {
		Result best = null;
		for (String expected : accepted) {
			Result result = grade(expected, typed);
			if (best == null || result.diff().distance() < best.diff().distance()) {
				best = result;
			}
		}
		if (best == null) {
			throw new IllegalArgumentException("no accepted answer");
		}
		return best;
	}
}
