package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** The authored trails and the graph over them, rebuilt whenever the list changes. */
final class TrailSet {
	private final Logger log;
	private List<Questline> trails;
	/** Lookup, parents, children and depth of {@link #trails}. */
	private TrailGraph graph;

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
		graph = TrailGraph.of(trails);
		if (graph.hasCycle()) {
			log.warn("questlines require each other in a cycle, their tree level falls back to 1: {}",
					graph.cyclic());
		}
	}
}
