package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.sense.Observation;

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
		/**
		 * AcquireItem baseline: how many of the item the player held when the step began (or when it
		 * was restored). {@code -1} means "not taken yet"; the next tick takes it.
		 */
		public int baseCount = -1;
		/** The gained count that was already known when the baseline was taken (restored progress). */
		public int carried;
		/** Sticky for the current step: some progress was made in creative or spectator mode. */
		public boolean assisted;
		public String goalKey;
		private Goal goalRef;

		public void resetFor(String key) {
			if (key.equals(goalKey)) {
				return;
			}
			goalKey = key;
			goalRef = null;
			count = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
			carried = 0;
			assisted = false;
		}

		/** Same as {@link #resetFor(String)} but skips building the key while the goal object is unchanged. */
		void resetFor(Goal goal) {
			if (goal == goalRef) {
				return;
			}
			resetFor(key(goal));
			goalRef = goal;
		}

		public void clear() {
			goalKey = null;
			goalRef = null;
			count = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
			carried = 0;
			assisted = false;
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

	/**
	 * The nearest entity of that type must be within {@code radius} of the player. When the sensor
	 * did not record a distance (hand-built observations), presence in the scan is enough.
	 */
	private static boolean nearEntity(Observation obs, String entity, double radius) {
		if (radius < 0 || obs.nearbyEntities.getOrDefault(entity, 0) <= 0) {
			return false;
		}
		Double distance = obs.nearestEntityDistance.get(entity);
		return distance == null || distance <= radius;
	}

	/** The block the player looks at must be within {@code radius} of the player's position. */
	private static boolean nearBlock(Observation obs, String block, double radius) {
		if (radius < 0 || block == null || !block.equals(obs.lookedBlock)) {
			return false;
		}
		if (obs.lookedBlockPos == null) {
			return true;
		}
		return obs.position.distanceToSqr(obs.lookedBlockPos.getX() + 0.5,
				obs.lookedBlockPos.getY() + 0.5, obs.lookedBlockPos.getZ() + 0.5) <= radius * radius;
	}

	/**
	 * True when this goal needs the sensor to pick entities along the camera ray far beyond
	 * vanilla's reach (the ender dragon, a ghast, a parrot through the spyglass).
	 */
	public static boolean needsFarEntityLook(Goal goal) {
		return goal instanceof Goal.LookAtEntity;
	}

	/**
	 * Advances the tracker; returns true when the goal is satisfied. Progress made in creative or
	 * spectator mode still counts, but marks the tracker {@link Tracker#assisted}.
	 */
	public static boolean tick(Goal goal, List<Condition> conditions, Observation obs, Tracker tracker) {
		tracker.resetFor(goal);
		if (needsFarEntityLook(goal)) {
			obs.farEntityLookWanted = true;
		}
		if (goal instanceof Goal.AcquireItem g) {
			// The baseline is taken even while conditions fail, so items picked up meanwhile count later.
			baselineAcquired(tracker, familyCount(obs, g.item()), obs.inventorySettling);
		}
		int countBefore = tracker.count;
		long dwellBefore = tracker.dwellMs;
		boolean complete = evaluate(goal, conditions, obs, tracker);
		if (obs.assisted() && (complete || tracker.count > countBefore || tracker.dwellMs > dwellBefore)) {
			tracker.assisted = true;
		}
		return complete;
	}

	private static boolean evaluate(Goal goal, List<Condition> conditions, Observation obs, Tracker tracker) {
		boolean nightLatch = (goal instanceof Goal.UseBlock || goal instanceof Goal.PlaceBlock
				|| goal instanceof Goal.LookAtBlock || goal instanceof Goal.ConsumeItem
				|| goal instanceof Goal.DefeatEntity || goal instanceof Goal.ReachBiome)
				&& hasNightCondition(conditions)
				&& !conditionsMet(conditions, obs)
				&& conditionsMetIgnoringTime(conditions, obs);
		// Being in bed is proof of the night, even after the server skipped to morning. It excuses the
		// time-of-day condition only: biome, dimension and the rest must still hold.
		boolean sleepingProof = goal instanceof Goal.UseBlock use
				&& obs.sleeping && isBed(use.block())
				&& hasNightCondition(conditions)
				&& conditionsMetIgnoringTime(conditions, obs);
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
				if (!obs.inventorySettling) {
					trackAcquired(tracker, familyCount(obs, g.item()));
				}
				yield tracker.count >= Math.max(1, g.count());
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

	/**
	 * Counts only items gained since the step began. The first tick of a step (and every tick while
	 * the inventory is still syncing after a world change) takes the held count as the baseline;
	 * afterwards {@code count = carried + (held - baseline)}, never going down. Shuffling stacks in a
	 * menu or dropping and re-collecting items therefore earns nothing, because only a rise above the
	 * baseline counts.
	 * <p>
	 * Only {@code count} is persisted (as goalProgress). After a restart the step is re-baselined at
	 * whatever the player holds then, with the saved count carried over. Trade-off: items picked up
	 * after the last save but before the restart are not credited, and neither are items that were
	 * already held when the game restarted. Items consumed from the baseline stack (crafting, eating)
	 * mean more must be gained before progress resumes.
	 */
	static void baselineAcquired(Tracker tracker, int held, boolean settling) {
		if (tracker.baseCount < 0 || settling) {
			tracker.baseCount = held;
			tracker.carried = tracker.count;
		}
	}

	/** Raises the gained count; run only while the step's conditions hold. */
	static void trackAcquired(Tracker tracker, int held) {
		tracker.count = Math.max(tracker.count, tracker.carried + held - tracker.baseCount);
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
