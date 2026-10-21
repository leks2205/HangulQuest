package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.learn.ProgressData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Saves the {@link TrackRegistry} into {@link ProgressData.QuestData} and builds it back from there. */
final class QuestPersistence {
	private final ProgressData data;
	private final LearningSink learn;
	private final Catalog catalog;
	private final Function<String, Questline> trails;

	QuestPersistence(LearningSink learn, Catalog catalog, Function<String, Questline> trails) {
		this.learn = learn;
		this.data = learn.data();
		this.catalog = catalog;
		this.trails = trails;
	}

	void restore(TrackRegistry registry) {
		ProgressData.QuestData quests = data.quests;
		if (quests == null) {
			return;
		}
		if (quests.active != null && quests.active.trailId != null) {
			Track track = trackOf(quests.active);
			if (track != null) {
				registry.put(track);
				registry.restoreFocus(track.trail.id());
			} else {
				quests.active = null;
			}
		}
		if (quests.saved != null) {
			for (ProgressData.Active snapshot : quests.saved.values()) {
				Track track = trackOf(snapshot);
				if (track != null && !registry.has(track.trail.id())) {
					registry.put(track);
				}
			}
			// Rebuilt from the live tracks on the next persist.
			quests.saved = new LinkedHashMap<>();
		}
		String focusedId = registry.focusedId();
		if (quests.tracked != null) {
			for (String id : quests.tracked) {
				if (registry.has(id)) {
					registry.restoreTracked(id);
				}
			}
		} else if (focusedId != null) {
			// Before the log had checkboxes only the active quest advanced; keep it that way.
			registry.restoreTracked(focusedId);
		}
		if (focusedId != null) {
			registry.restoreTracked(focusedId);
		}
		registry.normalize();
	}

	/** The saved state of a trail the player left earlier, or null. */
	Track resumed(String trailId) {
		ProgressData.Active snapshot = data.quests.saved == null ? null : data.quests.saved.get(trailId);
		return snapshot == null ? null : trackOf(snapshot);
	}

	Track trackOf(ProgressData.Active snapshot) {
		if (snapshot == null || snapshot.trailId == null) {
			return null;
		}
		Questline trail = trails.apply(snapshot.trailId);
		if (trail == null) {
			return null;
		}
		Track track = new Track(trail, snapshot.startedAt);
		track.stepIndex = 0;
		List<QuestStep> steps = trail.allSteps();
		if (snapshot.stepId != null) {
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(snapshot.stepId)) {
					track.stepIndex = i;
					break;
				}
			}
		}
		track.hintUsed = snapshot.hintUsed;
		track.lastSeenAt = snapshot.lastSeenAt == 0 ? snapshot.startedAt : snapshot.lastSeenAt;
		QuestStep step = track.step();
		if (step != null) {
			GoalEvaluator.seed(track.tracker, step.goal(), snapshot.goalProgress);
		}
		if (snapshot.sidequest != null && snapshot.sidequest.wordId != null) {
			catalog.byId(snapshot.sidequest.wordId).ifPresent(word ->
					track.sideQuest = SideQuest.restore(word, snapshot.sidequest.stage,
							snapshot.sidequest.attempts, catalog));
		}
		return track;
	}

	void persist(TrackRegistry registry, long now) {
		ProgressData.Active focusedSnapshot = null;
		Map<String, ProgressData.Active> background = new LinkedHashMap<>();
		for (Track track : registry.all()) {
			ProgressData.Active snapshot = snapshot(track, now);
			if (track.trail.id().equals(registry.focusedId())) {
				focusedSnapshot = snapshot;
			} else {
				background.put(track.trail.id(), snapshot);
			}
		}
		data.quests.active = focusedSnapshot;
		data.quests.saved = background;
		data.quests.tracked = new ArrayList<>(registry.trackedIds());
		learn.markDirty();
	}

	/** Drops a trail for good (finished or empty): its live state and its saved copy. */
	void forget(TrackRegistry registry, String trailId) {
		registry.remove(trailId);
		if (data.quests.saved != null) {
			data.quests.saved.remove(trailId);
		}
	}

	static ProgressData.Active snapshot(Track track, long now) {
		ProgressData.Active snapshot = new ProgressData.Active();
		snapshot.trailId = track.trail.id();
		QuestStep step = track.step();
		snapshot.stepId = step == null ? null : step.id();
		snapshot.goalProgress = track.tracker.count;
		snapshot.hintUsed = track.hintUsed;
		snapshot.startedAt = track.startedAt;
		snapshot.lastSeenAt = now;
		if (track.sideQuest != null) {
			snapshot.sidequest = new ProgressData.Side();
			snapshot.sidequest.wordId = track.sideQuest.word().id;
			snapshot.sidequest.stage = track.sideQuest.stage();
			snapshot.sidequest.attempts = track.sideQuest.attempts();
		}
		return snapshot;
	}
}
