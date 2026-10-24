package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.Requests;
import io.github.leks2205.hangulquest.villager.VillagerProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The rows of the Villagers page, as plain data so they can be built (and tested) without a font: every villager the
 * player has met in the order they were met (a selection or a new request never reorders them).
 */
public final class VillagerRows {
	/**
	 * One villager. {@code request} is null when it has none open, {@code requestDef} for a request whose file is gone.
	 * {@code profile} is null for an open request of a villager whose profile was lost (villagers.json is separate from
	 * the progress); such rows come last, so the request can still be seen and abandoned.
	 */
	public record Row(VillagerProfile profile, ProgressData.OpenRequest request, Request requestDef) {
		/** The villager's name, from the request when the profile is gone. */
		public String name() {
			return profile != null ? profile.name : request != null && request.name != null ? request.name : "?";
		}

		public boolean hasRequest() {
			return request != null;
		}
	}

	private VillagerRows() {
	}

	public static List<Row> of(Collection<VillagerProfile> villagers, Requests requests) {
		List<Row> rows = new ArrayList<>();
		java.util.Set<String> known = new java.util.HashSet<>();
		for (VillagerProfile profile : villagers) {
			known.add(profile.uuid);
			ProgressData.OpenRequest open = requests == null ? null : requests.openFor(profile.uuid);
			rows.add(new Row(profile, open, requests == null ? null : requests.request(open)));
		}
		if (requests != null) {
			for (ProgressData.OpenRequest open : requests.open()) {
				if (!known.contains(open.villager)) {
					rows.add(new Row(null, open, requests.request(open)));
				}
			}
		}
		return rows;
	}

	/** Friendship as ten hearts: full ones for what is reached. */
	public static String hearts(int friendship) {
		int full = Math.max(0, Math.min(VillagerProfile.MAX_FRIENDSHIP, friendship));
		return "♥".repeat(full) + "♡".repeat(VillagerProfile.MAX_FRIENDSHIP - full);
	}
}
