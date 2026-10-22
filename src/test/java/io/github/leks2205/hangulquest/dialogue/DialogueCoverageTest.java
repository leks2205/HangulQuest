package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which words of a line the player does not know yet. */
class DialogueCoverageTest {
	private static final DialogueLexicon LEXICON = DialogueLexicon.parse("""
			{"words": [
			 {"id": "i", "forms": ["저", "나"], "known": true, "en": "I", "de": "ich"},
			 {"id": "need", "forms": ["필요"], "known": true, "en": "need", "de": "Bedarf"},
			 {"id": "farmer", "forms": ["농부"], "known": false, "en": "farmer", "de": "Bauer"},
			 {"id": "bring", "forms": ["가져오"], "known": false, "en": "to bring", "de": "bringen"}
			]}""");

	private static Concept concept(String id, String ko, String en, String de, String category) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.en = en;
		concept.de = de;
		concept.category = category;
		return concept;
	}

	private static final Catalog CATALOG = Catalog.ofConcepts(List.of(
			concept("apple", "사과", "Apple", "Apfel", "FOOD"),
			concept("bread", "빵", "Bread", "Brot", "FOOD"),
			concept("eat", "먹다", "to eat", "essen", "ACTIONS"),
			concept("talk", "이야기하다", "to talk", "sprechen", "ACTIONS")));

	private static DialogueCoverage.Vocabulary vocabulary(Set<String> known) {
		return DialogueCoverage.vocabulary(CATALOG, LEXICON, known::contains, Lang.EN);
	}

	private static List<DialogueCoverage.Token> analyze(String line, String... known) {
		return DialogueCoverage.analyze(line, vocabulary(Set.of(known)));
	}

	@Test
	void aWordWithItsParticleIsFoundAndPunctuationIsIgnored() {
		List<DialogueCoverage.Token> tokens = analyze("저는 사과를 먹어요.", "concept:apple", "concept:eat");
		assertEquals(3, tokens.size());
		assertEquals("concept:apple", tokens.get(1).id());
		assertEquals("concept:eat", tokens.get(2).id());
		assertTrue(tokens.stream().allMatch(token -> token.state() == DialogueCoverage.State.KNOWN), tokens.toString());
		assertEquals(0, DialogueCoverage.unknownWords(tokens));
	}

	@Test
	void aWordThePlayerHasNotMetIsUnknownAndGlossed() {
		List<DialogueCoverage.Token> tokens = analyze("사과가 필요해요.");
		assertEquals(DialogueCoverage.State.UNKNOWN, tokens.get(0).state());
		assertEquals("Apple", tokens.get(0).gloss());
		assertEquals(DialogueCoverage.State.KNOWN, tokens.get(1).state(), "everyday words of the word list are known");
		assertEquals(1, DialogueCoverage.unknownWords(tokens));
		assertTrue(DialogueCoverage.meetsRule(tokens));
	}

	@Test
	void theSameLineDependsOnWhatThePlayerKnows() {
		String line = "빵과 사과가 필요해요.";
		assertTrue(DialogueCoverage.meetsRule(analyze(line, "concept:bread")), "one new word is fine");
		assertFalse(DialogueCoverage.meetsRule(analyze(line)), "two new words break the rule");
		assertTrue(DialogueCoverage.meetsRule(analyze(line, "concept:bread", "concept:apple")));
	}

	@Test
	void theSameNewWordTwiceCountsOnce() {
		List<DialogueCoverage.Token> tokens = analyze("사과를 사과가 필요해요.", "concept:bread");
		assertEquals(2, tokens.stream().filter(token -> token.state() == DialogueCoverage.State.UNKNOWN).count());
		assertEquals(1, DialogueCoverage.unknownWords(tokens));
	}

	@Test
	void anEojeolThatMatchesNothingIsUnresolvedAndCounts() {
		List<DialogueCoverage.Token> tokens = analyze("저는 용암을 먹어요.", "concept:eat");
		assertEquals(DialogueCoverage.State.UNRESOLVED, tokens.get(1).state());
		assertNull(tokens.get(1).gloss());
		assertEquals(1, DialogueCoverage.unknownWords(tokens));
	}

	@Test
	void aStemWithAnUnlistedEndingIsUnresolved() {
		// 사과 + 라는 is not a listed tail: the test must fail loudly instead of guessing.
		assertEquals(DialogueCoverage.State.UNRESOLVED, analyze("사과라는", "concept:apple").get(0).state());
	}

	@Test
	void verbsTakeTheirEndingsFromTheStem() {
		for (String form : List.of("먹어요.", "먹자!", "먹습니다.", "먹습니까?")) {
			assertEquals("concept:eat", analyze(form, "concept:eat").get(0).id(), form);
		}
		assertEquals("concept:talk", analyze("이야기해요.", "concept:talk").get(0).id(), "하다 verbs contract to 해");
		assertEquals("concept:talk", analyze("이야기하세요.", "concept:talk").get(0).id());
	}

	@Test
	void theLongestStemWins() {
		DialogueLexicon lexicon = DialogueLexicon.parse("""
				{"words": [{"id": "short", "forms": ["가"], "known": true, "en": "go", "de": "gehen"},
				 {"id": "long", "forms": ["가져오"], "known": false, "en": "bring", "de": "bringen"}]}""");
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(CATALOG, lexicon, id -> false, Lang.EN);
		assertEquals("dialogue:long", DialogueCoverage.analyze("가져오겠습니다.", vocabulary).get(0).id());
		assertEquals("dialogue:short", DialogueCoverage.analyze("가세요.", vocabulary).get(0).id());
	}

	@Test
	void theGlossFollowsTheInterfaceLanguage() {
		DialogueCoverage.Vocabulary german = DialogueCoverage.vocabulary(CATALOG, LEXICON, id -> false, Lang.DE);
		assertEquals("Bauer", DialogueCoverage.analyze("농부예요.", german).get(0).gloss());
		assertEquals("Apfel", DialogueCoverage.analyze("사과를", german).get(0).gloss());
		assertEquals("farmer", analyze("농부예요.").get(0).gloss());
	}

	@Test
	void textWithoutHangulIsNotAWord() {
		List<DialogueCoverage.Token> tokens = analyze("... 12 !", "concept:apple");
		assertEquals(0, DialogueCoverage.unknownWords(tokens));
		assertEquals(3, tokens.size());
	}

	@Test
	void theWordListWinsOverTheCatalogForTheSameForm() {
		DialogueLexicon lexicon = DialogueLexicon.parse("""
				{"words": [{"id": "own", "forms": ["빵"], "known": true, "en": "loaf", "de": "Laib"}]}""");
		DialogueCoverage.Vocabulary vocabulary = DialogueCoverage.vocabulary(CATALOG, lexicon, id -> false, Lang.EN);
		DialogueCoverage.Token token = DialogueCoverage.analyze("빵을", vocabulary).get(0);
		assertEquals("dialogue:own", token.id());
		assertEquals(DialogueCoverage.State.KNOWN, token.state());
	}
}
