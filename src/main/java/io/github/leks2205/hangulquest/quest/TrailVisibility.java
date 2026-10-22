package io.github.leks2205.hangulquest.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which trails the journal draws. Only what is useful now is shown, so a new player does not face the whole
 * tree: the trails that can be started, the ones in the quest log, and the ones that wait directly behind them.
 *
 * <ul>
 * <li>{@link State#COMPLETED}: finished; kept in a collapsed group.</li>
 * <li>{@link State#ACTIVE}: in the quest log.</li>
 * <li>{@link State#AVAILABLE}: can be started now (every prerequisite done, enough words).</li>
 * <li>{@link State#NEXT}: waits for something that can be reached from here: a prerequisite that is itself
 * available or active, or, for a trail without unmet prerequisites, a few words more
 * ({@value #WORD_LOOKAHEAD} or fewer). Drawn greyed out with what it waits for.</li>
 * <li>{@link State#HIDDEN}: everything further away. Not drawn.</li>
 * </ul>
 *
 * A trail that is finished or in the log keeps that state whatever its prerequisites say.
 */
public final class TrailVisibility {
	public enum State {
		COMPLETED, ACTIVE, AVAILABLE, NEXT, HIDDEN
	}

	/** A trail that needs at most this many more words than the player has is shown greyed out. */
	public static final int WORD_LOOKAHEAD = 10;

	private TrailVisibility() {
	}

	/**
	 * @param discovered the words the player has met; what {@code minWordsKnown} is compared with
	 * @param unlocked   whether a trail can be started now
	 */
	public static Map<String, State> of(TrailGraph graph, Predicate<String> completed, Predicate<String> tracked,
										Predicate<Questline> unlocked, int discovered) {
		Map<String, State> states = new HashMap<>();
		for (Questline trail : graph.byId().values()) {
			String id = trail.id();
			if (completed.test(id)) {
				states.put(id, State.COMPLETED);
			} else if (tracked.test(id)) {
				states.put(id, State.ACTIVE);
			} else if (unlocked.test(trail)) {
				states.put(id, State.AVAILABLE);
			}
		}
		Map<String, State> first = new HashMap<>(states);
		for (Questline trail : graph.byId().values()) {
			String id = trail.id();
			if (!first.containsKey(id)) {
				states.put(id, isNext(trail, graph, first, discovered) ? State.NEXT : State.HIDDEN);
			}
		}
		return states;
	}

	/** Decided against the first-round states only, so a trail two steps away stays hidden. */
	private static boolean isNext(Questline trail, TrailGraph graph, Map<String, State> first, int discovered) {
		boolean waitsForParent = false;
		for (String parent : graph.parents(trail.id())) {
			State state = first.get(parent);
			if (state == State.COMPLETED) {
				continue;
			}
			if (state != State.AVAILABLE && state != State.ACTIVE) {
				return false;
			}
			waitsForParent = true;
		}
		if (waitsForParent) {
			return true;
		}
		return trail.requires().minWordsKnown() - discovered <= WORD_LOOKAHEAD;
	}

	/** True for the states the tree draws: the log, what can be started and what waits behind it. */
	public static boolean drawn(State state) {
		return state == State.ACTIVE || state == State.AVAILABLE || state == State.NEXT;
	}
}
