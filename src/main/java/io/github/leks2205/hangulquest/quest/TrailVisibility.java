package io.github.leks2205.hangulquest.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
 * <li>{@link State#NEXT}: a direct follow-up of an available or active trail. Drawn greyed out with what it
 * waits for.</li>
 * <li>{@link State#HIDDEN}: everything further away. Not drawn.</li>
 * </ul>
 *
 * A trail that is finished or in the log keeps that state whatever its prerequisites say.
 *
 * <p>A trail that waits only for words (no prerequisite left, but not enough words yet) is no follow-up of
 * anything, so it stays hidden until the player has the words. The one exception: with fewer than
 * {@value #MIN_SHOWN} trails to start or in the log, the word-gated trails closest to being open are shown
 * greyed out until there are that many, so a new player always sees something to look forward to.
 */
public final class TrailVisibility {
	public enum State {
		COMPLETED, ACTIVE, AVAILABLE, NEXT, HIDDEN
	}

	/** Fewer available or active trails than this: the nearest word-gated ones are shown greyed out. */
	public static final int MIN_SHOWN = 3;

	private TrailVisibility() {
	}

	/**
	 * @param unlocked whether a trail can be started now
	 */
	public static Map<String, State> of(TrailGraph graph, Predicate<String> completed, Predicate<String> tracked,
										Predicate<Questline> unlocked) {
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
		List<Questline> wordGated = new ArrayList<>();
		for (Questline trail : graph.byId().values()) {
			String id = trail.id();
			if (first.containsKey(id)) {
				continue;
			}
			switch (follow(trail, graph, first)) {
				case FOLLOWS -> states.put(id, State.NEXT);
				case WORDS_ONLY -> {
					states.put(id, State.HIDDEN);
					wordGated.add(trail);
				}
				case FAR -> states.put(id, State.HIDDEN);
			}
		}
		long shown = first.values().stream().filter(state -> state == State.AVAILABLE || state == State.ACTIVE).count();
		if (shown < MIN_SHOWN) {
			wordGated.sort(Comparator.comparingInt((Questline trail) -> trail.requires().minWordsKnown())
					.thenComparingInt(Questline::order).thenComparing(Questline::id));
			for (int i = 0; i < wordGated.size() && shown + i < MIN_SHOWN; i++) {
				states.put(wordGated.get(i).id(), State.NEXT);
			}
		}
		return states;
	}

	private enum Follow {
		FOLLOWS, WORDS_ONLY, FAR
	}

	/**
	 * Decided against the first-round states only, so a trail two steps away stays hidden. It follows when some
	 * prerequisite is available or active and the rest are done or are that too.
	 */
	private static Follow follow(Questline trail, TrailGraph graph, Map<String, State> first) {
		boolean waitsForParent = false;
		for (String parent : graph.parents(trail.id())) {
			State state = first.get(parent);
			if (state == State.COMPLETED) {
				continue;
			}
			if (state != State.AVAILABLE && state != State.ACTIVE) {
				return Follow.FAR;
			}
			waitsForParent = true;
		}
		return waitsForParent ? Follow.FOLLOWS : Follow.WORDS_ONLY;
	}

	/** True for the states the tree draws: the log, what can be started and what waits behind it. */
	public static boolean drawn(State state) {
		return state == State.ACTIVE || state == State.AVAILABLE || state == State.NEXT;
	}
}
