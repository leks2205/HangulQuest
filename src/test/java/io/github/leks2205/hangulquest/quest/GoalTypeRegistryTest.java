package io.github.leks2205.hangulquest.quest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.condition.ConditionType;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goal and condition registries. Adding a goal type means a record in {@link Goal}, one
 * {@link GoalType} class and one line in {@link GoalTypes}; these tests fail when the line is missing
 * or the schema in docs/ is stale.
 */
class GoalTypeRegistryTest {
	private static final Path SCHEMA = Path.of("docs", "questline.schema.json");

	@Test
	void everyGoalRecordHasExactlyOneType() {
		Set<Class<?>> records = new HashSet<>(List.of(Goal.class.getPermittedSubclasses()));
		Set<Class<?>> registered = GoalTypes.all().stream().map(GoalType::goalClass).collect(Collectors.toSet());
		assertEquals(records, registered, "a Goal record without a GoalTypes line (or the other way round)");
		assertEquals(records.size(), GoalTypes.all().size(), "two types for one record");
	}

	@Test
	void everyConditionRecordHasExactlyOneType() {
		Set<Class<?>> records = new HashSet<>(List.of(Condition.class.getPermittedSubclasses()));
		Set<Class<?>> registered = ConditionTypes.all().stream().map(ConditionType::conditionClass)
				.collect(Collectors.toSet());
		assertEquals(records, registered, "a Condition record without a ConditionTypes line");
		assertEquals(records.size(), ConditionTypes.all().size());
	}

	@Test
	void idsAreUniqueUpperSnakeCase() {
		Set<String> ids = new HashSet<>();
		for (GoalType<?> type : GoalTypes.all()) {
			assertTrue(type.id().matches("[A-Z]+(_[A-Z]+)*"), type.id());
			assertTrue(ids.add(type.id()), "duplicate " + type.id());
		}
		for (ConditionType<?> type : ConditionTypes.all()) {
			assertTrue(type.id().matches("[A-Z]+(_[A-Z]+)*"), type.id());
			assertTrue(ids.add("condition " + type.id()), "duplicate " + type.id());
		}
	}

	@Test
	void lookupByIdIsCaseInsensitiveAndUnknownIsNull() {
		assertEquals("BREAK_BLOCK", GoalTypes.byId("break_block").id());
		assertNull(GoalTypes.byId("FLY"));
		assertNull(GoalTypes.byId(null));
		assertEquals("IN_BIOME", ConditionTypes.byId("in_biome").id());
		assertNull(ConditionTypes.byId("FLY"));
	}

	@Test
	void goalsSurviveAJsonRoundTrip() {
		for (GoalType<?> type : GoalTypes.all()) {
			JsonObject sparse = new JsonObject();
			sparse.addProperty("type", type.id());
			Goal parsed = GoalTypes.parse(sparse);
			assertNotNull(parsed, type.id());
			assertEquals(type.goalClass(), parsed.getClass());
			assertEquals(parsed, GoalTypes.parse(GoalTypes.toJson(parsed)), type.id());
		}
		Goal withVerb = new Goal.BreakBlock("minecraft:oak_log", 3, "CHOP");
		assertEquals(withVerb, GoalTypes.parse(GoalTypes.toJson(withVerb)));
	}

	@Test
	void conditionsSurviveAJsonRoundTrip() {
		for (ConditionType<?> type : ConditionTypes.all()) {
			JsonObject sparse = new JsonObject();
			sparse.addProperty("type", type.id());
			Condition parsed = ConditionTypes.parse(sparse);
			assertNotNull(parsed, type.id());
			assertEquals(parsed, ConditionTypes.parse(ConditionTypes.toJson(parsed)), type.id());
		}
		Condition near = new Condition.NearEntity("minecraft:cow", 7.5);
		assertEquals(near, ConditionTypes.parse(ConditionTypes.toJson(near)));
	}

	@Test
	void unknownTypesAreSkippedNotFatal() {
		JsonObject unknown = new JsonObject();
		unknown.addProperty("type", "FLY_TO_THE_MOON");
		assertNull(GoalTypes.parse(unknown));
		assertNull(ConditionTypes.parse(unknown));
		assertNull(QuestLoader.parseGoal(unknown));
	}

	@Test
	void taskSignatureNamesTypeObjectAndVerb() {
		Goal stone = new Goal.BreakBlock("minecraft:stone", 1, null);
		String signature = GoalTypes.of(stone).taskSignature(stone);
		assertTrue(signature.startsWith("BREAK_BLOCK:minecraft:stone:"), signature);

		Goal chopped = new Goal.BreakBlock("minecraft:stone", 1, "CHOP");
		assertNotEquals(signature, GoalTypes.of(chopped).taskSignature(chopped), "the verb is part of the task");
		Goal other = new Goal.BreakBlock("minecraft:dirt", 1, null);
		assertNotEquals(signature, GoalTypes.of(other).taskSignature(other));
		Goal place = new Goal.PlaceBlock("minecraft:stone", 1);
		assertNotEquals(signature, GoalTypes.of(place).taskSignature(place));

		Goal height = new Goal.ReachHeight(80, 320);
		assertEquals("REACH_HEIGHT::CLIMB:80", GoalTypes.of(height).taskSignature(height));
	}

	@Test
	void countersAndDwellComeFromTheTypes() {
		assertTrue(GoalTypes.of(new Goal.BreakBlock("a:b", 3, null)).countable());
		assertEquals(3, GoalTypes.of(new Goal.BreakBlock("a:b", 3, null)).needed(new Goal.BreakBlock("a:b", 3, null)));
		Goal look = new Goal.LookAtEntity("minecraft:cow", 1200);
		assertEquals(1200L, GoalTypes.of(look).dwellNeededMs(look));
		assertTrue(GoalTypes.of(look).needsFarEntityLook());
		Goal use = new Goal.UseBlock("minecraft:oak_door");
		assertTrue(!GoalTypes.of(use).countable());
		assertEquals(1, GoalTypes.of(use).needed(use));
	}

	/**
	 * docs/questline.schema.json lists every goal and condition exactly as the registries do. To update
	 * it after adding a type, run the tests once with HANGULQUEST_WRITE_SCHEMA=1.
	 */
	@Test
	void publishedSchemaMatchesTheRegistries() throws Exception {
		JsonObject published = JsonParser.parseString(Files.readString(SCHEMA, StandardCharsets.UTF_8))
				.getAsJsonObject();
		JsonObject generated = QuestSchema.generate(published);
		if (System.getenv("HANGULQUEST_WRITE_SCHEMA") != null) {
			String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(generated);
			Files.writeString(SCHEMA, text + "\n", StandardCharsets.UTF_8);
			return;
		}
		assertEquals(generated, published, "docs/questline.schema.json is out of date: "
				+ "run the tests with HANGULQUEST_WRITE_SCHEMA=1");
	}
}
