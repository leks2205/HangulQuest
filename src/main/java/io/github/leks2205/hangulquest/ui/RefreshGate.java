package io.github.leks2205.hangulquest.ui;

/**
 * Says when a cached view model has to be rebuilt: once per client tick, and again when what it
 * was laid out for (rows, screen width) changes. Nothing is allocated per frame.
 */
final class RefreshGate {
	private long tick = -1;
	private int first;
	private int second;

	/** True the first time, and whenever the tick or one of the two layout inputs differs. */
	boolean due(long currentTick, int layoutA, int layoutB) {
		if (currentTick == tick && layoutA == first && layoutB == second) {
			return false;
		}
		tick = currentTick;
		first = layoutA;
		second = layoutB;
		return true;
	}

	/** Forces the next {@link #due} to be true. */
	void reset() {
		tick = -1;
	}
}
