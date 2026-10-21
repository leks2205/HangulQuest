package io.github.leks2205.hangulquest.quest;

import java.util.function.Function;

/** Puts trails into the quest log and takes them out again, and saves after every change. */
final class TrackingService {
	private final TrackRegistry registry;
	private final QuestPersistence persistence;
	private final NextQuestPolicy next;
	private final Function<String, Questline> trails;

	TrackingService(TrackRegistry registry, QuestPersistence persistence, NextQuestPolicy next,
					Function<String, Questline> trails) {
		this.registry = registry;
		this.persistence = persistence;
		this.next = next;
		this.trails = trails;
	}

	/** Checks a trail (resuming its saved state) and focuses it; false when the log has no room. */
	boolean track(String trailId, long now) {
		Track track = registry.get(trailId);
		boolean fresh = false;
		if (track == null) {
			track = persistence.resumed(trailId);
			if (track == null) {
				track = new Track(trails.apply(trailId), now);
				fresh = true;
			}
			registry.put(track);
		}
		if (!registry.track(trailId)) {
			if (fresh) {
				registry.remove(trailId);
			}
			return false;
		}
		next.dismiss();
		persistence.persist(registry, now);
		return true;
	}

	boolean focus(String trailId, long now) {
		if (!registry.focus(trailId)) {
			return false;
		}
		persistence.persist(registry, now);
		return true;
	}

	/** Checks or unchecks a trail. Checking fails for an unknown trail or a log full of quests with progress. */
	boolean setTracked(String trailId, boolean on, long now) {
		if (on) {
			return (registry.has(trailId) || trails.apply(trailId) != null) && track(trailId, now);
		}
		if (registry.untrack(trailId)) {
			persistence.persist(registry, now);
		}
		return true;
	}
}
