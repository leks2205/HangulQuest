package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 5 (from BATCH-007 and the BATCH-006 review): 요리하다 speaks the input, the HOLDING phrase
 * uses the taught word, stairs are piled up, an authored clue may carry a counter, a roof over a placed
 * block, the missing sign and hoe words, a proximity check for NEAR_BLOCK and the seconds of a wait.
 */
class GeneratorWaveFiveTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	// ------------------------------------------------------------------ 3. 요리하다 speaks the input

	@Test
	void i3_cookingSpeaksTheFoodThatGoesInNotTheProduct() {
		Goal potato = new Goal.Smelt("minecraft:potato", "minecraft:baked_potato", 1);
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		assertEquals("감자를 요리해요.", SentenceBuilder.normal(potato, null, word("cook"), List.of(), 1, false, names));
		assertEquals("감자를 요리했어요.", SentenceBuilder.ceremony(potato, null, word("cook"), List.of(), names));
		Goal chicken = new Goal.Smelt("minecraft:chicken", "minecraft:cooked_chicken", 1);
		assertEquals("익히지 않은 닭고기를 요리해요.",
				SentenceBuilder.normal(chicken, null, word("cook"), List.of(), 1, false, names));
		// a taught word that names the product keeps the product in the sentence
		assertEquals("닭고기를 요리해요.",
				SentenceBuilder.normal(chicken, word("chicken_meat"), word("cook"), List.of(), 1, false, names));
		// a count shows on the input
		Goal three = new Goal.Smelt("minecraft:potato", "minecraft:baked_potato", 3);
		assertEquals("감자를 세 개 요리해요.",
				SentenceBuilder.normal(three, null, word("cook"), List.of(), 1, false, names));
		// no input: nothing else to name
		Goal bare = new Goal.Smelt(null, "minecraft:baked_potato", 1);
		assertEquals("구운 감자를 요리해요.", SentenceBuilder.normal(bare, null, word("cook"), List.of(), 1, false, names));
	}

	// ------------------------------------------------------------------ 5. stairs are piled up

	@Test
	void i5_stairsArePiledUpLikeAPillar() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal stairs = new Goal.BuildPattern(Pattern.STAIRS, "minecraft:cobblestone", 3);
		assertEquals("조약돌로 계단을 쌓아요.", SentenceBuilder.normal(stairs, null, List.of(), 1, false, names));
		assertEquals("조약돌로 계단을 쌓았어요.", SentenceBuilder.ceremony(stairs, null, List.of(), names));
		// a cross is still made
		Goal cross = new Goal.BuildPattern(Pattern.CROSS, "minecraft:cobblestone", 3);
		assertEquals("조약돌로 십자 모양을 만들어요.", SentenceBuilder.normal(cross, null, List.of(), 1, false, names));
	}

	// ------------------------------------------------------------ 8. the words 표지판 and 괭이

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new java.util.ArrayList<>();
		io.github.leks2205.hangulquest.quest.QuestStep step = new io.github.leks2205.hangulquest.quest.QuestStep("s1",
				List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new io.github.leks2205.hangulquest.quest.goal.ValidationContext("t/s1", step,
				CATALOG, problems));
		return problems;
	}

	@Test
	void i8_theWordForASignCoversEverySignAndHangingSign() {
		Word sign = word("sign");
		assertEquals("표지판", sign.ko);
		for (String wood : List.of("oak", "birch", "dark_oak", "bamboo", "crimson", "pale_oak", "poplar")) {
			for (String kind : List.of("sign", "hanging_sign")) {
				assertTrue(io.github.leks2205.hangulquest.quest.goal.SentenceContext.wordNames(sign,
						io.github.leks2205.hangulquest.quest.goal.ObjectKind.ITEM, "minecraft:" + wood + "_" + kind),
						wood + "_" + kind);
			}
		}
		assertFalse(io.github.leks2205.hangulquest.quest.goal.SentenceContext.wordNames(sign,
				io.github.leks2205.hangulquest.quest.goal.ObjectKind.ITEM, "minecraft:oak_door"));
		// a step can teach it for a sign of any wood, standing or hanging
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:birch_sign"), "sign"));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:cherry_hanging_sign"), "sign"));
		assertEquals("표지판을 사용해요.", SentenceBuilder.normal(new Goal.UseBlock("minecraft:spruce_sign"), sign,
				List.of(), 1, false, KoreanFixtures.namer(CATALOG)));
	}

	@Test
	void i8_aPlacedSignOfAnotherWoodOrOnAWallCountsForTheWord() {
		Goal place = new Goal.PlaceBlock("minecraft:oak_sign", 1);
		io.github.leks2205.hangulquest.sense.Observation obs = new io.github.leks2205.hangulquest.sense.Observation();
		obs.now = 1000L;
		obs.placedBlocks.add("minecraft:spruce_wall_sign");
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(place, List.of(), obs,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker()));
		assertTrue(io.github.leks2205.hangulquest.quest.goal.Matching.matchesBlock("minecraft:oak_sign",
				"minecraft:warped_wall_hanging_sign"));
	}

	@Test
	void i8_theWordForAHoeCoversEveryHoe() {
		Word hoe = word("hoe");
		for (String material : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite", "copper")) {
			assertTrue(io.github.leks2205.hangulquest.quest.goal.SentenceContext.wordNames(hoe,
					io.github.leks2205.hangulquest.quest.goal.ObjectKind.ITEM, "minecraft:" + material + "_hoe"),
					material);
		}
		assertEquals(List.of(), problems(new Goal.Craft("minecraft:wooden_hoe", 1, null, null), "hoe"));
		assertEquals("괭이를 만들어요.", SentenceBuilder.normal(new Goal.Craft("minecraft:stone_hoe", 1, null, null), hoe, List.of(),
				1, false, KoreanFixtures.namer(CATALOG)));
	}

	// --------------------------------------- 7. a roof over the placed block, not only over the player

	private static final Condition ROOFED = new Condition.Sky(Condition.SkyState.ROOFED);

	private static io.github.leks2205.hangulquest.sense.PlacementFact barrelPlaced(boolean roofed) {
		return new io.github.leks2205.hangulquest.sense.PlacementFact("minecraft:barrel",
				new net.minecraft.core.BlockPos(10, 64, 10), 1L, false, net.minecraft.world.phys.Vec3.ZERO,
				io.github.leks2205.hangulquest.sense.BlockNeighborhood.capture(10, 64, 10, (x, y, z) -> null), roofed);
	}

	@Test
	void i7_aPlacedBlockUnderARoofIsPutThereTheCraftingPlayerStandsThere() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal barrel = new Goal.PlaceBlock("minecraft:barrel", 1);
		Word barrelWord = word("gen_barrel");
		assertEquals("지붕 아래에 통을 놓아요.",
				SentenceBuilder.normal(barrel, barrelWord, List.of(ROOFED), 1, false, names));
		assertEquals("지붕 아래에 통을 놓아요.", SentenceBuilder.hint(barrel, barrelWord, List.of(ROOFED), names));
		assertEquals("지붕 아래에 통을 놓았어요.", SentenceBuilder.ceremony(barrel, barrelWord, List.of(ROOFED), names));
		// crafting has no place for the thing: 에서 stays, it is the player's
		Goal hay = new Goal.Craft("minecraft:hay_block", 1, null, null);
		assertEquals("지붕 아래에서 건초 더미를 만들어요.", SentenceBuilder.normal(hay, null, List.of(ROOFED), 1, false, names));
		// an authored clue gets the target phrase too, and keeps its own when it has it
		assertEquals("지붕 아래에 통을 놓아요.",
				SentenceBuilder.authored(barrel, "통을 놓아요.", List.of(ROOFED), barrelWord, names, 1));
		assertEquals("지붕 아래에 통을 놓아요.",
				SentenceBuilder.authored(barrel, "지붕 아래에 통을 놓아요.", List.of(ROOFED), barrelWord, names, 1));
		// grammar: a place of the thing, not an activity place
		assertTrue(SentenceBuilder.grammar(barrel, barrelWord, List.of(ROOFED), 1, false, names).contains("L1.e_place"));
		assertFalse(SentenceBuilder.grammar(barrel, barrelWord, List.of(ROOFED), 1, false, names)
				.contains("L2.eseo_activity"));
	}

	@Test
	void i7_theRoofHasToBeOverTheBarrelNotOverThePlayer() {
		Goal barrel = new Goal.PlaceBlock("minecraft:barrel", 1);
		// the player stands outside, the barrel is under the eaves
		var under = standing(1000L);
		under.roofed = false;
		under.addPlacement(barrelPlaced(true));
		under.placedBlocks.add("minecraft:barrel");
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(barrel, List.of(ROOFED), under,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker()));
		// the player is under a roof but puts the barrel out in the open
		var open = standing(1000L);
		open.roofed = true;
		open.addPlacement(barrelPlaced(false));
		open.placedBlocks.add("minecraft:barrel");
		assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(barrel, List.of(ROOFED), open,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker()));
		// without the condition nothing changes
		var plain = standing(1000L);
		plain.placedBlocks.add("minecraft:barrel");
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(barrel, List.of(), plain,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker()));
		// other goals keep the player's roof
		var playerRoof = standing(1000L);
		playerRoof.roofed = true;
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionsMet(
				io.github.leks2205.hangulquest.quest.GoalEvaluator.playerConditions(
						new Goal.Craft("minecraft:hay_block", 1, null, null), List.of(ROOFED)), playerRoof));
		assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionsMet(
				io.github.leks2205.hangulquest.quest.GoalEvaluator.playerConditions(
						new Goal.Craft("minecraft:hay_block", 1, null, null), List.of(ROOFED)), standing(1000L)));
	}

	// ------------------------------------------------------- 9. NEAR_BLOCK is proximity, not a stare

	private static final Condition NEAR_WHEAT = new Condition.NearBlock("minecraft:wheat", 4);

	private static io.github.leks2205.hangulquest.sense.Observation standing(long now) {
		io.github.leks2205.hangulquest.sense.Observation obs = new io.github.leks2205.hangulquest.sense.Observation();
		obs.now = now;
		obs.position = new net.minecraft.world.phys.Vec3(10.5, 64, 10.5);
		return obs;
	}

	@Test
	void i9_theSensorFindsTheNearestBlockOfAKindWithinTheRadius() {
		java.util.Map<String, String> world = new java.util.HashMap<>();
		world.put("13,64,10", "minecraft:wheat");  // 3 blocks east
		world.put("10,64,16", "minecraft:wheat");  // 6 blocks south
		world.put("10,64,5", "minecraft:beehive"); // 5 blocks north
		world.put("10,63,10", "minecraft:grass_block");
		var found = io.github.leks2205.hangulquest.sense.BlockProximity.nearest(10.5, 64, 10.5,
				java.util.Map.of("minecraft:wheat", 4, "minecraft:beehive", 4, "minecraft:bee_nest", 8),
				(x, y, z) -> world.get(x + "," + y + "," + z), io.github.leks2205.hangulquest.quest.goal.Matching::matchesBlock);
		assertEquals(java.util.Set.of("minecraft:wheat"), found.keySet(), "the hive is 5 away (radius 4), the nest is not there");
		assertEquals(3.0, found.get("minecraft:wheat"), 0.1, "the nearest wheat, not the one 6 blocks away");
		// nothing wanted, nothing scanned
		assertTrue(io.github.leks2205.hangulquest.sense.BlockProximity.nearest(10.5, 64, 10.5, java.util.Map.of(),
				(x, y, z) -> { throw new AssertionError("no lookups"); },
				io.github.leks2205.hangulquest.quest.goal.Matching::matchesBlock).isEmpty());
	}

	@Test
	void i9_aBlockNearbyIsNearWithoutLookingAtIt() {
		var obs = standing(1000L);
		assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionMet(NEAR_WHEAT, obs), "nothing found yet");
		obs.nearestBlock = java.util.Map.of("minecraft:wheat", 2.5);
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionMet(NEAR_WHEAT, obs));
		obs.nearestBlock = java.util.Map.of("minecraft:wheat", 4.5);
		assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionMet(NEAR_WHEAT, obs), "outside the radius");
		// the old way still holds: a far look at the block within the radius
		obs.nearestBlock = java.util.Map.of();
		obs.lookedBlock = "minecraft:wheat";
		obs.lookedBlockPos = new net.minecraft.core.BlockPos(12, 64, 10);
		assertTrue(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionMet(NEAR_WHEAT, obs));
		obs.lookedBlockPos = new net.minecraft.core.BlockPos(30, 64, 10);
		assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.conditionMet(NEAR_WHEAT, obs));
	}

	@Test
	void i9_aStepWithNearBlockAsksTheSensorToScan() {
		var obs = standing(1000L);
		io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(new Goal.Wait(5, null), List.of(NEAR_WHEAT), obs,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker());
		assertEquals(java.util.Map.of("minecraft:wheat", 4), obs.nearBlocksWanted);
		var none = standing(1000L);
		io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(new Goal.Wait(5, null), List.of(), none,
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker());
		assertTrue(none.nearBlocksWanted.isEmpty(), "no scan without a NEAR_BLOCK condition");
	}

	@Test
	void i9_waitingBesideACropNeedsNoStare() {
		Goal wait = new Goal.Wait(2, null);
		var tracker = new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i <= 60 && !done; i++) {
			var obs = standing(100_000L + i * 50L); // nothing in the crosshair
			obs.nearestBlock = java.util.Map.of("minecraft:wheat", 2.0);
			done = io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(wait, List.of(NEAR_WHEAT), obs, tracker);
		}
		assertTrue(done, "two seconds beside the wheat");
		// away from the wheat the wait does not run
		var far = new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker();
		for (int i = 0; i <= 60; i++) {
			assertFalse(io.github.leks2205.hangulquest.quest.GoalEvaluator.tick(wait, List.of(NEAR_WHEAT),
					standing(100_000L + i * 50L), far));
		}
	}

	// ------------------------------------------------------------ 6. an authored clue with a counter

	@Test
	void i6_aCounterBetweenTheObjectAndTheVerbStillNamesTheAction() {
		assertTrue(QuestlineValidationTest.namesAction(new Goal.PutIn("minecraft:potato", "minecraft:barrel", 1),
				"감자를 한 개 넣어요."));
		assertTrue(QuestlineValidationTest.namesAction(new Goal.PutIn("minecraft:potato", "minecraft:barrel", 3),
				"통 안에 감자를 세 개 넣어요."));
		// 줘요 holds no 주: the check reads conjugated forms, not only stems
		Goal give = new Goal.Give("minecraft:hay_block", "minecraft:wolf", null, 1);
		assertTrue(QuestlineValidationTest.namesAction(give, "건초 더미를 줘요."));
		assertTrue(QuestlineValidationTest.namesAction(give, "건초 더미를 한 개 줘요."));
		assertFalse(QuestlineValidationTest.namesAction(give, "건초 더미를 봐요."));
	}
}
