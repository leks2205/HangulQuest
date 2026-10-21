package io.github.leks2205.hangulquest.quest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable prerequisite graph of a trail list: lookup by id, parents, children, tree depth and
 * topological order. Built once whenever the trail list is set; every query is a map lookup.
 *
 * <p>Depth is the tree level: roots are 1, a trail is one deeper than its deepest known parent.
 * Parents that do not exist are ignored. Trails that sit on a prerequisite cycle (or between two
 * cycles) cannot be ordered; they are reported by {@link #cyclic()}, get depth 1 and stay out of
 * {@link #topologicalOrder()}. Nothing here recurses, so a cycle can never overflow the stack.
 */
public final class TrailGraph {
	private final Map<String, Questline> byId;
	private final Map<String, List<String>> parents;
	private final Map<String, List<Questline>> children;
	private final Map<String, Integer> depth;
	private final List<Questline> topological;
	private final Set<String> cyclic;
	private final int maxDepth;
	private final int evaluations;

	private TrailGraph(Map<String, Questline> byId, Map<String, List<String>> parents,
					   Map<String, List<Questline>> children, Map<String, Integer> depth,
					   List<Questline> topological, Set<String> cyclic, int maxDepth, int evaluations) {
		this.byId = byId;
		this.parents = parents;
		this.children = children;
		this.depth = depth;
		this.topological = topological;
		this.cyclic = cyclic;
		this.maxDepth = maxDepth;
		this.evaluations = evaluations;
	}

	public static TrailGraph of(List<Questline> trails) {
		Map<String, Questline> byId = new LinkedHashMap<>();
		for (Questline trail : trails) {
			byId.putIfAbsent(trail.id(), trail); // the first one wins, as a linear scan would
		}
		Map<String, List<String>> parents = new HashMap<>();
		Map<String, List<Questline>> children = new HashMap<>();
		for (Questline trail : byId.values()) {
			List<String> known = new ArrayList<>();
			for (String parentId : new LinkedHashSet<>(trail.requires().completed())) {
				if (byId.containsKey(parentId)) {
					known.add(parentId);
					children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(trail);
				}
			}
			parents.put(trail.id(), List.copyOf(known));
		}

		// Kahn's algorithm: a trail is evaluated once, after all of its parents.
		Map<String, Integer> waiting = new HashMap<>();
		ArrayDeque<String> ready = new ArrayDeque<>();
		for (String id : byId.keySet()) {
			int open = parents.get(id).size();
			waiting.put(id, open);
			if (open == 0) {
				ready.add(id);
			}
		}
		Map<String, Integer> depth = new HashMap<>();
		List<Questline> topological = new ArrayList<>(byId.size());
		int evaluations = 0;
		int maxDepth = 1;
		while (!ready.isEmpty()) {
			String id = ready.poll();
			int level = 1;
			for (String parentId : parents.get(id)) {
				level = Math.max(level, depth.get(parentId) + 1);
			}
			evaluations++;
			depth.put(id, level);
			maxDepth = Math.max(maxDepth, level);
			topological.add(byId.get(id));
			for (Questline child : children.getOrDefault(id, List.of())) {
				if (waiting.merge(child.id(), -1, Integer::sum) == 0) {
					ready.add(child.id());
				}
			}
		}

		// What is left is on a cycle or downstream of one. Peel away everything without a
		// remaining child, so only trails on a cycle (or between cycles) stay reported.
		Set<String> cyclic = new LinkedHashSet<>();
		for (String id : byId.keySet()) {
			if (!depth.containsKey(id)) {
				cyclic.add(id);
			}
		}
		boolean peeled = true;
		while (peeled) {
			peeled = false;
			for (String id : new ArrayList<>(cyclic)) {
				boolean hasChildInside = false;
				for (Questline child : children.getOrDefault(id, List.of())) {
					if (cyclic.contains(child.id())) {
						hasChildInside = true;
						break;
					}
				}
				if (!hasChildInside) {
					cyclic.remove(id);
					peeled = true;
				}
			}
		}
		for (String id : byId.keySet()) {
			depth.putIfAbsent(id, 1);
		}

		Map<String, List<Questline>> frozenChildren = new HashMap<>();
		children.forEach((key, value) -> frozenChildren.put(key, List.copyOf(value)));
		return new TrailGraph(Collections.unmodifiableMap(byId), Map.copyOf(parents),
				Map.copyOf(frozenChildren), Map.copyOf(depth), List.copyOf(topological),
				Collections.unmodifiableSet(cyclic), maxDepth, evaluations);
	}

	/** The trail with this id, or null. */
	public Questline byId(String id) {
		return id == null ? null : byId.get(id);
	}

	/** Every trail by id (first occurrence of a duplicated id). */
	public Map<String, Questline> byId() {
		return byId;
	}

	/** Ids of the existing prerequisites of a trail; unknown ids are left out. */
	public List<String> parents(String id) {
		return parents.getOrDefault(id, List.of());
	}

	/** Trails that list this one as a prerequisite. */
	public List<Questline> children(String id) {
		return children.getOrDefault(id, List.of());
	}

	/** Tree level of a trail: roots are 1. Unknown ids and trails on a cycle give 1. */
	public int depth(String id) {
		return depth.getOrDefault(id, 1);
	}

	/** The deepest tree level. */
	public int maxDepth() {
		return maxDepth;
	}

	/** Trails with their prerequisites first; trails on a cycle are missing. */
	public List<Questline> topologicalOrder() {
		return topological;
	}

	/** Ids of trails that sit on a prerequisite cycle. */
	public Set<String> cyclic() {
		return cyclic;
	}

	public boolean hasCycle() {
		return !cyclic.isEmpty();
	}

	/** How many depth evaluations building this graph took; one per orderable trail. */
	public int evaluations() {
		return evaluations;
	}
}
