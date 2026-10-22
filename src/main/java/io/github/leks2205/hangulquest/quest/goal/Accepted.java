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
 * <p>It also holds the ids whose colour the clue says: a coloured block in such a goal is matched exactly (a red
 * bed is no blue bed), because the player was told which one. Where the clue leaves the colour out (a taught
 * general word below level 3) the colour is not checked either. See {@link Colours}.
 *
 * @param from  the goal ids (object, anchor, container ...) that the taught word covers
 * @param items every referent of the taught word
 * @param exact the goal ids of a coloured block that only that very id satisfies
 */
public record Accepted(Set<String> from, List<String> items, java.util.Map<String, List<String>> extra,
					   Set<String> exact) {
	public static final Accepted NONE = new Accepted(Set.of(), List.of(), java.util.Map.of(), Set.of());

	/** Marks an exact spec for {@link io.github.leks2205.hangulquest.quest.spatial.BlockSpec}. */
	public static final String EXACT = "=";

	/** The cooked form of what a furnace turns into food: a covered raw fish widens the cooked output. */
	private static final java.util.Map<String, String> COOKED = java.util.Map.ofEntries(
			java.util.Map.entry("minecraft:cod", "minecraft:cooked_cod"),
			java.util.Map.entry("minecraft:salmon", "minecraft:cooked_salmon"),
			java.util.Map.entry("minecraft:beef", "minecraft:cooked_beef"),
			java.util.Map.entry("minecraft:porkchop", "minecraft:cooked_porkchop"),
			java.util.Map.entry("minecraft:chicken", "minecraft:cooked_chicken"),
			java.util.Map.entry("minecraft:mutton", "minecraft:cooked_mutton"),
			java.util.Map.entry("minecraft:rabbit", "minecraft:cooked_rabbit"),
			java.util.Map.entry("minecraft:potato", "minecraft:baked_potato"),
			java.util.Map.entry("minecraft:kelp", "minecraft:dried_kelp"));

	public Accepted(Set<String> from, List<String> items, java.util.Map<String, List<String>> extra) {
		this(from, items, extra, Set.of());
	}

	public Accepted(Set<String> from, List<String> items) {
		this(from, items, java.util.Map.of());
	}

	/** The same set with these goal ids matched exactly (the ones whose colour the clue says). */
	public Accepted withExact(Set<String> ids) {
		return ids.equals(exact) ? this : new Accepted(from, items, extra, Set.copyOf(ids));
	}

	/** Whether the clue names the colour of this goal id, so only that block satisfies it. */
	public boolean isExact(String goalId) {
		return goalId != null && exact.contains(goalId);
	}

	/** The set for a step: the goal ids that are referents of its first taught concept, and all referents. */
	public static Accepted of(QuestStep step, Concept first) {
		if (step == null || step.goal() == null || first == null || first.items == null) {
			return NONE;
		}
		List<String> all = new java.util.ArrayList<>(first.items);
		if (first.covers != null) {
			all.addAll(first.covers);
		}
		if (first.accepts != null) {
			all.addAll(first.accepts);
		}
		if (first.biome != null && !all.contains(first.biome)) {
			all.add(0, first.biome); // a biome word covers its own biome and the ones it lists
		}
		java.util.Map<String, List<String>> extra = new java.util.HashMap<>();
		List<String> creatures = first.entities == null ? List.of() : first.entities;
		if (!creatures.isEmpty()) { // 물고기 for a cod, a salmon or a tropical fish: any of them is the creature
			for (GoalType.Mention mention : GoalTypes.of(step.goal()).mentions(step.goal())) {
				if (mention.kind() == ObjectKind.ENTITY && creatures.contains(mention.id())) {
					extra.put(mention.id(), List.copyOf(creatures));
				}
			}
		}
		if (all.size() < 2) {
			return extra.isEmpty() ? NONE : new Accepted(Set.of(), List.of(), java.util.Map.copyOf(extra));
		}
		Set<String> from = new LinkedHashSet<>();
		for (GoalType.Mention mention : GoalTypes.of(step.goal()).mentions(step.goal())) {
			if (mention.kind() == ObjectKind.BIOME) {
				if (first.biome != null && all.contains(mention.id())) {
					from.add(mention.id());
				}
				continue;
			}
			if (mention.kind() != ObjectKind.BLOCK && mention.kind() != ObjectKind.ITEM) {
				continue;
			}
			String id = io.github.leks2205.hangulquest.quest.spatial.BlockSpec.representative(mention.id());
			if (Tiers.isGeneralWordOf(id, first.ko)) {
				continue; // the sentence names the tier (철 곡괭이): only that one counts
			}
			for (String item : all) {
				if (item.equals(id) || Matching.matchesBlock(id, item)) {
					from.add(mention.id());
					break;
				}
			}
		}
		if (step.goal() instanceof io.github.leks2205.hangulquest.quest.Goal.Smelt smelt && smelt.input() != null
				&& from.contains(smelt.input())) {
			// 물고기 for a cod in the furnace: the cooked form of every fish the word covers is the product
			List<String> cooked = all.stream().map(COOKED::get).filter(java.util.Objects::nonNull).toList();
			if (!cooked.isEmpty()) {
				extra.put(smelt.output(), cooked);
			}
		}
		return from.isEmpty() && extra.isEmpty() ? NONE
				: new Accepted(Set.copyOf(from), List.copyOf(all), java.util.Map.copyOf(extra));
	}

	/** True when {@code is} holds for the goal biome or for one of the biomes the taught word covers. */
	public boolean anyBiome(String goalBiome, java.util.function.Predicate<String> is) {
		if (is.test(goalBiome)) {
			return true;
		}
		return widens(goalBiome) && items.stream().anyMatch(is);
	}

	/** True when {@code goalId} is covered by the taught word and {@code observed} is one of its referents. */
	public boolean matches(String goalId, String observed) {
		if (observed != null && goalId != null && extra.getOrDefault(goalId, List.of()).contains(observed)) {
			return true;
		}
		if (observed == null || goalId == null || !from.contains(goalId) || exact.contains(goalId)) {
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
		if (isExact(spec)) {
			return EXACT + spec;
		}
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
