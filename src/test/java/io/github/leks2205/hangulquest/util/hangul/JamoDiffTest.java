package io.github.leks2205.hangulquest.util.hangul;

import io.github.leks2205.hangulquest.learn.Grader;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JamoDiffTest {
	@Test
	void exactIsDistanceZero() {
		JamoDiff diff = JamoDiff.of("안녕하세요", "안녕하세요");
		assertTrue(diff.exact());
		assertEquals(0, diff.distance());
		assertTrue(diff.ops().stream().allMatch(op -> op.kind() == JamoDiff.Kind.SAME));
		assertNull(diff.onlyDifference());
	}

	@Test
	void aSubstitutedJamoIsOneOff() {
		// 감 against 강: only the final differs.
		JamoDiff diff = JamoDiff.of("강", "감");
		assertEquals(1, diff.distance());
		JamoDiff.Op wrong = diff.onlyDifference();
		assertEquals(JamoDiff.Kind.WRONG, wrong.kind());
		assertEquals('ㅇ', wrong.expected());
		assertEquals('ㅁ', wrong.typed());
	}

	@Test
	void aMissingJamoIsOneOff() {
		// 바 for 밥 and 오 for 와 (the ㅏ of ㅘ is missing).
		JamoDiff.Op final_ = JamoDiff.of("밥", "바").onlyDifference();
		assertEquals(JamoDiff.Kind.MISSING, final_.kind());
		assertEquals('ㅂ', final_.expected());
		assertEquals(0, final_.typed());
		JamoDiff.Op vowel = JamoDiff.of("와", "오").onlyDifference();
		assertEquals(JamoDiff.Kind.MISSING, vowel.kind());
		assertEquals('ㅏ', vowel.expected());
	}

	@Test
	void anExtraJamoIsOneOff() {
		JamoDiff.Op extra = JamoDiff.of("바", "밥").onlyDifference();
		assertEquals(JamoDiff.Kind.EXTRA, extra.kind());
		assertEquals('ㅂ', extra.typed());
		assertEquals(0, extra.expected());
	}

	@Test
	void compoundFinalsAreTwoKeys() {
		assertEquals(1, JamoDiff.of("값", "갑").distance(), "ㅅ of ㅄ missing");
		assertEquals(1, JamoDiff.of("값", "갓").distance(), "ㅂ of ㅄ missing");
		assertEquals(1, JamoDiff.of("읽", "일").distance());
		assertEquals(2, JamoDiff.of("값", "가").distance());
	}

	@Test
	void shiftedConsonantsAreDifferentKeys() {
		assertEquals(1, JamoDiff.of("빠", "바").distance());
		assertEquals(1, JamoDiff.of("예", "에").distance(), "ㅖ against ㅔ");
		assertEquals(1, JamoDiff.of("얘", "에").distance(), "ㅒ against ㅔ");
	}

	@Test
	void aSwappedPairIsTwoOff() {
		assertEquals(2, JamoDiff.of("가나", "나가").distance());
	}

	@Test
	void anotherWordIsFarOff() {
		assertTrue(JamoDiff.of("사과", "바나나").distance() > 1);
		assertTrue(JamoDiff.of("사과", "").distance() > 1);
		assertEquals(JamoDiff.of("사과", "").distance(), HangulCompose.toJamo("사과").length());
	}

	@Test
	void spacesDoNotCount() {
		assertTrue(JamoDiff.of("물 한 잔", "물한잔").exact());
		assertTrue(JamoDiff.of("물한잔", " 물  한 잔 ").exact());
	}

	@Test
	void decomposedInputCountsAsComposed() {
		// 한 as conjoining jamo (U+1112 U+1161 U+11AB) is the same answer as the block.
		assertTrue(JamoDiff.of("한", "한").exact());
	}

	@Test
	void anUnfinishedSyllableIsTheMissingKeys() {
		// The player stopped after ㅎㅏㄴㄱ: 한ㄱ against 한글 lacks ㅡ and ㄹ.
		JamoDiff diff = JamoDiff.of("한글", "한ㄱ");
		assertEquals(2, diff.distance());
		assertEquals("ㅎㅏㄴㄱㅡㄹ", diff.expectedJamo());
		assertEquals("ㅎㅏㄴㄱ", diff.typedJamo());
	}

	@Test
	void theAlignmentCoversBothSides() {
		JamoDiff diff = JamoDiff.of("값이", "갑이");
		assertEquals(HangulCompose.toJamo("값이"), diff.expectedJamo());
		assertEquals(HangulCompose.toJamo("갑이"), diff.typedJamo());
		assertEquals(1, diff.ops().stream().filter(op -> op.kind() != JamoDiff.Kind.SAME).count());
	}

	@Test
	void nullIsEmpty() {
		assertEquals(HangulCompose.toJamo("물").length(), JamoDiff.of("물", null).distance());
		assertTrue(JamoDiff.of(null, "").exact());
	}

	@Test
	void distanceIsSymmetric() {
		String[] words = {"안녕", "한글", "값", "읽어요", "왜요", "괜찮아요", "사과", "바나나"};
		for (String a : words) {
			for (String b : words) {
				assertEquals(JamoDiff.of(a, b).distance(), JamoDiff.of(b, a).distance(), a + " / " + b);
			}
		}
	}

	// ------------------------------------------------------------- grading

	@Test
	void exactIsGood() {
		TypedAnswer.Result result = TypedAnswer.grade("사과", "사과");
		assertEquals(Fsrs.GOOD, result.graded().grade());
		assertEquals(ReviewMode.TYPED, result.graded().mode());
		assertTrue(result.graded().schedules());
		assertTrue(result.exact());
	}

	@Test
	void oneJamoOffIsHardAndStillRight() {
		TypedAnswer.Result result = TypedAnswer.grade("사과", "사가");
		assertEquals(Fsrs.HARD, result.graded().grade());
		assertTrue(result.graded().correct());
		assertFalse(result.exact());
		assertTrue(result.diff().oneOff());
	}

	@Test
	void oneJamoOffInAShortWordIsAnotherWord() {
		// 물 and 불 differ by one jamo but are different words: that is wrong, not a typo.
		TypedAnswer.Result result = TypedAnswer.grade("물", "불");
		assertEquals(Fsrs.AGAIN, result.graded().grade());
		assertFalse(result.graded().correct());
		assertTrue(result.diff().oneOff());
	}

	@Test
	void furtherOffIsAgain() {
		TypedAnswer.Result result = TypedAnswer.grade("사과", "바나나");
		assertEquals(Fsrs.AGAIN, result.graded().grade());
		assertFalse(result.graded().correct());
	}

	@Test
	void theGradeIsTheGradersOwn() {
		for (String typed : new String[] {"사과", "사가", "사", "바나나", ""}) {
			TypedAnswer.Result result = TypedAnswer.grade("사과", typed);
			Grader.Graded direct = Grader.typed(result.diff().distance());
			assertEquals(direct, result.graded(), "for " + typed);
		}
	}

	@Test
	void theClosestAcceptedSpellingDecides() {
		TypedAnswer.Result result = TypedAnswer.grade(List.of("수박", "워터멜론"), "수바");
		assertEquals(Fsrs.HARD, result.graded().grade());
		assertEquals("ㅅㅜㅂㅏㄱ", result.diff().expectedJamo());
		assertThrows(IllegalArgumentException.class, () -> TypedAnswer.grade(List.of(), "수박"));
	}
}
