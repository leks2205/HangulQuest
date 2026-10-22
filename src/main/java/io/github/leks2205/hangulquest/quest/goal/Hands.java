package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.sense.Observation;

/** What the player holds, from the hand facts of the observation. */
public final class Hands {
	private Hands() {
	}

	/** Same, for a step whose clue may name the colour of the item: then only that very item counts. */
	public static boolean holds(GoalEvaluator.Tracker tracker, Observation obs, String item) {
		return holds(tracker.accepted, obs, item);
	}

	public static boolean holds(Accepted accepted, Observation obs, String item) {
		if (item == null || item.isBlank()) {
			return false;
		}
		if (accepted.isExact(item)) {
			return item.equals(obs.mainHand) || item.equals(obs.offHand);
		}
		return holds(obs, item);
	}

	/** True when the item (or one of its block family, like torch and wall torch) is in either hand. */
	public static boolean holds(Observation obs, String item) {
		return item != null && !item.isBlank()
				&& (Matching.matchesBlock(item, obs.mainHand) || Matching.matchesBlock(item, obs.offHand));
	}
}
