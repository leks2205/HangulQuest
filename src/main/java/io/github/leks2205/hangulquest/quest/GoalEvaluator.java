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
		/** Sticky for the current step: some progress was made in creative or spectator mode. */
		public boolean assisted;
		public String goalKey;
		private Goal goalRef;
		/** Trackers of the sub-goals of a sequence, an avoid or a hold; created by the type on first use. */
		public List<Tracker> subs;
		/** Sequence in any order: bit i is set once part i is done. */
		public long doneMask;

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

	private static boolean conditionsMetIgnoringTime(List<Condition> conditions, Observation obs) {
		if (conditions == null) {
			return true;
		}
		for (Condition condition : conditions) {
			if (ConditionTypes.of(condition).timeOfDay()) {
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
		return ConditionTypes.of(condition).met(condition, obs);
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
		type.beforeTick(goal, obs, tracker);
		int countBefore = tracker.count;
		long dwellBefore = tracker.dwellMs;
		boolean complete = evaluate(type, goal, conditions, obs, tracker);
		if (obs.assisted() && (complete || tracker.count > countBefore || tracker.dwellMs > dwellBefore)) {
			tracker.assisted = true;
		}
		return complete;
	}

	private static boolean evaluate(GoalType<Goal> type, Goal goal, List<Condition> conditions,
									Observation obs, Tracker tracker) {
		boolean nightLatch = type.nightLatchable()
				&& hasNightCondition(conditions)
				&& !conditionsMet(conditions, obs)
				&& conditionsMetIgnoringTime(conditions, obs);
		// Being in bed is proof of the night, even after the server skipped to morning. It excuses the
		// time-of-day condition only: biome, dimension and the rest must still hold.
		boolean sleepingProof = type.excusesTimeCondition(goal, obs)
				&& hasNightCondition(conditions)
				&& conditionsMetIgnoringTime(conditions, obs);
		if (!conditionsMet(conditions, obs) && !nightLatch && !sleepingProof) {
			return false;
		}
		return type.evaluate(goal, obs, tracker, nightLatch);
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
