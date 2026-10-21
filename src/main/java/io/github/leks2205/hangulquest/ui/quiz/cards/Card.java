package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.ReviewMode;

import java.util.Optional;

/**
 * One kind of review question that the quiz screen hosts. A card holds no game types: it reacts to
 * the player's input, says what to draw ({@link #view()}) and, once, what the first real attempt was
 * ({@link Answer}). It never records anything; {@code QuizSession} is the one place that does.
 *
 * <p>After a wrong first attempt the card shows a prompt and allows one more try. That try is practice and
 * is not recorded: the schedule sees the first attempt only.
 */
public interface Card {
	enum Phase {
		/** Waiting for the first attempt. */
		ASKING,
		/** Self-grade card only: the Korean is shown and the player grades. */
		REVEALED,
		/** The first attempt was wrong: prompt shown, one more try. */
		RETRY,
		/** The correct form, icon, gloss and example are on screen. */
		FEEDBACK,
		/** The screen can close. */
		DONE
	}

	/**
	 * What the player did, as the grader needs it. {@code options} counts the multiple-choice options,
	 * {@code grade} is the self grade (1 to 4), {@code jamoOff} how many jamo a tile answer is off.
	 */
	record Answer(ReviewMode mode, boolean correct, int options, int grade, int jamoOff) {
		public static Answer multipleChoice(int options, boolean correct) {
			return new Answer(ReviewMode.multipleChoice(options), correct, options, 0, 0);
		}

		public static Answer selfGraded(int grade) {
			int clamped = Math.max(1, Math.min(4, grade));
			return new Answer(ReviewMode.SELF, clamped >= 2, 0, clamped, 0);
		}

		public static Answer tiles(int jamoOff) {
			return new Answer(ReviewMode.TILE, jamoOff <= 1, 0, 0, Math.max(0, jamoOff));
		}
	}

	CardInfo info();

	/** The mode the first attempt will be logged with. */
	ReviewMode mode();

	Phase phase();

	/** True once the first attempt has been handed out. */
	boolean answered();

	default boolean finished() {
		return phase() == Phase.DONE;
	}

	CardView view();

	/** A number key or a click on option {@code index} (a choice, a grade, a tile). */
	Optional<Answer> choose(int index);

	/** Enter, Space or a click on the main button: reveal, check or continue. */
	Optional<Answer> confirm();

	/** Backspace: take the last tile back. Other cards ignore it. */
	default void undo() {
	}
}
