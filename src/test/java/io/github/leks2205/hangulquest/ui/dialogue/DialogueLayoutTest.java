package io.github.leks2205.hangulquest.ui.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueLayoutTest {
	/** Ten units a word, plus its length. */
	private static final DialogueLayout.Measure MEASURE = word -> word.length() * 10;

	@Test
	void wordsFollowEachOtherWithASpaceBetween() {
		DialogueLayout.Flow flow = DialogueLayout.flow(List.of("가가", "나", "다다다"), MEASURE, 1000, 4, 12);
		assertEquals(List.of(0, 24, 38), flow.words().stream().map(DialogueLayout.Placed::x).toList());
		assertEquals(12, flow.height());
	}

	@Test
	void aWordThatDoesNotFitStartsTheNextRow() {
		DialogueLayout.Flow flow = DialogueLayout.flow(List.of("가가", "나나", "다다"), MEASURE, 50, 4, 12);
		assertEquals(3, flow.words().size());
		assertEquals(0, flow.words().get(0).y());
		assertEquals(0, flow.words().get(1).y(), "20 + 4 + 20 fits in 50");
		assertEquals(12, flow.words().get(2).y());
		assertEquals(0, flow.words().get(2).x());
		assertEquals(24, flow.height());
	}

	@Test
	void aWordWiderThanTheRowStillGetsItsOwnRow() {
		DialogueLayout.Flow flow = DialogueLayout.flow(List.of("가", "아주아주긴단어"), MEASURE, 40, 4, 12);
		assertEquals(12, flow.words().get(1).y());
		assertEquals(0, flow.words().get(1).x());
	}

	@Test
	void anEmptyLineHasNoHeight() {
		assertEquals(0, DialogueLayout.flow(List.of(), MEASURE, 100, 4, 12).height());
	}

	@Test
	void aPointFindsTheWordUnderIt() {
		DialogueLayout.Flow flow = DialogueLayout.flow(List.of("가가", "나나", "다다"), MEASURE, 50, 4, 12);
		assertEquals(0, flow.hit(5, 5, 12));
		assertEquals(1, flow.hit(30, 3, 12));
		assertEquals(2, flow.hit(10, 15, 12));
		assertEquals(-1, flow.hit(22, 5, 12), "the gap between two words");
		assertEquals(-1, flow.hit(45, 15, 12));
		assertEquals(-1, flow.hit(5, 30, 12));
	}
}
