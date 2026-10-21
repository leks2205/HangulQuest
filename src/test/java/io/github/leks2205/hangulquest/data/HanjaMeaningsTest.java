package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every hanja the lexicon tags use has a short meaning in English and German. */
class HanjaMeaningsTest {
	@AfterEach
	void resetLang() {
		Lang.set("EN");
	}

	@Test
	void everyHanjaInTheTagsHasAMeaning() {
		LexiconTags tags = LexiconTags.shared();
		HanjaMeanings meanings = HanjaMeanings.shared();
		Set<String> missing = new HashSet<>();
		int seen = 0;
		for (Concept concept : ConceptLoader.load()) {
			LexiconTags.Tag tag = tags.tag(concept.id);
			if (tag == null) {
				continue;
			}
			for (String hanja : tag.hanja()) {
				seen++;
				if (meanings.suffix(hanja) == null) {
					missing.add(hanja);
				}
			}
		}
		assertTrue(seen > 500, "found only " + seen + " hanja in the tags");
		assertTrue(missing.isEmpty(), "no meaning for " + missing + " in data/hanja-meanings.json");
	}

	@Test
	void everyMeaningKeyExistsInEnglishAndGermanAndNothingIsLeftOver() {
		HanjaMeanings meanings = HanjaMeanings.shared();
		Map<String, String> english = I18n.bundle("en_us");
		Map<String, String> german = I18n.bundle("de_de");
		Set<String> used = new HashSet<>();
		for (String hanja : meanings.hanja()) {
			String key = HanjaMeanings.KEY_PREFIX + meanings.suffix(hanja);
			used.add(key);
			assertFalse(english.getOrDefault(key, "").isBlank(), "no English text for " + hanja + " (" + key + ")");
			assertFalse(german.getOrDefault(key, "").isBlank(), "no German text for " + hanja + " (" + key + ")");
		}
		Set<String> leftOver = new HashSet<>();
		for (String key : english.keySet()) {
			if (key.startsWith(HanjaMeanings.KEY_PREFIX) && !used.contains(key)) {
				leftOver.add(key);
			}
		}
		assertTrue(leftOver.isEmpty(), "lang keys no hanja uses: " + leftOver);
	}

	@Test
	void theMeaningFollowsTheInterfaceLanguage() {
		HanjaMeanings meanings = HanjaMeanings.shared();
		Lang.set("EN");
		assertEquals("fire", meanings.meaning("火"));
		Lang.set("DE");
		assertEquals("Feuer", meanings.meaning("火"));
	}

	@Test
	void anUnknownCharacterHasNoMeaning() {
		assertEquals("", HanjaMeanings.shared().meaning("龘"));
		assertEquals("", HanjaMeanings.of(Map.of()).meaning("火"));
		assertEquals(List.of(), List.copyOf(HanjaMeanings.of(Map.of()).hanja()));
	}
}
