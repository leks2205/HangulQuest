package io.github.leks2205.hangulquest.sense;

import java.util.function.IntPredicate;

/** What is above the player's head: pure rules, so the game-side code only supplies lookups. */
public final class SkyExposure {
	/** A solid block this far above the player (or closer) makes the spot roofed. */
	public static final int ROOF_RANGE = 6;

	private SkyExposure() {
	}

	/** The eye is at or above the surface heightmap, so nothing (not even water or leaves) covers it. */
	public static boolean canSeeSky(int eyeY, int surfaceHeight) {
		return eyeY >= surfaceHeight;
	}

	/** True when some block within {@link #ROOF_RANGE} above the head blocks movement. */
	public static boolean roofed(int headY, IntPredicate solidAt) {
		for (int dy = 1; dy <= ROOF_RANGE; dy++) {
			if (solidAt.test(headY + dy)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Underground means the surface heightmap (the y above the topmost blocking block) is more than
	 * the player's height above their feet. Meaningless without a sky, so false there.
	 */
	public static boolean underground(boolean hasSky, int feetY, int surfaceHeight) {
		return hasSky && feetY + 1 < surfaceHeight;
	}
}
