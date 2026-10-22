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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * data/grammar.json is the ladder: every construction has a place, its prerequisites exist, and
 * no example or cloze uses a form from a higher level than its own.
 */
class GrammarDataTest {
	private static final String RESOURCE = "/assets/hangulquest/data/grammar.json";

	/** Goal types (first part of a hook, before ':') that Phase T and the existing goals provide. */
	private static final Set<String> GOAL_TYPES = Set.of("PLACE_REL", "BUILD_PATTERN", "CRAFT", "SMELT", "GIVE", "FEED",
			"HOLD_WHILE", "SEQUENCE", "AVOID", "RIDE", "BREED", "USE_ON", "USE_BLOCK", "CONSUME_ITEM", "ACQUIRE_ITEM",
			"PLACE_BLOCK", "BREAK_BLOCK", "DEFEAT", "REACH_BIOME", "PUT_IN", "LIGHT_LEVEL", "INTERACT_ENTITY", "TRADE", "NEAR_ENTITY");

	/**
	 * Surface forms that only exist from a given level on, as rough regular expressions. They catch
	 * a lower-level sentence that borrows a construction it has not met yet; the 'uses' lists are the
	 * exact record, these are the safety net.
	 */
	private static final Map<Integer, List<Pattern>> LATER_FORMS = Map.of(
			2, patterns("세요", "에서", "에게", "[가-힣]고 ", "싶어요", "사이", "[가-힣]하고 ", "[가-힣](은|는) "),
			3, patterns("[았었갔왔봤했줬]어요|[았었갔왔봤했줬]다", "[가-힣] 거예요", "기 전에", "[은ㄴ] 후에|[가-힣] 후에", "수 있|수 없",
					"지 마세요", "한테", "지 않"),
			4, patterns("야 해요", "[가-힣]면 ", "지만", "[워해가와]서 ", "러 [가오]", "[을ㄹ]게요", "면 안 돼요"),
			5, patterns("는 동안", "[아어워해]도 ", "는데|[은ㄴ]데", "니까", "니다|니까\\?"));

	private static List<Pattern> patterns(String... regex) {
		List<Pattern> list = new ArrayList<>();
		for (String r : regex) {
			list.add(Pattern.compile(r));
		}
		return list;
	}

	private static List<JsonObject> constructions() {
		try (InputStream stream = GrammarDataTest.class.getResourceAsStream(RESOURCE)) {
			assertTrue(stream != null, RESOURCE + " is missing");
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonArray all = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("constructions");
				List<JsonObject> list = new ArrayList<>();
				for (JsonElement element : all) {
					list.add(element.getAsJsonObject());
				}
				return list;
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	private static Map<String, JsonObject> byId() {
		Map<String, JsonObject> map = new HashMap<>();
		for (JsonObject c : constructions()) {
			assertTrue(map.put(c.get("id").getAsString(), c) == null, "duplicate id " + c.get("id"));
		}
		return map;
	}

	private static List<String> strings(JsonArray array) {
		List<String> list = new ArrayList<>();
		for (JsonElement element : array) {
			list.add(element.getAsString());
		}
		return list;
	}

	private static int level(JsonObject c) {
		return c.get("level").getAsInt();
	}

	@Test
	void theLadderHasEveryLevelAndAboutSixtyConstructions() {
		List<JsonObject> all = constructions();
		assertTrue(all.size() >= 45 && all.size() <= 70, "constructions: " + all.size());
		Set<Integer> levels = new HashSet<>();
		for (JsonObject c : all) {
			levels.add(level(c));
		}
		assertEquals(Set.of(1, 2, 3, 4, 5, 6), levels);
		Map<String, JsonObject> ids = byId();
		for (String needed : new String[]{"L1.obj_eul", "L1.subj_i", "L1.eoyo", "L1.loc_wi_e", "L1.loc_arae_e", "L1.loc_yeop_e",
				"L1.loc_ap_e", "L1.loc_dwi_e", "L1.loc_an_e", "L1.loc_bak_e", "L1.num_native", "L1.neg_an", "L1.itda_eopda",
				"L2.seyo", "L2.eseo_activity", "L2.loc_sai_e", "L2.ege", "L2.euro_material", "L2.euro_direction",
				"L2.go_sequence", "L2.go_held", "L2.eun_neun_topic", "L2.go_sipeoyo", "L3.past", "L3.future_geoyeyo",
				"L3.gi_jeone", "L3.eun_hue", "L3.su_itda", "L3.jima_seyo", "L3.euro_instrument", "L4.eoya_haeyo",
				"L4.myeon", "L4.jiman", "L4.eoseo", "L4.eureo_gayo", "L4.eulgeyo", "L5.adn_past_eun", "L5.adn_present_neun",
				"L5.adn_future_eul", "L5.neun_dongan", "L5.eodo", "L5.neunde", "L5.eunikka", "L5.hapsyo", "L6.eulsurok",
				"L6.deusi", "L6.gi_maryeon_ida", "L6.su_bakke_eopda", "L6.banmal"}) {
			assertTrue(ids.containsKey(needed), "the roadmap ladder needs " + needed);
		}
	}

	@Test
	void idsMatchTheirLevelAndPrerequisitesExistAndNeverPointUpward() {
		Map<String, JsonObject> ids = byId();
		for (JsonObject c : ids.values()) {
			String id = c.get("id").getAsString();
			assertTrue(id.startsWith("L" + level(c) + "."), id + " does not match level " + level(c));
			assertFalse(c.get("form").getAsString().isBlank(), id + " has no form");
			assertFalse(c.get("generator").getAsString().isBlank(), id + " has no generator");
			int grade = c.get("curriculumGrade").getAsInt();
			assertTrue(grade >= 1 && grade <= 6, id + ": grade " + grade);
			for (String prerequisite : strings(c.getAsJsonArray("prereq"))) {
				assertTrue(ids.containsKey(prerequisite), id + ": unknown prerequisite " + prerequisite);
				assertTrue(level(ids.get(prerequisite)) <= level(c), id + ": prerequisite " + prerequisite + " is higher");
				assertFalse(prerequisite.equals(id), id + " requires itself");
			}
		}
		// no cycles: walking the prerequisites must always end
		for (String id : ids.keySet()) {
			assertAcyclic(id, ids, new HashSet<>());
		}
	}

	private static void assertAcyclic(String id, Map<String, JsonObject> ids, Set<String> path) {
		assertTrue(path.add(id), "prerequisite cycle through " + id);
		for (String prerequisite : strings(ids.get(id).getAsJsonArray("prereq"))) {
			assertAcyclic(prerequisite, ids, path);
		}
		path.remove(id);
	}

	@Test
	void ruleCardsAreTwoShortLinesInKoreanEnglishAndGerman() {
		for (JsonObject c : constructions()) {
			JsonObject card = c.getAsJsonObject("ruleCard");
			for (String language : new String[]{"ko", "en", "de"}) {
				List<String> lines = strings(card.getAsJsonArray(language));
				assertEquals(2, lines.size(), c.get("id") + " ruleCard." + language);
				for (String line : lines) {
					assertFalse(line.isBlank(), c.get("id") + " ruleCard." + language);
					assertTrue(line.length() <= 170, c.get("id") + " ruleCard." + language + " is too long: " + line);
				}
			}
			for (String sentence : strings(card.getAsJsonArray("ko"))) {
				assertTrue(sentence.matches("[가-힣 ,.?!]+"), c.get("id") + ": " + sentence);
			}
		}
	}

	@Test
	void examplesAndClozesOnlyUseConstructionsAtOrBelowTheirLevel() {
		Map<String, JsonObject> ids = byId();
		for (JsonObject c : ids.values()) {
			String id = c.get("id").getAsString();
			JsonArray examples = c.getAsJsonArray("examples");
			assertTrue(examples.size() >= 3 && examples.size() <= 5, id + ": " + examples.size() + " examples");
			for (JsonElement element : examples) {
				JsonObject example = element.getAsJsonObject();
				assertFalse(example.get("en").getAsString().isBlank(), id);
				List<String> uses = strings(example.getAsJsonArray("uses"));
				assertTrue(uses.contains(id), id + ": example '" + example.get("ko") + "' does not list its own construction");
				checkUses(id, level(c), example.get("ko").getAsString(), uses, ids);
			}
			JsonArray clozes = c.getAsJsonArray("clozeTemplates");
			assertTrue(clozes.size() >= 2 && clozes.size() <= 5, id + ": " + clozes.size() + " clozes");
			for (JsonElement element : clozes) {
				JsonObject cloze = element.getAsJsonObject();
				List<String> uses = strings(cloze.getAsJsonArray("uses"));
				assertTrue(uses.contains(id), id + ": cloze '" + cloze.get("sentence") + "' does not list its own construction");
				checkUses(id, level(c), cloze.get("sentence").getAsString(), uses, ids);
			}
		}
	}

	private static void checkUses(String id, int level, String sentence, List<String> uses, Map<String, JsonObject> ids) {
		for (String used : uses) {
			assertTrue(ids.containsKey(used), id + ": '" + sentence + "' uses unknown " + used);
			assertTrue(level(ids.get(used)) <= level, id + ": '" + sentence + "' uses " + used + ", which is a later level");
		}
	}

	@Test
	void noSentenceBorrowsAFormFromALaterLevel() {
		for (JsonObject c : constructions()) {
			String id = c.get("id").getAsString();
			int level = level(c);
			List<String> texts = new ArrayList<>(strings(c.getAsJsonObject("ruleCard").getAsJsonArray("ko")));
			for (JsonElement example : c.getAsJsonArray("examples")) {
				texts.add(example.getAsJsonObject().get("ko").getAsString());
			}
			for (JsonElement element : c.getAsJsonArray("clozeTemplates")) {
				JsonObject cloze = element.getAsJsonObject();
				String sentence = cloze.get("sentence").getAsString();
				texts.add(sentence.replace("___", cloze.get("answer").getAsString()));
				for (String distractor : strings(cloze.getAsJsonArray("distractors"))) {
					texts.add(sentence.replace("___", distractor));
				}
			}
			for (String text : texts) {
				for (Map.Entry<Integer, List<Pattern>> later : LATER_FORMS.entrySet()) {
					if (later.getKey() <= level) {
						continue;
					}
					for (Pattern pattern : later.getValue()) {
						assertFalse(pattern.matcher(text + " ").find(),
								id + " (L" + level + "): '" + text + "' contains a level " + later.getKey() + " form " + pattern);
					}
				}
			}
		}
	}

	@Test
	void clozeTemplatesHaveOneBlankTwoWrongAnswersAndAGloss() {
		for (JsonObject c : constructions()) {
			String id = c.get("id").getAsString();
			for (JsonElement element : c.getAsJsonArray("clozeTemplates")) {
				JsonObject cloze = element.getAsJsonObject();
				String sentence = cloze.get("sentence").getAsString();
				assertEquals(sentence.indexOf("___"), sentence.lastIndexOf("___"), id + ": two blanks in " + sentence);
				assertTrue(sentence.contains("___"), id + ": no blank in " + sentence);
				String answer = cloze.get("answer").getAsString();
				List<String> wrong = strings(cloze.getAsJsonArray("distractors"));
				assertEquals(2, wrong.size(), id + ": " + sentence);
				assertEquals(2, new HashSet<>(wrong).size(), id + ": repeated distractor in " + sentence);
				assertFalse(wrong.contains(answer), id + ": the answer is also a distractor in " + sentence);
				if (cloze.has("alsoAccept")) {
					for (String accepted : strings(cloze.getAsJsonArray("alsoAccept"))) {
						assertFalse(wrong.contains(accepted), id + ": '" + accepted + "' is accepted and a distractor in " + sentence);
						assertFalse(accepted.equals(answer), id + ": " + sentence);
					}
				}
				assertFalse(cloze.get("gloss").getAsString().isBlank(), id + ": " + sentence + " has no gloss");
			}
		}
	}

	@Test
	void everyFormDependentHookNamesARealGoalType() {
		for (JsonObject c : constructions()) {
			for (String hook : strings(c.getAsJsonArray("formDependentHooks"))) {
				String type = hook.contains(":") ? hook.substring(0, hook.indexOf(':')) : hook;
				assertTrue(GOAL_TYPES.contains(type), c.get("id") + ": unknown goal type in hook " + hook);
			}
			assertTrue(c.getAsJsonArray("teachingNotes").size() >= 2, c.get("id") + " needs teaching notes");
		}
	}

	@Test
	void everyLocationNounHasAPlaceRelationHook() {
		Map<String, String> relation = Map.of(
				"L1.loc_wi_e", "PLACE_REL:ON_TOP_OF", "L1.loc_arae_e", "PLACE_REL:UNDER", "L1.loc_yeop_e", "PLACE_REL:NEXT_TO",
				"L1.loc_ap_e", "PLACE_REL:IN_FRONT_OF", "L1.loc_dwi_e", "PLACE_REL:BEHIND", "L1.loc_an_e", "PLACE_REL:INSIDE",
				"L1.loc_bak_e", "PLACE_REL:OUTSIDE", "L2.loc_sai_e", "PLACE_REL:BETWEEN");
		Map<String, JsonObject> ids = byId();
		for (Map.Entry<String, String> entry : relation.entrySet()) {
			assertTrue(strings(ids.get(entry.getKey()).getAsJsonArray("formDependentHooks")).contains(entry.getValue()),
					entry.getKey() + " should hook " + entry.getValue());
		}
	}
}
