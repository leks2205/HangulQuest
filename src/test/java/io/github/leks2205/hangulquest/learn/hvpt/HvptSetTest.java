package io.github.leks2205.hangulquest.learn.hvpt;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped stimulus set: the contrasts the roadmap names, nothing else, and a consistent file. */
class HvptSetTest {
	private final HvptSet set = HvptSet.shared();

	@Test
	void theFileLoadsAndIsConsistent() {
		assertFalse(set.isEmpty());
		assertEquals(List.of(), set.problems());
		assertEquals("tts", set.source(), "the audio is synthetic until natural recordings replace it");
	}

	@Test
	void theContrastsAreTheOnesFromTheRoadmap() {
		assertEquals(List.of("ㄱ ㄲ ㅋ", "ㄷ ㄸ ㅌ", "ㅂ ㅃ ㅍ", "ㅈ ㅉ ㅊ", "ㅅ ㅆ", "ㅓ ㅗ"),
				set.contrasts().stream().map(HvptSet.Contrast::label).toList());
	}

	@Test
	void theVowelPairAeEIsNeverTrained() {
		for (HvptSet.Item item : set.items()) {
			for (HvptSet.Form form : item.forms()) {
				assertFalse(form.ko().matches(".*[ㅐㅔ].*"), item.id());
				for (char c : form.ko().toCharArray()) {
					int vowel = c >= 0xAC00 && c <= 0xD7A3 ? ((c - 0xAC00) / 28) % 21 : -1;
					assertFalse(vowel == 1 || vowel == 5, item.id() + " has ㅐ or ㅔ in " + form.ko());
				}
			}
		}
		for (HvptSet.Contrast contrast : set.contrasts()) {
			assertFalse(contrast.label().contains("ㅐ") || contrast.label().contains("ㅔ"));
		}
	}

	@Test
	void everyContrastHasEnoughItemsForTrainingAndTesting() {
		for (HvptSet.Contrast contrast : set.contrasts()) {
			List<HvptSet.Item> items = set.itemsOf(contrast.id());
			long test = items.stream().filter(HvptSet.Item::test).count();
			assertTrue(items.size() >= 6, contrast.id());
			assertTrue(test >= set.params().testTrials() / set.contrasts().size(), contrast.id() + " test items");
			assertTrue(items.stream().anyMatch(item -> !item.word()), contrast.id() + " has syllables");
			assertTrue(items.stream().anyMatch(HvptSet.Item::word), contrast.id() + " has words");
		}
	}

	@Test
	void fourOrMoreTrainingVoicesAndHeldOutOnesThatNeverOverlap() {
		assertTrue(set.trainingVoices().size() >= 4);
		assertFalse(set.heldOutVoices().isEmpty());
		Set<String> overlap = new HashSet<>(set.trainingVoices());
		overlap.retainAll(set.heldOutVoices());
		assertEquals(Set.of(), overlap);
	}

	@Test
	void wordsCarryAGlossInBothLanguages() {
		for (HvptSet.Item item : set.items()) {
			for (HvptSet.Form form : item.forms()) {
				if (item.word()) {
					assertTrue(form.en() != null && !form.en().isBlank(), item.id() + " en");
					assertTrue(form.de() != null && !form.de().isBlank(), item.id() + " de");
				}
			}
		}
	}

	@Test
	void eventNamesAreSafeSoundIds() {
		for (HvptSet.Item item : set.items()) {
			for (HvptSet.Form form : item.forms()) {
				for (String voice : set.trainingVoices()) {
					assertTrue(HvptSet.event(item.id(), form.key(), voice).matches("hvpt\\.[a-z0-9_]+\\.[a-z]+\\.[a-z0-9]+"));
				}
			}
		}
	}

	@Test
	void aBrokenItemIsReported() {
		HvptSet broken = new HvptSet("tts", List.of("a"), List.of("a"), set.params(),
				List.of(new HvptSet.Contrast("gk", "x", List.of("l", "t"))),
				List.of(new HvptSet.Item("gk_w1", "gk", true, false,
						List.of(new HvptSet.Form("l", "가", null, null), new HvptSet.Form("zz", "까", null, null)))));
		List<String> problems = broken.problems();
		assertEquals(2, problems.size(), problems.toString());
	}
}
