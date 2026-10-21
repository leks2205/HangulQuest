package io.github.leks2205.hangulquest.quest.goal;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.QuestStep;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What else a step accepts besides the exact ids of its goal. A clue names the taught word (꽃, 물고기),
 * the goal checks one item (poppy, cod); when the word has several referents, any of them does. The
 * set is built once per step from the first taught concept, so evaluation stays a few set lookups.
 * A concept with a single referent never widens a goal.
 *
 * @param from  the goal ids (object, anchor, container ...) that the taught word covers
 * @param items every referent of the taught word
 */
public record Accepted(Set<String> from, List<String> items) {
	public static final Accepted NONE = new Accepted(Set.of(), List.of());

	/** The set for a step: the goal ids that are referents of its first taught concept, and all referents. */
	public static Accepted of(QuestStep step, Concept first) {
		if (step == null || step.goal() == null || first == null || first.items == null || first.items.size() < 2) {
			return NONE;
		}
		Set<String> from = new LinkedHashSet<>();
		for (GoalType.Mention mention : GoalTypes.of(step.goal()).mentions(step.goal())) {
			if (mention.kind() != ObjectKind.BLOCK && mention.kind() != ObjectKind.ITEM) {
				continue;
			}
			String id = io.github.leks2205.hangulquest.quest.spatial.BlockSpec.representative(mention.id());
			for (String item : first.items) {
				if (item.equals(id) || Matching.matchesBlock(id, item)) {
					from.add(mention.id());
					break;
				}
			}
		}
		return from.isEmpty() ? NONE : new Accepted(Set.copyOf(from), List.copyOf(first.items));
	}

	/** True when {@code goalId} is covered by the taught word and {@code observed} is one of its referents. */
	public boolean matches(String goalId, String observed) {
		if (observed == null || goalId == null || !from.contains(goalId)) {
			return false;
		}
		for (String item : items) {
			if (Matching.matchesBlock(item, observed)) {
				return true;
			}
		}
		return false;
	}

	/** True when the goal id is widened. */
	public boolean widens(String goalId) {
		return goalId != null && from.contains(goalId);
	}

	/** A block spec that also matches the other referents: {@code spec|other|other} (see BlockSpec). */
	public String union(String spec) {
		if (!widens(spec)) {
			return spec;
		}
		StringBuilder out = new StringBuilder(spec);
		for (String item : items) {
			out.append('|').append(item);
		}
		return out.toString();
	}
}
