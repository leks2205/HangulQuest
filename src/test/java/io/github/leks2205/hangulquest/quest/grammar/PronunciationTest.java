package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.util.hangul.HangulCompose;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PronunciationTest {
	private static String pron(String ko) {
		return Pronunciation.of(ko).pron();
	}

	// ------------------------------------------------------------------ the bracket

	@Test
	void aBracketIsOnlyGivenWhenTheWordIsNotSaidAsWritten() {
		assertEquals("[궁물]", Pronunciation.of("국물").bracket());
		assertEquals("[머거요]", Pronunciation.of("먹어요").bracket());
		assertNull(Pronunciation.of("돌").bracket());
		assertNull(Pronunciation.of("나무").bracket());
		assertNull(Pronunciation.of("안녕하세요").bracket());
		assertFalse(Pronunciation.of("사과").differs());
		assertTrue(Pronunciation.of("꽃").differs()); // 꽃 is said [꼳], even alone
	}

	@Test
	void ruleIdsFollowTheRuleOrderNotTheOrderTheyFired() {
		Pronunciation.Result flower = Pronunciation.of("꽃잎");
		assertEquals("nasalisation,neutralisation,n_insertion", flower.ruleIds());
		assertEquals(EnumSet.of(Pronunciation.Rule.NASALISATION, Pronunciation.Rule.NEUTRALISATION,
				Pronunciation.Rule.N_INSERTION), flower.rules());
		assertEquals("", Pronunciation.of("돌").ruleIds());
	}

	@Test
	void spacesAndOtherCharactersSurvive() {
		assertEquals("궁물 밥", pron("국물 밥"));
		assertEquals("abc 궁물", pron("abc 국물"));
		assertEquals("궁물.", pron("국물."));
		assertEquals("", pron(""));
		assertEquals("", pron(null));
		assertEquals("궁물", pron("  국물  "));
	}

	@Test
	void betweenWordsOnlyTheFinalSoundsAndNasalisationApply() {
		// no tensification, no ㄹ rules across a space
		assertEquals("국 밥", pron("국 밥"));
		assertEquals("신 라", pron("신 라"));
		// the final is carried over to ㅏ ㅓ ㅗ ㅜ ㅟ, but only when it is ㄱ ㄷ ㅂ after neutralising
		assertEquals("꼬 뒤", pron("꽃 위"));
		assertEquals("눈 위", pron("눈 위"));
		assertEquals("용암 양동이", pron("용암 양동이"));
		assertEquals("옫 입따", pron("옷 입다")); // ㅣ is not carried over between words
		// ㄱ ㄷ ㅂ before ㄴ ㅁ
		assertEquals("면 명", pron("몇 명"));
	}

	// ------------------------------------------------------------------ the rules

	@Test
	void tensificationNeedsAnObstruentBefore() {
		assertEquals("학꾜", pron("학교"));
		assertEquals("신기", pron("신기")); // ㄴ is no trigger in a noun
		assertEquals("물고기", pron("물고기")); // nor is ㄹ outside Sino-Korean
		assertEquals("발딸", Pronunciation.of("발달", true).pron());
		assertEquals("발달", Pronunciation.of("발달", false).pron());
		assertEquals("발견", Pronunciation.of("발견", true).pron()); // only ㄷ ㅅ ㅈ
	}

	@Test
	void verbStemsInNasalsTenseOnlyTheDictionaryFormOfListedStems() {
		assertEquals("신따", pron("신다"));
		assertEquals("안따", pron("안다"));
		assertEquals("간다", pron("간다"));
		assertEquals("안기다", pron("안기다")); // -기- never tenses
		assertEquals("신고", pron("신고")); // the noun 申告
		assertEquals("감지", pron("감지"));
		assertEquals("안따", pron("앉다"));
	}

	@Test
	void doubleFinalsBeforeConsonantsAndVowels() {
		assertEquals("닥", pron("닭"));
		assertEquals("달기", pron("닭이"));
		assertEquals("달글", pron("닭을"));
		assertEquals("갑씨", pron("값이"));
		assertEquals("익따", pron("읽다"));
		assertEquals("일꼬", pron("읽고")); // ㄺ before ㄱ in a stem
		assertEquals("닥꼬기", pron("닭고기")); // but not in a noun
		assertEquals("밥따", pron("밟다")); // 밟 keeps ㅂ
		assertEquals("널따", pron("넓다"));
		assertEquals("마나", pron("많아"));
		assertEquals("만타", pron("많다"));
	}

	@Test
	void aHBeforeAVowelIsSilent() {
		assertEquals("조아요", pron("좋아요"));
		assertEquals("노코", pron("놓고"));
		assertEquals("추카", pron("축하"));
		assertEquals("구치다", pron("굳히다"));
		assertEquals("오 탄 벌", pron("옷 한 벌"));
	}

	@Test
	void palatalisationOnlyBeforeALoneIAfterDOrT() {
		assertEquals("가치", pron("같이"));
		assertEquals("구지", pron("굳이"));
		assertEquals("바치", pron("밭이"));
		assertEquals("반닐", pron("밭일")); // 일 is a word, not the particle: no ㅌ+이 palatalisation
		assertEquals("마디", pron("마디"));
	}

	@Test
	void ouBeforeAConsonantIsSaidIAndAloneStays() {
		assertEquals("히망", pron("희망"));
		assertEquals("의사", pron("의사"));
		assertEquals("무니", pron("무늬"));
	}

	@Test
	void theResultNeverChangesTheLengthOfAHangulWord() {
		for (String word : List.of("밟는", "읊어", "넓히다", "맛있다", "꽃 한 송이", "닭고기 볶음", "굳이", "읽히다")) {
			assertEquals(word.length(), pron(word).length(), word);
			word.chars().forEach(c -> {
				if (c != ' ') {
					assertTrue(HangulCompose.isSyllable(c));
				}
			});
		}
	}

	// ------------------------------------------------------------------ the exceptions data

	private static PronunciationExceptions parse(String json) throws IOException {
		return PronunciationExceptions.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void exceptionsOverrideAWholeWord() throws IOException {
		PronunciationExceptions data = parse("""
				{"overrides": {"토끼": {"pron": "토끼끼", "rules": ["tensification"]}}}""");
		Pronunciation.Result result = Pronunciation.of("토끼", false, data);
		assertEquals("토끼끼", result.pron());
		assertEquals("tensification", result.ruleIds());
		assertTrue(result.lexical());
		assertEquals("토끼", Pronunciation.of("토끼", false, PronunciationExceptions.empty()).pron());
		assertFalse(Pronunciation.of("토끼", false, PronunciationExceptions.empty()).lexical());
	}

	@Test
	void joinsMarkTheSecondPartOfACompound() throws IOException {
		PronunciationExceptions data = parse("""
				{"nInsertion": ["솜|이불"], "linked": ["맛|없다"], "tenseJoin": ["길|가"], "stems": ["살"]}""");
		assertEquals("솜니불", Pronunciation.of("솜이불", false, data).pron());
		assertEquals("마덥따", Pronunciation.of("맛없다", false, data).pron());
		assertEquals("길까", Pronunciation.of("길가", false, data).pron());
		// without the data a rule alone says the plain liaison
		assertEquals("소미불", Pronunciation.of("솜이불", false, PronunciationExceptions.empty()).pron());
		assertEquals("마섭따", Pronunciation.of("맛없다", false, PronunciationExceptions.empty()).pron());
	}

	@Test
	void anUnknownRuleIdOrABarlessJoinIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> parse("{\"overrides\": {\"가\": {\"pron\": \"나\", \"rules\": [\"nope\"]}}}"));
		assertThrows(IllegalArgumentException.class, () -> parse("{\"nInsertion\": [\"솜이불\"]}"));
	}

	@Test
	void theShippedExceptionsLoadAndStaySmall() throws IOException {
		PronunciationExceptions data = PronunciationExceptions.shared();
		assertTrue(data.size() > 20, "the data file did not load");
		assertTrue(data.size() < 150, "the exceptions are for what no rule can know, not a second dictionary");
		assertNotNull(data.override("의견란"));
		assertEquals("꼰닙", Pronunciation.of("꽃잎").pron());
	}

	@Test
	void everyShippedExceptionIsConsistentWithItsRules() throws IOException {
		// an override's rules must name something that really differs from the spelling, or be empty
		try (InputStream stream = PronunciationExceptions.class.getResourceAsStream(PronunciationExceptions.RESOURCE)) {
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var entry : root.getAsJsonObject("overrides").entrySet()) {
				JsonObject override = entry.getValue().getAsJsonObject();
				String pron = override.get("pron").getAsString();
				JsonArray rules = override.getAsJsonArray("rules");
				assertEquals(entry.getKey().length(), pron.length(), entry.getKey());
				assertEquals(!pron.equals(entry.getKey()), rules.size() > 0, entry.getKey() + " rules vs. pron");
			}
		}
	}

	// ------------------------------------------------------------------ the lexicon

	@Test
	void sinoKoreanComesFromTheLexiconTags() {
		LexiconTags.Tag sino = new LexiconTags.Tag("x", null, 0, "sino", List.of(), false, 0);
		LexiconTags.Tag nativeTag = new LexiconTags.Tag("x", null, 0, "native", List.of(), false, 0);
		LexiconTags tags = LexiconTags.of(Map.of("brown", sino, "ground", nativeTag));
		assertEquals("갈쌕", Pronunciation.of("갈색", "brown", tags).pron());
		assertEquals("갈색", Pronunciation.of("갈색", "ground", tags).pron());
		assertEquals("갈색", Pronunciation.of("갈색", "missing", tags).pron());
		assertEquals("갈색", Pronunciation.of("갈색", null, tags).pron());
		assertEquals("갈색", Pronunciation.of("갈색", "brown", null).pron());
	}

	@Test
	void everyRuleNamesItselfInBothLanguages() throws IOException {
		Set<String> ids = new java.util.HashSet<>();
		for (Pronunciation.Rule rule : Pronunciation.Rule.values()) {
			assertTrue(ids.add(rule.id()), "duplicate id " + rule.id());
			assertEquals(rule, Pronunciation.Rule.byId(rule.id()));
			assertFalse(rule.ko().isBlank());
			for (String code : List.of("en_us", "de_de")) {
				JsonObject lang = JsonParser.parseString(Files.readString(
						Path.of("src/main/resources/assets/hangulquest/lang/" + code + ".json"), StandardCharsets.UTF_8))
						.getAsJsonObject();
				assertTrue(lang.has(rule.key()), code + " lacks " + rule.key());
				assertFalse(lang.get(rule.key()).getAsString().isBlank());
			}
		}
		assertNull(Pronunciation.Rule.byId("nope"));
	}
}
