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
	/** One villager. {@code request} is null when it has none open; {@code requestTitle} is null for a deleted request. */
	public record Row(VillagerProfile profile, ProgressData.OpenRequest request, Request requestDef) {
		public boolean hasRequest() {
			return request != null;
		}
	}

	private VillagerRows() {
	}

	public static List<Row> of(Collection<VillagerProfile> villagers, Requests requests) {
		List<Row> rows = new ArrayList<>();
		for (VillagerProfile profile : villagers) {
			ProgressData.OpenRequest open = requests == null ? null : requests.openFor(profile.uuid);
			rows.add(new Row(profile, open, requests == null ? null : requests.request(open)));
		}
		return rows;
	}

	/** Friendship as ten hearts: full ones for what is reached. */
	public static String hearts(int friendship) {
		int full = Math.max(0, Math.min(VillagerProfile.MAX_FRIENDSHIP, friendship));
		return "♥".repeat(full) + "♡".repeat(VillagerProfile.MAX_FRIENDSHIP - full);
	}
}
