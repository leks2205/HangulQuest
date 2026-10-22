package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.Grader;
import io.github.leks2205.hangulquest.learn.Grader.Graded;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.quest.spatial.SpatialRules;
import io.github.leks2205.hangulquest.quest.spatial.StepRef;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.I18n;

import java.util.ArrayList;
import java.util.List;

/** What happens when a goal is met: teach the words, move to the next clue, or finish the trail. */
final class StepCompletion {
	/** Starts whatever comes after a finished trail; {@code focused} says whether it was the focused one. */
	interface Advance {
		void after(String completedId, Observation obs, QuestEngine.Feedback feedback, boolean focused);
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
			ExposureLedger.Context context = context(track, step, i == 0 ? (step.reverse() ? "reverse" : "primary") : "secondary");
			catalog.byConceptId(step.teach().get(i))
					.ifPresent(word -> learn.questStep(word, obs.now, assisted, hinted, context, step.id()));
		}
		learn.noteQuestStepCompleted();
		List<String> grammar = text.grammarIds(step, track.trail.koreanLevel());
		if (!grammar.isEmpty()) {
			learn.grammarStep(grammar, text.effectiveLevel(track.trail.koreanLevel()), obs.now,
					track.trail.id() + "/" + step.id());
		}
		rememberPlaced(track, step);
		if (!assisted) {
			Graded graded = Grader.quest(false, hinted);
			for (String id : DecidingForms.of(step, text.effectiveLevel(track.trail.koreanLevel()))) {
				learn.grammarOutcome(id, graded, Grader.UNKNOWN_LATENCY, source(track, step), obs.now);
			}
		}
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

	/** Form slips of one step that the grammar tracking hears about; more than this adds nothing. */
	private static final int MAX_MISSES = 3;
	private static final Graded MISS = new Graded(ReviewMode.QUEST_UNAIDED, Fsrs.AGAIN);

	private static String source(Track track, QuestStep step) {
		return "quest:" + track.trail.id() + "/" + step.id();
	}

	/**
	 * Slips the goal noticed this tick (a right block in the wrong relation, an ordered sequence done out of
	 * order) count against the construction that decides the step. Each slip is reported once, and a step
	 * stops counting after {@value #MAX_MISSES}. Progress made in creative or spectator mode proves nothing.
	 */
	void formMisses(Track track, Observation obs) {
		GoalEvaluator.Tracker tracker = track.tracker;
		if (tracker.formErrors <= 0) {
			return;
		}
		int fresh = Math.min(tracker.formErrors, MAX_MISSES - tracker.formErrorsReported);
		tracker.formErrors = 0;
		QuestStep step = track.step();
		if (fresh <= 0 || step == null || tracker.assisted || obs.assisted()) {
			return;
		}
		tracker.formErrorsReported += fresh;
		for (String id : DecidingForms.of(step, text.effectiveLevel(track.trail.koreanLevel()))) {
			for (int i = 0; i < fresh; i++) {
				learn.grammarOutcome(id, MISS, Grader.UNKNOWN_LATENCY, source(track, step), obs.now);
			}
		}
	}

	/**
	 * A later step may point at the block that finished this one ("아까 놓은 횃불 옆에"): keep where it
	 * stands, with the trail's progress.
	 */
	private static void rememberPlaced(Track track, QuestStep step) {
		int[] at = track.tracker.lastPlaced;
		if (at == null || SpatialRules.placedBlock(step.goal()) == null) {
			return;
		}
		List<QuestStep> steps = track.trail.allSteps();
		for (int i = track.stepIndex + 1; i < steps.size(); i++) {
			if (steps.get(i).goal() instanceof Goal.PlaceRel later && StepRef.isRef(later.anchor())
					&& StepRef.stepId(later.anchor()).equals(step.id())) {
				track.tracker.placed.put(step.id(), at.clone());
				return;
			}
		}
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
		} else {
			feedback.banner(track.trail.titleKo(), I18n.t("hangulquest.ui.quest.line_complete", track.trail.title()), true);
			feedback.sound(true);
		}
		advance.after(trailId, obs, feedback, focused);
	}
}
