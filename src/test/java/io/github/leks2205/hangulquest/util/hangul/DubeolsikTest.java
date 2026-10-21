package io.github.leks2205.hangulquest.util.hangul;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 두벌식 automaton. The vectors were checked against the trainer the logic was ported from;
 * a backspace in a vector is a backspace key.
 */
class DubeolsikTest {
	/** Keys typed, then the text a Korean IME shows. */
	private static final String[][] TYPING = {
			{"ㄱㅏ", "가"},
			{"ㅇㅏㄴ", "안"},
			{"ㅇㅏㄴㄴㅕㅇ", "안녕"},
			{"ㅎㅏㄴㄱㅡㄹ", "한글"},
			{"ㅎㅏㄴㄱㅜㄱㅇㅓ", "한국어"},
			{"ㄱㅏㅇ", "강"},
			{"ㅁㅜㄹ", "물"},
			{"ㅂㅏㅂ", "밥"},
			{"ㅅㅏㄹㅏㅇㅎㅐㅇㅛ", "사랑해요"},
			{"ㄱㅏㅁㅅㅏㅎㅏㅂㄴㅣㄷㅏ", "감사합니다"},
			{"ㄴㅓㄱㅅ", "넋"},
			{"ㅇㅏㄴㅈ", "앉"},
			{"ㅇㅏㄴㅎ", "않"},
			{"ㄷㅏㄹㄱ", "닭"},
			{"ㅅㅏㄹㅁ", "삶"},
			{"ㅇㅕㄷㅓㄹㅂ", "여덟"},
			{"ㅇㅗㅣㄱㅗㄹㅅ", "외곬"},
			{"ㅎㅏㄹㅌ", "핥"},
			{"ㅇㅡㄹㅍ", "읊"},
			{"ㅇㅣㄹㅎ", "잃"},
			{"ㄱㅏㅂㅅ", "값"},
			{"ㄴㅓㄱㅅㅏ", "넉사"},
			{"ㅇㅏㄴㅈㅏ", "안자"},
			{"ㅇㅏㄴㅎㅏ", "안하"},
			{"ㄷㅏㄹㄱㅏ", "달가"},
			{"ㅅㅏㄹㅁㅏ", "살마"},
			{"ㅇㅕㄷㅓㄹㅂㅏ", "여덜바"},
			{"ㅇㅗㅣㄱㅗㄹㅅㅏ", "외골사"},
			{"ㅎㅏㄹㅌㅏ", "할타"},
			{"ㅇㅡㄹㅍㅏ", "을파"},
			{"ㅇㅣㄹㅎㅏ", "일하"},
			{"ㄱㅏㅂㅅㅏ", "갑사"},
			{"ㅎㅏㄴㅏ", "하나"},
			{"ㅂㅏㅂㅓ", "바버"},
			{"ㅂㅏㄲ", "밖"},
			{"ㅂㅏㄲㅔ", "바께"},
			{"ㅇㅣㅆ", "있"},
			{"ㅇㅣㅆㅇㅓ", "있어"},
			{"ㅇㅣㅆㅏ", "이싸"},
			{"ㅇㅗㅏ", "와"},
			{"ㅇㅗㅐ", "왜"},
			{"ㅇㅗㅣ", "외"},
			{"ㅇㅜㅓ", "워"},
			{"ㅇㅜㅔ", "웨"},
			{"ㅇㅜㅣ", "위"},
			{"ㅇㅡㅣ", "의"},
			{"ㅇㅗㅓ", "오ㅓ"},
			{"ㅇㅗㅏㄴ", "완"},
			{"ㅇㅗㅏㄴㅏ", "와나"},
			{"ㅇㅗㅐㅇㅛ", "왜요"},
			{"ㅃㅏㄹㄹㅣ", "빨리"},
			{"ㄸㅏ", "따"},
			{"ㅉㅏ", "짜"},
			{"ㄲㅏ", "까"},
			{"ㅆㅏ", "싸"},
			{"ㅇㅏㅃ", "아ㅃ"},
			{"ㅇㅏㅃㅏ", "아빠"},
			{"ㅇㅏㄸㅏ", "아따"},
			{"ㅇㅏㅉㅣ", "아찌"},
			{"ㅇㅖ", "예"},
			{"ㅇㅒ", "얘"},
			{"ㄱ", "ㄱ"},
			{"ㅏ", "ㅏ"},
			{"ㄱㄴ", "ㄱㄴ"},
			{"ㅏㅏ", "ㅏㅏ"},
			{"ㅗㅏ", "ㅘ"},
			{"ㅎㅏㄴ ㄱㅡㄹ", "한 글"},
			{"ㅇㅏ1", "아1"},
			{"ㄱㅏㅂㅅ ㅏ", "값 ㅏ"},
			{"ㅇㅣㄹㄱㄷㅏ", "읽다"},
			{"ㅇㅣㄹㄱㅇㅓㅇㅛ", "읽어요"},
			{"ㄷㅏㄹㄱㅇㅣ", "닭이"},
			{"ㄱㅏㅂㅅㅇㅣ", "값이"},
			{"ㅇㅓㅂㅅㅇㅓㅇㅛ", "없어요"},
			{"ㄱㅗㅐㄴㅊㅏㄴㅎㅇㅏㅇㅛ", "괜찮아요"},
			{"ㅇㅏㄴㅎㅇㅏ", "않아"},
			{"ㅇㅏㅅㅅ", "앗ㅅ"},
			{"ㅇㅏㄱㄱ", "악ㄱ"},
			{"ㄱㅅ", "ㄱㅅ"},
			{"ㅇㅕㄷㅓㄹㅂㅅ", "여덟ㅅ"},
			{"ㅇㅓㅂㅅㅓ", "업서"},
			{"ㅁㅏㅅㅇㅣㅆㅇㅓㅇㅛ", "맛있어요"},
			{"ㅁㅏㅅㅇㅣㅆㅇㅓㅇㅛ?", "맛있어요?"},
			{"ㅈㅗㅎㅇㅡㄴ ㅇㅏㅊㅣㅁ", "좋은 아침"},
			{"ㅈㅗㅎㅇㅡㄴ", "좋은"},
			{"ㄴㅏㅁㅜ", "나무"},
			{"ㅅㅜㅇㅓㅂ", "수업"},
			{"ㅇㅗㅏㅇㅡㅣ", "와의"},
			{"ㅎㅗㅏㄱㅇㅣㄴ", "확인"},
			{"ㅎㅗㅏㅇㅜㅓㄹ", "화월"},
			{"ㅇㅏㅂㅏㅃㅏ", "아바빠"},
			{"ㄱㅏㅅㅅㅏ", "갓사"}
	};

	/** The same, with backspace presses among the keys. */
	private static final String[][] BACKSPACE = {
			{"ㄱㅏ\b", "ㄱ"},
			{"ㄱㅏ\b\b", ""},
			{"\b", ""},
			{"ㄱㅏㅂㅅ\b", "갑"},
			{"ㄱㅏㅂㅅ\b\b", "가"},
			{"ㄱㅏㅂㅅㅏ", "갑사"},
			{"ㄱㅏㅂㅅㅏ\b", "값"},
			{"ㄱㅏㅂㅅㅏ\b\b", "갑"},
			{"ㄱㅏㅂㅅㅏ\b\b\b", "가"},
			{"ㅇㅗㅏ\b", "오"},
			{"ㅇㅗㅏㄴ\b", "와"},
			{"ㅇㅗㅏㄴ\b\b", "오"},
			{"ㅇㅏ \bㄴ", "안"},
			{"ㅎㅏㄴㄱㅡㄹ\b", "한그"},
			{"ㅎㅏㄴㄱㅡㄹ\b\b", "한ㄱ"},
			{"ㅎㅏㄴㄱㅡㄹ\b\b\b", "한"},
			{"ㅎㅏㄴㄱㅡㄹ\b\b\b\b", "하"},
			{"ㅃㅏ\b", "ㅃ"},
			{"ㅇㅏㄴㄴㅕㅇ\b", "안녀"},
			{"ㅇㅏㄴㅎㅏ\b", "않"},
			{"ㅇㅏㄴㅎㅏ\b\b", "안"},
			{"ㅇㅡㅣ\b", "으"},
			{"ㅇㅜㅓ\b", "우"},
			{"ㅇㅣㅆㅏ\b", "있"},
			{"ㅇㅣㅆㅏ\b\b", "이"},
			{"ㄱㅏㅂㅅ\bㅅ", "값"},
			{"ㅂㅏㅂㅓ\b", "밥"},
			{"ㅇㅏㅃㅏ\b", "아ㅃ"},
			{"ㅇㅏㅃㅏ\b\b", "아"},
			{"ㅇㅏㅃㅏ\b\b\b", "ㅇ"},
			{"ㄷㅏㄹㄱㅏ\b", "닭"},
			{"ㄷㅏㄹㄱㅏ\b\b", "달"}
	};

	/** Types the keys; a backspace character is the Backspace key and a jamo is its key. */
	private static String type(String keys) {
		Dubeolsik automaton = new Dubeolsik();
		keys.codePoints().forEach(cp -> {
			if (cp == '\b') {
				automaton.backspace();
			} else if (HangulCompose.isKeyJamo((char) cp)) {
				automaton.jamo((char) cp);
			} else {
				automaton.literal(cp);
			}
		});
		return automaton.text();
	}

	@Test
	void typingVectors() {
		assertTrue(TYPING.length >= 80, "enough vectors: " + TYPING.length);
		for (String[] vector : TYPING) {
			assertEquals(vector[1], type(vector[0]), "typing " + vector[0]);
		}
	}

	@Test
	void backspaceVectors() {
		for (String[] vector : BACKSPACE) {
			assertEquals(vector[1], type(vector[0]), "typing " + vector[0].replace("\b", "<-"));
		}
	}

	@Test
	void theStaticComposeAgreesWithTheStateful() {
		for (String[] vector : TYPING) {
			assertEquals(vector[1], Dubeolsik.compose(vector[0]));
		}
	}

	@Test
	void aFinalMovesToTheNextSyllableWhenAVowelFollows() {
		assertEquals("갑사", type("ㄱㅏㅂㅅㅏ"));
		assertEquals("하나", type("ㅎㅏㄴㅏ"));
		assertEquals("한글", type("ㅎㅏㄴㄱㅡㄹ"), "ㄴㄱ is no cluster, so ㄱ opens a new syllable");
	}

	@Test
	void backspaceUndoesOneKeyAtATime() {
		Dubeolsik automaton = new Dubeolsik();
		for (char key : "ㄱㅏㅂㅅㅏ".toCharArray()) {
			automaton.jamo(key);
		}
		String[] expected = {"갑사", "값", "갑", "가", "ㄱ", ""};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], automaton.text(), "after " + i + " backspaces");
			automaton.backspace();
		}
		assertFalse(automaton.backspace(), "nothing left to remove");
		assertTrue(automaton.isEmpty());
	}

	@Test
	void compositionIsMarkedWhileTheLastKeyIsAJamo() {
		Dubeolsik automaton = new Dubeolsik();
		assertFalse(automaton.composing());
		automaton.jamo('ㅎ');
		assertTrue(automaton.composing());
		automaton.literal(' ');
		assertFalse(automaton.composing(), "a space commits");
	}

	@Test
	void composedTextFromAnInputMethodBecomesKeysAgain() {
		Dubeolsik automaton = new Dubeolsik();
		automaton.insert("값");
		assertEquals("ㄱㅏㅂㅅ", automaton.strokes());
		automaton.backspace();
		assertEquals("갑", automaton.text());
	}

	@Test
	void everyPrefixOfEveryKeySequenceBacksOutExactly() {
		// Backspace is "type the keys without the last one", for every prefix of a mixed word list.
		String[] words = {"값이없어요", "괜찮아요", "닭이읽다", "왜요의와워", "빨리까따짜싸", "맛있어요 좋은 아침"};
		for (String word : words) {
			String keys = HangulCompose.toJamo(word);
			Dubeolsik automaton = new Dubeolsik();
			keys.codePoints().forEach(cp -> {
				if (HangulCompose.isKeyJamo((char) cp)) {
					automaton.jamo((char) cp);
				} else {
					automaton.literal(cp);
				}
			});
			for (int length = keys.length(); length > 0; length--) {
				assertEquals(Dubeolsik.compose(keys.substring(0, length)), automaton.text(),
						word + " cut to " + length + " keys");
				automaton.backspace();
			}
		}
	}

	@Test
	void anyTwoOrThreeSyllablesTypeBackToThemselves() {
		Random random = new Random(7);
		for (int i = 0; i < 4000; i++) {
			StringBuilder word = new StringBuilder();
			for (int n = 2 + random.nextInt(2); n > 0; n--) {
				word.append((char) (HangulCompose.FIRST_SYLLABLE + random.nextInt(HangulCompose.SYLLABLES)));
			}
			assertEquals(word.toString(), Dubeolsik.compose(HangulCompose.toJamo(word)), word.toString());
		}
	}
}
