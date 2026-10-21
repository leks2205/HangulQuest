package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The morpheme panel: families group by the same hanja only, native words have none, homophones warn. */
class MorphemeFamiliesTest {
	private final Map<String, LexiconTags.Tag> tags = new HashMap<>();
	private final List<Word> words = new ArrayList<>();
	private final Set<String> learned = new HashSet<>();

	private Word word(String id, String ko, String origin, String... hanja) {
		Word word = new Word(id, ko, id, id, id, Category.MISC, 1, id, null, null, null, null, Origin.CURATED,
				false, null);
		tags.put(id, new LexiconTags.Tag("set", null, 0, origin, List.of(hanja), false, 0));
		words.add(word);
		return word;
	}

	private MorphemeFamilies.Panel panel(Word word) {
		return MorphemeFamilies.of(word, LexiconTags.of(tags), words, other -> learned.contains(other.id));
	}

	private void fire() {
		word("furnace", "화로", "sino", "火", "爐");
		word("gunpowder", "화약", "sino", "火", "藥");
		word("volcano", "화산", "sino", "火", "山");
		word("flower_pot", "화분", "sino", "花", "盆");
		word("mountain", "산", "sino", "山");
	}

	@Test
	void aFamilyGroupsTheWordsThatShareTheSameHanja() {
		fire();
		MorphemeFamilies.Panel panel = panel(words.get(0));

		assertEquals(2, panel.families().size());
		MorphemeFamilies.Family fire = panel.families().get(0);
		assertEquals("火", fire.hanja());
		assertEquals("화", fire.reading());
		assertEquals(List.of("화산", "화약"), fire.members().stream().map(MorphemeFamilies.Member::ko).toList());
		MorphemeFamilies.Family furnace = panel.families().get(1);
		assertEquals("爐", furnace.hanja());
		assertTrue(furnace.members().isEmpty(), "nothing else shares 爐");
	}

	@Test
	void theSameSyllableWithAnotherHanjaIsNotInTheFamily() {
		fire();
		MorphemeFamilies.Family fire = panel(words.get(0)).families().get(0);

		assertFalse(fire.members().stream().anyMatch(member -> member.ko().equals("화분")),
				"화분 reads 화 too but is 花, not 火");
	}

	@Test
	void theWordItselfIsNeverInItsOwnFamily() {
		fire();
		for (MorphemeFamilies.Family family : panel(words.get(0)).families()) {
			assertFalse(family.members().stream().anyMatch(member -> member.wordId().equals("furnace")));
		}
	}

	@Test
	void learnedWordsComeFirst() {
		fire();
		learned.add("gunpowder");

		MorphemeFamilies.Family fire = panel(words.get(0)).families().get(0);

		assertEquals("화약", fire.members().get(0).ko());
		assertTrue(fire.members().get(0).learned());
		assertFalse(fire.members().get(1).learned());
	}

	@Test
	void aNativeWordGetsNoFamily() {
		Word stone = word("stone", "돌", "native");
		fire();

		assertTrue(panel(stone).isEmpty());
		assertEquals(MorphemeFamilies.Panel.NONE, panel(stone));
	}

	@Test
	void aWordTaggedNativeNeverGetsOneEvenIfHanjaAreListed() {
		Word odd = word("odd", "누리", "native", "火");
		fire();

		assertTrue(panel(odd).isEmpty());
	}

	@Test
	void anUntaggedWordAndAWordWithoutHanjaGetNoFamily() {
		fire();
		Word untagged = new Word("x", "엑스", "x", "x", "x", Category.MISC, 1, "x", null, null, null, null,
				Origin.LUTE, false, null);
		Word loan = word("tnt", "티엔티", "loan");

		assertTrue(panel(untagged).isEmpty());
		assertTrue(panel(loan).isEmpty());
		assertTrue(MorphemeFamilies.of(null, LexiconTags.of(tags), words, w -> true).isEmpty());
	}

	// ------------------------------------------------------------------ homophones

	@Test
	void aLearnedHomophoneWithAnotherHanjaRaisesAWarning() {
		fire();
		learned.add("flower_pot");

		MorphemeFamilies.Panel panel = panel(words.get(0));

		assertEquals(List.of(new MorphemeFamilies.Warning("화", "火", "花", "화분")), panel.warnings());
	}

	@Test
	void aHomophoneThePlayerHasNotMetRaisesNoWarning() {
		fire();

		assertTrue(panel(words.get(0)).warnings().isEmpty());
	}

	@Test
	void sameHanjaIsNoHomophone() {
		fire();
		learned.add("gunpowder");
		learned.add("volcano");

		assertTrue(panel(words.get(0)).warnings().isEmpty(), "화약 and 화산 are 火 too");
	}

	@Test
	void aDifferentSyllableIsNoHomophone() {
		fire();
		learned.add("mountain");

		assertTrue(panel(words.get(0)).warnings().isEmpty(), "산 is not read 화");
	}

	@Test
	void oneWarningPerOtherHanjaEvenWhenSeveralWordsShowIt() {
		fire();
		word("granite", "화강암", "sino", "花", "崗", "巖");
		learned.add("flower_pot");
		learned.add("granite");

		List<MorphemeFamilies.Warning> warnings = panel(words.get(0)).warnings();

		assertEquals(1, warnings.size());
		assertEquals("花", warnings.get(0).other());
	}

	// ------------------------------------------------------------------ readings

	@Test
	void theReadingFollowsTheSyllablesWhenTheCountsAgree() {
		assertEquals(List.of("화", "약"), MorphemeFamilies.readings("화약", List.of("火", "藥")));
	}

	@Test
	void aTrailingHadaIsLeftOut() {
		assertEquals(List.of("수", "영"), MorphemeFamilies.readings("수영하다", List.of("水", "泳")));
	}

	@Test
	void aSinoEojeolBesideNativeWordsIsFoundWhenItIsTheOnlyFit() {
		assertEquals(List.of("철"), MorphemeFamilies.readings("철 블록", List.of("鐵")));
		assertEquals(List.of("액", "자"), MorphemeFamilies.readings("아이템 액자", List.of("額", "子")));
	}

	@Test
	void anAmbiguousOrUnmatchedWordHasNoReading() {
		assertNull(MorphemeFamilies.readings("윤나는 화강암", List.of("花", "崗", "巖")), "two eojeol fit");
		assertNull(MorphemeFamilies.readings("양동이", List.of("洋")), "no eojeol of one syllable");
		assertNull(MorphemeFamilies.readings("돌", List.of()));
	}

	@Test
	void aWordWithoutAReadingStillJoinsFamiliesButNeverWarns() {
		fire();
		Word bucket = word("bucket", "양동이", "mixed", "火");
		learned.add("bucket");

		MorphemeFamilies.Panel own = panel(bucket);
		assertNull(own.families().get(0).reading());
		assertTrue(own.warnings().isEmpty());
		// and it is a member of the 火 family of the others, but gives no homophone evidence
		MorphemeFamilies.Panel furnace = panel(words.get(0));
		assertTrue(furnace.families().get(0).members().stream().anyMatch(member -> member.ko().equals("양동이")));
		assertTrue(furnace.warnings().isEmpty());
	}

	// ------------------------------------------------------------------ the shipped data

	@Test
	void theShippedTagsGiveFireItsFamilyAndFlowerItsWarning() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		LexiconTags shipped = LexiconTags.shared();
		Word furnace = catalog.byConceptId("furnace").orElseThrow();
		Set<String> met = new HashSet<>();
		met.add(catalog.byConceptId("flower_pot").orElseThrow().id);

		MorphemeFamilies.Panel panel = MorphemeFamilies.of(furnace, shipped, catalog.all(), w -> met.contains(w.id));

		MorphemeFamilies.Family fire = panel.families().get(0);
		assertEquals("火", fire.hanja());
		assertTrue(fire.members().stream().anyMatch(member -> member.ko().equals("화약")));
		assertTrue(panel.warnings().stream().anyMatch(w -> w.hanja().equals("火") && w.other().equals("花")),
				panel.warnings().toString());
	}

	@Test
	void noShippedNativeWordHasAFamily() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		LexiconTags shipped = LexiconTags.shared();
		int checked = 0;
		for (Word word : catalog.all()) {
			LexiconTags.Tag tag = shipped.tag(word);
			if (tag != null && "native".equals(tag.origin())) {
				assertTrue(MorphemeFamilies.of(word, shipped, catalog.all(), w -> true).isEmpty(), word.ko);
				checked++;
			}
		}
		assertTrue(checked > 100, "found only " + checked + " native words");
	}
}
