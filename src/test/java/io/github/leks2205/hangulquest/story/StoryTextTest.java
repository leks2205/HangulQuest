package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage.State;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a paragraph is cut into words and how a conjugated verb is traced back to its dictionary form. */
class StoryTextTest {
	private static final StoryLexicon LEXICON = StoryLexicon.parse("""
			{"words": [
			 {"id": "i", "forms": ["저"], "known": true, "en": "I", "de": "ich"},
			 {"id": "not", "forms": ["안"], "tails": [""], "grammar": "L1.neg_an", "known": true, "en": "not", "de": "nicht"},
			 {"id": "in", "forms": ["안"], "tails": ["에", "에서"], "grammar": "L1.loc_an_e", "known": true, "en": "inside", "de": "innen"},
			 {"id": "exist", "verb": "있다", "known": true, "en": "to be", "de": "sein"},
			 {"id": "want", "verb": "싶다", "grammar": "L2.go_sipeoyo", "known": true, "en": "to want", "de": "wollen"},
			 {"id": "farmer", "forms": ["농부"], "known": false, "en": "farmer", "de": "Bauer"}
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
			concept("house", "집", "House", "Haus", "ENVIRONMENT"),
			concept("stone", "돌", "Stone", "Stein", "BLOCKS"),
			concept("place", "놓다", "to place", "platzieren", "ACTIONS"),
			concept("eat", "먹다", "to eat", "essen", "ACTIONS"),
			concept("use", "쓰다", "to use", "benutzen", "ACTIONS"),
			concept("build", "짓다", "to build", "bauen", "ACTIONS"),
			concept("help", "돕다", "to help", "helfen", "ACTIONS"),
			concept("walk", "걷다", "to walk", "gehen", "ACTIONS"),
			concept("talk", "이야기하다", "to talk", "sprechen", "ACTIONS"),
			concept("make", "만들다", "to make", "herstellen", "ACTIONS"),
			concept("open", "열다", "to open", "öffnen", "ACTIONS")));

	private static StoryVocabulary vocabulary(Set<String> known) {
		return StoryVocabulary.of(CATALOG, LEXICON, known::contains, Lang.EN);
	}

	private static StoryText.Token first(String text, String... known) {
		return StoryText.analyze(text, vocabulary(Set.of(known))).get(0);
	}

	@Test
	void aVerbIsTracedBackToItsDictionaryFormWithStemEndingAndConstruction() {
		StoryText.Token token = first("놓아요.", "concept:place");
		assertEquals(State.KNOWN, token.state());
		assertEquals("concept:place", token.id());
		assertEquals("to place", token.gloss());
		assertEquals(new StoryText.Breakdown("놓다", "놓", "-아/어요", "L1.eoyo"), token.breakdown());
	}

	@Test
	void irregularVerbsAreFoundThroughTheirConjugatedForms() {
		// each of these cannot be read off the stem by cutting letters: the stem changes in front of the ending
		assertEquals(new StoryText.Breakdown("쓰다", "쓰", "-아/어요", "L1.eoyo"), first("써요").breakdown());
		assertEquals(new StoryText.Breakdown("짓다", "짓", "-아/어요", "L1.eoyo"), first("지어요").breakdown());
		assertEquals(new StoryText.Breakdown("돕다", "돕", "-아/어요", "L1.eoyo"), first("도와요").breakdown());
		assertEquals(new StoryText.Breakdown("걷다", "걷", "-아/어요", "L1.eoyo"), first("걸어요").breakdown());
		assertEquals(new StoryText.Breakdown("만들다", "만들", "-아/어요", "L1.eoyo"), first("만들어요").breakdown());
		assertEquals(new StoryText.Breakdown("이야기하다", "이야기하", "-아/어요", "L1.eoyo"), first("이야기해요").breakdown());
	}

	@Test
	void theRequestAndTheSequenceFormsNameTheirOwnConstruction() {
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-(으)세요", "L2.seyo"), first("먹으세요").breakdown());
		assertEquals(new StoryText.Breakdown("열다", "열", "-(으)세요", "L2.seyo"), first("여세요").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-고", "L2.go_sequence"), first("먹고").breakdown());
	}

	@Test
	void everyGeneratedFormOfEveryVerbFindsItsDictionaryFormAgain() {
		StoryVocabulary vocabulary = vocabulary(Set.of());
		int checked = 0;
		for (String form : vocabulary.verbForms()) {
			StoryVocabulary.VerbMatch match = vocabulary.verb(form);
			StoryText.Token token = StoryText.analyze(form, vocabulary).get(0);
			assertEquals(match.dictionary(), token.breakdown().base(), form);
			assertNotNull(token.gloss(), form);
			checked++;
		}
		assertTrue(checked >= 3 * 11 + 1, "at least three forms for each of the eleven verbs, and 먹으세요 next to 드세요, then the later levels");
	}

	@Test
	void theEndingsOfLevelsThreeToSixNameTheirConstruction() {
		assertEquals(new StoryText.Breakdown("놓다", "놓", "-았/었어요", "L3.past"), first("놓았어요").breakdown());
		assertEquals(new StoryText.Breakdown("쓰다", "쓰", "-았/었어요", "L3.past"), first("썼어요").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-기", "L3.gi_jeone"), first("먹기").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-(으)면", "L4.myeon"), first("먹으면").breakdown());
		assertEquals(new StoryText.Breakdown("만들다", "만들", "-아/어서", "L4.eoseo"), first("만들어서").breakdown());
		assertEquals(new StoryText.Breakdown("걷다", "걷", "-아/어서", "L4.eoseo"), first("걸어서").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-(으)ㄴ", "L5.adn_past_eun"), first("먹은").breakdown());
		assertEquals(new StoryText.Breakdown("만들다", "만들", "-는", "L5.adn_present_neun"), first("만드는").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-(으)ㄹ", "L5.adn_future_eul"), first("먹을").breakdown());
		assertEquals(new StoryText.Breakdown("만들다", "만들", "-(으)ㄹ수록", "L6.eulsurok"), first("만들수록").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-지", "L3.ji_anayo"), first("먹지").breakdown());
		for (String form : List.of("놓았어요", "먹기", "먹으면", "만들어서", "먹은", "만드는", "먹을", "만들수록")) {
			assertEquals(State.UNKNOWN, first(form).state(), form + " is the verb, and the player does not know it yet");
			assertEquals(State.KNOWN, first(form, "concept:place", "concept:eat", "concept:make").state(), form);
		}
	}

	@Test
	void theCopulaHasAPastForm() {
		StoryText.Token token = first("집이었어요", "concept:house");
		assertEquals(State.KNOWN, token.state());
		assertEquals("L3.past", token.breakdown().grammar());
	}

	@Test
	void aParticleOfTheSourceCanFollowAnEojeolThatMeansFrom() {
		assertEquals(new StoryText.Breakdown("돌", "돌", "에게서", "L2.ege"), first("돌에게서").breakdown());
	}

	@Test
	void existenceAndWantingCarryTheirOwnConstruction() {
		assertEquals("L1.itda_eopda", first("있어요").breakdown().grammar());
		assertEquals("L2.go_sipeoyo", first("싶어요").breakdown().grammar());
	}

	@Test
	void aNounAndItsParticleBreakDownIntoStemAndConstruction() {
		StoryText.Token place = first("집에서", "concept:house");
		assertEquals(new StoryText.Breakdown("집", "집", "에서", "L2.eseo_activity"), place.breakdown());
		assertEquals("concept:house", place.id());
		assertEquals(new StoryText.Breakdown("돌", "돌", "을", "L1.obj_eul"), first("돌을").breakdown());
		assertEquals(new StoryText.Breakdown("집", "집", "이", "L1.subj_i"), first("집이").breakdown());
		assertNull(first("집").breakdown(), "a bare noun has nothing to break down");
		// 에 teaches two constructions (place and time), so none is named
		assertEquals("", first("집에").breakdown().grammar());
	}

	@Test
	void theAnThatNegatesAndTheAnThatMeansInsideAreToldApart() {
		List<StoryText.Token> negated = StoryText.analyze("안 먹어요", vocabulary(Set.of()));
		assertEquals("lexicon:not", negated.get(0).id());
		assertEquals("L1.neg_an", negated.get(0).breakdown().grammar());
		List<StoryText.Token> inside = StoryText.analyze("집 안에 있어요", vocabulary(Set.of()));
		assertEquals("lexicon:in", inside.get(1).id());
		assertEquals(new StoryText.Breakdown("안", "안", "에", "L1.loc_an_e"), inside.get(1).breakdown());
		assertEquals(State.UNRESOLVED, StoryText.analyze("안이", vocabulary(Set.of())).get(0).state(),
				"안 with a subject particle is neither reading");
	}

	@Test
	void whetherAWordIsKnownComesFromThePlayerOrFromTheWordList() {
		assertEquals(State.UNKNOWN, first("집이").state());
		assertEquals(State.KNOWN, first("집이", "concept:house").state());
		assertEquals(State.KNOWN, first("저는").state(), "an everyday word of the list is known");
		assertEquals(State.UNKNOWN, first("농부예요").state(), "a word the list marks as new is not");
		assertEquals("Bauer", StoryText.analyze("농부예요", StoryVocabulary.of(CATALOG, LEXICON, id -> false, Lang.DE)).get(0).gloss());
	}

	@Test
	void anEojeolThatMatchesNothingIsUnresolvedAndHasNoGloss() {
		StoryText.Token token = first("용암을");
		assertEquals(State.UNRESOLVED, token.state());
		assertNull(token.gloss());
		assertNull(token.id());
		assertTrue(token.word());
	}

	@Test
	void punctuationIsNotAWordAndNeverCounts() {
		List<StoryText.Token> tokens = StoryText.analyze("집이 있어요. ... !", vocabulary(Set.of("concept:house")));
		assertEquals(4, tokens.size());
		assertTrue(tokens.get(0).word() && tokens.get(1).word());
		assertFalse(tokens.get(2).word());
		assertFalse(tokens.get(3).word());
		assertEquals("있어요.", tokens.get(1).text(), "the text keeps its punctuation for drawing");
		assertEquals(2, StoryCoverage.of(tokens).total());
	}

	@Test
	void aWordThatTwoEntriesShareIsKnownWhenThePlayerKnowsEitherOne() {
		Catalog two = Catalog.ofConcepts(List.of(concept("door", "문", "Door", "Tür", "BLOCKS"),
				concept("gate", "문", "Gate", "Tor", "BLOCKS")));
		StoryVocabulary vocabulary = StoryVocabulary.of(two, StoryLexicon.EMPTY, id -> id.equals("concept:gate"), Lang.EN);
		StoryText.Token token = StoryText.analyze("문을", vocabulary).get(0);
		assertEquals(State.KNOWN, token.state());
		assertEquals("concept:gate", token.id());
	}
	@Test
	void theFormsOfLevelsThreeToSixAreTracedBackToo() {
		assertEquals(new StoryText.Breakdown("놓다", "놓", "-았/었어요", "L3.past"), first("놓았어요").breakdown());
		assertEquals(new StoryText.Breakdown("먹다", "먹", "-기", "L3.gi_jeone"), first("먹기").breakdown());
		assertEquals(new StoryText.Breakdown("만들다", "만들", "-(으)면", "L4.myeon"), first("만들면").breakdown());
		assertEquals(new StoryText.Breakdown("짓다", "짓", "-아/어서", "L4.eoseo"), first("지어서").breakdown());
		assertEquals(new StoryText.Breakdown("열다", "열", "-(으)ㄹ수록", "L6.eulsurok"), first("열수록").breakdown());
		assertEquals("L5.adn_present_neun", first("먹는").breakdown().grammar());
	}

	@Test
	void aNounOrFunctionWordWinsOverAFormOfALaterLevelThatIsSpelledTheSame() {
		// 안 can be read as the -(으)ㄴ form of a verb (알다): the lexicon word is meant
		Catalog catalog = Catalog.ofConcepts(List.of(concept("know", "알다", "to know", "wissen", "ACTIONS")));
		StoryVocabulary vocabulary = StoryVocabulary.of(catalog, LEXICON, id -> true, Lang.EN);
		assertEquals("lexicon:not", StoryText.analyze("안", vocabulary).get(0).id());
		assertEquals("concept:know", StoryText.analyze("알아요", vocabulary).get(0).id());
	}

	@Test
	void beforeSuOrHuEOrTheFutureGeoTheAdnominalIsNotALevelFiveConstruction() {
		StoryLexicon lexicon = StoryLexicon.parse("""
				{"words": [
				 {"id": "can", "forms": ["수"], "tails": ["", "가"], "grammar": "L3.su_itda", "known": true, "en": "way", "de": "Weg"},
				 {"id": "exist", "verb": "있다", "known": true, "en": "to be", "de": "sein"},
				 {"id": "when", "forms": ["때"], "known": true, "en": "when", "de": "wenn"}
				]}""");
		StoryVocabulary vocabulary = StoryVocabulary.of(CATALOG, lexicon, id -> true, Lang.EN);
		assertEquals("L3.su_itda", StoryText.analyze("먹을 수 있어요", vocabulary).get(0).breakdown().grammar());
		assertEquals("", StoryText.analyze("먹을 때", vocabulary).get(0).breakdown().grammar());
		assertEquals("L5.adn_future_eul", StoryText.analyze("먹을 집", vocabulary).get(0).breakdown().grammar());
	}

	@Test
	void aNameOfSeveralWordsIsOneWordWithItsParticle() {
		Catalog catalog = Catalog.ofConcepts(List.of(concept("ender_dragon", "엔더 드래곤", "Ender Dragon", "Enderdrache", "ANIMALS")));
		List<StoryText.Token> tokens = StoryText.analyze("엔더 드래곤을 봐요", StoryVocabulary.of(catalog, LEXICON, id -> true, Lang.EN));
		assertEquals(2, tokens.size());
		assertEquals("엔더 드래곤을", tokens.get(0).text());
		assertEquals("concept:ender_dragon", tokens.get(0).id());
		assertEquals("L1.obj_eul", tokens.get(0).breakdown().grammar());
		StoryText.Token alone = StoryText.analyze("엔더", StoryVocabulary.of(catalog, LEXICON, id -> true, Lang.EN)).get(0);
		assertEquals(State.UNRESOLVED, alone.state(), "half a name is not the name");
	}
}
