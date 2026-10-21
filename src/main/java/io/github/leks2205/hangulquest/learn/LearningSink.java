package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Word;

import java.util.List;

/** The learning calls the quest engine makes; {@link LearningService} is the real one. */
public interface LearningSink {
	ProgressData data();

	void markDirty();

	/** Seeing a word during a quest step: an encounter, never a review. */
	void expose(Word word, long now, ExposureLedger.Context context);

	/** An encounter with no context to record. */
	default void expose(Word word, long now) {
		expose(word, now, ExposureLedger.Context.NONE);
	}

	/**
	 * A quest step that taught this word was completed. When the word was due and the step was not
	 * assisted, this is a recall: Good, or Hard after a hint ({@link Grader}). Otherwise it is only an
	 * encounter. {@code step} is the step id, for the review log.
	 */
	void questStep(Word word, long now, boolean assisted, boolean hinted, ExposureLedger.Context context, String step);

	/**
	 * A sidequest answer among {@code optionCount} options. Counts, is logged, and never moves the
	 * schedule: a guess is not proof of recall.
	 */
	void practice(String wordId, int optionCount, boolean correct, long now);

	/** Same, for callers that do not know the option count (three, like the sidequest). */
	default void practice(String wordId, boolean correct, long now) {
		practice(wordId, 3, correct, now);
	}

	boolean isKnown(String wordId);

	int discovered();

	void noteQuestStepCompleted();

	void noteSidequestCompleted();

	List<Achievements.Achievement> checkAchievements();
}
