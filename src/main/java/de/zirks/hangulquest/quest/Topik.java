package de.zirks.hangulquest.quest;

/** Maps tree depth (1..18) to a TOPIK band, more gradual than one level per TOPIK band. */
public final class Topik {
	public static final int MAX_DEPTH = 18;

	private Topik() {
	}

	public static int levelForDepth(int depth) {
		if (depth <= 3) {
			return 1;
		}
		if (depth <= 6) {
			return 2;
		}
		if (depth <= 9) {
			return 3;
		}
		if (depth <= 12) {
			return 4;
		}
		if (depth <= 15) {
			return 5;
		}
		return 6;
	}
}
