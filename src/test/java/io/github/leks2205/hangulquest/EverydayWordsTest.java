package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import io.github.leks2205.hangulquest.story.StoryText;
import io.github.leks2205.hangulquest.story.StoryVocabulary;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 3, A1: the everyday words of the curriculum list that the mod lacked (content/curriculum/core-gap.csv,
 * 150 words) are concepts now, with tags, glosses in English and German, and the items they stand for.
 */
class EverydayWordsTest {
	/** The 150 words of the gap list as it stood before they became concepts. */
	private static final List<String> GAP = List.of(
			"사람", "주다", "가다", "한", "오다", "크다", "많다", "안", "받다", "나오다", "살다", "속", "두", "앞", "명", "소리",
			"개", "뒤", "듣다", "아이", "시간", "하나", "위", "돈", "몸", "얼굴", "들어가다", "만나다", "쓰다", "길", "지금", "앉다",
			"손", "찾다", "나가다", "이야기", "사다", "잡다", "읽다", "일어나다", "시장", "넣다", "부르다", "죽다", "이름", "밖",
			"작다", "타다", "가운데", "높다", "머리", "기다리다", "떨어지다", "오늘", "방", "땅", "아침", "입다", "하루", "그림",
			"길다", "세", "그리다", "옆", "아래", "흐르다", "깊다", "배", "새", "바람", "노래", "불", "옷", "발", "둘", "빠르다",
			"아기", "던지다", "저녁", "넓다", "밑", "팔다", "태어나다", "멀다", "다리", "자라다", "꺼내다", "짧다", "달리다",
			"낮다", "키우다", "눕다", "내려가다", "무섭다", "고기", "벗다", "마리", "뜨겁다", "가볍다", "내일", "밝다", "심다",
			"색", "어둡다", "과일", "남쪽", "춥다", "무겁다", "열", "왼쪽", "굽다", "오른쪽", "닫다", "흰색", "차갑다", "어제",
			"북쪽", "신발", "동쪽", "셋", "생선", "기차", "채소", "검은색", "서쪽", "갈색", "회색", "열쇠", "접시", "호수", "냄비",
			"공책", "계란", "넷", "다섯", "노란색", "빨간색", "분홍색", "아홉", "장미", "파란색", "강아지", "초록색", "보라색",
			"여섯", "일곱", "여덟", "하얀색", "주황색", "켤레");

	private static final Catalog CATALOG = KoreanFixtures.catalog();

	private static Map<String, Concept> byWord() {
		Map<String, Concept> words = new HashMap<>();
		for (Concept concept : ConceptLoader.load()) {
			words.putIfAbsent(concept.ko, concept);
			for (String alias : concept.aliases) {
				words.putIfAbsent(alias, concept);
			}
		}
		return words;
	}

	private static Concept concept(String id) {
		return ConceptLoader.load().stream().filter(c -> c.id.equals(id)).findFirst().orElseThrow();
	}

	@Test
	void theGapListIsComplete() {
		assertEquals(150, GAP.size());
		assertEquals(150, Set.copyOf(GAP).size());
	}

	@Test
	void everyGapWordIsAConceptOrTheAliasOfOne() {
		Map<String, Concept> words = byWord();
		List<String> missing = GAP.stream().filter(word -> !words.containsKey(word)).toList();
		assertEquals(List.of(), missing);
		// the synonyms and the bound forms join the word they belong to
		assertEquals("egg", words.get("계란").id);
		assertEquals("one", words.get("한").id);
		assertEquals("two", words.get("두").id);
		assertEquals("three", words.get("세").id);
		assertEquals("white", words.get("흰색").id);
		assertEquals("white", words.get("하얀색").id);
	}

	@Test
	void everyNewConceptHasTagsGlossesAndASentenceThatUsesTheWord() throws IOException {
		LexiconTags tags = LexiconTags.shared();
		Map<String, Concept> words = byWord();
		for (String word : GAP) {
			Concept concept = words.get(word);
			LexiconTags.Tag tag = tags.tag(concept.id);
			assertTrue(tag != null, concept.id + " has no tags");
			assertEquals("초급", tag.curriculumBand(), concept.id + " is an everyday word");
			assertFalse("abstract".equals(tag.semSet()) && !Set.of("money", "name", "story").contains(concept.id),
					concept.id + " fell into the default set");
			assertFalse(concept.en == null || concept.en.isBlank(), concept.id + " has no English gloss");
			assertFalse(concept.de == null || concept.de.isBlank(), concept.id + " has no German gloss");
			assertTrue(concept.sentenceKo != null && !concept.sentenceKo.isBlank(), concept.id + " has no sentence");
			assertTrue(concept.descKo != null && !concept.descKo.isBlank(), concept.id + " has no description");
			assertTrue(concept.sentenceEn == null || !concept.sentenceEn.matches(".*[가-힣].*"));
		}
	}

	@Test
	void numbersCountersDirectionsAndColoursAreTaggedByKind() {
		LexiconTags tags = LexiconTags.shared();
		for (String id : List.of("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")) {
			assertEquals("number", tags.tag(id).semSet(), id);
		}
		for (String id : List.of("counter_thing", "counter_person", "counter_animal", "pair")) {
			assertEquals("counter", tags.tag(id).semSet(), id);
		}
		for (String id : List.of("above", "below", "underside", "beside", "in_front", "behind", "inside", "interior",
				"outside", "middle", "left", "right", "north", "south", "east", "west")) {
			assertEquals("direction", tags.tag(id).semSet(), id);
		}
		for (String id : List.of("red", "orange", "yellow", "green", "blue", "purple", "pink", "black", "gray", "brown",
				"white", "colour")) {
			assertEquals("colour", tags.tag(id).semSet(), id);
		}
		// the place words and the numbers name no item: lexicon entries
		for (String id : List.of("above", "inside", "one", "ten", "counter_animal", "left", "north")) {
			Concept concept = concept(id);
			assertTrue(concept.items.isEmpty() && concept.covers.isEmpty(), id + " names no item");
		}
		assertEquals("native", tags.tag("one").origin());
		assertEquals("sino", tags.tag("counter_person").origin(), "명 is 名");
		assertEquals(List.of("個"), tags.tag("counter_thing").hanja());
		assertEquals(List.of("名"), tags.tag("counter_person").hanja());
	}

	@Test
	void everyColourStandsForItsDyeAndItsWool() {
		Map<String, String> colours = Map.ofEntries(Map.entry("red", "red"), Map.entry("orange", "orange"),
				Map.entry("yellow", "yellow"), Map.entry("green", "green"), Map.entry("blue", "blue"),
				Map.entry("purple", "purple"), Map.entry("pink", "pink"), Map.entry("black", "black"),
				Map.entry("gray", "gray"), Map.entry("brown", "brown"), Map.entry("white", "white"));
		colours.forEach((id, name) -> {
			Concept concept = concept(id);
			assertEquals(List.of("minecraft:" + name + "_dye", "minecraft:" + name + "_wool"), concept.covers, id);
			assertEquals("minecraft:" + name + "_dye", concept.icon, id);
			assertTrue(concept.items.isEmpty(), id + ": the dye keeps its own name 빨간색 염료");
			assertEquals("DESCRIPTOR", concept.grammar, id + " describes things, it does not name them");
		});
		assertEquals(16, concept("colour").covers.size(), "색 covers all sixteen dyes");
		// the official names of the dyes and the wool still carry the colour word
		Map<String, String> names = KoreanFixtures.koNames();
		assertEquals("빨간색 염료", names.get("item.minecraft.red_dye"));
		assertEquals("빨간색 양털", names.get("block.minecraft.red_wool"));
		assertEquals("하얀색 양털", names.get("block.minecraft.white_wool"));
		assertEquals("주황색 양털", names.get("block.minecraft.orange_wool"));
		assertEquals("보라색 양털", names.get("block.minecraft.purple_wool"));
		assertEquals("회색 양털", names.get("block.minecraft.gray_wool"));
	}

	@Test
	void thingsWithAGameReferentNameIt() {
		assertEquals("불", CATALOG.byItemId("minecraft:fire").orElseThrow().ko);
		assertEquals("그림", CATALOG.byItemId("minecraft:painting").orElseThrow().ko);
		assertEquals("달걀", CATALOG.byConceptId("egg").orElseThrow().ko);
		assertTrue(concept("egg").aliases.contains("계란"));
		// the food words cover the foods of the game without renaming them
		assertTrue(concept("meat").covers.contains("minecraft:cooked_beef"));
		assertTrue(concept("fish_dish").covers.contains("minecraft:cooked_salmon"));
		assertTrue(concept("fruit").covers.contains("minecraft:apple"));
		assertTrue(concept("vegetable").covers.contains("minecraft:carrot"));
		assertEquals("소고기", CATALOG.byItemId("minecraft:cooked_beef").orElseThrow().ko);
		assertTrue(concept("boat").covers.contains("minecraft:spruce_boat"));
		assertTrue(concept("shoes").covers.contains("minecraft:leather_boots"));
	}

	@Test
	void theirEnglishAndGermanGlossesDifferWhereTheWordsDo() {
		Lang.set("EN");
		assertEquals("to go in", CATALOG.byConceptId("enter").orElseThrow().en);
		assertEquals("to go out", CATALOG.byConceptId("go_out").orElseThrow().en);
		assertEquals("to come out", CATALOG.byConceptId("come_out").orElseThrow().en);
		assertEquals("hineingehen", CATALOG.byConceptId("enter").orElseThrow().de);
		assertEquals("Mitte", CATALOG.byConceptId("middle").orElseThrow().de);
		assertEquals("counter for animals", CATALOG.byConceptId("counter_animal").orElseThrow().en);
	}

	@Test
	void theGapListIsEmptyNow() throws IOException {
		List<String> lines = Files.readAllLines(Path.of("content", "curriculum", "core-gap.csv"), StandardCharsets.UTF_8);
		assertEquals(1, lines.size(), "only the header is left: " + lines);
		assertTrue(lines.get(0).startsWith("word,grade,pos"));
	}

	@Test
	void theCoverageToolSeesTheWordsOfTheLexiconEvenWhenTheyNameNoItem() {
		CoverageTest.requirePython();
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/coverage.py", "--tokens",
				"문 위에 횃불을 놓아요. 집 안으로 들어가요. 소 두 마리를 키워요. 빨간색이에요. 돌 세 개예요.");
		assertEquals(0, run.exit(), run.output());
		List<String> words = List.of(run.output().split("\\R"));
		// 두 and 세 are the bound forms of 둘 and 셋, so the count of a counter feeds the numeral
		for (String word : List.of("위", "안", "둘", "셋", "마리", "개", "빨간색", "키우다", "들어가다", "놓다")) {
			assertTrue(words.contains(word), word + " not found in " + words);
		}
	}

	@Test
	void theGapWordsAreCoreWordsOfTheRecyclingMetricsNow() {
		CoverageTest.requirePython();
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/coverage.py");
		assertEquals(0, run.exit(), run.output());
		// the report counts them as core words of the lexicon, not as words missing from it
		assertTrue(run.output().contains("common words missing from the lexicon: 0"), run.output());
	}

	@Test
	void theConjunctionStaysTheConjunctionNextToTheVerbDraw() {
		// 그리고 is also the -고 form of 그리다; the story text reader must still read the word of the lexicon
		Lang.set("EN");
		StoryVocabulary vocabulary = StoryVocabulary.of(CATALOG, StoryFixtures.lexicon(), id -> true, Lang.EN);
		List<StoryText.Token> tokens = StoryText.analyze("그리고 소는 우유를 줘요.", vocabulary);
		assertTrue(tokens.get(0).id().startsWith("lexicon:"), tokens.get(0).toString());
		assertEquals(null, tokens.get(0).breakdown());
		// while the verb itself is still found in its other forms
		List<StoryText.Token> draw = StoryText.analyze("그림을 그려요.", vocabulary);
		assertEquals("concept:draw", draw.get(1).id());
		assertTrue(new ArrayList<>(vocabulary.verbForms()).contains("그려요"));
		Word word = CATALOG.byConceptId("draw").orElseThrow();
		assertEquals("그리다", word.ko);
	}
}
