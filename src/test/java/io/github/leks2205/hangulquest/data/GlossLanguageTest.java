package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Glosses by language code, and dynamic vocabulary that does not need German. */
class GlossLanguageTest {
	private static Word word(String de, String en, Map<String, String> glosses) {
		return new Word("w", "고양이", de, en, de, Category.ANIMALS, 1, "w", null, null, null, null,
				Origin.CURATED, false, null, List.of(), glosses);
	}

	@Test
	void theGlossOfTheLanguageCodeWins() {
		Word cat = word("Katze", "cat", Map.of("es_es", "gato"));
		assertEquals("gato", cat.gloss(Lang.of("es_es")));
		assertEquals("cat", cat.gloss(Lang.EN));
		assertEquals("Katze", cat.gloss(Lang.DE));
	}

	@Test
	void aLanguageWithoutAGlossFallsBackToEnglishNotGerman() {
		Word cat = word("Katze", "cat", Map.of("es_es", "gato"));
		assertEquals("cat", cat.gloss(Lang.of("la_la")));
	}

	@Test
	void withoutEnglishTheGermanTextShowsWithItsMarker() {
		Word cat = word("Katze", null, Map.of());
		assertEquals(Word.UNTRANSLATED_PREFIX + "Katze", cat.gloss(Lang.of("es_es")));
		assertEquals("Katze", cat.gloss(Lang.DE));
	}

	@Test
	void deShortStaysTheGermanGlossOnly() {
		Word cat = new Word("w", "고양이", "Hauskatze", "cat", "Katze", Category.ANIMALS, 1, "w", null, null,
				null, null, Origin.CURATED, false, null);
		assertEquals("Katze", cat.deShort);
		assertEquals("Katze", cat.gloss(Lang.DE));
		assertEquals("Hauskatze", cat.glossFull(Lang.DE));
		assertEquals("cat", cat.glossFull(Lang.EN));
	}

	@Test
	void aWordWithoutGermanAtAllHasNoGermanMarker() {
		Word dog = word(null, "dog", Map.of());
		assertEquals("", dog.deShort);
		assertEquals("dog", dog.gloss(Lang.DE));
		assertEquals("dog", dog.gloss(Lang.EN));
	}

	@Test
	void aConceptCanCarryGlossesForLaterLanguages() {
		Concept concept = new Concept();
		concept.id = "cat";
		concept.ko = "고양이";
		concept.de = "Katze";
		concept.en = "cat";
		concept.glosses = Map.of("es_es", "gato");
		assertEquals("gato", concept.toWord().gloss(Lang.of("es_es")));
		concept.glosses = null;
		assertEquals("cat", concept.toWord().gloss(Lang.of("es_es")));
	}

	@Test
	void dynamicVocabularyNeedsNoGerman() {
		String key = "item.minecraft.apple";
		LangTables tables = LangTables.ofTables(Map.of(
				"ko_kr", Map.of(key, "사과"),
				"en_us", Map.of(key, "Apple")));
		Word apple = Catalog.dynamicWord(Identifier.fromNamespaceAndPath("minecraft", "apple"), key, tables, false);
		assertNotNull(apple, "an English player without de_de gets no dynamic vocabulary");
		assertEquals("사과", apple.ko);
		assertEquals("Apple", apple.gloss(Lang.EN));
		assertEquals("Apple", apple.gloss(Lang.DE));
		assertEquals(List.of("minecraft:apple"), apple.referents);
	}

	@Test
	void theInterfaceLanguageFileSuppliesTheGloss() {
		String key = "entity.minecraft.cow";
		LangTables tables = LangTables.ofTables(Map.of(
				"ko_kr", Map.of(key, "소"),
				"en_us", Map.of(key, "Cow"),
				"es_es", Map.of(key, "Vaca")));
		Word cow = Catalog.dynamicWord(Identifier.fromNamespaceAndPath("minecraft", "cow"), key, tables, true);
		assertNotNull(cow);
		assertEquals("Vaca", cow.gloss(Lang.of("es_es")));
		assertEquals("Cow", cow.gloss(Lang.EN));
		assertEquals("minecraft:cow", cow.entityId);
	}

	@Test
	void germanIsStillUsedWhenItIsThere() {
		String key = "item.minecraft.apple";
		LangTables tables = LangTables.ofTables(Map.of(
				"ko_kr", Map.of(key, "사과"),
				"en_us", Map.of(key, "Apple"),
				"de_de", Map.of(key, "Apfel")));
		Word apple = Catalog.dynamicWord(Identifier.fromNamespaceAndPath("minecraft", "apple"), key, tables, false);
		assertNotNull(apple);
		assertEquals("Apfel", apple.gloss(Lang.DE));
		assertEquals("Apfel", apple.deShort);
		assertEquals("Apple", apple.gloss(Lang.EN));
	}

	@Test
	void withoutAKoreanNameThereIsNoEntry() {
		String key = "item.minecraft.apple";
		LangTables tables = LangTables.ofTables(Map.of(
				"en_us", Map.of(key, "Apple"),
				"de_de", Map.of(key, "Apfel")));
		assertNull(Catalog.dynamicWord(Identifier.fromNamespaceAndPath("minecraft", "apple"), key, tables, false));
	}

	@Test
	void technicalEntriesAreStillRejectedWithoutGerman() {
		String key = "block.minecraft.barrier";
		LangTables tables = LangTables.ofTables(Map.of("ko_kr", Map.of(key, "배리어")));
		assertNull(Catalog.dynamicWord(Identifier.fromNamespaceAndPath("minecraft", "barrier"), key, tables, false));
		assertTrue(tables.has(key));
	}
}
