package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.NearMiss;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "상자와 화로 사이에 횃불을 놓아요": a BETWEEN with two different anchors (A와/과 B 사이에). */
class BetweenTwoAnchorsTest {
	private static final String TORCH = "minecraft:torch";
	private static final String CHEST = "minecraft:chest";
	private static final String FURNACE = "minecraft:furnace";
	private static final String DOORS = "#minecraft:doors";

	private final Catalog catalog = KoreanFixtures.catalog();
	private final SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);

	private static Goal between(String first, String second) {
		return new Goal.PlaceRel(TORCH, Relation.BETWEEN, first, 1, second);
	}

	private static Observation observing(PlacementFact fact) {
		Observation obs = new Observation();
		obs.now = 50_000L;
		obs.clockTime = 1000L;
		obs.addPlacement(fact);
		return obs;
	}

	private static boolean met(Goal goal, PlacementFact fact) {
		return GoalEvaluator.tick(goal, List.of(), observing(fact), new GoalEvaluator.Tracker());
	}

	/** A torch at (100, 64, 100) with the given blocks beside it. */
	private static PlacementFact torchWith(Object... blocks) {
		World world = new World().put(100, 64, 100, TORCH);
		for (int i = 0; i < blocks.length; i += 2) {
			int[] at = (int[]) blocks[i];
			world.put(100 + at[0], 64 + at[1], 100 + at[2], (String) blocks[i + 1]);
		}
		return world.placed(new BlockPos(100, 64, 100));
	}

	// ------------------------------------------------------------------ the check

	@Test
	void theTorchHasToBeBetweenTheOneAndTheOther() {
		Goal goal = between(CHEST, FURNACE);
		assertTrue(met(goal, torchWith(new int[]{-1, 0, 0}, CHEST, new int[]{2, 0, 0}, FURNACE)));
		assertTrue(met(goal, torchWith(new int[]{-1, 0, 0}, FURNACE, new int[]{2, 0, 0}, CHEST)), "either way round");
		assertTrue(met(goal, torchWith(new int[]{0, 0, -2}, CHEST, new int[]{0, 0, 3}, FURNACE)), "along z");
		assertFalse(met(goal, torchWith(new int[]{-1, 0, 0}, CHEST, new int[]{2, 0, 0}, CHEST)), "two chests");
		assertFalse(met(goal, torchWith(new int[]{-1, 0, 0}, CHEST)), "only the chest");
		assertFalse(met(goal, torchWith(new int[]{-1, 0, 0}, CHEST, new int[]{0, 0, 2}, FURNACE)), "a right angle");
	}

	@Test
	void aFamilyOrATagCanBeOneOfTheTwo() {
		Goal goal = between(DOORS, CHEST);
		assertTrue(met(goal, torchWith(new int[]{-1, 0, 0}, "minecraft:spruce_door", new int[]{1, 0, 0}, CHEST)));
		assertFalse(met(goal, torchWith(new int[]{-1, 0, 0}, "minecraft:iron_door", new int[]{1, 0, 0}, CHEST)),
				"an iron door is not one of the doors");
	}

	@Test
	void theGoalTypeCanBeSatisfiedFromItsOwnFixture() {
		for (Goal goal : List.of(between(CHEST, FURNACE), between(DOORS, CHEST),
				new Goal.PlaceRel("minecraft:crafting_table", Relation.BETWEEN, "#minecraft:beds", 1, DOORS))) {
			assertTrue(met(goal, SpatialWorlds.placementFor(goal, false)), goal.toString());
		}
	}

	@Test
	void nextToJustOneOfThemIsTheWrongRelationNotProgress() {
		Goal goal = between(CHEST, FURNACE);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(torchWith(new int[]{1, 0, 0}, FURNACE));
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertEquals(0, tracker.count);
		assertEquals(Relation.NEXT_TO, NearMiss.current(tracker, obs.now));
		assertEquals(1, tracker.formErrors, "one wrong placement is one slip for the learning side");
	}

	// ------------------------------------------------------------------- the JSON

	@Test
	void theSecondAnchorIsWrittenAndReadBack() {
		Goal goal = between(CHEST, FURNACE);
		JsonObject json = GoalTypes.toJson(goal);
		assertEquals(FURNACE, json.get("anchor2").getAsString());
		assertEquals(goal, GoalTypes.parse(json));
		// a goal without one stays a goal without one, and writes no key for it
		Goal single = new Goal.PlaceRel(TORCH, Relation.BETWEEN, TORCH, 2);
		assertFalse(GoalTypes.toJson(single).has("anchor2"));
		assertNull(((Goal.PlaceRel) GoalTypes.parse(GoalTypes.toJson(single))).anchor2());
		JsonObject written = JsonParser.parseString("{\"type\":\"PLACE_REL\",\"block\":\"minecraft:torch\","
				+ "\"relation\":\"BETWEEN\",\"anchor\":\"minecraft:chest\",\"anchor2\":\"minecraft:furnace\"}")
				.getAsJsonObject();
		assertEquals(between(CHEST, FURNACE), GoalTypes.parse(written));
		JsonObject blank = JsonParser.parseString("{\"type\":\"PLACE_REL\",\"block\":\"minecraft:torch\","
				+ "\"relation\":\"BETWEEN\",\"anchor\":\"minecraft:chest\",\"anchor2\":\"\"}").getAsJsonObject();
		assertNull(((Goal.PlaceRel) GoalTypes.parse(blank)).anchor2(), "an empty second anchor is none");
	}

	@Test
	void theSecondAnchorMakesAnotherTask() {
		String two = GoalTypes.of(between(CHEST, FURNACE)).taskSignature(between(CHEST, FURNACE));
		String swapped = GoalTypes.of(between(FURNACE, CHEST)).taskSignature(between(FURNACE, CHEST));
		String alone = GoalTypes.of(between(CHEST, null)).taskSignature(between(CHEST, null));
		assertNotEquals(two, alone);
		assertNotEquals(two, GoalTypes.of(between(CHEST, DOORS)).taskSignature(between(CHEST, DOORS)));
		assertTrue(two.contains(CHEST) && two.contains(FURNACE), two);
		assertEquals(two, swapped, "상자와 화로 사이 and 화로와 상자 사이 are one task");
	}

	@Test
	void theSchemaListsTheSecondAnchorAsOptional() {
		var fields = GoalTypes.of(between(CHEST, FURNACE)).fields();
		var second = fields.stream().filter(f -> f.name().equals("anchor2")).findFirst().orElseThrow();
		assertFalse(second.required());
		var first = fields.stream().filter(f -> f.name().equals("anchor")).findFirst().orElseThrow();
		assertTrue(first.required());
		assertTrue(first.schema().get("pattern").getAsString().contains("@"), "the first may be an earlier step");
		assertFalse(second.schema().toString().contains("@"), "the second is a block");
	}

	// ----------------------------------------------------------------- validation

	private List<String> problems(Goal goal) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, null, null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, catalog, problems));
		return problems;
	}

	@Test
	void twoDifferentBlocksAreFine() {
		assertEquals(List.of(), problems(between(CHEST, FURNACE)));
		assertEquals(List.of(), problems(between(DOORS, CHEST)));
		assertEquals(List.of(), problems(between(TORCH, null)), "the one-anchor form still works");
	}

	@Test
	void theSecondAnchorHasToBeAGoodDistinctBlockAndOnlyForBetween() {
		assertTrue(problems(between(CHEST, CHEST)).stream().anyMatch(p -> p.contains("same as anchor")));
		assertTrue(problems(between(CHEST, "not an id")).stream().anyMatch(p -> p.contains("bad anchor2")));
		assertTrue(problems(between(CHEST, "#minecraft:nonsense")).stream().anyMatch(p -> p.contains("bad anchor2")));
		assertTrue(problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, CHEST, 1, FURNACE)).stream()
				.anyMatch(p -> p.contains("anchor2 only goes with BETWEEN")));
		assertTrue(problems(between("@s0", FURNACE)).stream().anyMatch(p -> p.contains("BETWEEN needs two blocks")));
		assertTrue(problems(between(CHEST, "minecraft:glass_pane_nameless_probe")).stream()
				.anyMatch(p -> p.contains("no Korean name")));
	}

	// ----------------------------------------------------------------- the Korean

	private String say(Goal goal, int level) {
		return SentenceBuilder.normal(goal, null, List.of(), level, false, namer);
	}

	@Test
	void theTwoNamesAreJoinedWithWaOrGwaByTheFinalSound() {
		assertEquals("상자와 화로 사이에 횃불을 놓아요.", say(between(CHEST, FURNACE), 1));
		assertEquals("문과 상자 사이에 횃불을 놓아요.", say(between(DOORS, CHEST), 1));
		assertEquals("화로와 상자 사이에 횃불을 놓아요.", say(between(FURNACE, CHEST), 1));
		assertEquals("침대와 문 사이에 횃불을 놓아요.", say(between("#minecraft:beds", DOORS), 1));
		assertEquals("제작대와 화로 사이에 횃불을 놓아요.", say(between("minecraft:crafting_table", FURNACE), 1));
	}

	@Test
	void theRelationWordSurvivesEveryLevelHintAndCeremony() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.BETWEEN, CHEST, 2, FURNACE);
		for (int level = 1; level <= 6; level++) {
			String ko = say(goal, level);
			assertTrue(ko.startsWith("상자와 화로 사이에 횃불을 "), ko);
		}
		assertEquals("상자와 화로 사이에 횃불을 놓아요.", SentenceBuilder.hint(goal, null, List.of(), namer));
		assertEquals("상자와 화로 사이에 횃불을 두 개 놓았어요.", SentenceBuilder.ceremony(goal, null, List.of(), namer));
	}

	@Test
	void theGrammarPointsAreTheLocationNounAndTheJoiningParticle() {
		List<String> ids = SentenceBuilder.grammar(between(CHEST, FURNACE), null, List.of(), 1, false, namer);
		assertTrue(ids.containsAll(List.of("L2.loc_sai_e", "L2.hago_wa_gwa", "L1.obj_eul", "L1.eoyo")), ids.toString());
		assertFalse(ids.contains("L1.num_native"), "no 두 here: two different blocks, not two of one");
		// the one-anchor form still says 두
		List<String> single = SentenceBuilder.grammar(new Goal.PlaceRel(TORCH, Relation.BETWEEN, TORCH, 1), null,
				List.of(), 1, false, namer);
		assertTrue(single.contains("L1.num_native") && !single.contains("L2.hago_wa_gwa"), single.toString());
	}
}
