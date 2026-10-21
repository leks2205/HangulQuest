package io.github.leks2205.hangulquest.ui.quiz;

import java.util.List;

/**
 * What the quiz screen shows right now: a review question, a sidequest question or the choice of the
 * next quest. Plain data, rebuilt by the HUD controller (which owns the real state) whenever the screen asks.
 *
 * @param titleKo       the heading, already Korean
 * @param prompt        the question, or empty when the options are the whole content
 * @param promptKorean  whether the prompt is Korean (and needs the Hangul font)
 * @param promptIcon    item id for the prompt icon, or null for the "?" tile
 * @param options       the answers, in key order (1, 2, 3 ...)
 * @param optionsKorean whether the option text is Korean
 */
public record QuizView(Kind kind, String titleKo, String prompt, boolean promptKorean, String promptIcon,
					   List<Option> options, boolean optionsKorean) {
	public enum Kind { REVIEW, SIDEQUEST, NEXT_QUEST }

	/** One answer; {@code note} is an optional smaller second column (the translation of a quest title). */
	public record Option(String text, String note) {
		public Option(String text) {
			this(text, "");
		}
	}

	public QuizView {
		options = List.copyOf(options);
	}
}
