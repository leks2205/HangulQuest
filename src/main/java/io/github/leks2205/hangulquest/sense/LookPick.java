package io.github.leks2205.hangulquest.sense;

/** Numbers for the sensor's own long-range entity pick (vanilla's crosshair reaches about 3 blocks). */
public final class LookPick {
	public static final double RANGE = 64.0;
	public static final double SCOPING_RANGE = 128.0;

	private LookPick() {
	}

	public static double range(boolean scoping) {
		return scoping ? SCOPING_RANGE : RANGE;
	}

	/** An entity is seen only if no block is closer along the same ray. */
	public static boolean visible(double entityDistanceSq, double blockDistanceSq) {
		return entityDistanceSq <= blockDistanceSq;
	}
}
