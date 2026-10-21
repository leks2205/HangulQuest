package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.Tr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interface language is the mod's own setting, so the texts come from our lang files and not from
 * the game's translations. The stub {@code es_es.json} in the test resources has two keys.
 */
class I18nTest {
	private static final String BACK = "hangulquest.ui.words.back";
	private static final String REVIEW = "hangulquest.ui.study.review_now";
	private static final String LOADING = "hangulquest.ui.info.loading";

	@AfterEach
	void reset() {
		Lang.setLocaleSource(null);
		Lang.set("EN");
	}

	@Test
	void spanishShowsSpanishWhereKeysExist() {
		Lang.set("es_es");
		assertSame(Lang.of("es_es"), Lang.current());
		assertEquals("Repasar ahora", I18n.t(REVIEW));
		assertEquals("Atrás", I18n.t(BACK));
	}

	@Test
	void spanishShowsEnglishWhereKeysAreMissingNeverGerman() {
		Lang.set("es_es");
		Map<String, String> english = I18n.bundle("en_us");
		Map<String, String> german = I18n.bundle("de_de");
		assertEquals("Loading the learning system…", I18n.t(LOADING));
		int fallbacks = 0;
		for (Map.Entry<String, String> entry : english.entrySet()) {
			if (!entry.getKey().startsWith("hangulquest.ui.") || I18n.bundle("es_es").containsKey(entry.getKey())) {
				continue;
			}
			fallbacks++;
			assertEquals(entry.getValue(), I18n.t(Lang.of("es_es"), entry.getKey()), entry.getKey());
			if (!german.get(entry.getKey()).equals(entry.getValue())) {
				assertNotEquals(german.get(entry.getKey()), I18n.t(entry.getKey()), entry.getKey());
			}
		}
		assertTrue(fallbacks > 80, "only " + fallbacks + " keys checked");
	}

	@Test
	void achievementLabelsFollowTheLanguageAtRuntime() {
		Achievements.Achievement first = Achievements.ALL.get(0);
		Lang.set("EN");
		assertEquals("First word", first.label());
		Lang.set("DE");
		assertEquals("Erstes Wort", first.label());
		Lang.set("es_es");
		assertEquals("First word", first.label());
	}

	@Test
	void autoFollowsTheGameLanguage() {
		Lang.set(Lang.AUTO);
		Lang.setLocaleSource(() -> "es_es");
		assertEquals("Repasar ahora", I18n.t(REVIEW));
		Lang.setLocaleSource(() -> "de_de");
		assertEquals("Jetzt wiederholen", I18n.t(REVIEW));
		Lang.setLocaleSource(() -> "en_us");
		assertEquals("Review now", I18n.t(REVIEW));
	}

	@Test
	void aRegionVariantUsesTheShippedLanguage() {
		Lang.set(Lang.AUTO);
		Lang.setLocaleSource(() -> "de_at");
		assertSame(Lang.DE, Lang.current());
		Lang.setLocaleSource(() -> "en_gb");
		assertSame(Lang.EN, Lang.current());
		Lang.setLocaleSource(() -> "es_mx");
		assertSame(Lang.of("es_es"), Lang.current());
	}

	@Test
	void aGameLanguageWithoutTextKeepsItsCodeAndShowsEnglish() {
		Lang.set(Lang.AUTO);
		Lang.setLocaleSource(() -> "fr_fr");
		assertEquals("fr_fr", Lang.current().code());
		assertEquals("Review now", I18n.t(REVIEW));
		Lang.setLocaleSource(() -> "ko_kr");
		assertEquals("Review now", I18n.t(REVIEW));
	}

	@Test
	void anExplicitLanguageWithoutTextIsEnglish() {
		Lang.set("fr");
		assertSame(Lang.EN, Lang.current());
		assertEquals("en_us", Lang.normalise("fr"));
		assertEquals("de_de", Lang.normalise("DE"));
		assertEquals("en_us", Lang.normalise("EN"));
		assertEquals("es_es", Lang.normalise("es_ES"));
		assertEquals("AUTO", Lang.normalise("auto"));
	}

	@Test
	void argumentsFillThePlaceholders() {
		Lang.set("EN");
		assertEquals("Today 3 seen, 2 correct", I18n.t("hangulquest.ui.info.today", 3, 2));
		Lang.set("DE");
		assertEquals("Heute 3 gesehen, 2 richtig", I18n.t("hangulquest.ui.info.today", 3, 2));
		Lang.set("EN");
		assertEquals("✓ ▶ ○ ★  wheel:zoom  drag  F:fit  120%", I18n.t("hangulquest.ui.quest.tree_legend", 120));
	}

	@Test
	void aTextWithoutArgumentsKeepsItsPlaceholders() {
		Lang.set("EN");
		assertEquals("Quiz ready: press %s", I18n.t("hangulquest.ui.hud.quiz_ready"));
	}

	@Test
	void aMissingKeyShowsTheKey() {
		assertEquals("hangulquest.ui.no.such.key", I18n.t("hangulquest.ui.no.such.key"));
	}

	@Test
	void theLanguageIndexNamesTheLanguagesInThemselves() {
		assertEquals("Deutsch", Lang.DE.label());
		assertEquals("English", Lang.EN.label());
		assertEquals("fr_fr", Lang.of("fr_fr").label());
	}

	@Test
	void languagesAreInternedSoTheyCompareByIdentity() {
		assertSame(Lang.EN, Lang.of("EN_US"));
		assertSame(Lang.of("pt_br"), Lang.of("pt-BR"));
		assertNotEquals(Lang.EN.id(), Lang.DE.id());
	}

	@Test
	@SuppressWarnings("removal")
	void theOldLiteralApiStillWorksAndMapsToKeys() {
		Lang.set("EN");
		assertEquals("Back", Tr.t("Zurück"));
		assertEquals("Not a known text", Tr.t("Not a known text"));
		Lang.set("DE");
		assertEquals("Zurück", Tr.t("Zurück"));
	}
}
