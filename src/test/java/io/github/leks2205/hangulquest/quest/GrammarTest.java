package io.github.leks2205.hangulquest.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every verb must produce a well-formed ending at all six TOPIK ladder levels. */
class GrammarTest {
	@Test
	void allVerbsFormEveryLevel() {
		for (Verb verb : Verb.values()) {
			for (int level = 1; level <= 6; level++) {
				String phrase = SentenceBuilder.verbPhrase(verb, level, false);
				String where = verb + " level " + level + ": " + phrase;
				assertFalse(phrase.contains("ㄹ "), "standalone ㄹ in " + where);
				assertFalse(phrase.contains("을을") || phrase.contains("를를"), "doubled particle " + where);
				switch (level) {
					case 1 -> assertTrue(phrase.endsWith("요"), where);
					case 2 -> assertTrue(phrase.endsWith("야 해요"), where);
					case 3 -> assertTrue(phrase.endsWith("야만 해요"), where);
					case 4 -> assertTrue(phrase.endsWith("지 않으면 안 돼요"), where);
					case 5 -> assertTrue(phrase.contains("지 않고서는"), where);
					case 6 -> assertTrue(phrase.endsWith("수밖에 없어요"), where);
					default -> {
					}
				}
				if (level == 6 && hasFinalRieul(verb.stem())) {
					assertEquals(verb.stem() + " 수밖에 없어요", phrase, where);
				}
			}
		}
	}

	private static boolean hasFinalRieul(String stem) {
		char last = stem.charAt(stem.length() - 1);
		return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8;
	}
}
