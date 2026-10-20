package de.zirks.hangulquest.quest;

import de.zirks.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/** Pure evaluation of goals and conditions against an {@link Observation}. */
public final class GoalEvaluator {
	public static final long TICK_MS = 50L;

	/** Mutable progress for the currently active goal. */
	public static final class Tracker {
		public int count;
		public long dwellMs;
		public String target;
		public int baseCount = -1;
		public String goalKey;

		public void resetFor(String key) {
			if (key.equals(goalKey)) {
				return;
			}
			goalKey = key;
			count = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
		}

		public void clear() {
			goalKey = null;
			count = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
		}
	}

	/** Resume a counter goal without the next tick resetting it. */
	public static void seed(Tracker tracker, Goal goal, int count) {
		tracker.clear();
		tracker.goalKey = key(goal);
		tracker.count = Math.max(0, count);
	}

	private GoalEvaluator() {
	}

	private static boolean hasNightCondition(List<Condition> conditions) {
		if (conditions == null) {
			return false;
		}
		for (Condition condition : conditions) {
			if (condition instanceof Condition.Time time && time.phase() == Condition.Phase.NIGHT) {
				return true;
			}
		}
		return false;
	}

	private static boolean conditionsMetIgnoringTime(List<Condition> conditions, Observation obs) {
		if (conditions == null) {
			return true;
		}
		for (Condition condition : conditions) {
			if (condition instanceof Condition.Time) {
				continue;
			}
			if (!conditionMet(condition, obs)) {
				return false;
			}
		}
		return true;
	}

	public static boolean conditionsMet(List<Condition> conditions, Observation obs) {
		if (conditions == null || conditions.isEmpty()) {
			return true;
		}
		for (Condition condition : conditions) {
			if (!conditionMet(condition, obs)) {
				return false;
			}
		}
		return true;
	}

	public static boolean conditionMet(Condition condition, Observation obs) {
		return switch (condition) {
			case Condition.Time t -> obs.phase() == t.phase();
			case Condition.WeatherState w -> switch (w.weather()) {
				case CLEAR -> !obs.rain && !obs.thunder;
				case RAIN -> obs.rain;
				case THUNDER -> obs.thunder;
			};
			case Condition.YRange y -> obs.y >= y.minY() && obs.y <= y.maxY();
			case Condition.NearEntity n -> nearEntity(obs, n.entity(), n.radius());
			case Condition.NearBlock n -> nearBlock(obs, n.block(), n.radius());
			case Condition.InDimension d -> d.dimension().equals(obs.dimension);
			case Condition.InBiome b -> obs.biomeIs(b.biome());
		};
	}

	private static boolean nearEntity(Observation obs, String entity, double radius) {
		return radius >= 0 && obs.nearbyEntities.getOrDefault(entity, 0) > 0;
	}

	private static boolean nearBlock(Observation obs, String block, double radius) {
		return radius >= 0 && block != null && block.equals(obs.lookedBlock);
	}

	/** Advances the tracker; returns true when the goal is satisfied. */
	public static boolean tick(Goal goal, List<Condition> conditions, Observation obs, Tracker tracker) {
		tracker.resetFor(key(goal));
		boolean nightLatch = (goal instanceof Goal.UseBlock || goal instanceof Goal.PlaceBlock
				|| goal instanceof Goal.LookAtBlock || goal instanceof Goal.ConsumeItem
				|| goal instanceof Goal.DefeatEntity || goal instanceof Goal.ReachBiome)
				&& hasNightCondition(conditions)
				&& !conditionsMet(conditions, obs)
				&& conditionsMetIgnoringTime(conditions, obs);
		boolean sleepingProof = goal instanceof Goal.UseBlock use
				&& obs.sleeping && isBed(use.block());
		if (!conditionsMet(conditions, obs) && !nightLatch && !sleepingProof) {
			return false;
		}
		return switch (goal) {
			case Goal.BreakBlock g -> {
				for (String broken : obs.brokenBlocks) {
					if (matchesBlock(g.block(), broken)) {
						tracker.count++;
					}
				}
				yield tracker.count >= Math.max(1, g.count());
			}
			case Goal.PlaceBlock g -> {
				List<String> placements = nightLatch ? obs.placedBlocksAtNight : obs.placedBlocks;
				for (String placed : placements) {
					if (matchesBlock(g.block(), placed)) {
						tracker.count++;
					}
				}
				yield tracker.count >= Math.max(1, g.count());
			}
			case Goal.AcquireItem g -> {
				// Already-held items count: a step says "have X", not "find X more".
				yield familyCount(obs, g.item()) >= Math.max(1, g.count());
			}
			case Goal.ConsumeItem g -> {
				List<String> eaten = nightLatch ? obs.consumedItemsAtNight : obs.consumedItems;
				for (String consumed : eaten) {
					if (consumed.equals(g.item())) {
						tracker.count++;
					}
				}
				yield tracker.count >= Math.max(1, g.count());
			}
			case Goal.LookAtBlock g -> {
				String looked = matchesBlock(g.block(), obs.lookedBlock) ? g.block() : null;
				if (looked == null && nightLatch && matchesBlock(g.block(), obs.lookedBlockAtNight)) {
					looked = g.block();
				}
				yield dwell(obs, tracker, g.block(), looked, g.dwellMs());
			}
			case Goal.LookAtEntity g -> dwell(obs, tracker, g.entity(), obs.lookedEntity, g.dwellMs());
			case Goal.DefeatEntity g -> {
				List<String> kills = nightLatch ? obs.defeatedEntitiesAtNight : obs.defeatedEntities;
				for (String defeated : kills) {
					if (defeated.equals(g.entity())) {
						tracker.count++;
					}
				}
				yield tracker.count >= Math.max(1, g.count());
			}
			case Goal.ReachBiome g -> {
				boolean matches = obs.biomeIs(g.biome());
				if (!matches && nightLatch) {
					matches = biomeAtNightMatches(obs, g.biome());
				}
				yield dwell(obs, tracker, g.biome(), matches ? g.biome() : null, g.dwellMs());
			}
			case Goal.ReachDimension g -> g.dimension().equals(obs.dimension);
			case Goal.ReachHeight g -> obs.y >= g.minY() && obs.y <= g.maxY();
			case Goal.UseBlock g -> {
				List<String> uses = nightLatch ? obs.usedBlocksAtNight : obs.usedBlocks;
				for (String used : uses) {
					if (matchesBlock(g.block(), used)) {
						tracker.count++;
					}
				}
				if (obs.sleeping && obs.sleepingBed != null
						&& matchesBlock(g.block(), obs.sleepingBed)) {
					tracker.count++;
				}
				yield tracker.count >= 1;
			}
			case Goal.InteractEntity g -> {
				for (String interacted : obs.interactedEntities) {
					if (interacted.equals(g.entity())) {
						tracker.count++;
					}
				}
				yield tracker.count >= 1;
			}
		};
	}

	/** Beds, doors, planks, fences, wool and logs are interchangeable within their family. */
	private static boolean matchesBlock(String goal, String observed) {
		if (goal == null || observed == null) {
			return false;
		}
		if (goal.equals(observed)) {
			return true;
		}
		String family = blockFamily(goal);
		return !family.isEmpty() && family.equals(blockFamily(observed));
	}

	private static boolean biomeAtNightMatches(Observation obs, String biomeOrTag) {
		if (biomeOrTag == null || obs.biomeAtNight == null) {
			return false;
		}
		if (biomeOrTag.startsWith("#")) {
			return obs.biomeTagsAtNight.contains(biomeOrTag.substring(1));
		}
		return biomeOrTag.equals(obs.biomeAtNight);
	}

	private static boolean isBed(String id) {
		return id != null && blockFamily(id).equals("bed");
	}

	private static String blockFamily(String id) {
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

	/** How many of this item (or its family) the player holds. */
	public static int heldCount(Observation obs, String item) {
		return familyCount(obs, item);
	}

	/** Fish and wool are families too: a salmon fills a "fish" step, blue wool a "wool" step. */
	private static int familyCount(Observation obs, String item) {
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

	private static boolean dwell(Observation obs, Tracker tracker, String expected, String actual, long needed) {
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
		tracker.dwellMs += TICK_MS;
		return tracker.dwellMs >= Math.max(50, needed);
	}

	public static String key(Goal goal) {
		return goal.getClass().getSimpleName() + ":" + describe(goal);
	}

	private static String describe(Goal goal) {
		List<String> parts = new ArrayList<>();
		parts.add(switch (goal) {
			case Goal.BreakBlock g -> g.block() + "/" + g.count();
			case Goal.PlaceBlock g -> g.block() + "/" + g.count();
			case Goal.AcquireItem g -> g.item() + "/" + g.count();
			case Goal.ConsumeItem g -> g.item() + "/" + g.count();
			case Goal.LookAtBlock g -> g.block() + "/" + g.dwellMs();
			case Goal.LookAtEntity g -> g.entity() + "/" + g.dwellMs();
			case Goal.DefeatEntity g -> g.entity() + "/" + g.count();
			case Goal.ReachBiome g -> g.biome() + "/" + g.dwellMs();
			case Goal.ReachDimension g -> g.dimension();
			case Goal.ReachHeight g -> g.minY() + "-" + g.maxY();
			case Goal.UseBlock g -> g.block();
			case Goal.InteractEntity g -> g.entity();
		});
		return String.join(",", parts);
	}
}
