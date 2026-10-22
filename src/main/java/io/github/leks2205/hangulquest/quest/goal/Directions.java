package io.github.leks2205.hangulquest.quest.goal;

import java.util.List;

/**
 * The four compass directions as the game has them: north is -Z, south +Z, east +X, west -X, and the yaw of a
 * player looking south is 0, west 90, north 180 (or -180), east -90. Shared by the walk goal and the facing
 * condition.
 */
public final class Directions {
	public static final List<String> ALL = List.of("NORTH", "SOUTH", "EAST", "WEST");

	private Directions() {
	}

	public static boolean valid(String direction) {
		return ALL.contains(direction);
	}

	/** 북쪽, 남쪽, 동쪽, 서쪽. */
	public static String ko(String direction) {
		return switch (direction) {
			case "NORTH" -> "북쪽";
			case "SOUTH" -> "남쪽";
			case "EAST" -> "동쪽";
			default -> "서쪽";
		};
	}

	/** Blocks moved along the direction by a step of (dx, dz): positive when the step goes that way. */
	public static double along(String direction, double dx, double dz) {
		return switch (direction) {
			case "NORTH" -> -dz;
			case "SOUTH" -> dz;
			case "EAST" -> dx;
			default -> -dx;
		};
	}

	/** The yaw (degrees, as the game stores it) of a player facing the direction. */
	public static float yaw(String direction) {
		return switch (direction) {
			case "NORTH" -> 180.0F;
			case "SOUTH" -> 0.0F;
			case "EAST" -> -90.0F;
			default -> 90.0F;
		};
	}

	/** Whether a yaw points at the direction, within 45 degrees. */
	public static boolean faces(String direction, float yaw) {
		float diff = Math.abs(((yaw - yaw(direction)) % 360.0F + 540.0F) % 360.0F - 180.0F);
		return diff <= 45.0F;
	}
}
