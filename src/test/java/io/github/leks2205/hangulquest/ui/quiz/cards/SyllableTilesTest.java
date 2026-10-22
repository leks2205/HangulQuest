package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyllableTilesTest {
	@Test
	void jamoDistanceCountsChangedAddedAndRemovedJamo() {
		assertEquals(0, Syllables.jamoDistance("돌", "돌"));
		assertEquals(1, Syllables.jamoDistance("돌", "도"), "a missing tail");
		assertEquals(1, Syllables.jamoDistance("돌", "둘"), "a different vowel");
		assertEquals(1, Syllables.jamoDistance("돌", "톨"), "a different lead");
		assertEquals(2, Syllables.jamoDistance("돌", "툴"));
		assertEquals(1, Syllables.jamoDistance("횃불", "횃분"), "one tail");
		assertEquals(4, Syllables.jamoDistance("돌", "바다"), "a different word is far away");
	}

	@Test
	void onlyPlainHangulWordsAreEligible() {
		assertTrue(SyllableTileCard.eligible("횃불"));
		assertTrue(SyllableTileCard.eligible("돌"));
		assertFalse(SyllableTileCard.eligible("철 광석"), "a space");
		assertFalse(SyllableTileCard.eligible("TNT"));
		assertFalse(SyllableTileCard.eligible("가나다라마바사"), "seven syllables would need more than 8 tiles");
		assertFalse(SyllableTileCard.eligible(""));
		assertNull(SyllableTileCard.tilesFor("철 광석", new Random(1)));
	}

	@Test
	void tilesContainEverySyllableAndTwoToFourDistractors() {
		for (String word : List.of("돌", "횃불", "다이아몬드", "곡괭이", "고구마", "크리퍼")) {
			for (long seed = 0; seed < 25; seed++) {
				List<String> tiles = SyllableTileCard.tilesFor(word, new Random(seed));
				assertNotNull(tiles, word);
				List<String> pool = new ArrayList<>(tiles);
				for (char c : word.toCharArray()) {
					assertTrue(pool.remove(String.valueOf(c)), word + " lacks the tile " + c);
				}
				assertTrue(pool.size() >= 2 && pool.size() <= 4, word + " has " + pool.size() + " distractors");
				assertTrue(tiles.size() >= 4 && tiles.size() <= 8, word + " has " + tiles.size() + " tiles");
			}
		}
	}

	@Test
	void distractorsDifferFromTheWordAndFromEachOther() {
		for (String word : List.of("돌", "횃불", "구구", "다이아몬드", "아기", "다더", "바파", "도두")) {
			for (long seed = 0; seed < 25; seed++) {
				List<String> distractors = Syllables.distractors(word, SyllableTileCard.distractorCount(word.length()),
						new Random(seed));
				assertEquals(new HashSet<>(distractors).size(), distractors.size(), "duplicate distractor in " + distractors);
				for (String distractor : distractors) {
					assertFalse(word.contains(distractor), distractor + " is a syllable of " + word);
					assertEquals(1, distractor.length());
				}
			}
		}
	}

	@Test
	void aRepeatedSyllableNeedsBothTiles() {
		List<String> tiles = SyllableTileCard.tilesFor("구구", new Random(4));
		assertEquals(2, tiles.stream().filter("구"::equals).count());
	}

	@Test
	void distractorsLookOrSoundLikeTheirSyllable() {
		for (String distractor : Syllables.distractors("돌", 3, new Random(1))) {
			assertEquals(1, Syllables.jamoDistance("돌", distractor), distractor + " is more than one jamo away");
		}
	}

	@Test
	void tilesAreNotHandedOverInOrder() {
		for (long seed = 0; seed < 200; seed++) {
			List<String> tiles = SyllableTileCard.tilesFor("횃불", new Random(seed));
			assertNotEquals("횃불", String.join("", tiles.subList(0, 2)), "seed " + seed);
		}
	}

	@Test
	void everyShortCuratedWordCanBeBuilt() {
		List<String> failures = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Concept concept : ConceptLoader.load()) {
			if (!SyllableTileCard.eligible(concept.ko) || !seen.add(concept.ko)) {
				continue;
			}
			List<String> tiles = SyllableTileCard.tilesFor(concept.ko, new Random(concept.ko.hashCode()));
			if (tiles == null || tiles.size() < 4 || tiles.size() > 8 || tiles.size() - concept.ko.length() < 2) {
				failures.add(concept.ko + " -> " + tiles);
			}
		}
		assertTrue(seen.size() > 200, "expected many eligible words, found " + seen.size());
		assertTrue(failures.isEmpty(), failures.toString());
	}
}
