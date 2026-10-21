package io.github.leks2205.hangulquest.ui.quiz.cards;

import java.util.List;

/**
 * What the quiz screen draws for a card right now: plain data, rebuilt on demand.
 *
 * @param titleKo   heading, already Korean
 * @param icon      item id for the picture, or null for the "?" tile (the picture would give the answer away)
 * @param main      the big line next to the picture
 * @param mainKorean whether {@code main} is Korean (needs the Hangul font)
 * @param sub       a smaller second line, or empty
 * @param message   instruction or retry prompt, or empty
 * @param layout    how {@code items} are laid out
 * @param items     choices, grade buttons or tiles, in key order
 * @param slots     tile layout only: how many syllables the answer has
 * @param assembled tile layout only: the syllables placed so far, in order
 * @param button    label of the main button (Enter), or empty
 * @param keys      footer text with the keys that work now
 * @param feedback  the correct form once the card is done, else null
 */
public record CardView(String titleKo, String icon, String main, boolean mainKorean, String sub, String message,
					   Layout layout, List<Item> items, int slots, List<String> assembled, String button, String keys,
					   FeedbackCard feedback) {
	public enum Layout { ROWS, BUTTONS, TILES }

	public enum State { NORMAL, DISABLED, CORRECT, WRONG, USED }

	/** One choice; {@code korean} says whether it needs the Hangul font. */
	public record Item(String text, boolean korean, State state) {
		public Item(String text, boolean korean) {
			this(text, korean, State.NORMAL);
		}
	}

	public CardView {
		items = List.copyOf(items);
		assembled = List.copyOf(assembled);
	}
}
