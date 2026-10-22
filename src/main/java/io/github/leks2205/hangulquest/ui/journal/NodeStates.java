package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Per-trail state of the tree (done, unlocked, in the log, recommended next) for one moment of
 * progress. Asking the quest engine for it costs a lookup per trail, so the result is kept and only
 * rebuilt when what it was computed from changes ({@link #matches}).
 */
final class NodeStates {
	static final int DONE = 1;
	static final int UNLOCKED = 2;
	static final int TRACKED = 4;
	static final int RECOMMENDED = 8;
	/** Waits directly behind a trail that can be started or is in the log: drawn greyed out. */
	static final int NEXT = 16;

	private final Object layout;
	private final Object completedMap;
	private final int completedCount;
	private final int discovered;
	private final int tracked;
	private final String focused;
	private final Object activeTrail;
	private final byte[] flags;
	private final int current;

	private NodeStates(Object layout, Object completedMap, int completedCount, int discovered, int tracked,
					   String focused, Object activeTrail, byte[] flags, int current) {
		this.layout = layout;
		this.completedMap = completedMap;
		this.completedCount = completedCount;
		this.discovered = discovered;
		this.tracked = tracked;
		this.focused = focused;
		this.activeTrail = activeTrail;
		this.flags = flags;
		this.current = current;
	}

	/**
	 * True when the states were computed for exactly this situation: the same layout, the same
	 * completed map with the same number of entries, the same word count, quest log and focus.
	 */
	boolean matches(Object layout, Object completedMap, int completedCount, int discovered, int tracked,
					String focused, Object activeTrail) {
		return this.layout == layout && this.completedMap == completedMap
				&& this.completedCount == completedCount && this.discovered == discovered
				&& this.tracked == tracked && this.activeTrail == activeTrail
				&& Objects.equals(this.focused, focused);
	}

	boolean has(int index, int flag) {
		return (flags[index] & flag) != 0;
	}

	/** Index of the trail the recommendation is relative to (the active one, else the last finished), or -1. */
	int current() {
		return current;
	}

	/**
	 * @param completed trail id to completion time; may be null
	 * @param active the focused trail, or null; with the counts and the focus it is what {@link #matches} compares
	 */
	static NodeStates build(QuestTreeLayout layout, Map<String, Long> completed, int discovered, int tracked,
							String focused, Predicate<Questline> unlocked, Predicate<String> isTracked,
							Questline active) {
		return build(layout, completed, discovered, tracked, focused, unlocked, isTracked, active, id -> false);
	}

	/** As above; {@code greyed} marks the trails that wait directly behind an available or tracked one. */
	static NodeStates build(QuestTreeLayout layout, Map<String, Long> completed, int discovered, int tracked,
							String focused, Predicate<Questline> unlocked, Predicate<String> isTracked,
							Questline active, Predicate<String> greyed) {
		int count = layout.size();
		byte[] flags = new byte[count];
		boolean[] done = new boolean[count];
		boolean[] open = new boolean[count];
		for (int i = 0; i < count; i++) {
			Questline trail = layout.trail(i);
			done[i] = completed != null && completed.containsKey(trail.id());
			open[i] = unlocked.test(trail);
			int bits = (done[i] ? DONE : 0) | (open[i] ? UNLOCKED : 0)
					| (isTracked.test(trail.id()) ? TRACKED : 0)
					| (greyed.test(trail.id()) ? NEXT : 0);
			flags[i] = (byte) bits;
		}

		int current = active == null ? -1 : layout.indexOf(active.id());
		if (active == null && completed != null) {
			String lastId = null;
			long lastAt = Long.MIN_VALUE;
			for (Map.Entry<String, Long> entry : completed.entrySet()) {
				if (entry.getValue() > lastAt) {
					lastAt = entry.getValue();
					lastId = entry.getKey();
				}
			}
			current = layout.indexOf(lastId);
		}
		if (current >= 0) {
			Questline base = layout.trail(current);
			for (int i = 0; i < count; i++) {
				Questline trail = layout.trail(i);
				if (!done[i] && open[i] && i != current
						&& trail.koreanLevel() == base.koreanLevel()
						&& layout.topic(i).equals(layout.topic(current))) {
					flags[i] |= RECOMMENDED;
				}
			}
		}
		return new NodeStates(layout, completed, completed == null ? 0 : completed.size(), discovered, tracked,
				focused, active, flags, current);
	}
}
