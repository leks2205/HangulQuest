package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndingsTest {
	/** The generator prefixes that belong to verbs and adjectives; the rest are noun-side. */
	private static final Set<String> VERB_SIDE = Set.of("ending:", "connective:", "nominal:", "adnominal:");

	/** What every verb-side generator makes of 먹다 (ending:ieyo takes a noun, so 횃불). */
	private static final Map<String, String> FOR_EAT = Map.ofEntries(
			Map.entry("ending:eoyo", "먹어요"),
			Map.entry("ending:past", "먹었어요"),
			Map.entry("ending:seyo", "먹으세요"),
			Map.entry("ending:future", "먹을 거예요"),
			Map.entry("ending:go_sipeo", "먹고 싶어요"),
			Map.entry("ending:su_itda", "먹을 수 있어요"),
			Map.entry("ending:jima_seyo", "먹지 마세요"),
			Map.entry("ending:ji_anayo", "먹지 않아요"),
			Map.entry("ending:eoya_haeyo", "먹어야 해요"),
			Map.entry("ending:eulgeyo", "먹을게요"),
			Map.entry("ending:myeon_an_doeyo", "먹으면 안 돼요"),
			Map.entry("ending:hapsyo", "먹습니다"),
			Map.entry("ending:banmal", "먹어"),
			Map.entry("ending:handa", "먹는다"),
			Map.entry("connective:go", "먹고"),
			Map.entry("connective:go_manner", "먹고"),
			Map.entry("connective:myeon", "먹으면"),
			Map.entry("connective:jiman", "먹지만"),
			Map.entry("connective:eoseo", "먹어서"),
			Map.entry("connective:eureo", "먹으러"),
			Map.entry("connective:eodo", "먹어도"),
			Map.entry("connective:neunde", "먹는데"),
			Map.entry("connective:eunikka", "먹으니까"),
			Map.entry("connective:eulsurok", "먹을수록"),
			Map.entry("connective:deusi", "먹듯이"),
			Map.entry("nominal:eun_hue", "먹은 후에"),
			Map.entry("nominal:gi_jeone", "먹기 전에"),
			Map.entry("nominal:gi_maryeon", "먹기 마련이에요"),
			Map.entry("nominal:neun_dongan", "먹는 동안"),
			Map.entry("nominal:su_bakke", "먹을 수밖에 없어요"),
			Map.entry("adnominal:past", "먹은"),
			Map.entry("adnominal:present", "먹는"),
			Map.entry("adnominal:future", "먹을"),
			Map.entry("adnominal:adjective", "먹은"));

	private static Set<String> grammarGenerators() {
		Set<String> names = new LinkedHashSet<>();
		try (InputStream stream = EndingsTest.class.getResourceAsStream("/assets/hangulquest/data/grammar.json");
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject()
					.getAsJsonArray("constructions")) {
				JsonObject construction = element.getAsJsonObject();
				if (construction.has("generator")) {
					names.add(construction.get("generator").getAsString());
				}
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
		return names;
	}

	private static boolean verbSide(String generator) {
		return VERB_SIDE.stream().anyMatch(generator::startsWith);
	}

	@Test
	void everyVerbSideGeneratorOfGrammarJsonResolves() {
		Set<String> verbSide = new TreeSet<>();
		for (String generator : grammarGenerators()) {
			if (verbSide(generator)) {
				verbSide.add(generator);
			}
		}
		assertTrue(verbSide.size() >= 30, "found only " + verbSide);
		for (String generator : verbSide) {
			assertTrue(Endings.has(generator), generator + " has no entry in Endings");
			assertTrue(Endings.apply(generator, generator.equals("ending:ieyo") ? "횃불" : "먹다").length() > 1,
					generator);
		}
	}

	@Test
	void everyEndingsEntryIsUsedByGrammarJson() {
		Set<String> inGrammar = grammarGenerators();
		for (String generator : Endings.generators()) {
			assertTrue(inGrammar.contains(generator), generator + " is in Endings but not in grammar.json");
		}
	}

	@Test
	void eachGeneratorMakesTheRightFormOfEat() {
		for (String generator : grammarGenerators()) {
			if (!verbSide(generator) || generator.equals("ending:ieyo")) {
				continue;
			}
			assertEquals(FOR_EAT.get(generator), Endings.apply(generator, "먹다"), generator);
		}
		assertEquals("횃불이에요", Endings.apply("ending:ieyo", "횃불"));
		assertEquals("나무예요", Endings.apply("ending:ieyo", "나무"));
	}

	@Test
	void generatorsFollowIrregularVerbsAndAdjectives() {
		assertEquals("만드세요", Endings.apply("ending:seyo", "만들다"));
		assertEquals("걸을 수 있어요", Endings.apply("ending:su_itda", "걷다"));
		assertEquals("주워서", Endings.apply("connective:eoseo", "줍다"));
		assertEquals("어두운데", Endings.apply("connective:neunde", "어둡다"));
		assertEquals("큰", Endings.apply("adnominal:adjective", "크다"));
	}

	@Test
	void anUnknownGeneratorIsRejected() {
		assertEquals(false, Endings.has("ending:nope"));
		assertThrows(IllegalArgumentException.class, () -> Endings.apply("ending:nope", "먹다"));
	}
}
