package io.github.leks2205.hangulquest.quest;

import java.util.List;

/** Runtime state of one started trail. */
final class Track {
	Questline trail;
	int stepIndex;
	SideQuest sideQuest;
	boolean hintUsed;
	final GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
	long startedAt;
	long lastSeenAt;

	Track(Questline trail, long now) {
		this.trail = trail;
		this.startedAt = now;
		this.lastSeenAt = now;
	}

	QuestStep step() {
		List<QuestStep> steps = trail.allSteps();
		if (stepIndex < 0 || stepIndex >= steps.size()) {
			return null;
		}
		return steps.get(stepIndex);
	}

	int stepCount() {
		return trail.allSteps().size();
	}

	/** True once the player has done anything on this trail that would be lost by dropping it. */
	boolean hasProgress() {
		return stepIndex > 0 || tracker.count > 0 || tracker.dwellMs > 0 || sideQuest != null || hintUsed;
	}
}
