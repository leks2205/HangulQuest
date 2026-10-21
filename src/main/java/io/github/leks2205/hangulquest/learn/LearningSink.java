package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Word;

import java.util.List;

/** The learning calls the quest engine makes; {@link LearningService} is the real one. */
public interface LearningSink {
	ProgressData data();

	void markDirty();

	/** Seeing a word during a quest step: an encounter, never a review. */
	void expose(Word word, long now);

	/** A sidequest answer, graded. */
	void practice(String wordId, boolean correct, long now);

	boolean isKnown(String wordId);

	int discovered();

	void noteQuestStepCompleted();

	void noteSidequestCompleted();

	List<Achievements.Achievement> checkAchievements();
}
