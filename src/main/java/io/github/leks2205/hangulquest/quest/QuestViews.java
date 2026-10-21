package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/** Builds the rows of the quest log for the HUD and the journal. */
final class QuestViews {
	private QuestViews() {
	}

	/** The checked trails in the order the player added them; the focused one is only marked. */
	static List<QuestEngine.TrackView> of(TrackRegistry registry, QuestTextService text, Catalog catalog,
										  Observation lastObs) {
		List<QuestEngine.TrackView> views = new ArrayList<>();
		for (Track track : registry.all()) {
			if (!registry.isTracked(track.trail.id())) {
				continue;
			}
			QuestStep step = track.step();
			if (step == null) {
				continue;
			}
			GoalType<Goal> type = GoalTypes.of(step.goal());
			String status = lastObs == null ? "" : QuestStatus.text(step, lastObs, track.tracker, catalog);
			views.add(new QuestEngine.TrackView(track.trail.id(), track.trail.titleKo(), track.trail.titleDe(),
					track.trail.titleEn(), track.trail.topicKey(), track.trail.koreanLevel(),
					text.clueKo(step, track.trail.koreanLevel()), text.clueTranslation(step), status,
					track.tracker.count, type.needed(step.goal()), type.countable(),
					track.trail.id().equals(registry.focusedId()), track.stepIndex, track.stepCount()));
		}
		return views;
	}
}
