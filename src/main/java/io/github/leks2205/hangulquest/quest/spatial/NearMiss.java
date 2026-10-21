package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.GoalEvaluator;

/**
 * The "wrong relation" signal. When the player places the right block in the wrong place relative to
 * the anchor (under it instead of on top), the goal type leaves a note on the tracker. The status line
 * reads it for a few seconds; it never counts as progress. The note lives in the tracker's
 * {@code target} slot, which no spatial goal uses otherwise, and is never saved.
 */
public final class NearMiss {
	/** How long the status line keeps showing the hint. */
	public static final long SHOW_MS = 8_000L;

	private static final String PREFIX = "nearmiss:";

	private NearMiss() {
	}

	/** Remembers that the player did {@code wrong} instead of the wanted relation. */
	public static void mark(GoalEvaluator.Tracker tracker, Relation wrong, long now) {
		tracker.target = PREFIX + wrong.name() + "@" + now;
	}

	public static void clear(GoalEvaluator.Tracker tracker) {
		if (tracker.target != null && tracker.target.startsWith(PREFIX)) {
			tracker.target = null;
		}
	}

	/** The relation the player used by mistake, while the note is fresh; otherwise null. */
	public static Relation current(GoalEvaluator.Tracker tracker, long now) {
		String note = tracker.target;
		if (note == null || !note.startsWith(PREFIX)) {
			return null;
		}
		int at = note.indexOf('@');
		if (at < 0) {
			return null;
		}
		try {
			long since = Long.parseLong(note.substring(at + 1));
			if (now - since > SHOW_MS) {
				return null;
			}
		} catch (NumberFormatException broken) {
			return null;
		}
		return Relation.byId(note.substring(PREFIX.length(), at), null);
	}
}
