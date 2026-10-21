package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** data/counters.json is self-consistent and picks the counters natives expect for the common Minecraft ids. */
class CounterDataTest {
	private static final String RESOURCE = "/assets/hangulquest/data/counters.json";

	private static JsonObject raw() {
		try (InputStream stream = CounterDataTest.class.getResourceAsStream(RESOURCE)) {
			assertTrue(stream != null, RESOURCE + " is missing");
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	/** Same pattern language as verbs.json: exact, *suffix, prefix*, *part*. */
	private static boolean matches(String pattern, String path) {
		boolean head = pattern.startsWith("*");
		boolean tail = pattern.endsWith("*") && pattern.length() > 1;
		String core = pattern.substring(head ? 1 : 0, pattern.length() - (tail ? 1 : 0));
		if (head && tail) {
			return path.contains(core);
		}
		if (head) {
			return path.endsWith(core);
		}
		if (tail) {
			return path.startsWith(core);
		}
		return path.equals(core);
	}

	/** First matching rule wins; the result is null for 'do not count', or the counter. */
	private static String counterFor(String id) {
		JsonObject root = raw();
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		JsonObject families = root.getAsJsonObject("families");
		for (JsonElement element : root.getAsJsonArray("rules")) {
			JsonObject rule = element.getAsJsonObject();
			boolean hit = !rule.has("family");
			if (!hit) {
				for (JsonElement pattern : families.getAsJsonArray(rule.get("family").getAsString())) {
					if (matches(pattern.getAsString(), path)) {
						hit = true;
						break;
					}
				}
			}
			if (hit) {
				return rule.get("counter").isJsonNull() ? null : rule.get("counter").getAsString();
			}
		}
		throw new IllegalStateException("no default rule");
	}

	@Test
	void everyRuleNamesAnExistingFamilyAndAKnownCounterAndTheLastIsTheDefault() {
		JsonObject root = raw();
		Set<String> families = root.getAsJsonObject("families").keySet();
		Set<String> known = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("counters").entrySet()) {
			known.add(entry.getValue().getAsJsonObject().get("counter").getAsString());
		}
		JsonArray rules = root.getAsJsonArray("rules");
		Set<String> used = new HashSet<>();
		for (int i = 0; i < rules.size(); i++) {
			JsonObject rule = rules.get(i).getAsJsonObject();
			if (rule.has("family")) {
				assertTrue(families.contains(rule.get("family").getAsString()), "unknown family " + rule);
				used.add(rule.get("family").getAsString());
			} else {
				assertEquals(rules.size() - 1, i, "only the last rule may be the default");
				assertEquals("개", rule.get("counter").getAsString());
			}
			if (!rule.get("counter").isJsonNull()) {
				assertTrue(known.contains(rule.get("counter").getAsString()), "unknown counter in " + rule);
			}
		}
		assertTrue(rules.get(rules.size() - 1).getAsJsonObject().has("counter"));
		Set<String> dead = new HashSet<>(families);
		dead.removeAll(used);
		assertTrue(dead.isEmpty(), "families that no rule uses: " + dead);
	}

	@Test
	void everyCounterOfTheBriefIsDefinedWithAnExampleThatUsesIt() {
		JsonObject counters = raw().getAsJsonObject("counters");
		Set<String> wanted = Set.of("개", "마리", "송이", "그루", "장", "권", "병", "잔", "그릇", "자루", "벌", "켤레", "채", "명");
		Set<String> have = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : counters.entrySet()) {
			JsonObject counter = entry.getValue().getAsJsonObject();
			String word = counter.get("counter").getAsString();
			have.add(word);
			assertTrue(counter.get("example").getAsString().endsWith(word), entry.getKey() + ": the example must end in the counter");
			assertFalse(counter.get("gloss").getAsString().isBlank(), entry.getKey());
		}
		Set<String> missing = new HashSet<>(wanted);
		missing.removeAll(have);
		assertTrue(missing.isEmpty(), "missing counters: " + missing);
	}

	@Test
	void commonMinecraftIdsGetTheCounterNativesExpect() {
		assertEquals("마리", counterFor("minecraft:cow"));
		assertEquals("마리", counterFor("pig"));
		assertEquals("마리", counterFor("minecraft:zombie"));
		assertEquals("마리", counterFor("skeleton_horse"));
		assertEquals("마리", counterFor("cod"));
		assertEquals("명", counterFor("minecraft:villager"));
		assertEquals("송이", counterFor("minecraft:poppy"));
		assertEquals("그루", counterFor("minecraft:oak_sapling"));
		assertEquals("장", counterFor("minecraft:filled_map"));
		assertEquals("장", counterFor("minecraft:red_carpet"));
		assertEquals("장", counterFor("minecraft:music_disc_cat"));
		assertEquals("개", counterFor("minecraft:moss_carpet"));
		assertEquals("권", counterFor("minecraft:enchanted_book"));
		assertEquals("병", counterFor("minecraft:potion"));
		assertEquals("그릇", counterFor("minecraft:mushroom_stew"));
		assertEquals("자루", counterFor("minecraft:iron_sword"));
		assertEquals("자루", counterFor("minecraft:diamond_pickaxe"));
		assertEquals("켤레", counterFor("minecraft:iron_boots"));
		assertEquals("척", counterFor("minecraft:oak_boat"));
		assertEquals("대", counterFor("minecraft:chest_minecart"));
		// unsure means 개, or no count at all for people-like mobs
		assertEquals("개", counterFor("minecraft:torch"));
		assertEquals("개", counterFor("minecraft:stone"));
		assertEquals("개", counterFor("minecraft:iron_helmet"));
		assertEquals("개", counterFor("minecraft:glass_bottle"));
		assertNull(counterFor("minecraft:pillager"));
	}

	@Test
	void everyWordCounterIsAKnownCounter() {
		JsonObject root = raw();
		Set<String> known = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("counters").entrySet()) {
			known.add(entry.getValue().getAsJsonObject().get("counter").getAsString());
		}
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("wordCounters").entrySet()) {
			assertTrue(known.contains(entry.getValue().getAsString()), entry.getKey() + " -> " + entry.getValue());
		}
		// the counters that have no vanilla item are reachable through words
		Set<String> viaWords = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("wordCounters").entrySet()) {
			viaWords.add(entry.getValue().getAsString());
		}
		assertTrue(viaWords.containsAll(Set.of("잔", "벌", "채")));
	}

	@Test
	void nativeNumeralsRunFromOneToTwentyWithTheRightAttributiveForms() {
		JsonArray native20 = raw().getAsJsonObject("numerals").getAsJsonArray("native");
		assertEquals(20, native20.size());
		String[] attributive = {"한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한", "열두", "열세", "열네",
				"열다섯", "열여섯", "열일곱", "열여덟", "열아홉", "스무"};
		String[] word = {"하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열하나", "열둘", "열셋", "열넷",
				"열다섯", "열여섯", "열일곱", "열여덟", "열아홉", "스물"};
		for (int i = 0; i < 20; i++) {
			JsonObject entry = native20.get(i).getAsJsonObject();
			assertEquals(i + 1, entry.get("n").getAsInt());
			assertEquals(word[i], entry.get("word").getAsString(), "number " + (i + 1));
			assertEquals(attributive[i], entry.get("attributive").getAsString(), "number " + (i + 1));
		}
		assertEquals(7, raw().getAsJsonObject("numerals").getAsJsonArray("nativeTens").size());
	}

	@Test
	void secondsHeightsAndLevelsAreSinoKoreanAndCountersAreNative() {
		Map<String, String> system = new HashMap<>();
		for (JsonElement element : raw().getAsJsonArray("unitSystems")) {
			JsonObject unit = element.getAsJsonObject();
			system.put(unit.get("unit").getAsString(), unit.get("system").getAsString());
		}
		for (String sino : new String[]{"초", "분", "미터", "층", "레벨", "높이"}) {
			assertEquals("sino", system.get(sino), sino);
		}
		for (String nativeUnit : new String[]{"시", "시간", "번", "살"}) {
			assertEquals("native", system.get(nativeUnit), nativeUnit);
		}
	}
}
