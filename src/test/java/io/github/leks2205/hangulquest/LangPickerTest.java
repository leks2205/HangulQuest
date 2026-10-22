package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings button steps through Auto and every language in {@code data/languages.json}, each shown
 * by its own name (so a player who cannot read the current language still finds theirs).
 */
class LangPickerTest {
	@AfterEach
	void reset() {
		Lang.setLocaleSource(null);
		Lang.set("EN");
	}

	@Test
	void theChoicesAreAutoThenEveryListedLanguageInIndexOrder() {
		List<String> expected = new ArrayList<>();
		expected.add(Lang.AUTO);
		for (I18n.Language language : I18n.languages()) {
			expected.add(language.code());
		}
		assertEquals(expected, Lang.choices());
		assertTrue(Lang.choices().size() >= 3, "Auto, English and German at least");
	}

	@Test
	void everyLanguageHasItsOwnNameNotItsCode() {
		Set<String> seen = new HashSet<>();
		for (I18n.Language language : I18n.languages()) {
			assertFalse(language.name().isBlank(), language.code());
			assertNotEquals(language.code(), language.name(), "the name is shown, not the code");
			assertTrue(seen.add(language.name()), "two languages are called " + language.name());
			assertEquals(language.name(), Lang.of(language.code()).label());
		}
	}

	@Test
	void steppingVisitsEveryLanguageOnceAndComesBackToAuto() {
		assertEquals(Lang.choices().get(1), Lang.next(Lang.AUTO));
		List<String> visited = new ArrayList<>();
		String at = Lang.AUTO;
		for (int i = 0; i < Lang.choices().size(); i++) {
			at = Lang.next(at);
			visited.add(at);
		}
		assertEquals(Lang.AUTO, at);
		assertEquals(new HashSet<>(Lang.choices()), new HashSet<>(visited));
	}

	@Test
	void everyChoiceIsAcceptedByTheConfig() {
		for (String choice : Lang.choices()) {
			assertEquals(choice, Lang.normalise(choice));
		}
	}

	@Test
	void aStoredLanguageThatIsGoneStepsToAuto() {
		assertEquals(Lang.AUTO, Lang.next("xx_xx"));
		assertEquals(Lang.AUTO, Lang.next(null));
	}
}
