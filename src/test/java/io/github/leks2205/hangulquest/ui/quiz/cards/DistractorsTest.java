package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistractorsTest {
	@BeforeEach
	void english() {
		Lang.set("EN");
	}

	@AfterEach
	void reset() {
		Lang.set("EN");
	}

	private static Word word(String id, String ko, String en) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = en;
		concept.en = en;
		concept.category = "BLOCKS";
		concept.items = List.of("minecraft:" + id);
		return concept.toWord();
	}

	private static LexiconTags tags(String... idAndSet) {
		java.util.Map<String, LexiconTags.Tag> map = new java.util.HashMap<>();
		for (int i = 0; i < idAndSet.length; i += 2) {
			map.put(idAndSet[i], new LexiconTags.Tag(idAndSet[i + 1], "초급", 1, "native", List.of(), false, 100));
		}
		return LexiconTags.of(map);
	}

	@Test
	void distractorsComeFromOtherSemanticSets() {
		Word stone = word("stone", "돌", "stone");
		List<Word> pool = List.of(stone, word("granite", "화강암", "granite"), word("dirt", "흙", "dirt"),
				word("sand", "모래", "sand"), word("gravel", "자갈", "gravel"), word("cobble", "조약돌", "cobblestone"));
		LexiconTags tags = tags("stone", "stone", "granite", "stone", "cobble", "stone", "dirt", "earth", "sand", "earth",
				"gravel", "earth");

		List<Word> picked = Distractors.pick(stone, pool, tags, Lang.EN, 3, new Random(1));

		assertEquals(3, picked.size());
		for (Word distractor : picked) {
			assertFalse(tags.semSet(distractor).equals("stone"), distractor.id + " is in the answer's own set");
		}
	}

	@Test
	void fewerOptionsAreBetterThanAWordFromTheSameSet() {
		Word stone = word("stone", "돌", "stone");
		List<Word> pool = List.of(stone, word("granite", "화강암", "granite"), word("dirt", "흙", "dirt"));
		LexiconTags tags = tags("stone", "stone", "granite", "stone", "dirt", "earth");

		List<Word> picked = Distractors.pick(stone, pool, tags, Lang.EN, 3, new Random(1));

		assertEquals(List.of("concept:dirt"), picked.stream().map(w -> w.id).toList());
	}

	@Test
	void noTwoOptionsShowTheSameGloss() {
		Word stone = word("stone", "돌", "stone");
		// Three words that all display "Wood", and one that shows the answer's own gloss.
		List<Word> pool = List.of(stone, word("oak", "참나무", "Wood"), word("log", "통나무", "wood"),
				word("plank", "판자", " WOOD "), word("rock", "바위", "Stone"), word("dirt", "흙", "dirt"));
		LexiconTags tags = tags();

		List<Word> picked = Distractors.pick(stone, pool, tags, Lang.EN, 3, new Random(2));

		Set<String> glosses = new HashSet<>();
		glosses.add("stone");
		for (Word distractor : picked) {
			assertTrue(glosses.add(distractor.gloss(Lang.EN).strip().toLowerCase(Locale.ROOT)),
					"duplicate gloss " + distractor.gloss(Lang.EN));
		}
		assertEquals(2, picked.size(), "one wood, plus dirt");
	}

	@Test
	void everyCuratedWordGetsFourDistinctOptionsFromOtherSets() {
		List<Concept> concepts = ConceptLoader.load();
		Catalog catalog = Catalog.ofConcepts(concepts);
		LexiconTags tags = LexiconTags.shared();
		assertTrue(tags.size() > 1000, "lexicon-tags.json did not load");
		Random random = new Random(7);
		List<String> problems = new ArrayList<>();
		for (Word word : catalog.all()) {
			if (word.phrase || word.gloss(Lang.EN).isBlank()) {
				continue;
			}
			List<Word> picked = Distractors.pick(word, catalog.all(), tags, Lang.EN, 3, random);
			Set<String> shown = new HashSet<>();
			shown.add(word.gloss(Lang.EN).strip().toLowerCase(Locale.ROOT));
			for (Word distractor : picked) {
				if (tags.semSet(distractor).equals(tags.semSet(word))) {
					problems.add(word.id + " shares a set with " + distractor.id);
				}
				if (!shown.add(distractor.gloss(Lang.EN).strip().toLowerCase(Locale.ROOT))) {
					problems.add(word.id + " shows a gloss twice: " + distractor.gloss(Lang.EN));
				}
			}
			if (picked.size() < 3) {
				problems.add(word.id + " got only " + picked.size() + " distractors");
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems.subList(0, Math.min(15, problems.size()))));
	}

	@Test
	void theMultipleChoiceCardHasTheRightAnswerExactlyOnce() {
		Word stone = word("stone", "돌", "stone");
		List<Word> pool = List.of(stone, word("dirt", "흙", "dirt"), word("sand", "모래", "sand"),
				word("gravel", "자갈", "gravel"), word("clay", "점토", "clay"));
		RecognitionMc4Card card = RecognitionMc4Card.create(CardInfo.of(stone), pool, tags(), new Random(3));

		assertEquals(4, card.options().size());
		assertEquals("stone", card.options().get(card.correctIndex()));
		assertEquals(1, card.options().stream().filter("stone"::equals).count());
		assertEquals(4, new HashSet<>(card.options()).size());
	}
}
