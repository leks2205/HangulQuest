package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PseudowordsTest {
	private static final Path COMMON_WORDS = Path.of("tools", "research", "common_words_ko.txt");

	/** Everything we know to be a real word: the game's own names and concepts, its lexicon, a list of common words. */
	static Set<String> dictionary() throws IOException {
		List<String> lines = new ArrayList<>();
		try (InputStream stream = PseudowordsTest.class.getResourceAsStream("/assets/hangulquest/data/names.json");
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject names = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
				if (entry.getValue().isJsonArray()) {
					lines.add(entry.getValue().getAsJsonArray().get(0).getAsString());
				}
			}
		}
		for (Concept concept : ConceptLoader.load()) {
			lines.add(concept.ko);
			if (concept.aliases != null) {
				lines.addAll(concept.aliases);
			}
		}
		try (InputStream stream = PseudowordsTest.class.getResourceAsStream("/assets/hangulquest/data/lexicon-tags.json");
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject tags = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("tags");
			for (String id : tags.keySet()) {
				JsonElement ko = tags.getAsJsonObject(id).get("ko");
				if (ko != null && !ko.isJsonNull()) {
					lines.add(ko.getAsString());
				}
			}
		}
		for (String line : Files.readAllLines(COMMON_WORDS, StandardCharsets.UTF_8)) {
			if (!line.startsWith("#")) {
				lines.add(line);
			}
		}
		return Pseudowords.dictionary(lines);
	}

	// ------------------------------------------------------------------ the shipped list

	@Test
	void theShippedListIsThirtyVettedNonwords() throws IOException {
		List<Pseudowords.Item> items = Pseudowords.load();
		assertEquals(30, items.size());
		Set<String> dictionary = dictionary();
		assertTrue(dictionary.size() > 1500, "the dictionary is large enough to mean something: " + dictionary.size());
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < items.size(); i++) {
			Pseudowords.Item item = items.get(i);
			assertEquals("pseudo." + (i + 1), item.id());
			assertTrue(seen.add(item.ko()), "duplicate " + item.ko());
			assertTrue(Pseudowords.wellFormed(item.ko()), item.ko() + " is not spelled like a Korean word");
			assertFalse(Pseudowords.isReal(item.ko(), dictionary), item.ko() + " is a real word");
		}
	}

	@Test
	void theDictionaryHoldsTheGamesWordsAndTheCommonOnes() throws IOException {
		Set<String> dictionary = dictionary();
		assertTrue(dictionary.contains("좀비"), "a vanilla name from names.json");
		assertTrue(dictionary.contains("돌"), "a concept");
		assertTrue(dictionary.contains("사과"), "a common word");
		assertTrue(dictionary.contains("먹다"), "a common verb");
		assertTrue(Pseudowords.isReal("좀비를", dictionary));
		assertTrue(Pseudowords.isReal("먹다", dictionary));
	}

	@Test
	void pseudowordsAreNotAmongTheConceptsOrTheirKoreanNames() throws IOException {
		Set<String> concepts = new HashSet<>();
		for (Concept concept : ConceptLoader.load()) {
			concepts.add(concept.ko);
		}
		for (Pseudowords.Item item : Pseudowords.load()) {
			assertFalse(concepts.contains(item.ko()), item.ko());
		}
	}

	// ------------------------------------------------------------------ the rules

	@Test
	void theVettingRejectsRealWordsAndWordsBuiltFromThem() {
		Set<String> dictionary = Set.of("사과", "나무", "먹", "하늘");
		assertTrue(Pseudowords.isReal("사과", dictionary), "the word itself");
		assertTrue(Pseudowords.isReal("사과를", dictionary), "with a particle");
		assertTrue(Pseudowords.isReal("나무가", dictionary));
		assertTrue(Pseudowords.isReal("먹다", dictionary), "stem + 다");
		assertTrue(Pseudowords.isReal("나무하늘", dictionary), "two real words stuck together");
		assertTrue(Pseudowords.isReal("사과하다", dictionary), "noun + 하다 form of the dictionary word");
		assertTrue(Pseudowords.isReal("", dictionary));
		assertFalse(Pseudowords.isReal("바둠", dictionary), "a made-up word is fine");
		assertEquals(List.of("바둠", "소빔"), Pseudowords.vet(List.of("사과", "바둠", "나무가", "소빔", "바둠"), dictionary),
				"real words dropped, duplicates removed, order kept");
	}

	@Test
	void theVettingRejectsBadSpelling() {
		Set<String> none = Set.of();
		assertFalse(Pseudowords.wellFormed("라바"), "no word-initial ㄹ");
		assertFalse(Pseudowords.wellFormed("학문"), "학문 is pronounced 항문");
		assertFalse(Pseudowords.wellFormed("학교"), "tensing after ㄱ");
		assertFalse(Pseudowords.wellFormed("한국어"), "liaison into ㅇ");
		assertFalse(Pseudowords.wellFormed("가"), "too short");
		assertFalse(Pseudowords.wellFormed("가나다라"), "too long");
		assertFalse(Pseudowords.wellFormed("ab"), "not Hangul");
		assertFalse(Pseudowords.wellFormed("ㄱㄴ"), "loose jamo");
		assertFalse(Pseudowords.wellFormed("닭이"), "complex coda");
		assertTrue(Pseudowords.wellFormed("바둠"));
		assertTrue(Pseudowords.wellFormed("모다빈"));
		assertEquals(List.of(), Pseudowords.vet(List.of("라바", "학문", "ab"), none));
	}

	@Test
	void theDictionaryIsBuiltFromWordsAndIgnoresPunctuation() {
		Set<String> words = Pseudowords.dictionary(List.of("좀비 (Zombie)", "돌/흙, 나무", "abc"));
		assertEquals(Set.of("좀비", "돌", "흙", "나무"), words);
	}

	@Test
	void theFileTheGeneratorReadsIsThere() throws IOException {
		// the generator and this test share tools/research/common_words_ko.txt; keep it big enough to matter
		List<String> lines = Files.readAllLines(COMMON_WORDS, StandardCharsets.UTF_8);
		long words = lines.stream().filter(line -> !line.startsWith("#")).flatMap(line -> java.util.Arrays.stream(line.split("\\s+")))
				.filter(token -> !token.isBlank()).count();
		assertTrue(words > 800, "the common-word list has " + words + " words");
	}
}
