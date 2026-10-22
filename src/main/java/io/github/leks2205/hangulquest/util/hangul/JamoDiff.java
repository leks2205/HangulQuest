package io.github.leks2205.hangulquest.util.hangul;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares the expected word with the typed one one jamo at a time (an edit distance over the keys
 * that type each text), and keeps the alignment so the screen can mark what differs. Spaces are
 * ignored: 물 한 잔 and 물한잔 are the same answer.
 *
 * @param ops      the alignment, left to right
 * @param distance how many jamo are substituted, missing or extra
 */
public record JamoDiff(List<Op> ops, int distance) {
	/** What happened to one position of the alignment. */
	public enum Kind {
		/** Typed and expected agree. */
		SAME,
		/** The expected jamo was typed as another one. */
		WRONG,
		/** The expected jamo is missing from the typed answer. */
		MISSING,
		/** The typed answer has a jamo that is not expected. */
		EXTRA
	}

	/**
	 * One alignment step. {@code expected} is 0 for an extra jamo and {@code typed} is 0 for a
	 * missing one.
	 */
	public record Op(Kind kind, int expected, int typed) {
	}

	public JamoDiff {
		ops = List.copyOf(ops);
	}

	public boolean exact() {
		return distance == 0;
	}

	/** Exactly one jamo off: substituted, missing or extra. */
	public boolean oneOff() {
		return distance == 1;
	}

	/** The differing step of a one-off answer, or null when the answer is not exactly one off. */
	public Op onlyDifference() {
		if (distance != 1) {
			return null;
		}
		for (Op op : ops) {
			if (op.kind() != Kind.SAME) {
				return op;
			}
		}
		return null;
	}

	/** The expected side of the alignment: every jamo the expected word has, missing ones included. */
	public String expectedJamo() {
		StringBuilder out = new StringBuilder();
		for (Op op : ops) {
			if (op.expected() != 0) {
				out.appendCodePoint(op.expected());
			}
		}
		return out.toString();
	}

	/** The typed side of the alignment: every jamo that was typed, extra ones included. */
	public String typedJamo() {
		StringBuilder out = new StringBuilder();
		for (Op op : ops) {
			if (op.typed() != 0) {
				out.appendCodePoint(op.typed());
			}
		}
		return out.toString();
	}

	public static JamoDiff of(String expected, String typed) {
		int[] want = keys(expected);
		int[] got = keys(typed);
		int rows = want.length + 1;
		int cols = got.length + 1;
		int[][] cost = new int[rows][cols];
		for (int i = 0; i < rows; i++) {
			cost[i][0] = i;
		}
		for (int j = 0; j < cols; j++) {
			cost[0][j] = j;
		}
		for (int i = 1; i < rows; i++) {
			for (int j = 1; j < cols; j++) {
				int same = cost[i - 1][j - 1] + (want[i - 1] == got[j - 1] ? 0 : 1);
				cost[i][j] = Math.min(same, Math.min(cost[i - 1][j], cost[i][j - 1]) + 1);
			}
		}
		List<Op> ops = new ArrayList<>();
		int i = want.length;
		int j = got.length;
		while (i > 0 || j > 0) {
			if (i > 0 && j > 0 && want[i - 1] == got[j - 1] && cost[i][j] == cost[i - 1][j - 1]) {
				ops.add(new Op(Kind.SAME, want[i - 1], got[j - 1]));
				i--;
				j--;
			} else if (i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + 1) {
				ops.add(new Op(Kind.WRONG, want[i - 1], got[j - 1]));
				i--;
				j--;
			} else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
				ops.add(new Op(Kind.MISSING, want[i - 1], 0));
				i--;
			} else {
				ops.add(new Op(Kind.EXTRA, 0, got[j - 1]));
				j--;
			}
		}
		Collections.reverse(ops);
		return new JamoDiff(ops, cost[want.length][got.length]);
	}

	/** The keys that type the text, without spaces. */
	private static int[] keys(String text) {
		String normal = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
		StringBuilder jamo = new StringBuilder();
		normal.codePoints().filter(cp -> !Character.isWhitespace(cp))
				.forEach(cp -> HangulCompose.appendJamo(jamo, cp));
		return jamo.codePoints().toArray();
	}
}
