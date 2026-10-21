package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.Grader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;

import java.util.function.Predicate;
import java.util.function.LongSupplier;

/**
 * Records a conversation in the learning data. Words go through {@code LearningSink} (an encounter or
 * a practice answer, which never move the schedule). The learning data has no grammar items yet, so a
 * grammar point is written to the review log only, as kind GRAMMAR: exposures and answers among two or
 * three options (MC2/MC3), which do not schedule anything.
 */
public final class GameDialogueSink implements DialogueSink {
	private final LearningService learn;
	private final Catalog catalog;
	private final Predicate<String> trails;
	private final LongSupplier clock;
	private final String dialogueId;

	/** @param trails starts a trail by id and says whether it did */
	public GameDialogueSink(LearningService learn, Catalog catalog, Predicate<String> trails, LongSupplier clock,
							String dialogueId) {
		this.learn = learn;
		this.catalog = catalog;
		this.trails = trails;
		this.clock = clock;
		this.dialogueId = dialogueId;
	}

	@Override
	public void exposeWord(String wordId) {
		catalog.byId(wordId).ifPresent(word -> learn.expose(word, clock.getAsLong(),
				new ExposureLedger.Context("DIALOGUE", "", "dialogue", dialogueId)));
	}

	@Override
	public void exposeGrammar(String point) {
		log(point, ReviewMode.EXPOSURE, null);
	}

	@Override
	public void practiceGrammar(String point, int options, boolean correct) {
		// Like the sidequest, never more than three options count as a guess: nothing here may schedule.
		ReviewMode mode = ReviewMode.multipleChoice(Math.min(options, 3));
		log(point, mode, correct ? Fsrs.GOOD : Fsrs.AGAIN);
	}

	@Override
	public void practiceWord(String wordId, boolean correct) {
		learn.practice(wordId, 2, correct, clock.getAsLong());
	}

	@Override
	public boolean startTrail(String trailId) {
		return trails.test(trailId);
	}

	private void log(String point, ReviewMode mode, Integer grade) {
		learn.reviewLog().append(new ReviewLog.Event(clock.getAsLong(), point, ReviewKind.GRAMMAR, mode, grade,
				Grader.UNKNOWN_LATENCY, -1, "dialogue/" + dialogueId));
	}
}
