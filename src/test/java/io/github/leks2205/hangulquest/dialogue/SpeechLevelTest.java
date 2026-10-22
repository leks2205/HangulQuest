package io.github.leks2205.hangulquest.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeechLevelTest {
	@Test
	void sentenceEndingsShowTheLevel() {
		assertEquals(SpeechLevel.HAEYO, SpeechLevel.ofSentence("저는 농부예요."));
		assertEquals(SpeechLevel.HAEYO, SpeechLevel.ofSentence("고마워요!"));
		assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("저는 사서입니다."));
		assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("주시겠습니까?"));
		assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("안녕히 가십시오."));
		assertEquals(SpeechLevel.BANMAL, SpeechLevel.ofSentence("나는 사과를 좋아해."));
		assertEquals(SpeechLevel.BANMAL, SpeechLevel.ofSentence("우리 같이 먹자!"));
		assertEquals(SpeechLevel.PLAIN, SpeechLevel.ofSentence("나는 사과를 먹는다."));
	}

	@Test
	void anEndingThatSaysNothingGivesNoLevel() {
		assertNull(SpeechLevel.ofSentence("안녕!"));
		assertNull(SpeechLevel.ofSentence("잘 가!"));
		assertNull(SpeechLevel.ofSentence(""));
		assertNull(SpeechLevel.ofSentence("..."));
	}

	@Test
	void aLineIsReadSentenceBySentence() {
		assertEquals(Set.of(SpeechLevel.HAEYO), SpeechLevel.ofLine("안녕하세요! 저는 농부예요."));
		assertEquals(Set.of(SpeechLevel.BANMAL), SpeechLevel.ofLine("그래? 그럼 우리 같이 먹자!"));
		assertEquals(Set.of(SpeechLevel.HAPSYO), SpeechLevel.ofLine("어서 오십시오. 저는 사서입니다."));
	}

	@Test
	void mixedLevelsInOneLineAreBothReported() {
		assertEquals(Set.of(SpeechLevel.HAEYO, SpeechLevel.HAPSYO), SpeechLevel.ofLine("돌을 캤어요. 곡괭이를 썼습니다."));
	}

	@Test
	void onlyFinishedSentencesCount() {
		assertEquals(Set.of(), SpeechLevel.ofLine("저는 농부예요"));
	}

	/** The forms the Conjugator makes for the two upper styles are read as those styles (a dialogue line built from them). */
	@Test
	void theConjugatorFormsOfTheUpperStylesAreRecognised() {
		for (String dictionary : List.of("먹다", "가다", "만들다", "걷다", "줍다", "짓다", "쓰다", "누르다", "하다", "열다")) {
			var word = io.github.leks2205.hangulquest.quest.grammar.Conjugator.of(dictionary);
			assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("우리는 " + word.hapnida() + "."), word.hapnida());
			assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("우리가 " + word.hapnikka() + "?"), word.hapnikka());
			assertEquals(SpeechLevel.HAPSYO, SpeechLevel.ofSentence("어제 " + word.hapnidaPast() + "."), word.hapnidaPast());
			assertEquals(SpeechLevel.BANMAL, SpeechLevel.ofSentence("우리 같이 " + word.ja() + "!"), word.ja());
			assertEquals(SpeechLevel.BANMAL, SpeechLevel.ofSentence("너도 빨리 " + word.eora() + "!"), word.eora());
			assertEquals(SpeechLevel.BANMAL, SpeechLevel.ofSentence("나는 어제 그것을 " + word.banmalPast() + "."), word.banmalPast());
		}
	}

	@Test
	void dataCanNameTheThreeLevelsButNotThePlainStyle() {
		assertEquals(SpeechLevel.HAEYO, SpeechLevel.fromId("haeyo"));
		assertEquals(SpeechLevel.BANMAL, SpeechLevel.fromId(" Banmal "));
		assertEquals(SpeechLevel.HAPSYO, SpeechLevel.fromId("hapsyo"));
		assertNull(SpeechLevel.fromId("plain"));
		assertNull(SpeechLevel.fromId("polite"));
		assertNull(SpeechLevel.fromId(null));
	}
}
