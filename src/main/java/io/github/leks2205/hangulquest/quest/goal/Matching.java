package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.sense.Observation;

/** Shared matching rules for goal types: block families, fish and the dwell timer. */
public final class Matching {
	private Matching() {
	}

	/** Beds, doors, planks, fences, wool and logs are interchangeable within their family. */
	public static boolean matchesBlock(String goal, String observed) {
		if (goal == null || observed == null) {
			return false;
		}
		if (goal.equals(observed)) {
			return true;
		}
		if (goal.startsWith("#")) { // a tag: any flower, any sapling
			return io.github.leks2205.hangulquest.quest.spatial.BlockSpec.matches(goal, observed);
		}
		String family = blockFamily(goal);
		return !family.isEmpty() && family.equals(blockFamily(observed));
	}

	/** The goal's own match, or one of the other referents of the word the step teaches. */
	public static boolean accepts(GoalEvaluator.Tracker tracker, String goal, String observed) {
		if (tracker.accepted.isExact(goal)) {
			return goal.equals(observed); // the clue says the colour: a red bed is no blue bed
		}
		return matchesBlock(goal, observed) || tracker.accepted.matches(goal, observed);
	}

	/** The family match of {@link #matchesBlock}, unless the clue names the colour of the goal's block. */
	public static boolean matches(GoalEvaluator.Tracker tracker, String goal, String observed) {
		return tracker.accepted.isExact(goal) ? goal.equals(observed) : matchesBlock(goal, observed);
	}

	/** The creature the goal names, or one the taught word stands for as well (any fish for 물고기). */
	public static boolean sameEntity(GoalEvaluator.Tracker tracker, String goal, String observed) {
		return observed != null && (observed.equals(goal) || tracker.accepted.matches(goal, observed));
	}

	/** How many of the item the player holds, counting every referent the step accepts. */
	public static int heldCount(Observation obs, String item, GoalEvaluator.Tracker tracker) {
		if (tracker.accepted.isExact(item)) {
			return obs.count(item);
		}
		if (!tracker.accepted.widens(item)) {
			return familyCount(obs, item);
		}
		int total = 0;
		for (java.util.Map.Entry<String, Integer> entry : obs.inventory.entrySet()) {
			if (accepts(tracker, item, entry.getKey())) {
				total += entry.getValue();
			}
		}
		return total;
	}

	/** "boat" for every boat and raft (the ones with a chest too), "minecart" for the carts, else "". */
	public static String vehicleFamily(String id) {
		if (id == null) {
			return "";
		}
		String path = id.substring(id.indexOf(':') + 1);
		if (path.endsWith("_boat") || path.endsWith("_raft")) {
			return "boat";
		}
		return path.equals("minecart") || path.endsWith("_minecart") ? "minecart" : "";
	}

	/** The same vehicle, or two of one family (an oak boat and a spruce boat). */
	public static boolean sameVehicle(String goal, String observed) {
		if (goal == null || observed == null) {
			return false;
		}
		String family = vehicleFamily(goal);
		return goal.equals(observed) || !family.isEmpty() && family.equals(vehicleFamily(observed));
	}

	/** A candle (or one on a cake), which is lit with flint and steel and put out by hand. */
	public static boolean isCandle(String id) {
		if (id == null) {
			return false;
		}
		String path = id.substring(id.indexOf(':') + 1);
		return path.equals("candle") || path.endsWith("_candle") || path.equals("candle_cake")
				|| path.endsWith("_candle_cake");
	}

	/** Blocks with a {@code lit} state the player can change by hand: candles and campfires. */
	public static boolean isFlame(String id) {
		return isCandle(id) || id != null && blockFamily(id).equals("campfire");
	}

	public static boolean isBed(String id) {
		return id != null && blockFamily(id).equals("bed");
	}

	public static String blockFamily(String id) {
		int colon = id.indexOf(':');
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		if (path.endsWith("_bed")) {
			return "bed";
		}
		if (path.endsWith("_door") && !path.equals("iron_door")) {
			return "door";
		}
		if (path.endsWith("_planks")) {
			return "planks";
		}
		if (path.endsWith("_fence") && !path.endsWith("_fence_gate")) {
			return "fence";
		}
		if (path.endsWith("_wool")) {
			return "wool";
		}
		if (path.endsWith("_log") || path.endsWith("_wood")) {
			return "log";
		}
		if (path.equals("snow") || path.equals("snow_block")) {
			return "snow";
		}
		if (path.equals("campfire") || path.equals("soul_campfire")) {
			return "campfire";
		}
		if (path.endsWith("_sign")) { // signs and hanging signs, standing or on a wall
			return "sign";
		}
		if (path.equals("chest") || path.equals("trapped_chest")) {
			return "chest";
		}
		if (path.equals("ice") || path.equals("packed_ice") || path.equals("blue_ice")) {
			return "ice";
		}
		if (path.equals("torch") || path.equals("wall_torch")
				|| path.equals("soul_torch") || path.equals("soul_wall_torch")
				|| path.equals("redstone_torch") || path.equals("redstone_wall_torch")) {
			return "torch";
		}
		if (isFlower(path)) {
			return "flower";
		}
		String normalized = path.startsWith("deepslate_") ? path.substring("deepslate_".length()) : path;
		if (normalized.endsWith("_ore")) {
			return "ore:" + normalized;
		}
		return "";
	}

	private static boolean isFlower(String path) {
		return switch (path) {
			case "poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
				 "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony",
				 "torchflower", "pitcher_plant", "red_tulip", "orange_tulip", "white_tulip",
				 "pink_tulip" -> true;
			default -> false;
		};
	}

	/** Fish and wool are families too: a salmon fills a "fish" step, blue wool a "wool" step. */
	public static int familyCount(Observation obs, String item) {
		String path = item == null ? "" : item.substring(item.indexOf(':') + 1);
		if (path.equals("cod") || path.equals("salmon") || path.equals("tropical_fish")) {
			return obs.count("minecraft:cod") + obs.count("minecraft:salmon")
					+ obs.count("minecraft:tropical_fish");
		}
		String family = blockFamily(item);
		if (!family.isEmpty()) {
			int total = 0;
			for (String key : obs.inventory.keySet()) {
				if (family.equals(blockFamily(key))) {
					total += obs.inventory.getOrDefault(key, 0);
				}
			}
			return total;
		}
		return obs.count(item);
	}

	/** Counts the time the player keeps looking at the same target; true once {@code needed} is reached. */
	public static boolean dwell(GoalEvaluator.Tracker tracker, String expected, String actual, long needed) {
		if (actual == null || !actual.equals(expected)) {
			tracker.target = null;
			tracker.dwellMs = 0;
			return false;
		}
		if (!actual.equals(tracker.target)) {
			tracker.target = actual;
			tracker.dwellMs = 0;
			return false;
		}
		tracker.dwellMs += GoalEvaluator.TICK_MS;
		return tracker.dwellMs >= Math.max(50, needed);
	}
}
