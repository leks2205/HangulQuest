package io.github.leks2205.hangulquest.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Every trail the player has started, the checked subset that forms the quest log, and which of
 * those is focused. The focus always sits on a checked trail (or nowhere).
 */
final class TrackRegistry {
	/** How many trails the quest log holds at once, the focused one included. */
	static final int MAX_TRACKED = 6;

	/** Runtime state of every trail the player has started, tracked or not. */
	private final LinkedHashMap<String, Track> tracks = new LinkedHashMap<>();
	/** Insertion order of the quest log: what the HUD shows and what is checked against the world. */
	private final LinkedHashSet<String> trackedIds = new LinkedHashSet<>();
	private String focusedId;

	Collection<Track> all() {
		return tracks.values();
	}

	boolean isEmpty() {
		return tracks.isEmpty();
	}

	Track get(String trailId) {
		return tracks.get(trailId);
	}

	boolean has(String trailId) {
		return tracks.containsKey(trailId);
	}

	void put(Track track) {
		tracks.put(track.trail.id(), track);
	}

	boolean isTracked(String trailId) {
		return trackedIds.contains(trailId);
	}

	Set<String> trackedIds() {
		return trackedIds;
	}

	int trackedCount() {
		return trackedIds.size();
	}

	String focusedId() {
		return focusedId;
	}

	Track focused() {
		return focusedId == null ? null : tracks.get(focusedId);
	}

	/**
	 * Checks a trail and focuses it. Fails when the log is full of trails with progress; a trail that
	 * is already checked is only focused.
	 */
	boolean track(String trailId) {
		if (!trackedIds.contains(trailId) && !makeRoom()) {
			return false;
		}
		trackedIds.add(trailId);
		focusedId = trailId;
		return true;
	}

	/** Takes a trail out of the log; its state is kept. True when it was in the log. */
	boolean untrack(String trailId) {
		if (!trackedIds.remove(trailId)) {
			return false;
		}
		refocusIfLost(trailId);
		return true;
	}

	/** Forgets a trail altogether (finished or dropped). */
	void remove(String trailId) {
		tracks.remove(trailId);
		trackedIds.remove(trailId);
		refocusIfLost(trailId);
	}

	boolean focus(String trailId) {
		if (!trackedIds.contains(trailId)) {
			return false;
		}
		focusedId = trailId;
		return true;
	}

	/** After a reload: points every started trail at its replacement, keeping its step by id. */
	void rebind(Function<String, Questline> trails) {
		for (Track track : tracks.values()) {
			Questline updated = trails.apply(track.trail.id());
			if (updated == null) {
				continue;
			}
			String stepId = track.step() == null ? null : track.step().id();
			track.trail = updated;
			List<QuestStep> steps = updated.allSteps();
			track.stepIndex = 0;
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(stepId)) {
					track.stepIndex = i;
					break;
				}
			}
		}
	}

	private void refocusIfLost(String trailId) {
		if (trailId.equals(focusedId)) {
			focusedId = trackedIds.isEmpty() ? null : trackedIds.iterator().next();
		}
	}

	/**
	 * Makes room in the quest log by taking the oldest background trail without progress out of it
	 * (it stays startable). A trail the player has worked on is never dropped silently: when only
	 * those are left, the log stays as it is and the caller must decline.
	 */
	private boolean makeRoom() {
		while (trackedIds.size() >= MAX_TRACKED) {
			String victim = null;
			for (String id : trackedIds) {
				Track track = tracks.get(id);
				if (!id.equals(focusedId) && (track == null || !track.hasProgress())) {
					victim = id;
					break;
				}
			}
			if (victim == null) {
				return false;
			}
			trackedIds.remove(victim);
		}
		return true;
	}

	// ---------------------------------------------------------------- restore

	void restoreFocus(String trailId) {
		focusedId = trailId;
	}

	void restoreTracked(String trailId) {
		trackedIds.add(trailId);
	}

	/** Trims an oversized saved log and puts the focus on a checked trail. */
	void normalize() {
		if (trackedIds.size() > MAX_TRACKED) {
			List<String> keep = new ArrayList<>(trackedIds).subList(0, MAX_TRACKED);
			trackedIds.retainAll(keep);
		}
		if (focusedId == null || !trackedIds.contains(focusedId)) {
			focusedId = trackedIds.isEmpty() ? null : trackedIds.iterator().next();
		}
	}
}
