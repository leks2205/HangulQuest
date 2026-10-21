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

	// ---- grammar: what the player has met of the ladder, derived from the review log.
	// The defaults describe a player who has met everything, for callers that do not track grammar.

	/** The player's grammar level, 0 for a new player; generated text may use this level plus one. */
	default int grammarLevel() {
		return 6;
	}

	/** Whether the player has met this construction (a step used it, or its rule card was shown). */
	default boolean grammarMet(String id) {
		return true;
	}

	/** Whether the rule card of this construction has been shown already. */
	default boolean grammarCardSeen(String id) {
		return true;
	}

	/**
	 * A quest step was completed. {@code ids} are the constructions it carried (goal type ids are
	 * resolved to grammar.json ids on the way in); it counts as one step written at {@code ladderLevel}.
	 */
	default void grammarStep(List<String> ids, int ladderLevel, long now, String where) {
	}

	/** The rule card of a construction was shown (once per construction); it counts as met from here. */
	default void grammarCardShown(String id, long now) {
	}

	/**
	 * A form-dependent outcome for a construction (a cloze answer): logged with kind GRAMMAR and fed to
	 * the knowledge tracing. It never moves a word's schedule.
	 */
	default void grammarOutcome(String id, Grader.Graded graded, long latencyMs, String source, long now) {
	}

	boolean isKnown(String wordId);

	int discovered();

	void noteQuestStepCompleted();

	void noteSidequestCompleted();

	List<Achievements.Achievement> checkAchievements();
}
