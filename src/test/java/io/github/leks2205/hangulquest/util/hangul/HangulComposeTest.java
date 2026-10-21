package io.github.leks2205.hangulquest.util.hangul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangulComposeTest {
	@Test
	void tablesHaveTheRightSizes() {
		assertEquals(19, HangulCompose.CHO.length());
		assertEquals(21, HangulCompose.JUNG.length());
		assertEquals(28, HangulCompose.JONG.length(), "27 finals and the blank");
	}

	@Test
	void everySyllableSurvivesTakingItApartAndPuttingItBack() {
		int count = 0;
		for (char c = HangulCompose.FIRST_SYLLABLE; c <= HangulCompose.LAST_SYLLABLE; c++) {
			HangulCompose.Syllable parts = HangulCompose.decompose(c);
			assertEquals(c, HangulCompose.compose(parts.cho(), parts.jung(), parts.jong()), "parts of " + c);
			int cho = HangulCompose.choIndex(parts.cho());
			int jung = HangulCompose.jungIndex(parts.jung());
			int jong = HangulCompose.jongIndex(parts.jong());
			assertEquals(c, HangulCompose.compose(cho, jung, jong), "indices of " + c);
			count++;
		}
		assertEquals(HangulCompose.SYLLABLES, count);
	}

	@Test
	void everySyllableSurvivesTheKeyRoundTrip() {
		for (char c = HangulCompose.FIRST_SYLLABLE; c <= HangulCompose.LAST_SYLLABLE; c++) {
			String keys = HangulCompose.toJamo(String.valueOf(c));
			assertTrue(keys.length() >= 2 && keys.length() <= 5, c + " is " + keys);
			assertEquals(String.valueOf(c), Dubeolsik.compose(keys), "keys of " + c);
		}
	}

	@Test
	void knownSyllables() {
		assertEquals('가', HangulCompose.compose('ㄱ', 'ㅏ', (char) 0));
		assertEquals('한', HangulCompose.compose('ㅎ', 'ㅏ', 'ㄴ'));
		assertEquals('힣', HangulCompose.compose('ㅎ', 'ㅣ', 'ㅎ'));
		assertEquals('값', HangulCompose.compose('ㄱ', 'ㅏ', 'ㅄ'));
		assertEquals(new HangulCompose.Syllable('ㅇ', 'ㅚ', 'ㄴ'), HangulCompose.decompose('왼'));
	}

	@Test
	void notASyllable() {
		assertNull(HangulCompose.decompose('a'));
		assertNull(HangulCompose.decompose('ㄱ'));
		assertThrows(IllegalArgumentException.class, () -> HangulCompose.compose('a', 'ㅏ', (char) 0));
		assertThrows(IllegalArgumentException.class, () -> HangulCompose.compose('ㄱ', 'ㅏ', 'ㄸ'));
	}

	@Test
	void compoundVowelsJoinAndSplit() {
		String[][] table = {{"ㅘ", "ㅗㅏ"}, {"ㅙ", "ㅗㅐ"}, {"ㅚ", "ㅗㅣ"}, {"ㅝ", "ㅜㅓ"}, {"ㅞ", "ㅜㅔ"},
				{"ㅟ", "ㅜㅣ"}, {"ㅢ", "ㅡㅣ"}};
		for (String[] row : table) {
			assertEquals(row[1], HangulCompose.splitVowel(row[0].charAt(0)));
			assertEquals(row[0].charAt(0), HangulCompose.joinVowel(row[1].charAt(0), row[1].charAt(1)));
		}
		assertEquals(0, HangulCompose.joinVowel('ㅗ', 'ㅓ'));
		assertNull(HangulCompose.splitVowel('ㅏ'));
	}

	@Test
	void compoundFinalsJoinAndSplit() {
		String[][] table = {{"ㄳ", "ㄱㅅ"}, {"ㄵ", "ㄴㅈ"}, {"ㄶ", "ㄴㅎ"}, {"ㄺ", "ㄹㄱ"}, {"ㄻ", "ㄹㅁ"},
				{"ㄼ", "ㄹㅂ"}, {"ㄽ", "ㄹㅅ"}, {"ㄾ", "ㄹㅌ"}, {"ㄿ", "ㄹㅍ"}, {"ㅀ", "ㄹㅎ"}, {"ㅄ", "ㅂㅅ"}};
		assertEquals(11, table.length);
		for (String[] row : table) {
			assertEquals(row[1], HangulCompose.splitFinal(row[0].charAt(0)));
			assertEquals(row[0].charAt(0), HangulCompose.joinFinal(row[1].charAt(0), row[1].charAt(1)));
			assertFalse(HangulCompose.canBeFinal(row[0].charAt(0)), "a compound is typed with two keys");
		}
		assertEquals(0, HangulCompose.joinFinal('ㄱ', 'ㄱ'));
	}

	@Test
	void whichConsonantsCanClose() {
		for (char c : "ㄸㅃㅉ".toCharArray()) {
			assertFalse(HangulCompose.canBeFinal(c), c + " never closes a syllable");
		}
		for (char c : "ㄱㄲㄴㄷㄹㅁㅂㅅㅆㅇㅈㅊㅋㅌㅍㅎ".toCharArray()) {
			assertTrue(HangulCompose.canBeFinal(c), c + " closes a syllable");
		}
	}

	@Test
	void toJamoSplitsCompoundsAndKeepsTheRest() {
		assertEquals("ㅇㅗㅐㅇㅛ", HangulCompose.toJamo("왜요"));
		assertEquals("ㄱㅏㅂㅅ", HangulCompose.toJamo("값"));
		assertEquals("ㅇㅡㅣ", HangulCompose.toJamo("의"));
		assertEquals("ㅗㅏ", HangulCompose.toJamo("ㅘ"), "a lone compound vowel splits too");
		assertEquals("ㄱㅅ", HangulCompose.toJamo("ㄳ"));
		assertEquals("ㅎㅏㄴ a1!", HangulCompose.toJamo("하ㄴ a1!"));
		assertEquals("", HangulCompose.toJamo(""));
	}
}
