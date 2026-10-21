package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.Tr;

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
			feedback.banner(achievement.ko(), achievement.de(), true);
			feedback.sound(true);
		}
	}

	void complete(Track track, Observation obs, QuestEngine.Feedback feedback) {
		QuestStep step = track.step();
		if (step == null) {
			return;
		}
		boolean focused = track.trail.id().equals(registry.focusedId());
		for (String conceptId : step.teach()) {
			// Doing the step is an encounter, not recall: it never moves the review schedule.
			catalog.byConceptId(conceptId).ifPresent(word -> learn.expose(word, obs.now));
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
			feedback.banner(track.trail.titleKo(), Tr.t("Ziel erreicht · ") + track.trail.title(), true);
		}
		feedback.sound(true);
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
			feedback.banner(track.trail.titleKo(), Tr.t("Questline geschafft · ") + track.trail.title(), true);
			feedback.sound(true);
		}
	}
}
