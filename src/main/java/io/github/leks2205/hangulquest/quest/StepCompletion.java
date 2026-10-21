package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.I18n;

import java.util.ArrayList;
import java.util.List;

/** What happens when a goal is met: teach the words, move to the next clue, or finish the trail. */
final class StepCompletion {
	/** Starts whatever comes after a finished focused trail. */
	interface Advance {
		void after(String completedId, Observation obs, QuestEngine.Feedback feedback);
	}

	private final TrackRegistry registry;
	private final QuestPersistence persistence;
	private final QuestTextService text;
	private final LearningSink learn;
	private final Catalog catalog;
	private final Advance advance;

	StepCompletion(TrackRegistry registry, QuestPersistence persistence, QuestTextService text,
				   LearningSink learn, Catalog catalog, Advance advance) {
		this.registry = registry;
		this.persistence = persistence;
		this.text = text;
		this.learn = learn;
		this.catalog = catalog;
		this.advance = advance;
	}

	static void unlock(QuestEngine.Feedback feedback, List<Achievements.Achievement> unlocked) {
		for (Achievements.Achievement achievement : unlocked) {
			feedback.banner(achievement.ko(), achievement.label(), true);
			feedback.sound(true);
		}
	}

	void complete(Track track, Observation obs, QuestEngine.Feedback feedback) {
		QuestStep step = track.step();
		if (step == null) {
			return;
		}
		boolean focused = track.trail.id().equals(registry.focusedId());
		// Doing the step is an encounter; LearningSink grades it as a recall only for a due word and an
		// unassisted step (Good, Hard after a hint). A new word is only exposed.
		boolean assisted = track.tracker.assisted;
		boolean hinted = track.hintUsed;
		for (int i = 0; i < step.teach().size(); i++) {
			ExposureLedger.Context context = context(track, step, i == 0 ? "primary" : "secondary");
			catalog.byConceptId(step.teach().get(i))
					.ifPresent(word -> learn.questStep(word, obs.now, assisted, hinted, context, step.id()));
		}
		learn.noteQuestStepCompleted();
		if (track.stepIndex >= track.stepCount() - 1) {
			finish(track, obs, feedback);
			return;
		}
		track.stepIndex++;
		track.tracker.clear();
		track.hintUsed = false;
		track.lastSeenAt = obs.now;
		persistence.persist(registry, obs.now);
		StepCompletion.unlock(feedback, learn.checkAchievements());
		QuestStep following = track.step();
		if (focused) {
			feedback.stepCompleted("좋아요!", text.clueKo(following, track.trail.koreanLevel()),
					text.clueTranslation(following));
		} else {
			feedback.banner(track.trail.titleKo(), I18n.t("hangulquest.ui.quest.goal_reached", track.trail.title()), true);
		}
		feedback.sound(true);
	}

	/** Where a word was met: goal type, verb, role in the step and trail. */
	static ExposureLedger.Context context(Track track, QuestStep step, String role) {
		String goalType = "";
		String verb = "";
		if (step.goal() != null) {
			GoalType<Goal> type = GoalTypes.of(step.goal());
			goalType = type.id();
			Verb chosen = type.verb(step.goal(), VerbTable.shared());
			verb = chosen == null ? "" : chosen.name();
		}
		return new ExposureLedger.Context(goalType, verb, role, track.trail.id());
	}

	private void finish(Track track, Observation obs, QuestEngine.Feedback feedback) {
		String trailId = track.trail.id();
		boolean focused = trailId.equals(registry.focusedId());
		ProgressData.QuestData quests = learn.data().quests;
		quests.completed.put(trailId, obs.now);
		if (quests.badges == null) {
			quests.badges = new ArrayList<>();
		}
		if (!quests.badges.contains(track.trail.badge())) {
			quests.badges.add(track.trail.badge());
		}
		if (quests.history == null) {
			quests.history = new ArrayList<>();
		}
		quests.history.add(new ProgressData.History(trailId, obs.now));
		persistence.forget(registry, trailId);
		persistence.persist(registry, obs.now);
		StepCompletion.unlock(feedback, learn.checkAchievements());
		if (focused) {
			feedback.trailCompleted(track.trail);
			feedback.sound(true);
			advance.after(trailId, obs, feedback);
		} else {
			feedback.banner(track.trail.titleKo(), I18n.t("hangulquest.ui.quest.line_complete", track.trail.title()), true);
			feedback.sound(true);
		}
	}
}
