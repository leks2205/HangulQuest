package io.github.leks2205.hangulquest.quest.grammar;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wave 4, item 11: how the sound rules treat two words of a phrase is a switch of the exceptions file
 * ({@code crossWord}); the default is what the generator did before it existed.
 */
class PronunciationCrossWordTest {
	private static PronunciationExceptions with(String crossWord) throws IOException {
		String json = "{\"schema\":1" + (crossWord == null ? "" : ",\"crossWord\":\"" + crossWord + "\"") + "}";
		return PronunciationExceptions.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	private static String pron(String ko, String mode) throws IOException {
		return Pronunciation.of(ko, false, with(mode)).pron();
	}

	@Test
	void theDefaultIsTheOldBehaviour() throws IOException {
		assertEquals(PronunciationExceptions.CrossWord.MIXED, with(null).crossWord());
		assertEquals(PronunciationExceptions.CrossWord.MIXED, PronunciationExceptions.shared().crossWord(),
				"the shipped file keeps the default until the native review decides");
		for (String ko : new String[] {"진홍빛 울타리", "옷 한 벌", "현수막 무늬", "밭 아래", "좋은 집", "꽃 이름"}) {
			assertEquals(Pronunciation.of(ko).pron(), pron(ko, null), ko);
			assertEquals(Pronunciation.of(ko).pron(), pron(ko, "mixed"), ko);
		}
	}

	@Test
	void noneKeepsEachWordItsOwnSounds() throws IOException {
		assertEquals("진홍빋 울타리", pron("진홍빛 울타리", "none"));
		assertEquals("옫 한 벌", pron("옷 한 벌", "none"), "no aspiration across the space");
		assertEquals("현수막 무니", pron("현수막 무늬", "none"), "no nasalisation across the space (ㅢ is another rule)");
		assertEquals("받 아래", pron("밭 아래", "none"));
	}

	@Test
	void allDoesWhatHappensInsideAWord() throws IOException {
		assertEquals("진홍비 둘타리", pron("진홍빛 울타리", "all"));
		assertEquals("오 탄 벌", pron("옷 한 벌", "all"));
		assertEquals("현수망 무니", pron("현수막 무늬", "all"));
		assertEquals("바 다래", pron("밭 아래", "all"));
		assertEquals("조은 집", pron("좋은 집", "all"), "a ㅎ final goes silent before a vowel");
	}

	@Test
	void mixedAppliesTheStandardCarryOverOnlyBeforeTheVowelsItNames() throws IOException {
		assertEquals("진홍비 둘타리", pron("진홍빛 울타리", "mixed"), "ㅜ: carried over");
		assertEquals("진홍빋 이끼", pron("진홍빛 이끼", "mixed"), "ㅣ: not carried over");
		assertEquals("진홍비 디끼", pron("진홍빛 이끼", "all"));
		assertEquals("진홍빋 이끼", pron("진홍빛 이끼", "none"));
	}

	@Test
	void anUnknownModeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> with("sometimes"));
	}
}
