package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.quest.grammar.Conjugator.Irregular;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edge cases around the gold table: contractions, ㄹ-drop, the lexicon and the ladder forms. */
class ConjugatorTest {
	private static Conjugator.Word w(String dictionary) {
		return Conjugator.of(dictionary);
	}

	@Test
	void vowelContractionsInEoyoAndPast() {
		assertEquals("와요", w("오다").eoyo());
		assertEquals("왔어요", w("오다").past());
		assertEquals("봐요", w("보다").eoyo());
		assertEquals("쏴요", w("쏘다").eoyo());
		assertEquals("줘요", w("주다").eoyo());
		assertEquals("줬어요", w("주다").past());
		assertEquals("세워요", w("세우다").eoyo());
		assertEquals("마셔요", w("마시다").eoyo());
		assertEquals("셔요", w("시다").eoyo());
		assertEquals("보여요", w("보이다").eoyo());
		assertEquals("가져요", w("가지다").eoyo());
		assertEquals("해요", w("하다").eoyo());
		assertEquals("했어요", w("하다").past());
		assertEquals("돼요", w("되다").eoyo());
		assertEquals("됐어요", w("되다").past());
		assertEquals("돼서", w("되다").aseo());
	}

	@Test
	void sameVowelAbsorbsTheEndingAndOtherVowelsKeepIt() {
		assertEquals("가요", w("가다").eoyo());
		assertEquals("서요", w("서다").eoyo());
		assertEquals("캐요", w("캐다").eoyo());
		assertEquals("켜요", w("켜다").eoyo());
		// ㅔ, ㅟ and ㅢ-type vowels do not contract
		assertEquals("베어요", w("베다").eoyo());
		assertEquals("쉬어요", w("쉬다").eoyo());
		assertEquals("뛰어요", w("뛰다").eoyo());
		assertEquals("쥐었어요", w("쥐다").past());
	}

	@Test
	void harmonyFollowsTheLastVowelAndBrightVowelsTakeA() {
		assertEquals("앉아요", w("앉다").eoyo());
		assertEquals("놓아요", w("놓다").eoyo());
		assertEquals("먹어요", w("먹다").eoyo());
		assertEquals("얇아요", w("얇다").eoyo());
		assertEquals("괜찮아요", w("괜찮다").eoyo());
		assertEquals("맛있어요", w("맛있다").eoyo());
	}

	@Test
	void euDropPicksTheVowelFromTheSyllableBefore() {
		assertEquals("써요", w("쓰다").eoyo());
		assertEquals("꺼요", w("끄다").eoyo());
		assertEquals("커요", w("크다").eoyo());
		assertEquals("모아요", w("모으다").eoyo());
		assertEquals("아파요", w("아프다").eoyo());
		assertEquals("예뻐요", w("예쁘다").eoyo());
		assertEquals("기뻐요", w("기쁘다").eoyo());
		assertEquals("잠가요", w("잠그다").eoyo());
		assertEquals("따라요", w("따르다").eoyo());
		assertEquals("치렀어요", w("치르다").past());
	}

	@Test
	void reuIrregularDoublesTheRieul() {
		assertEquals("눌러요", w("누르다").eoyo());
		assertEquals("잘라요", w("자르다").eoyo());
		assertEquals("몰라요", w("모르다").eoyo());
		assertEquals("올랐어요", w("오르다").past());
		assertEquals("서둘러요", w("서두르다").eoyo());
		assertEquals("빨라요", w("빠르다").eoyo());
		assertEquals("푸르러요", w("푸르다").eoyo());
		assertEquals("푸르렀어요", w("푸르다").past());
	}

	@Test
	void rieulStemsKeepTheLiquidInTheLiquidSlotsAndLoseItBeforeNBS() {
		assertEquals("만들어요", w("만들다").eoyo());
		assertEquals("만들고", w("만들다").go());
		assertEquals("만들", w("만들다").future());
		assertEquals("만들면", w("만들다").myeon());
		assertEquals("만드세요", w("만들다").seyo());
		assertEquals("만드니까", w("만들다").nikka());
		assertEquals("만든", w("만들다").eun());
		assertEquals("만드는", w("만들다").neun());
		assertEquals("만드는데", w("만들다").neunde());
		assertEquals("만듭니다", w("만들다").hapnida());
		assertEquals("만든다", w("만들다").handa());
		assertEquals("만들러", w("만들다").eureo());
		assertEquals("만들게요", w("만들다").eulgeyo());
		assertEquals("아세요", w("알다").seyo());
		assertEquals("압니다", w("알다").hapnida());
		assertEquals("엽니다", w("열다").hapnida());
		assertEquals("연다", w("열다").handa());
		assertEquals("긴", w("길다").neun());
	}

	@Test
	void dBAndSIrregularsChangeBeforeAVowel() {
		assertEquals("걸어요", w("걷다").eoyo());
		assertEquals("걷고", w("걷다").go());
		assertEquals("걸은", w("걷다").eun());
		assertEquals("걸으니까", w("걷다").nikka());
		assertEquals("걷는", w("걷다").neun());
		assertEquals("걷습니다", w("걷다").hapnida());
		assertEquals("도와요", w("돕다").eoyo());
		assertEquals("도왔어요", w("돕다").past());
		assertEquals("구워요", w("굽다").eoyo());
		assertEquals("구운", w("굽다").eun());
		assertEquals("주우니까", w("줍다").nikka());
		assertEquals("줍는다", w("줍다").handa());
		assertEquals("지어요", w("짓다").eoyo());
		assertEquals("지은", w("짓다").eun());
		assertEquals("나아서", w("낫다").aseo());
		assertEquals("지었어요", w("짓다").past());
	}

	@Test
	void hIrregularAdjectivesLoseTheHBeforeAVowelOrNAndL() {
		assertEquals("빨개요", w("빨갛다").eoyo());
		assertEquals("하얘요", w("하얗다").eoyo());
		assertEquals("그래요", w("그렇다").eoyo());
		assertEquals("퍼레요", w("퍼렇다").eoyo());
		assertEquals("빨간", w("빨갛다").neun());
		assertEquals("빨갛고", w("빨갛다").go());
		assertEquals("빨가니까", w("빨갛다").nikka());
		assertEquals("빨갈", w("빨갛다").future());
	}

	@Test
	void classifyUsesTheLexiconWhereSpellingCannotDecide() {
		// same final consonant, different class
		assertEquals(Irregular.REG, Conjugator.classify("입다"));
		assertEquals(Irregular.REG, Conjugator.classify("잡다"));
		assertEquals(Irregular.REG, Conjugator.classify("씹다"));
		assertEquals(Irregular.B_IRR, Conjugator.classify("줍다"));
		assertEquals(Irregular.REG, Conjugator.classify("웃다"));
		assertEquals(Irregular.S_IRR, Conjugator.classify("짓다"));
		assertEquals(Irregular.REG, Conjugator.classify("받다"));
		assertEquals(Irregular.D_IRR, Conjugator.classify("듣다"));
		// ㅎ final: only the adjectives with the colour/demonstrative endings
		assertEquals(Irregular.REG, Conjugator.classify("좋다"));
		assertEquals(Irregular.REG, Conjugator.classify("놓다"));
		assertEquals(Irregular.REG, Conjugator.classify("쌓다"));
		assertEquals(Irregular.H_IRR, Conjugator.classify("파랗다"));
		// 르 verbs: most double the ㄹ, three do not, one takes 러
		assertEquals(Irregular.REU_IRR, Conjugator.classify("부르다"));
		assertEquals(Irregular.EU_DROP, Conjugator.classify("따르다"));
		assertEquals(Irregular.EU_DROP, Conjugator.classify("들르다"));
		assertEquals(Irregular.LEO_IRR, Conjugator.classify("푸르다"));
		assertEquals(Irregular.U_IRR, Conjugator.classify("푸다"));
		// by spelling
		assertEquals(Irregular.HADA, Conjugator.classify("좋아하다"));
		assertEquals(Irregular.RIEUL_DROP, Conjugator.classify("놀다"));
		assertEquals(Irregular.EU_DROP, Conjugator.classify("쓰다"));
		assertEquals(Irregular.REG, Conjugator.classify("쓰다듬다"));
		assertEquals(Irregular.REG, Conjugator.classify("먹다"));
	}

	@Test
	void suffixRulesCatchCompoundsAndDerivedAdjectives() {
		assertEquals(Irregular.D_IRR, Conjugator.classify("엿듣다"));
		assertEquals("엿들어요", w("엿듣다").eoyo());
		assertEquals(Irregular.D_IRR, Conjugator.classify("알아듣다"));
		assertEquals(Irregular.B_IRR, Conjugator.classify("자유롭다"));
		assertEquals("자유로워요", w("자유롭다").eoyo());
		assertEquals(Irregular.B_IRR, Conjugator.classify("사랑스럽다"));
		assertTrue(Conjugator.isAdjective("자유롭다"));
		assertEquals(Irregular.H_IRR, Conjugator.classify("누렇다"));
	}

	@Test
	void theSecondReadingOfASpellingCanBeForced() {
		// 묻다 "ask" is ㄷ-irregular, 묻다 "bury" is regular
		assertEquals("물어요", w("묻다").eoyo());
		Conjugator.Word bury = Conjugator.of("묻다", Irregular.REG, false);
		assertEquals("묻어요", bury.eoyo());
		assertEquals("묻은", bury.eun());
	}

	@Test
	void adjectivesTakeEunWhereVerbsTakeNeun() {
		assertEquals("큰", w("크다").neun());
		assertEquals("큰데", w("크다").neunde());
		assertEquals("높은", w("높다").neun());
		assertEquals("어두운", w("어둡다").neun());
		assertEquals("어두운데", w("어둡다").neunde());
		assertEquals("크다", w("크다").handa());
		assertEquals("크게", w("크다").ge());
		assertEquals("먹는", w("먹다").neun());
		assertEquals("먹는데", w("먹다").neunde());
		// 있다 and 없다 words stay on 는 even as adjectives
		assertEquals("맛있는", w("맛있다").neun());
		assertEquals("없는데", w("없다").neunde());
		assertEquals("있는", w("있다").neun());
		assertEquals("있다", w("있다").handa());
	}

	@Test
	void theLadderFormsOfAnOrdinaryVerb() {
		Conjugator.Word eat = w("먹다");
		assertEquals("먹어야 해요", eat.eoyaHaeyo());
		assertEquals("먹기 전에", eat.giJeone());
		assertEquals("먹은", eat.eun());
		assertEquals("먹은 후에", eat.eunHue());
		assertEquals("먹고서", eat.goseo());
		assertEquals("먹지 않고", eat.jiAnko());
		assertEquals("먹을 수 있어요", eat.suIsseoyo());
		assertEquals("먹을 수밖에 없어요", eat.suBakke());
		assertEquals("먹으러", eat.eureo());
		assertEquals("먹지만", eat.jiman());
		assertEquals("먹으니까", eat.nikka());
		assertEquals("먹어도", eat.ado());
		assertEquals("먹게", eat.ge());
		assertEquals("먹고 있어요", eat.goIsseoyo());
		assertEquals("먹을 거예요", eat.geoyeyo());
		assertEquals("먹고 싶어요", eat.goSipeo());
		assertEquals("먹지 않아요", eat.jiAnayo());
		assertEquals("먹을게요", eat.eulgeyo());
		assertEquals("먹으면 안 돼요", eat.myeonAnDoeyo());
		assertEquals("먹습니다", eat.hapnida());
		assertEquals("먹어", eat.banmal());
		assertEquals("먹는다", eat.handa());
		assertEquals("먹듯이", eat.deusi());
		assertEquals("먹을수록", eat.eulsurok());
		assertEquals("먹기 마련이에요", eat.giMaryeon());
		assertEquals("먹는 동안", eat.neunDongan());
	}

	@Test
	void vowelStemsTakeBatchimFormsAndHonorificSuppletionIsLeftAlone() {
		assertEquals("갈", w("가다").future());
		assertEquals("간", w("가다").eun());
		assertEquals("갑니다", w("가다").hapnida());
		assertEquals("간다", w("가다").handa());
		assertEquals("갈게요", w("가다").eulgeyo());
		assertEquals("가세요", w("가다").seyo());
		// 먹다 asks for 드세요 (the honorific list of irregulars.json); the regular form is plainSeyo
		assertEquals("드세요", w("먹다").seyo());
		assertEquals("먹으세요", w("먹다").plainSeyo());
		assertEquals("들", w("들다").future());
		assertEquals("드세요", w("들다").seyo());
	}

	@Test
	void notADictionaryFormIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> Conjugator.of("먹"));
		assertThrows(IllegalArgumentException.class, () -> Conjugator.of("eat다"));
		assertThrows(IllegalArgumentException.class, () -> Conjugator.of("다"));
		assertThrows(IllegalArgumentException.class, () -> Conjugator.of(null));
	}

	@Test
	void everyLexiconEntryIsADictionaryFormWithAKnownClass() {
		assertFalse(Lexicon.SHARED.exactWords().isEmpty());
		for (String word : Lexicon.SHARED.exactWords()) {
			Conjugator.Word conjugated = Conjugator.of(word);
			assertEquals(Lexicon.SHARED.listed(word), conjugated.irregular(), word);
			assertFalse(conjugated.eoyo().isEmpty(), word);
		}
	}
}
