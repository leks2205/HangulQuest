package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.quest.condition.ConditionType;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.Matching;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/**
 * Pure evaluation of goals and conditions against an {@link Observation}. The per-type rules live in
 * {@link GoalType} and {@link ConditionType}; this class keeps what they share: the tracker, the
 * conditions gate and the night latch.
 */
public final class GoalEvaluator {
	public static final long TICK_MS = 50L;

	/** Mutable progress for the currently active goal. */
	public static final class Tracker {
		public int count;
		/** BuildPattern: the most blocks of the structure that stood together so far (status line only). */
		public int progress;
		public long dwellMs;
		/** What the step accepts besides the goal's own ids; set by the engine, never saved. */
		public io.github.leks2205.hangulquest.quest.goal.Accepted accepted =
				io.github.leks2205.hangulquest.quest.goal.Accepted.NONE;
		public String target;
		/**
		 * AcquireItem baseline: how many of the item the player held when the step began (or when it
		 * was restored). {@code -1} means "not taken yet"; the next tick takes it.
		 */
		public int baseCount = -1;
		/** The gained count that was already known when the baseline was taken (restored progress). */
		public int carried;
		/** Scratch for goals that watch a state over time (wait, swim, transit, breed); never saved. */
		public long mark;
		public int phase;
		public double distance;
		public double[] at;
		public final java.util.Set<String> marks = new java.util.HashSet<>();
		/** Breed: the animals fed since the step began, by uuid, with where they were last seen (x, y, z; null if not yet). */
		public final java.util.Map<String, double[]> parents = new java.util.HashMap<>();
		/** Sticky for the current step: some progress was made in creative or spectator mode. */
		public boolean assisted;
		public String goalKey;
		private Goal goalRef;
		/** Set every tick: the goal checks the roof over the block it places, not over the player. Never saved. */
		public boolean roofedPlacement;
		/** Trackers of the sub-goals of a sequence, an avoid or a hold; created by the type on first use. */
		public List<Tracker> subs;
		/** Sequence in any order: bit i is set once part i is done. */
		public long doneMask;
		/** Where the last placement that counted toward this step stands (x, y, z); null when none did. */
		public int[] lastPlaced;
		/**
		 * Form mistakes of this step that the learning side has not heard about yet: a right block in the
		 * wrong relation, an ordered sequence done out of order. Never saved; the engine reports and clears them.
		 */
		public int formErrors;
		/** Mistakes already reported for this step; capped, so spamming placements cannot sink a construction. */
		public int formErrorsReported;
		/**
		 * The act of the step happened while a condition that is spoken as a clause (밤이 되면, 비가 오면) did not
		 * hold: a slip against that clause, kept apart from {@link #formErrors} because it is evidence about a
		 * different construction. Never saved; the engine reports and clears it.
		 */
		public int conditionSlips;
		public int conditionSlipsReported;
		/**
		 * The act of the step happened with the wrong colour although the clue names the colour (빨간 침대): a slip
		 * against the colour adjective, kept apart from the other two kinds for the same reason. Never saved; the
		 * engine reports and clears it.
		 */
		public int colourSlips;
		public int colourSlipsReported;
		/**
		 * Blocks the player placed to finish earlier steps of the trail, by step id, as x, y, z. This is the
		 * memory behind the anchor {@code "@s2"}. It outlives {@link #clear()} (it is per trail, not per
		 * step) and is saved with the trail's progress.
		 */
		public final java.util.Map<String, int[]> placed = new java.util.LinkedHashMap<>();
		/**
		 * Only gifts to this entity count (a villager's request is handed to that villager); null for any of the
		 * goal's type. Set by the owner, never saved, kept by {@link #clear()}.
		 */
		public java.util.UUID onlyEntity;
		/** The block each earlier placing step of the trail placed, by step id; set by the engine, never saved. */
		public java.util.Map<String, String> refBlocks = java.util.Map.of();

		public void resetFor(String key) {
			if (key.equals(goalKey)) {
				return;
			}
			goalKey = key;
			goalRef = null;
			count = 0;
			progress = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
			carried = 0;
			assisted = false;
			subs = null;
			doneMask = 0;
			lastPlaced = null;
			formErrors = 0;
			formErrorsReported = 0;
			conditionSlips = 0;
			conditionSlipsReported = 0;
			colourSlips = 0;
			colourSlipsReported = 0;
			scratch();
		}

		private void scratch() {
			mark = 0L;
			phase = 0;
			distance = 0;
			at = null;
			marks.clear();
			parents.clear();
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
			progress = 0;
			dwellMs = 0;
			target = null;
			baseCount = -1;
			carried = 0;
			assisted = false;
			subs = null;
			doneMask = 0;
			lastPlaced = null;
			formErrors = 0;
			formErrorsReported = 0;
			conditionSlips = 0;
			conditionSlipsReported = 0;
			colourSlips = 0;
			colourSlipsReported = 0;
			scratch();
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
			if (ConditionTypes.of(condition).requiresNight(condition)) {
				return true;
			}
		}
		return false;
	}

	private static boolean conditionsMetIgnoringTime(List<Condition> conditions, Observation obs, io.github.leks2205.hangulquest.quest.goal.Accepted accepted) {
		if (conditions == null) {
			return true;
		}
		for (Condition condition : conditions) {
			if (ConditionTypes.of(condition).timeOfDay()) {
				continue;
			}
			if (!conditionMet(condition, obs, accepted)) {
				return false;
			}
		}
		return true;
	}

	public static boolean conditionsMet(List<Condition> conditions, Observation obs) {
		return conditionsMet(conditions, obs, io.github.leks2205.hangulquest.quest.goal.Accepted.NONE);
	}

	public static boolean conditionsMet(List<Condition> conditions, Observation obs, io.github.leks2205.hangulquest.quest.goal.Accepted accepted) {
		if (conditions == null || conditions.isEmpty()) {
			return true;
		}
		for (Condition condition : conditions) {
			if (!conditionMet(condition, obs, accepted)) {
				return false;
			}
		}
		return true;
	}

	public static boolean conditionMet(Condition condition, Observation obs) {
		return ConditionTypes.of(condition).met(condition, obs);
	}

	public static boolean conditionMet(Condition condition, Observation obs, io.github.leks2205.hangulquest.quest.goal.Accepted accepted) {
		return ConditionTypes.of(condition).met(condition, obs, accepted);
	}

	/**
	 * True when this goal needs the sensor to pick entities along the camera ray far beyond
	 * vanilla's reach (the ender dragon, a ghast, a parrot through the spyglass).
	 */
	public static boolean needsFarEntityLook(Goal goal) {
		return GoalTypes.of(goal).needsFarEntityLook();
	}

	/**
	 * Advances the tracker; returns true when the goal is satisfied. Progress made in creative or
	 * spectator mode still counts, but marks the tracker {@link Tracker#assisted}.
	 */
	public static boolean tick(Goal goal, List<Condition> conditions, Observation obs, Tracker tracker) {
		tracker.resetFor(goal);
		GoalType<Goal> type = GoalTypes.of(goal);
		if (type.needsFarEntityLook()) {
			obs.farEntityLookWanted = true;
		}
		if (type.needsFluidLook(goal)) {
			obs.fluidLookWanted = true;
		}
		if (type.needsMusicScan(goal)) {
			obs.musicScanWanted = true;
		}
		if (type.needsShotScan(goal)) {
			obs.shotScanWanted = true;
		}
		for (Condition condition : conditions) {
			if (condition instanceof Condition.NearBlock near && near.radius() >= 0) {
				obs.nearBlocksWanted.merge(near.block(), (int) Math.ceil(near.radius()), Math::max);
			}
		}
		tracker.roofedPlacement = conditions.stream().anyMatch(c -> c instanceof Condition.Sky sky
				&& sky.state() == Condition.SkyState.ROOFED && type.takesOver(goal, c));
		type.beforeTick(goal, obs, tracker);
		int countBefore = tracker.count;
		long dwellBefore = tracker.dwellMs;
		boolean complete = evaluate(type, goal, conditions, obs, tracker);
		if (obs.assisted() && (complete || tracker.count > countBefore || tracker.dwellMs > dwellBefore)) {
			tracker.assisted = true;
		}
		return complete;
	}

	/** The conditions that are about the player: those the goal does not check on its target itself. */
	public static List<Condition> playerConditions(Goal goal, List<Condition> conditions) {
		if (conditions == null || conditions.isEmpty()) {
			return conditions;
		}
		GoalType<Goal> type = GoalTypes.of(goal);
		return conditions.stream().filter(condition -> !type.takesOver(goal, condition)).toList();
	}

	private static boolean evaluate(GoalType<Goal> type, Goal goal, List<Condition> allConditions,
									Observation obs, Tracker tracker) {
		List<Condition> conditions = playerConditions(goal, allConditions);
		boolean nightLatch = type.nightLatchable()
				&& hasNightCondition(conditions)
				&& !conditionsMet(conditions, obs, tracker.accepted)
				&& conditionsMetIgnoringTime(conditions, obs, tracker.accepted);
		// Being in bed is proof of the night, even after the server skipped to morning. It excuses the
		// time-of-day condition only: biome, dimension and the rest must still hold.
		boolean sleepingProof = type.excusesTimeCondition(goal, obs)
				&& hasNightCondition(conditions)
				&& conditionsMetIgnoringTime(conditions, obs, tracker.accepted);
		if (!conditionsMet(conditions, obs, tracker.accepted) && !nightLatch && !sleepingProof) {
			if (clauseBroken(goal, conditions, obs, tracker)) {
				tracker.conditionSlips++;
			}
			return false;
		}
		int before = tracker.count;
		boolean done = type.evaluate(goal, obs, tracker, nightLatch);
		if (nightLatch && !done && tracker.count == before && clauseBroken(goal, conditions, obs, tracker)) {
			tracker.conditionSlips++; // done by day, where the night is what counts
		}
		if (!done && tracker.count == before && colourWrong(goal, obs, tracker)) {
			tracker.colourSlips++;
		}
		return done;
	}

	/**
	 * The act happened this tick with a bed, wool or carpet of another colour than the one the clue names. Told by
	 * running the goal once more as if the clue had left the colour out: if that counts, the colour was the only
	 * thing wrong. Events only, like {@link #clauseBroken}.
	 */
	private static boolean colourWrong(Goal goal, Observation obs, Tracker tracker) {
		if (tracker.accepted.exact().isEmpty() || !io.github.leks2205.hangulquest.quest.goal.Clauses.isEvent(goal)) {
			return false;
		}
		Tracker probe = new Tracker();
		probe.accepted = tracker.accepted.withExact(java.util.Set.of());
		probe.roofedPlacement = tracker.roofedPlacement;
		probe.refBlocks = tracker.refBlocks;
		probe.resetFor(goal);
		GoalTypes.of(goal).evaluate(goal, obs, probe, false);
		return probe.count > 0;
	}

	/**
	 * The act happened this tick although the only conditions that failed are ones spoken as a clause (-(으)면: 밤이
	 * 되면, 비가 오면). Only a single event can be told: a fresh tracker sees it on the tick it happens. A dwell or a
	 * distance is not an act, and a lapse of the world (the day ends while the player waits) is not a slip.
	 */
	private static boolean clauseBroken(Goal goal, List<Condition> conditions, Observation obs, Tracker tracker) {
		if (!io.github.leks2205.hangulquest.quest.goal.Clauses.isEvent(goal)) {
			return false;
		}
		for (Condition condition : conditions) {
			if (!conditionMet(condition, obs, tracker.accepted) && !io.github.leks2205.hangulquest.quest.condition.ConditionForms.hasClause(condition)) {
				return false;
			}
		}
		Tracker probe = new Tracker();
		probe.accepted = tracker.accepted;
		probe.roofedPlacement = tracker.roofedPlacement;
		probe.refBlocks = tracker.refBlocks;
		probe.resetFor(goal);
		GoalTypes.of(goal).evaluate(goal, obs, probe, false);
		return probe.count > 0;
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
	 * already held when the game restarted. When the held count drops below the baseline while no menu
	 * is open (eating, placing, dropping, or a crafting menu that was closed), the baseline follows it
	 * down, so using the item never blocks progress. Inside a menu it stays put, because a stack on the
	 * cursor is not in the inventory and putting it back must not count as a gain.
	 */
	public static void baselineAcquired(Tracker tracker, int held, boolean settling, boolean screenOpen) {
		if (tracker.baseCount < 0 || settling || (held < tracker.baseCount && !screenOpen)) {
			tracker.baseCount = held;
			tracker.carried = tracker.count;
		}
	}

	/** Raises the gained count; run only while the step's conditions hold. */
	public static void trackAcquired(Tracker tracker, int held) {
		tracker.count = Math.max(tracker.count, tracker.carried + held - tracker.baseCount);
	}

	/** How many of this item (or its family) the player holds. */
	public static int heldCount(Observation obs, String item) {
		return Matching.familyCount(obs, item);
	}

	public static String key(Goal goal) {
		return goal.getClass().getSimpleName() + ":" + GoalTypes.of(goal).keyPart(goal);
	}
}
