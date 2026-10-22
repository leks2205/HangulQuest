package io.github.leks2205.hangulquest.util.hangul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Key codes here are the scancodes Minecraft 26.3 reports, which name the positions of a US keyboard. A German QWERTZ keyboard
 * reports the same codes for the same positions, so these cases are also the QWERTZ cases.
 */
class HangulInputTest {
	private static final int SHIFT = HangulInput.MOD_SHIFT;

	/** Key position (as a US letter) to the jamo, copied from the trainer's layout table. */
	private static final String[][] LAYOUT = {
			{"Q", "ㅂ", "ㅃ"}, {"W", "ㅈ", "ㅉ"}, {"E", "ㄷ", "ㄸ"}, {"R", "ㄱ", "ㄲ"}, {"T", "ㅅ", "ㅆ"},
			{"Y", "ㅛ", "ㅛ"}, {"U", "ㅕ", "ㅕ"}, {"I", "ㅑ", "ㅑ"}, {"O", "ㅐ", "ㅒ"}, {"P", "ㅔ", "ㅖ"},
			{"A", "ㅁ", "ㅁ"}, {"S", "ㄴ", "ㄴ"}, {"D", "ㅇ", "ㅇ"}, {"F", "ㄹ", "ㄹ"}, {"G", "ㅎ", "ㅎ"},
			{"H", "ㅗ", "ㅗ"}, {"J", "ㅓ", "ㅓ"}, {"K", "ㅏ", "ㅏ"}, {"L", "ㅣ", "ㅣ"},
			{"Z", "ㅋ", "ㅋ"}, {"X", "ㅌ", "ㅌ"}, {"C", "ㅊ", "ㅊ"}, {"V", "ㅍ", "ㅍ"}, {"B", "ㅠ", "ㅠ"},
			{"N", "ㅜ", "ㅜ"}, {"M", "ㅡ", "ㅡ"}};

	private static HangulInput press(String keys) {
		HangulInput input = new HangulInput();
		for (char key : keys.toCharArray()) {
			input.key(DubeolsikLayout.key(key), 0);
		}
		return input;
	}

	@Test
	void everyLetterKeyTypesItsJamo() {
		assertEquals(26, LAYOUT.length);
		for (String[] row : LAYOUT) {
			int key = DubeolsikLayout.key(row[0].charAt(0));
			assertEquals(row[1].charAt(0), DubeolsikLayout.jamoFor(key, false), row[0]);
			assertEquals(row[2].charAt(0), DubeolsikLayout.jamoFor(key, true), row[0] + " with Shift");
		}
	}

	@Test
	void shiftChangesOnlyTheSevenKeys() {
		int changed = 0;
		for (String[] row : LAYOUT) {
			if (!row[1].equals(row[2])) {
				changed++;
			}
		}
		assertEquals(7, changed);
	}

	@Test
	void qwertzNeedsNoSwap() {
		// The key printed Y on a QWERTZ keyboard sits where a US keyboard has Z: the scancode is KEY_Z for
		// it, and 두벌식 puts ㅋ there. The key printed Z sits at the US Y position and types ㅛ.
		assertEquals('ㅋ', DubeolsikLayout.jamoFor(DubeolsikLayout.KEY_Z, false));
		assertEquals('ㅛ', DubeolsikLayout.jamoFor(DubeolsikLayout.KEY_Y, false));
		// The keys QWERTZ moves elsewhere (ö ä ü, ß) are punctuation in 두벌식 and type no jamo.
		for (int key : new int[] {DubeolsikLayout.KEY_SEMICOLON, DubeolsikLayout.KEY_COMMA,
				DubeolsikLayout.KEY_PERIOD, DubeolsikLayout.KEY_SLASH, 39, 91, 93, 45, 61, 32, 48}) {
			assertEquals(0, DubeolsikLayout.jamoFor(key, false), "key " + key);
		}
	}

	@Test
	void everyJamoHasAKeyAndTheKeyTypesItBack() {
		String all = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡㅃㅉㄸㄲㅆㅒㅖ";
		assertEquals(33, all.length());
		for (char jamo : all.toCharArray()) {
			int key = DubeolsikLayout.keyFor(jamo);
			assertTrue(key > 0, "key for " + jamo);
			assertEquals(jamo, DubeolsikLayout.jamoFor(key, DubeolsikLayout.needsShift(jamo)), "back to " + jamo);
		}
		assertEquals(-1, DubeolsikLayout.keyFor('ㅘ'), "compounds have no key");
		assertEquals(-1, DubeolsikLayout.keyFor('a'));
	}

	@Test
	void theOverlayRowsCoverEveryLetterKeyOnce() {
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		int letters = 0;
		for (int[] row : DubeolsikLayout.ROWS) {
			for (int key : row) {
				assertTrue(seen.add(key), "key twice: " + key);
				letters += DubeolsikLayout.jamoFor(key, false) != 0 ? 1 : 0;
				assertFalse(DubeolsikLayout.usLabel(key).isEmpty());
			}
		}
		assertEquals(26, letters);
	}

	@Test
	void typingAWordByKeyCodes() {
		assertEquals("한글", press("GKSRMF").text());
		assertEquals("안녕", press("DKSSUD").text());
	}

	@Test
	void shiftTypesTheDoubledConsonants() {
		HangulInput input = new HangulInput();
		input.key(DubeolsikLayout.key('Q'), SHIFT);
		input.key(DubeolsikLayout.key('K'), 0);
		input.key(DubeolsikLayout.key('Q'), SHIFT);
		input.key(DubeolsikLayout.key('K'), 0);
		assertEquals("빠빠", input.text());
		input.key(DubeolsikLayout.key('O'), SHIFT);
		assertEquals("빠빠ㅒ", input.text());
	}

	@Test
	void backspaceRemovesOneJamo() {
		HangulInput input = press("RKQT");
		assertEquals("값", input.text());
		input.key(HangulInput.KEY_BACKSPACE, 0);
		assertEquals("갑", input.text());
	}

	@Test
	void controlBackspaceClears() {
		HangulInput input = press("RKQT");
		input.key(HangulInput.KEY_BACKSPACE, HangulInput.MOD_CONTROL);
		assertEquals("", input.text());
	}

	@Test
	void shortcutsAreNotTyped() {
		HangulInput input = new HangulInput();
		assertFalse(input.key(DubeolsikLayout.key('A'), HangulInput.MOD_CONTROL), "Ctrl+A");
		assertFalse(input.key(DubeolsikLayout.key('C'), HangulInput.MOD_SUPER));
		assertEquals("", input.text());
	}

	@Test
	void theToggleKeySwitchesModes() {
		HangulInput input = new HangulInput();
		assertTrue(input.korean());
		assertTrue(input.key(HangulInput.KEY_RIGHT_ALT, 0));
		assertFalse(input.korean());
		assertFalse(input.key(DubeolsikLayout.key('G'), 0), "in Latin mode the letter comes from the character event");
		assertTrue(input.character('g'));
		assertEquals("g", input.text());
		input.key(HangulInput.KEY_RIGHT_ALT, 0);
		assertTrue(input.korean());
	}

	@Test
	void theToggleKeyCanBeChanged() {
		HangulInput input = new HangulInput();
		input.setToggleKey(342);
		assertFalse(input.key(HangulInput.KEY_RIGHT_ALT, 0), "no longer the toggle");
		assertTrue(input.korean());
		assertTrue(input.key(342, 0));
		assertFalse(input.korean());
	}

	@Test
	void koreanModeTakesNoLettersFromTheCharacterEvent() {
		// The key press already typed ㅎ; the 'g' that follows it must not be added again.
		HangulInput input = new HangulInput();
		input.key(DubeolsikLayout.key('G'), 0);
		assertFalse(input.character('g'));
		assertFalse(input.character('ö'), "a QWERTZ letter key is punctuation position, but still a letter");
		assertEquals("ㅎ", input.text());
	}

	@Test
	void spaceDigitsAndPunctuationComeFromTheCharacterEvent() {
		HangulInput input = press("GKS");
		assertTrue(input.character(' '));
		input.character('1');
		input.character('?');
		assertEquals("한 1?", input.text());
	}

	@Test
	void textFromAnOsInputMethodIsTakenAsItIs() {
		HangulInput input = new HangulInput();
		assertTrue(input.character('한'));
		assertTrue(input.character('ㄱ'));
		assertEquals("한ㄱ", input.text());
		input.key(DubeolsikLayout.key('M'), 0);
		assertEquals("한그", input.text(), "an IME syllable and the automaton continue each other");
		input.key(HangulInput.KEY_BACKSPACE, 0);
		input.key(HangulInput.KEY_BACKSPACE, 0);
		input.key(HangulInput.KEY_BACKSPACE, 0);
		assertEquals("하", input.text(), "backspace still goes one jamo at a time");
	}

	@Test
	void anImeSyllableIsAcceptedInLatinModeToo() {
		HangulInput input = new HangulInput();
		input.setKorean(false);
		assertTrue(input.character('값'));
		assertEquals("값", input.text());
	}

	@Test
	void controlCharactersAreIgnored() {
		HangulInput input = new HangulInput();
		assertFalse(input.character('\n'));
		assertFalse(input.character(0x7F));
		assertEquals("", input.text());
	}

	@Test
	void theLengthLimitHolds() {
		HangulInput input = new HangulInput();
		input.setMaxLength(3);
		for (char key : "GKSGKSGKS".toCharArray()) {
			input.key(DubeolsikLayout.key(key), 0);
		}
		assertEquals("한한한".length(), input.text().length());
		assertEquals("한한한", input.text());
		input.key(DubeolsikLayout.key('Q'), 0);
		assertEquals("한한한", input.text(), "a fourth syllable does not fit");
		input.key(DubeolsikLayout.key('G'), 0);
		assertEquals("한한핞", input.text(), "but a key that only extends the last one does");
		input.key(HangulInput.KEY_BACKSPACE, 0);
		input.key(HangulInput.KEY_BACKSPACE, 0);
		assertEquals("한한하", input.text());
		input.key(DubeolsikLayout.key('M'), 0);
		assertEquals("한한하", input.text(), "still full: the lone vowel would be a fourth character");
		input.key(DubeolsikLayout.key('S'), 0);
		assertEquals("한한한", input.text());
	}
}
