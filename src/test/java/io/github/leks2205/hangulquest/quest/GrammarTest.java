package io.github.leks2205.hangulquest.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every verb must produce a well-formed ending at all six levels of the grammar ladder. */
class GrammarTest {
	@Test
	void allVerbsFormEveryLevel() {
		for (Verb verb : Verb.values()) {
			for (int level = 1; level <= 6; level++) {
				String phrase = SentenceBuilder.verbPhrase(verb, level, false);
				String where = verb + " level " + level + ": " + phrase;
				assertFalse(phrase.contains("ㄹ "), "standalone ㄹ in " + where);
				assertFalse(phrase.contains("을을") || phrase.contains("를를"), "doubled particle " + where);
				assertTrue(phrase.startsWith(verb.prefix()), where);
				switch (level) {
					case 1 -> assertEquals(verb.prefix() + verb.polite(), phrase, where);
					case 2, 3 -> assertEquals(verb.prefix() + verb.honorific(), phrase, where);
					default -> assertEquals(verb.prefix() + verb.eoyo() + "야 해요", phrase, where);
				}
				assertTrue(level == 1 ? phrase.endsWith("요") : phrase.endsWith("세요") || phrase.endsWith("야 해요"), where);
				String forced = SentenceBuilder.verbPhrase(verb, level, true);
				if (level == 6) {
					assertTrue(forced.endsWith("수밖에 없어요"), where);
					if (hasFinalRieul(verb.stem())) {
						assertEquals(verb.prefix() + verb.stem() + " 수밖에 없어요", forced, where);
					}
				} else {
					assertEquals(phrase, forced, "only level 6 has a forced ending: " + where);
				}
			}
		}
	}

	@Test
	void irregularAndNewVerbsHaveTheRightStems() {
		// 아/어 forms
		assertEquals("부숴요", Verb.BREAK.polite());
		assertEquals("심어요", Verb.PLANT.polite());
		assertEquals("깔아요", Verb.SPREAD.polite());
		assertEquals("걸어요", Verb.HANG.polite());
		assertEquals("꺾어요", Verb.SNAP.polite());
		assertEquals("뽑아요", Verb.PULL.polite());
		assertEquals("주워요", Verb.PICK_UP.polite());
		assertEquals("파요", Verb.DIG.polite());
		assertEquals("깨요", Verb.SHATTER.polite());
		assertEquals("눌러요", Verb.PRESS.polite());
		assertEquals("먹이를 줘요", Verb.FEED.prefix() + Verb.FEED.polite());
		// -(으)ㄹ forms: ㄹ-final stems keep their ㄹ, ㅂ-irregular 줍다 becomes 주울
		assertEquals("부술", Verb.BREAK.future());
		assertEquals("심을", Verb.PLANT.future());
		assertEquals("깔", Verb.SPREAD.future());
		assertEquals("걸", Verb.HANG.future());
		assertEquals("꺾을", Verb.SNAP.future());
		assertEquals("뽑을", Verb.PULL.future());
		assertEquals("주울", Verb.PICK_UP.future());
		assertEquals("팔", Verb.DIG.future());
		assertEquals("누를", Verb.PRESS.future());
		assertEquals("캘", Verb.MINE.future());
		assertEquals("먹을", Verb.EAT.future());
		assertEquals("줄", Verb.FEED.future());
		assertEquals("올라갈", Verb.CLIMB.future());
		assertEquals("주울 수밖에 없어요", SentenceBuilder.verbPhrase(Verb.PICK_UP, 6, true));
		assertEquals("먹이를 줄 수밖에 없어요", SentenceBuilder.verbPhrase(Verb.FEED, 6, true));
		assertEquals("말을 걸 수밖에 없어요", SentenceBuilder.verbPhrase(Verb.ADDRESS, 6, true));
	}

	@Test
	void everyVerbStemIsConsistentWithItsDictionaryForm() {
		for (Verb verb : Verb.values()) {
			String dictionary = verb.dictionary();
			assertTrue(dictionary.endsWith("다"), verb + " is not a dictionary form: " + dictionary);
			assertEquals(dictionary.substring(0, dictionary.length() - 1), verb.stem(), verb.toString());
		}
	}

	private static boolean hasFinalRieul(String stem) {
		char last = stem.charAt(stem.length() - 1);
		return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8;
	}
}
