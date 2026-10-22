package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the English glosses of the curated vocabulary. Commit 90ec5d3 copied each gloss
 * from the English name of the concept's icon item, so 고양이 read "Raw Cod".
 */
class ConceptGlossTest {
	/** English item names for every id the concepts reference (tools/fix_glosses.py --names-from). */
	private static final Path NAMES_EN = Path.of("tools", "data", "names_en_26.3.json");

	/**
	 * Concepts whose gloss equals the English name of one of their items although the Korean
	 * word is spelled differently from the vanilla Korean name. Each one is the same thing.
	 */
	private static final Map<String, String> SAME_THING = Map.of(
			"crafting_table", "same block; concept says 작업대, vanilla says 제작대",
			"note_block", "same block; 음표 블록 vs vanilla 소리 블록",
			"ink_sac", "same item; spelling differs from vanilla 먹물 주머니",
			"cocoa_beans", "same item; 카카오 콩 vs vanilla 코코아 콩",
			"turtle_egg", "same item; spacing differs (거북 알 vs 거북알)",
			"honey_bottle", "same item; 꿀병 is the short form of vanilla 꿀이 든 병",
			"bone", "same item; 뼈 vs vanilla 뼈다귀",
			"jungle_log", "same block; 정글 나무 vs vanilla 정글나무 원목",
			"adv_item_tnt", "same block; the concept transliterates TNT and vanilla has no Korean entry");

	/** Concepts that may share a gloss inside a category. */
	private static final Map<String, String> SHARED_GLOSS = Map.of(
			"adv_item_silence_armor_trim_smithing_template",
			"two armour-trim templates; vanilla gives both the same Korean and English name",
			"adv_item_dune_armor_trim_smithing_template",
			"two armour-trim templates; vanilla gives both the same Korean and English name");

	private static List<Concept> curated() {
		List<Concept> result = new ArrayList<>();
		for (Concept concept : ConceptLoader.load()) {
			if (!concept.id.startsWith("gen_")) {
				result.add(concept);
			}
		}
		return result;
	}

	private static Map<String, String> englishItemNames() throws IOException {
		Map<String, String> names = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(NAMES_EN, StandardCharsets.UTF_8)) {
			JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
			object.entrySet().forEach(entry -> names.put(entry.getKey(), entry.getValue().getAsString()));
		}
		return names;
	}

	/** Vanilla Korean item name from the bundled language table, or null. */
	private static String vanillaKorean(JsonObject table, String itemId) {
		String path = itemId.substring(itemId.indexOf(':') + 1);
		for (String kind : new String[] {"item", "block"}) {
			var entry = table.get(kind + ".minecraft." + path);
			if (entry != null) {
				return entry.getAsJsonArray().get(0).getAsString();
			}
		}
		return null;
	}

	private static JsonObject koreanTable() throws IOException {
		try (InputStream stream = ConceptGlossTest.class.getResourceAsStream("/assets/hangulquest/data/names.json")) {
			return JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void curatedConceptsAreStillTheFullSet() {
		assertEquals(451, curated().size(), "the guard below must cover every curated concept");
	}

	@Test
	void glossIsNeverTheNameOfAnItemTheWordMerelyIllustrates() throws IOException {
		Map<String, String> names = englishItemNames();
		JsonObject korean = koreanTable();
		List<String> problems = new ArrayList<>();
		for (Concept concept : curated()) {
			List<String> shown = new ArrayList<>(concept.items);
			if (concept.icon != null) {
				shown.add(concept.icon);
			}
			for (String item : shown) {
				String name = names.get(item);
				if (name == null || !name.equalsIgnoreCase(concept.en)) {
					continue;
				}
				// The gloss reads like the item. That is only right if the word is that item.
				String vanilla = vanillaKorean(korean, item);
				if (concept.ko.equals(vanilla) || SAME_THING.containsKey(concept.id)) {
					continue;
				}
				problems.add(concept.id + " (" + concept.ko + "): gloss '" + concept.en + "' is the name of "
						+ item + " (vanilla Korean: " + vanilla + ")");
			}
		}
		assertTrue(problems.isEmpty(), "glosses copied from an icon item:\n" + String.join("\n", problems));
	}

	@Test
	void sameThingWhitelistIsNotStale() throws IOException {
		Map<String, String> names = englishItemNames();
		Map<String, Concept> byId = new LinkedHashMap<>();
		for (Concept concept : curated()) {
			byId.put(concept.id, concept);
		}
		for (String id : SAME_THING.keySet()) {
			Concept concept = byId.get(id);
			assertTrue(concept != null, "whitelisted concept is gone: " + id);
			boolean matches = concept.items.stream().anyMatch(item -> concept.en.equalsIgnoreCase(names.get(item)));
			assertTrue(matches, id + " is whitelisted but its gloss no longer equals an item name");
		}
	}

	@Test
	void noTwoCuratedConceptsInACategoryShareAGloss() {
		Map<String, List<String>> seen = new LinkedHashMap<>();
		for (Concept concept : curated()) {
			assertTrue(concept.en != null && !concept.en.isBlank(), concept.id + " has no English gloss");
			seen.computeIfAbsent(concept.category + "/" + concept.en.trim().toLowerCase(), key -> new ArrayList<>())
					.add(concept.id);
		}
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : seen.entrySet()) {
			if (entry.getValue().size() > 1 && !SHARED_GLOSS.keySet().containsAll(entry.getValue())) {
				problems.add(entry.getKey() + " -> " + entry.getValue());
			}
		}
		assertTrue(problems.isEmpty(), "identical glosses make quiz options ambiguous:\n" + String.join("\n", problems));
	}

	@Test
	void formerlyWrongGlossesAreFixed() {
		Map<String, String> expected = Map.of(
				"cat", "cat", "horse", "horse", "wolf", "wolf", "villager", "villager",
				"eat", "to eat", "mine", "to mine", "mountain", "mountain", "night", "night",
				"ocean", "ocean", "rain", "rain");
		Map<String, Concept> byId = new HashMap<>();
		for (Concept concept : curated()) {
			byId.put(concept.id, concept);
		}
		expected.forEach((id, gloss) -> assertEquals(gloss, byId.get(id).en, id));
	}

	@Test
	void blankEnglishIsMarkedUntranslatedNotFilledWithGerman() {
		Concept concept = new Concept();
		concept.id = "probe";
		concept.ko = "시험";
		concept.de = "Probe";
		concept.en = " ";
		Word word = concept.toWord();
		assertTrue(word.untranslated);
		assertNotEquals(concept.de, word.en, "German must not pass as the English gloss");
		assertTrue(word.en.startsWith(Word.UNTRANSLATED_PREFIX), word.en);

		concept.en = "test";
		Word translated = concept.toWord();
		assertFalse(translated.untranslated);
		assertEquals("test", translated.en);

		concept.en = null;
		assertTrue(concept.toWord().untranslated);
	}

	@Test
	void everyShippedConceptWordIsTranslated() {
		for (Concept concept : ConceptLoader.load()) {
			assertFalse(concept.toWord().untranslated, concept.id + " has no English gloss");
		}
	}
}
