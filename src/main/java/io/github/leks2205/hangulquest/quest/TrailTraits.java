package io.github.leks2205.hangulquest.quest;

import java.util.Map;
import java.util.Set;

/**
 * What the quest picker needs to know about a trail, worked out once when the trails are loaded: how hard
 * it is to start ({@link Reach}), how long it is, and which places and times it fits ({@link Situation}).
 *
 * @param hits per tag, how many steps hint at it
 * @param weak tags only its topic or chapter point at
 */
public record TrailTraits(Reach.Cost reach, int steps, Map<Situation.Tag, Integer> hits, Set<Situation.Tag> weak) {
	/** Points for a tag one step hints at; two steps count as much as a trail made for the place. */
	static final int STEP_POINTS = 20;
	static final int MAX_STEP_HITS = 2;
	static final int WEAK_POINTS = 10;

	static TrailTraits of(Questline trail, String chapterId) {
		return new TrailTraits(Reach.of(trail), trail.allSteps().size(), Situation.stepHits(trail),
				Situation.weak(trail, chapterId));
	}

	/**
	 * How well the trail fits the situation: the best of its tags that the player is in, by the steps that
	 * hint at it, plus what the topic or chapter says, plus the tag's priority. Zero when it fits nothing.
	 */
	public int fit(Set<Situation.Tag> context) {
		int best = 0;
		for (Situation.Tag tag : context) {
			int own = Math.min(MAX_STEP_HITS, hits.getOrDefault(tag, 0)) * STEP_POINTS
					+ (weak.contains(tag) ? WEAK_POINTS : 0);
			if (own > 0) {
				best = Math.max(best, own + Situation.priority(tag));
			}
		}
		return best;
	}
}
