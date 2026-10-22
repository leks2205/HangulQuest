package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The authored trails and the graph over them, rebuilt whenever the list changes. */
final class TrailSet {
	private final Logger log;
	private List<Questline> trails;
	/** Lookup, parents, children and depth of {@link #trails}. */
	private TrailGraph graph;
	/** What the quest picker knows about each trail, worked out when first asked and dropped when the list changes. */
	private final Map<String, TrailTraits> traits = new HashMap<>();
	/** Chapter id of a trail, or null; set by the game once the chapters are loaded. */
	private Function<String, String> chapterOf = id -> null;

	TrailSet(List<Questline> trails, Logger log) {
		this.log = log;
		set(trails);
	}

	List<Questline> trails() {
		return trails;
	}

	TrailGraph graph() {
		return graph;
	}

	/** The pre-computed picker facts of a trail. */
	TrailTraits traits(Questline trail) {
		return traits.computeIfAbsent(trail.id(), id -> TrailTraits.of(trail, chapterOf.apply(id)));
	}

	void chapterOf(Function<String, String> chapterOf) {
		this.chapterOf = chapterOf == null ? id -> null : chapterOf;
		traits.clear();
	}

	Questline byId(String trailId) {
		return graph.byId(trailId);
	}

	/** Replaces the whole list but keeps the generated adaptive trail. */
	void replace(List<Questline> replacement) {
		Questline adaptive = byId("adaptive");
		List<Questline> merged = new ArrayList<>(replacement);
		if (adaptive != null && merged.stream().noneMatch(trail -> trail.id().equals("adaptive"))) {
			merged.add(adaptive);
		}
		set(merged);
	}

	void add(Questline trail) {
		List<Questline> merged = new ArrayList<>(trails);
		merged.removeIf(existing -> existing.id().equals(trail.id()));
		merged.add(trail);
		set(merged);
	}

	private void set(List<Questline> next) {
		trails = List.copyOf(next);
		traits.clear();
		graph = TrailGraph.of(trails);
		if (graph.hasCycle()) {
			log.warn("questlines require each other in a cycle, their tree level falls back to 1: {}",
					graph.cyclic());
		}
	}
}
