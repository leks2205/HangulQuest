package io.github.leks2205.hangulquest.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Truncation keeps the style of what it keeps, so Korean stays in the Hangul font. */
class TextFitTest {
	private static final String KO = "hangul-font";
	private static final String EN = "default-font";
	private static final int ELLIPSIS = 6;

	/** Hangul in the bundled font is wider (8) than Latin in the default font (6). */
	private static final TextFit.Measure<String> MEASURE =
			(text, style) -> text.length() * (KO.equals(style) ? 8 : 6);

	private static List<TextFit.Piece<String>> pieces(Object... textAndStyle) {
		java.util.ArrayList<TextFit.Piece<String>> list = new java.util.ArrayList<>();
		for (int i = 0; i < textAndStyle.length; i += 2) {
			list.add(new TextFit.Piece<>((String) textAndStyle[i], (String) textAndStyle[i + 1]));
		}
		return list;
	}

	private static String join(TextFit.Fitted<String> fitted) {
		StringBuilder text = new StringBuilder();
		fitted.pieces().forEach(piece -> text.append(piece.text()));
		return text.toString();
	}

	@Test
	void textThatFitsIsLeftAlone() {
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("abcde", EN), 30, ELLIPSIS, MEASURE);

		assertFalse(fitted.cut());
		assertEquals("abcde", join(fitted));
	}

	@Test
	void textThatDoesNotFitLeavesRoomForTheEllipsis() {
		// 10 letters are 60 wide; with the 6-wide ellipsis 34 are left, which is 5 letters.
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("abcdefghij", EN), 40, ELLIPSIS, MEASURE);

		assertTrue(fitted.cut());
		assertEquals("abcde", join(fitted));
	}

	@Test
	void theKeptKoreanKeepsItsFont() {
		// The old code rebuilt the cut text as a plain literal, which dropped the style.
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("한글한글한글", KO), 30, ELLIPSIS, MEASURE);

		assertTrue(fitted.cut());
		assertEquals("한글한", join(fitted));
		assertEquals(KO, fitted.pieces().get(0).style());
	}

	@Test
	void everyPieceKeepsItsOwnStyleWhenTheCutFallsInALaterPiece() {
		// "abc" (18) fits whole; of the Korean piece only 16 of the remaining 34-18 are left: two syllables.
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("abc", EN, "한글한글", KO), 40, ELLIPSIS, MEASURE);

		assertTrue(fitted.cut());
		assertEquals(2, fitted.pieces().size());
		assertEquals("abc", fitted.pieces().get(0).text());
		assertEquals(EN, fitted.pieces().get(0).style());
		assertEquals("한글", fitted.pieces().get(1).text());
		assertEquals(KO, fitted.pieces().get(1).style());
	}

	@Test
	void nothingAfterTheCutIsKept() {
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("한글한글한글", KO, "tail", EN), 30, ELLIPSIS, MEASURE);

		assertEquals("한글한", join(fitted));
	}

	@Test
	void aSurrogatePairIsNeverSplit() {
		// Every UTF-16 unit counts 1; 3 units of room would end in the middle of the second emoji.
		TextFit.Measure<String> perUnit = (text, style) -> text.length();
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("😀😀😀", EN), 3, 0, perUnit);

		assertEquals("😀", join(fitted));
	}

	@Test
	void whenEvenTheEllipsisDoesNotFitNothingIsKept() {
		TextFit.Fitted<String> fitted = TextFit.fit(pieces("abcdef", EN), 4, ELLIPSIS, MEASURE);

		assertTrue(fitted.cut());
		assertEquals("", join(fitted));
	}

	@Test
	void emptyTextFits() {
		assertFalse(TextFit.fit(pieces("", EN), 0, ELLIPSIS, MEASURE).cut());
	}

	@Test
	void theResultAlwaysFitsWithItsEllipsis() {
		for (int max = ELLIPSIS; max < 120; max++) {
			TextFit.Fitted<String> fitted = TextFit.fit(
					pieces("Stone ", EN, "돌을 세 개 캐요. ", KO, "0/3", EN), max, ELLIPSIS, MEASURE);
			int width = 0;
			for (TextFit.Piece<String> piece : fitted.pieces()) {
				width += MEASURE.width(piece.text(), piece.style());
			}
			assertTrue(width + (fitted.cut() ? ELLIPSIS : 0) <= max, "max " + max + " -> " + width);
		}
	}
}
