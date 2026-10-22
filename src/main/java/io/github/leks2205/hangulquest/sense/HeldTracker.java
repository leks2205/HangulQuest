package io.github.leks2205.hangulquest.sense;

/**
 * How long each hand has held the same kind of item. Compares ids only, so a changing count does
 * not reset the clock. An empty hand is null and has held nothing.
 */
public final class HeldTracker {
	private String main;
	private String off;
	private long mainSince;
	private long offSince;

	public void update(String mainHand, String offHand, long now) {
		if (!same(main, mainHand)) {
			main = mainHand;
			mainSince = now;
		}
		if (!same(off, offHand)) {
			off = offHand;
			offSince = now;
		}
	}

	public long mainMs(long now) {
		return main == null ? 0L : Math.max(0L, now - mainSince);
	}

	public long offMs(long now) {
		return off == null ? 0L : Math.max(0L, now - offSince);
	}

	public void clear() {
		main = null;
		off = null;
	}

	private static boolean same(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}
}
