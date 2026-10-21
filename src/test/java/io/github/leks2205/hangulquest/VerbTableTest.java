package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** data/verbs.json must be self-consistent: real verbs, real families, a default for every goal. */
class VerbTableTest {
	private static JsonObject raw() {
		try (InputStream stream = VerbTableTest.class.getResourceAsStream(VerbTable.RESOURCE);
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	@Test
	void everyRuleNamesARealVerbAndAnExistingFamily() {
		JsonObject root = raw();
		Set<String> families = root.getAsJsonObject("families").keySet();
		Set<String> used = new HashSet<>();
		for (Map.Entry<String, JsonElement> goal : root.getAsJsonObject("rules").entrySet()) {
			JsonArray rules = goal.getValue().getAsJsonArray();
			assertTrue(rules.size() > 0, goal.getKey() + " has no rules");
			for (JsonElement element : rules) {
				JsonObject rule = element.getAsJsonObject();
				assertTrue(Verb.byId(rule.get("verb").getAsString(), null) != null,
						goal.getKey() + ": unknown verb " + rule.get("verb"));
				if (rule.has("family")) {
					assertTrue(families.contains(rule.get("family").getAsString()),
							goal.getKey() + ": unknown family " + rule.get("family"));
					used.add(rule.get("family").getAsString());
				}
			}
			JsonObject last = rules.get(rules.size() - 1).getAsJsonObject();
			assertFalse(last.has("family") || last.has("minCount") || last.has("maxCount"),
					goal.getKey() + " must end with a default rule");
		}
		// families only used for reading the table elsewhere are fine, but dead ones are clutter
		Set<String> dead = new HashSet<>(families);
		dead.removeAll(used);
		assertTrue(dead.isEmpty(), "families that no rule uses: " + dead);
	}

	@Test
	void everyGoalTypeHasRules() {
		JsonObject rules = raw().getAsJsonObject("rules");
		for (String type : new String[]{"BREAK_BLOCK", "PLACE_BLOCK", "ACQUIRE_ITEM", "CONSUME_ITEM",
				"LOOK_AT_BLOCK", "LOOK_AT_ENTITY", "DEFEAT_ENTITY", "REACH_BIOME", "REACH_DIMENSION",
				"USE_BLOCK", "INTERACT_ENTITY"}) {
			assertTrue(rules.has(type), "no rules for " + type);
		}
	}

	@Test
	void patternsMatchExactSuffixPrefixAndInfix() {
		VerbTable table = VerbTable.shared();
		assertTrue(table.inFamily("logs", "minecraft:oak_log"));
		assertTrue(table.inFamily("logs", "minecraft:crimson_stem"));
		assertFalse(table.inFamily("logs", "minecraft:oak_planks"));
		assertTrue(table.inFamily("ores", "minecraft:deepslate_iron_ore"));
		assertTrue(table.inFamily("ores", "minecraft:ancient_debris"));
		assertFalse(table.inFamily("ores", "minecraft:iron_block"));
		assertTrue(table.inFamily("uprootables", "minecraft:cave_vines_plant"));
		assertTrue(table.inFamily("carpets_and_rails", "minecraft:moss_carpet"));
		assertFalse(table.inFamily("flowers", "minecraft:red_mushroom"));
		assertFalse(table.inFamily("no_such_family", "minecraft:stone"));
		// registry ids and bare paths behave the same
		assertTrue(table.inFamily("logs", "oak_log"));
	}

	@Test
	void theFirstMatchingRuleWinsAndCountWindowsApply() {
		VerbTable table = VerbTable.shared();
		assertEquals(Verb.OBTAIN, table.choose("ACQUIRE_ITEM", "minecraft:emerald", 1, Verb.COLLECT));
		assertEquals(Verb.COLLECT, table.choose("ACQUIRE_ITEM", "minecraft:emerald", 2, Verb.OBTAIN));
		assertEquals(Verb.CRAFT, table.choose("ACQUIRE_ITEM", "minecraft:iron_pickaxe", 1, Verb.COLLECT));
		assertEquals(Verb.STACK, table.choose("PLACE_BLOCK", "minecraft:stone_bricks", 3, Verb.PLACE));
		assertEquals(Verb.PLACE, table.choose("PLACE_BLOCK", "minecraft:stone_bricks", 1, Verb.STACK));
		assertEquals(Verb.PLANT, table.choose("PLACE_BLOCK", "minecraft:oak_sapling", 1, Verb.PLACE));
	}

	@Test
	void everyShippedGoalGetsAVerbThatFitsItsObject() {
		// The mistakes this table exists to prevent.
		VerbTable table = VerbTable.shared();
		for (String carpet : new String[]{"minecraft:red_carpet", "minecraft:moss_carpet"}) {
			assertTrue(table.choose("PLACE_BLOCK", carpet, 1, Verb.PLACE) != Verb.PLACE, carpet);
		}
		assertEquals(Verb.PLANT, table.choose("PLACE_BLOCK", "minecraft:cherry_sapling", 1, Verb.PLACE));
		assertEquals(Verb.HANG, table.choose("PLACE_BLOCK", "minecraft:black_banner", 1, Verb.PLACE));
		assertEquals(Verb.HANG, table.choose("PLACE_BLOCK", "minecraft:painting", 1, Verb.PLACE));
		assertEquals(Verb.HANG, table.choose("PLACE_BLOCK", "minecraft:item_frame", 1, Verb.PLACE));
		for (String notMineral : new String[]{"minecraft:red_carpet", "minecraft:snow_block", "minecraft:oak_leaves",
				"minecraft:poppy", "minecraft:oak_log", "minecraft:glass", "minecraft:gravel"}) {
			assertTrue(table.choose("BREAK_BLOCK", notMineral, 1, Verb.MINE) != Verb.MINE, notMineral);
		}
		for (String mineral : new String[]{"minecraft:coal_ore", "minecraft:deepslate_diamond_ore",
				"minecraft:stone", "minecraft:obsidian", "minecraft:ancient_debris"}) {
			assertEquals(Verb.MINE, table.choose("BREAK_BLOCK", mineral, 1, Verb.BREAK), mineral);
		}
		for (String animal : new String[]{"minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:frog",
				"minecraft:panda", "minecraft:parrot"}) {
			assertTrue(table.choose("INTERACT_ENTITY", animal, 1, Verb.TALK) != Verb.TALK, animal);
		}
	}

	@Test
	void aBrokenTableNeverThrowsAtRuntime() {
		VerbTable empty = VerbTable.empty();
		assertEquals(Verb.PLACE, empty.choose("PLACE_BLOCK", "minecraft:stone", 1, Verb.PLACE));
		JsonObject unknown = JsonParser.parseString(
				"{\"rules\":{\"PLACE_BLOCK\":[{\"verb\":\"NOT_A_VERB\"}]}}").getAsJsonObject();
		assertThrows(IllegalArgumentException.class, () -> VerbTable.parse(unknown));
	}
}
