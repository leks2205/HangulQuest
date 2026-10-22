package io.github.leks2205.hangulquest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The task-variety metrics of the variety engine ("recycle words, vary tasks"). tools/variety.py
 * computes the same numbers from steps.json; {@code TaskVarietyTest} checks that the two agree.
 * Every key starts with {@code variety.} and matches a line of content/metrics-baseline.json.
 */
final class VarietyMetrics {
	/** How many earlier trails a trail's new mechanic must be new against. */
	static final int NOVELTY_WINDOW = 5;
	/** A (verb, object) pair may come back this often. */
	static final int PAIR_LIMIT = 3;

	private VarietyMetrics() {
	}

	/**
	 * @param includeDemo false for the ratchet: demo trails are measured in the report but exempt from it
	 */
	static Map<String, Double> compute(List<ContentFacts.Trail> trails, boolean includeDemo) {
		List<ContentFacts.Trail> path = ContentFacts.learnerPath(trails, includeDemo);
		List<ContentFacts.Step> steps = new ArrayList<>();
		List<Integer> levels = new ArrayList<>();
		for (ContentFacts.Trail trail : path) {
			for (ContentFacts.Step step : trail.steps()) {
				steps.add(step);
				levels.add(trail.level());
			}
		}

		Map<String, Double> out = new LinkedHashMap<>();

		// Duplicate full task signatures: copies beyond the first.
		Set<String> seen = new HashSet<>();
		int duplicates = 0;
		for (ContentFacts.Step step : steps) {
			if (!seen.add(step.signature())) {
				duplicates++;
			}
		}
		out.put("variety.duplicateSignatures", (double) duplicates);

		// Per trail: steps whose shape an earlier step of the same trail already had.
		int twins = 0;
		for (ContentFacts.Trail trail : path) {
			twins += twinsIn(trail);
		}
		out.put("variety.objectOnlyRepeats", (double) twins);

		// (verb, object) pairs.
		Map<String, Integer> pairs = new HashMap<>();
		for (ContentFacts.Step step : steps) {
			for (String pair : step.pairs()) { // a sequence counts the pairs of its parts
				pairs.merge(pair, 1, Integer::sum);
			}
		}
		int max = pairs.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		long over = pairs.values().stream().filter(count -> count > PAIR_LIMIT).count();
		out.put("variety.verbObjectMax", (double) max);
		out.put("variety.verbObjectPairsOver3", (double) over);

		// Novelty window.
		out.put("variety.noveltyShare", noveltyShare(path));

		// Boredom: consecutive steps of the learner path that differ only in the object.
		int same = 0;
		for (int i = 1; i < steps.size(); i++) {
			if (steps.get(i).shape().equals(steps.get(i - 1).shape())) {
				same++;
			}
		}
		out.put("variety.boredomIndex", steps.size() < 2 ? 0.0 : (double) same / (steps.size() - 1));

		// Per level: bare share, goal-type share, distinct goal types.
		Map<Integer, Integer> total = new TreeMap<>();
		Map<Integer, Integer> bare = new TreeMap<>();
		Map<Integer, Map<String, Integer>> types = new TreeMap<>();
		Map<Integer, Integer> forms = new TreeMap<>();
		for (int i = 0; i < steps.size(); i++) {
			ContentFacts.Step step = steps.get(i);
			int level = levels.get(i);
			total.merge(level, 1, Integer::sum);
			if (step.bare()) {
				bare.merge(level, 1, Integer::sum);
			}
			if (step.formDependent()) {
				forms.merge(level, 1, Integer::sum);
			}
			types.computeIfAbsent(level, key -> new HashMap<>()).merge(step.goalType(), 1, Integer::sum);
		}
		for (int level : total.keySet()) {
			double n = total.get(level);
			out.put("variety.bareShare." + level, bare.getOrDefault(level, 0) / n);
			out.put("variety.topTypeShare." + level,
					types.get(level).values().stream().mapToInt(Integer::intValue).max().orElse(0) / n);
			out.put("variety.distinctTypes." + level, (double) types.get(level).size());
			out.put("variety.formDependentShare." + level, forms.getOrDefault(level, 0) / n);
		}
		return out;
	}

	/** Steps of a trail whose shape an earlier step of that trail has: the tasks that only swap the word. */
	static int twinsIn(ContentFacts.Trail trail) {
		Set<String> shapes = new HashSet<>();
		int twins = 0;
		for (ContentFacts.Step step : trail.steps()) {
			if (!shapes.add(step.shape())) {
				twins++;
			}
		}
		return twins;
	}

	/** The share of trails that bring a mechanic none of the previous {@link #NOVELTY_WINDOW} trails had. */
	static double noveltyShare(List<ContentFacts.Trail> path) {
		if (path.isEmpty()) {
			return 1.0;
		}
		int novel = 0;
		for (int i = 0; i < path.size(); i++) {
			Set<String> mine = new HashSet<>();
			path.get(i).steps().forEach(step -> mine.addAll(step.mechanics()));
			Set<String> before = new HashSet<>();
			for (int j = Math.max(0, i - NOVELTY_WINDOW); j < i; j++) {
				path.get(j).steps().forEach(step -> before.addAll(step.mechanics()));
			}
			mine.removeAll(before);
			if (i == 0 || !mine.isEmpty()) {
				novel++;
			}
		}
		return (double) novel / path.size();
	}
}
