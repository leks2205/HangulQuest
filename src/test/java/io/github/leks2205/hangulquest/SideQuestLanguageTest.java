package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.SideQuest;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SideQuestLanguageTest {
	private static Word word(String id, String ko, String de, String en) {
		return new Word(id, ko, de, en, de, Category.ANIMALS, 1, id, null, null, null, null,
				Origin.CURATED, false, null);
	}

	private static final Word CAT = word("cat", "고양이", "Katze", "cat");
	private static final Word DOG = word("dog", "개", "Hund", "dog");
	private static final Word COW = word("cow", "소", "Kuh", "cow");
	private static final Word PIG = word("pig", "돼지", "Schwein", "pig");

	private static Catalog catalog() {
		Catalog catalog = Catalog.ofConcepts(List.of());
		for (Word word : List.of(CAT, DOG, COW, PIG)) {
			catalog.add(word);
		}
		return catalog;
	}

	@AfterEach
	void resetLang() {
		Lang.set("EN");
	}

	@Test
	void englishInterfaceNeverShowsGerman() {
		Lang.set("EN");
		SideQuest quest = SideQuest.start(CAT, catalog());
		assertEquals("고양이", quest.prompt());
		assertTrue(quest.options().contains("cat"));
		for (String option : quest.options()) {
			assertFalse(List.of("Katze", "Hund", "Kuh", "Schwein").contains(option), "German option " + option);
		}
		assertTrue(quest.answer(quest.correctIndex()));
		assertEquals("cat", quest.prompt());
	}

	@Test
	void germanInterfaceStillShowsGerman() {
		Lang.set("DE");
		SideQuest quest = SideQuest.start(CAT, catalog());
		assertTrue(quest.options().contains("Katze"));
		assertTrue(quest.answer(quest.correctIndex()));
		assertEquals("Katze", quest.prompt());
	}

	@Test
	void switchingLanguageMidQuestRebuildsTheOptions() {
		Lang.set("DE");
		SideQuest quest = SideQuest.start(CAT, catalog());
		assertTrue(quest.options().contains("Katze"));
		Lang.set("EN");
		assertTrue(quest.options().contains("cat"));
		assertFalse(quest.options().contains("Katze"));
		assertEquals("cat", quest.options().get(quest.correctIndex()));
	}

	@Test
	void glossAccessorFallsBackToGermanWhenThereIsNoEnglish() {
		Word noEnglish = new Word("x", "엑스", "Eks", null, "Eks", Category.MISC, 1, "x", null, null, null,
				null, Origin.CURATED, false, null);
		assertEquals("Eks", noEnglish.gloss(Lang.EN));
		assertEquals("cat", CAT.gloss(Lang.EN));
		assertEquals("Katze", CAT.gloss(Lang.DE));
	}
}
