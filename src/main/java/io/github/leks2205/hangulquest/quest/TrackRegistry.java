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
 *
 * <p>The log has {@value #REGULAR_SLOTS} regular slots and {@value #SITUATIONAL_SLOTS} situational one. Each
 * checked trail is either chosen by the player or picked by the game ({@link #isAuto}); the game only ever
 * replaces its own picks. The review trail ("adaptive") is not one of the slots.
 */
final class TrackRegistry {
	static final int REGULAR_SLOTS = 3;
	static final int SITUATIONAL_SLOTS = 1;
	/** How many trails the quest log holds at once, the focused one included. */
	static final int MAX_TRACKED = REGULAR_SLOTS + SITUATIONAL_SLOTS;
	/** A log saved by an older version may hold this many; it is kept as it was and only new ones are held to the cap. */
	static final int LEGACY_MAX = 6;

	/** Runtime state of every trail the player has started, tracked or not. */
	private final LinkedHashMap<String, Track> tracks = new LinkedHashMap<>();
	/** Insertion order of the quest log: what the HUD shows and what is checked against the world. */
	private final LinkedHashSet<String> trackedIds = new LinkedHashSet<>();
	/** The checked trails the game picked; the others were chosen by the player. */
	private final LinkedHashSet<String> autoIds = new LinkedHashSet<>();
	/** The checked trail in the situational slot (also in {@link #autoIds}), or null. */
	private String situationalId;
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

	/** True when the game picked this checked trail. */
	boolean isAuto(String trailId) {
		return autoIds.contains(trailId);
	}

	Set<String> autoIds() {
		return autoIds;
	}

	String situationalId() {
		return situationalId;
	}

	/** Checked trails that fill a slot: all but the review trail. */
	int slotCount() {
		int count = 0;
		for (String id : trackedIds) {
			if (inSlot(id)) {
				count++;
			}
		}
		return count;
	}

	/** Slots in use by trails that are not the situational one. */
	int regularCount() {
		return slotCount() - (situationalId != null && inSlot(situationalId) ? 1 : 0);
	}

	private boolean inSlot(String trailId) {
		Track track = tracks.get(trailId);
		return track == null || !track.trail.adaptive();
	}

	String focusedId() {
		return focusedId;
	}

	Track focused() {
		return focusedId == null ? null : tracks.get(focusedId);
	}

	/**
	 * The player checks a trail and focuses it; it is theirs from now on, even if the game had picked it.
	 * Fails when the log is full of trails with progress; a trail that is already checked is only focused.
	 */
	boolean track(String trailId) {
		if (!trackedIds.contains(trailId) && inSlot(trailId) && !makeRoom()) {
			return false;
		}
		trackedIds.add(trailId);
		autoIds.remove(trailId);
		if (trailId.equals(situationalId)) {
			situationalId = null;
		}
		focusedId = trailId;
		return true;
	}

	/**
	 * The game checks a trail for the player. It takes the situational slot or a regular one and fails when that
	 * one is full; it never makes room and keeps the focus where it is, unless nothing is focused.
	 */
	boolean trackAuto(String trailId, boolean situational) {
		if (trackedIds.contains(trailId)) {
			return false;
		}
		if (inSlot(trailId)) {
			boolean full = slotCount() >= MAX_TRACKED
					|| (situational ? situationalId != null : regularCount() >= REGULAR_SLOTS);
			if (full) {
				return false;
			}
		}
		trackedIds.add(trailId);
		autoIds.add(trailId);
		if (situational) {
			situationalId = trailId;
		}
		if (focusedId == null) {
			focusedId = trailId;
		}
		return true;
	}

	/** Takes a trail out of the log; its state is kept. True when it was in the log. */
	boolean untrack(String trailId) {
		if (!trackedIds.remove(trailId)) {
			return false;
		}
		forgetSlot(trailId);
		refocusIfLost(trailId);
		return true;
	}

	/** Forgets a trail altogether (finished or dropped). */
	void remove(String trailId) {
		tracks.remove(trailId);
		trackedIds.remove(trailId);
		forgetSlot(trailId);
		refocusIfLost(trailId);
	}

	private void forgetSlot(String trailId) {
		autoIds.remove(trailId);
		if (trailId.equals(situationalId)) {
			situationalId = null;
		}
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
	 * Makes room for one more trail by taking trails without progress out of the log (they stay startable):
	 * the game's own picks first, the situational one before the regular ones, then the oldest of the player's.
	 * The focused trail and any trail the player has worked on are never dropped silently. When there are not
	 * enough to drop, nothing is dropped and the caller must decline.
	 */
	private boolean makeRoom() {
		int need = slotCount() - MAX_TRACKED + 1;
		if (need <= 0) {
			return true;
		}
		List<String> victims = new ArrayList<>();
		for (int rank = 0; rank < 3; rank++) {
			for (String id : trackedIds) {
				Track track = tracks.get(id);
				boolean free = !id.equals(focusedId) && inSlot(id) && (track == null || !track.hasProgress());
				if (free && rank(id) == rank) {
					victims.add(id);
				}
			}
		}
		if (victims.size() < need) {
			return false;
		}
		for (String victim : victims.subList(0, need)) {
			trackedIds.remove(victim);
			forgetSlot(victim);
		}
		return true;
	}

	/** 0 = picked by the game for a regular slot, 1 = picked for the situational slot, 2 = chosen by the player. */
	private int rank(String id) {
		if (!autoIds.contains(id)) {
			return 2;
		}
		return id.equals(situationalId) ? 1 : 0;
	}

	// ---------------------------------------------------------------- restore

	void restoreFocus(String trailId) {
		focusedId = trailId;
	}

	void restoreTracked(String trailId) {
		trackedIds.add(trailId);
	}

	/** Marks a restored checked trail as the game's pick (the situational one when {@code situational}). */
	void restoreAuto(String trailId, boolean situational) {
		autoIds.add(trailId);
		if (situational) {
			situationalId = trailId;
		}
	}

	/** Trims an absurdly oversized saved log, forgets picks that are no longer checked and puts the focus on a checked trail. */
	void normalize() {
		if (trackedIds.size() > LEGACY_MAX) {
			List<String> keep = new ArrayList<>(trackedIds).subList(0, LEGACY_MAX);
			trackedIds.retainAll(keep);
		}
		autoIds.retainAll(trackedIds);
		if (situationalId != null && !trackedIds.contains(situationalId)) {
			situationalId = null;
		}
		if (focusedId == null || !trackedIds.contains(focusedId)) {
			focusedId = trackedIds.isEmpty() ? null : trackedIds.iterator().next();
		}
	}
}
