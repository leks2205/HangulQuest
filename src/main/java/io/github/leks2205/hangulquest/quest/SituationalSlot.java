package io.github.leks2205.hangulquest.quest;

import java.util.Set;

/**
 * The timing of the situational quest slot: a new context only counts once it has held for
 * {@value #HOLD_MS} ms, so walking through a cave mouth, over a hill or past a lake does not swap the
 * trail. Pure and driven by the observation clock, so it is tested without a game.
 */
final class SituationalSlot {
	static final long HOLD_MS = 20_000L;

	private Set<Situation.Tag> seen;
	private long since;
	private Set<Situation.Tag> decided;

	/**
	 * Feed the current context every tick. Returns it once, when it has held {@link #HOLD_MS} and is not the
	 * one that was last returned; null otherwise.
	 */
	Set<Situation.Tag> settled(Set<Situation.Tag> context, long now) {
		if (seen == null || !seen.equals(context)) {
			seen = Set.copyOf(context);
			since = now;
		}
		if (now - since >= HOLD_MS && !seen.equals(decided)) {
			decided = seen;
			return seen;
		}
		return null;
	}

	/** Asks for the current context to be returned again (after the slot's trail was finished). */
	void reevaluate() {
		decided = null;
	}
}
