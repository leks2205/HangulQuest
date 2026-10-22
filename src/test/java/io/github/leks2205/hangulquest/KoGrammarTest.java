package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.KoGrammar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoGrammarTest {
	@Test
	void batchimDetectsFinalConsonant() {
		assertTrue(KoGrammar.hasBatchim("돌"));
		assertTrue(KoGrammar.hasBatchim("숲"));
		assertTrue(KoGrammar.hasBatchim("집"));
		assertFalse(KoGrammar.hasBatchim("나무"));
		assertFalse(KoGrammar.hasBatchim("사과"));
		assertFalse(KoGrammar.hasBatchim("좀비"));
		assertFalse(KoGrammar.hasBatchim(""));
	}

	@Test
	void particlesFollowBatchim() {
		assertEquals("돌을", KoGrammar.eul("돌"));
		assertEquals("나무를", KoGrammar.eul("나무"));
		assertEquals("좀비가", KoGrammar.iGa("좀비"));
		assertEquals("숲이", KoGrammar.iGa("숲"));
		assertEquals("돌은", KoGrammar.eunNeun("돌"));
		assertEquals("판자는", KoGrammar.eunNeun("판자"));
		assertEquals("주민과", KoGrammar.waGwa("주민"));
		assertEquals("나무와", KoGrammar.waGwa("나무"));
	}

	@Test
	void copulaFollowsBatchim() {
		assertEquals("숲이에요", KoGrammar.ieyo("숲"));
		assertEquals("나무예요", KoGrammar.ieyo("나무"));
	}

	@Test
	void quotationFormsTakeIWhenThereIsAFinalConsonant() {
		// The 193+ broken finales: 원목라는, 랜턴라는, 골렘라는 ...
		assertEquals("원목이라는", KoGrammar.ira("원목"));
		assertEquals("랜턴이라는", KoGrammar.ira("랜턴"));
		assertEquals("골렘이라는", KoGrammar.ira("골렘"));
		assertEquals("가디언이라는", KoGrammar.ira("가디언"));
		assertEquals("횃불이라는", KoGrammar.ira("횃불"));
		assertEquals("머리라는", KoGrammar.ira("머리"));
		assertEquals("벡스라는", KoGrammar.ira("벡스"));
		assertEquals("횃불이라고", KoGrammar.iraGo("횃불"));
		assertEquals("나무라고", KoGrammar.iraGo("나무"));
		assertEquals("횃불이란", KoGrammar.iran("횃불"));
		assertEquals("나무란", KoGrammar.iran("나무"));
	}

	@Test
	void directionParticleTakesRoAfterVowelsAndRieul() {
		assertEquals("숲으로", KoGrammar.euro("숲"));
		assertEquals("산으로", KoGrammar.euro("산"));
		assertEquals("강으로", KoGrammar.euro("강"));
		assertEquals("바다로", KoGrammar.euro("바다"));
		assertEquals("물로", KoGrammar.euro("물"));
		assertEquals("네더로", KoGrammar.euro("네더"));
		assertEquals("엔드로", KoGrammar.euro("엔드"));
		assertTrue(KoGrammar.endsInRieul("물"));
		assertFalse(KoGrammar.endsInRieul("숲"));
	}

	@Test
	void nativeNumeralsUpToNinetyNine() {
		String[] expected = {"한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두",
				"열세", "열네", "열다섯", "열여섯", "열일곱", "열여덟", "열아홉", "스무"};
		for (int n = 1; n <= 20; n++) {
			assertEquals(expected[n - 1], KoGrammar.nativeNumeral(n), "n=" + n);
		}
		assertThrows(IllegalArgumentException.class, () -> KoGrammar.nativeNumeral(0));
		assertThrows(IllegalArgumentException.class, () -> KoGrammar.nativeNumeral(100));
		// beyond twenty: the tens never change, 스무 is only 20, and 1 to 4 shorten before a counter
		String[] tens = {"스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"};
		for (int ten = 0; ten < tens.length; ten++) {
			for (int one = 0; one <= 9; one++) {
				int n = (ten + 2) * 10 + one;
				if (n == 20) {
					continue;
				}
				String expectedOne = one == 0 ? "" : expected[one - 1].equals("열") ? "" : expected[one - 1];
				assertEquals(tens[ten] + expectedOne, KoGrammar.nativeNumeral(n), "n=" + n);
			}
		}
	}

	@Test
	void countersMixNativeNumeralsAndDigitsOnPurpose() {
		assertEquals("한 개", KoGrammar.counter(1, "개"));
		assertEquals("세 마리", KoGrammar.counter(3, "마리"));
		assertEquals("스무 개", KoGrammar.counter(20, "개"));
		assertEquals("스물한 개", KoGrammar.counter(21, "개"));
		assertEquals("스물네 장", KoGrammar.counter(24, "장"));
		assertEquals("예순네 개", KoGrammar.counter(64, "개"));
		assertEquals("아흔아홉 마리", KoGrammar.counter(99, "마리"));
		assertEquals("100개", KoGrammar.counter(100, "개"));
		// Sino-Korean counters stay with digits.
		assertEquals("3블록", KoGrammar.counter(3, "블록"));
		assertEquals("한 개", KoGrammar.counter(0, "개"));
	}
}
