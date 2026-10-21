package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What to start after the focused trail is finished, and the "pick the next one" popup that is
 * kept alive across ticks until the player picks, starts something else or it runs out.
 */
final class NextQuestPolicy {
	/** How long the player has to pick before the first option is started for them. */
	static final long OFFER_MS = 60_000L;
	private static final long GRACE_MS = 5_000L;

	private final QuestEngine engine;
	private final TrackRegistry registry;
	private final ProgressData data;

	private List<Questline> pending = List.of();
	private long pendingUntil;

	NextQuestPolicy(QuestEngine engine, TrackRegistry registry, ProgressData data) {
		this.engine = engine;
		this.registry = registry;
		this.data = data;
	}

	// ------------------------------------------------------------------ popup

	/**
	 * The options of the open popup, empty when there is none. Other trails being active does not close
	 * it: it ends when the player picks, checks another trail, every option is done, or the time is up.
	 */
	List<Questline> pending(long now) {
		if (pending.isEmpty()) {
			return pending;
		}
		if (now > pendingUntil || pending.stream().allMatch(trail -> isCompleted(trail.id()))) {
			pending = List.of();
		}
		return pending;
	}

	void dismiss() {
		pending = List.of();
	}

	// ----------------------------------------------------------------- policy

	/** With an empty quest log: starts the first unlocked, unfinished trail that fits the dimension. */
	void autoStart(Observation obs, QuestEngine.Feedback feedback) {
		if (registry.trackedCount() > 0) {
			return;
		}
		Questline best = null;
		for (Questline trail : engine.trails()) {
			if (trail.adaptive() || !engine.unlocked(trail) || isCompleted(trail.id())) {
				continue;
			}
			String dimension = trail.requires().dimension();
			if (dimension != null && obs != null && !dimension.equals(obs.dimension)) {
				continue;
			}
			if (best == null || trail.order() < best.order()
					|| (trail.order() == best.order() && trail.id().compareTo(best.id()) < 0)) {
				best = trail;
			}
		}
		if (best != null) {
			engine.startTrail(best.id(), obs, feedback);
		}
	}

	/**
	 * After the focused trail: start its unlocked child, or the missing prerequisite of a locked child.
	 * Several options are handed to the UI for a selection popup.
	 */
	void afterCompletion(String completedId, Observation obs, QuestEngine.Feedback feedback) {
		// Stay on the same tree level, taking the most similar unfinished topic; only then climb.
		Questline finished = engine.byId(completedId);
		TrailGraph graph = engine.graph();
		List<Questline> trails = engine.trails();
		if (finished != null) {
			Map<String, Questline> all = graph.byId();
			int startDepth = graph.depth(finished.id());
			int maxDepth = graph.maxDepth();
			Comparator<Questline> bestFirst = Comparator
					.comparingInt((Questline trail) -> topicSimilarity(trail, finished, all)).reversed()
					.thenComparingInt(Questline::order);
			for (int depth = startDepth; depth <= maxDepth; depth++) {
				int currentDepth = depth;
				List<Questline> candidates = trails.stream()
						.filter(trail -> !"adaptive".equals(trail.id()))
						.filter(trail -> !trail.id().equals(completedId))
						.filter(trail -> !registry.has(trail.id()))
						.filter(trail -> !isCompleted(trail.id()))
						.filter(engine::unlocked)
						.filter(trail -> graph.depth(trail.id()) == currentDepth)
						.filter(trail -> topicSimilarity(trail, finished, all) > 0)
						.sorted(bestFirst)
						.toList();
				if (!candidates.isEmpty()) {
					offer(candidates.subList(0, 1), obs, feedback);
					return;
				}
			}
		}

		List<Questline> children = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> trail.requires().completed().contains(completedId))
				.toList();
		List<Questline> ready = children.stream()
				.filter(trail -> !registry.has(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offer(ready, obs, feedback)) {
			return;
		}

		Set<String> missing = new LinkedHashSet<>();
		for (Questline child : children) {
			for (String parent : child.requires().completed()) {
				if (!isCompleted(parent)) {
					missing.add(parent);
				}
			}
		}
		List<Questline> prereq = missing.stream()
				.map(engine::byId)
				.filter(Objects::nonNull)
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !registry.has(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offer(prereq, obs, feedback)) {
			return;
		}

		List<Questline> fallback = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !registry.has(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		offer(fallback, obs, feedback);
	}

	/** 3 = same branch topic, 1 = sibling (shares a parent), 0 = unrelated. */
	int topicSimilarity(Questline candidate, Questline finished, Map<String, Questline> all) {
		if (Objects.equals(TopicResolver.keyFor(candidate, all), TopicResolver.keyFor(finished, all))) {
			return 3;
		}
		for (String parent : candidate.requires().completed()) {
			if (finished.requires().completed().contains(parent)) {
				return 1;
			}
		}
		return 0;
	}

	/**
	 * Starts a single option, or opens the popup for several. A full quest log (checked trails, not
	 * every started one) also starts the first option directly instead of asking.
	 */
	private boolean offer(List<Questline> options, Observation obs, QuestEngine.Feedback feedback) {
		if (options.isEmpty()) {
			return false;
		}
		if (options.size() == 1 || registry.trackedCount() >= TrackRegistry.MAX_TRACKED) {
			engine.startTrail(options.get(0).id(), obs, feedback);
		} else {
			feedback.chooseNext(options);
			pending = List.copyOf(options.subList(0, Math.min(5, options.size())));
			// A little longer than the UI's own timer, so the UI can still start the first option itself.
			pendingUntil = (obs == null ? System.currentTimeMillis() : obs.now) + OFFER_MS + GRACE_MS;
		}
		return true;
	}

	private boolean isCompleted(String trailId) {
		return data.quests.completed != null && data.quests.completed.containsKey(trailId);
	}
}
