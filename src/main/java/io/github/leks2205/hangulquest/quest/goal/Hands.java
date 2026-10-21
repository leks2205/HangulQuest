package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.sense.Observation;

/** What the player holds, from the hand facts of the observation. */
public final class Hands {
	private Hands() {
	}

	/** True when the item (or one of its block family, like torch and wall torch) is in either hand. */
	public static boolean holds(Observation obs, String item) {
		return item != null && !item.isBlank()
				&& (Matching.matchesBlock(item, obs.mainHand) || Matching.matchesBlock(item, obs.offHand));
	}
}
