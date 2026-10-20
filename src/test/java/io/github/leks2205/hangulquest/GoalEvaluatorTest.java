package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalEvaluatorTest {
	private static Observation observation() {
		Observation obs = new Observation();
		obs.now = 1_000L;
		obs.clockTime = 6000L;
		obs.dimension = "minecraft:overworld";
		return obs;
	}

	@Test
	void breakBlockCountsEvents() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.BreakBlock("minecraft:stone", 2, null);
		Observation obs = observation();
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.brokenBlocks.add("minecraft:stone");
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.brokenBlocks.add("minecraft:stone");
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void acquireCountsHeldItems() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 2);
		Observation obs = observation();
		obs.inventory.put("minecraft:apple", 1);
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 2);
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void lookRequiresDwell() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.LookAtEntity("minecraft:cow", 150);
		Observation obs = observation();
		obs.lookedEntity = "minecraft:cow";
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void conditionsGateProgress() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.PlaceBlock("minecraft:torch", 1);
		Observation day = observation();
		day.placedBlocks.add("minecraft:torch");
		assertFalse(GoalEvaluator.tick(goal, List.of(new Condition.Time(Condition.Phase.NIGHT)), day, tracker));

		Observation night = observation();
		night.clockTime = 14000L;
		night.placedBlocks.add("minecraft:torch");
		assertTrue(GoalEvaluator.tick(goal, List.of(new Condition.Time(Condition.Phase.NIGHT)), night, tracker));
	}

	@Test
	void blockFamiliesAreInterchangeable() {
		Observation use = observation();
		use.usedBlocks.add("minecraft:blue_bed");
		assertTrue(GoalEvaluator.tick(new Goal.UseBlock("minecraft:red_bed"), List.of(), use,
				new GoalEvaluator.Tracker()));

		Observation place = observation();
		place.placedBlocks.add("minecraft:spruce_planks");
		assertTrue(GoalEvaluator.tick(new Goal.PlaceBlock("minecraft:oak_planks", 1), List.of(), place,
				new GoalEvaluator.Tracker()));

		Observation broken = observation();
		broken.brokenBlocks.add("minecraft:birch_log");
		assertTrue(GoalEvaluator.tick(new Goal.BreakBlock("minecraft:oak_log", 1, null), List.of(), broken,
				new GoalEvaluator.Tracker()));

		GoalEvaluator.Tracker lookTracker = new GoalEvaluator.Tracker();
		Observation look = observation();
		look.lookedBlock = "minecraft:blue_bed";
		for (int i = 0; i < 4; i++) {
			GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:red_bed", 100), List.of(), look, lookTracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:red_bed", 100), List.of(), look, lookTracker));

		Observation wallTorch = observation();
		wallTorch.placedBlocks.add("minecraft:wall_torch");
		wallTorch.placedBlocks.add("minecraft:wall_torch");
		assertTrue(GoalEvaluator.tick(new Goal.PlaceBlock("minecraft:torch", 2), List.of(),
				wallTorch, new GoalEvaluator.Tracker()));

		Observation wallLook = observation();
		wallLook.lookedBlock = "minecraft:wall_torch";
		GoalEvaluator.Tracker wallLookTracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 4; i++) {
			GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:torch", 100), List.of(), wallLook,
					wallLookTracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:torch", 100), List.of(),
				wallLook, wallLookTracker));

		Observation deepslate = observation();
		deepslate.brokenBlocks.add("minecraft:deepslate_coal_ore");
		assertTrue(GoalEvaluator.tick(new Goal.BreakBlock("minecraft:coal_ore", 1, null), List.of(),
				deepslate, new GoalEvaluator.Tracker()));

		Observation snowLayer = observation();
		snowLayer.lookedBlock = "minecraft:snow";
		GoalEvaluator.Tracker snowTracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 5; i++) {
			GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:snow_block", 100), List.of(), snowLayer, snowTracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:snow_block", 100), List.of(),
				snowLayer, snowTracker));

		Observation flower = observation();
		flower.lookedBlock = "minecraft:dandelion";
		GoalEvaluator.Tracker flowerTracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 5; i++) {
			GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:poppy", 100), List.of(), flower, flowerTracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:poppy", 100), List.of(),
				flower, flowerTracker));

		Observation salmon = observation();
		salmon.inventory.put("minecraft:salmon", 2);
		assertTrue(GoalEvaluator.tick(new Goal.AcquireItem("minecraft:cod", 1), List.of(), salmon,
				new GoalEvaluator.Tracker()));

		Observation bed = observation();
		bed.inventory.put("minecraft:blue_bed", 1);
		assertTrue(GoalEvaluator.tick(new Goal.AcquireItem("minecraft:red_bed", 1), List.of(), bed,
				new GoalEvaluator.Tracker()));

		Observation door = observation();
		door.inventory.put("minecraft:spruce_door", 1);
		assertTrue(GoalEvaluator.tick(new Goal.AcquireItem("minecraft:oak_door", 1), List.of(), door,
				new GoalEvaluator.Tracker()));

		Observation wool = observation();
		wool.inventory.put("minecraft:blue_wool", 1);
		assertTrue(GoalEvaluator.tick(new Goal.AcquireItem("minecraft:white_wool", 1), List.of(), wool,
				new GoalEvaluator.Tracker()));
	}

	@Test
	void nightConsumeDefeatAndBiomeSurviveMorningJump() {
		List<Condition> night = List.of(new Condition.Time(Condition.Phase.NIGHT));

		Observation eat = observation();
		eat.clockTime = 1000L;
		eat.consumedItemsAtNight.add("minecraft:bread");
		assertTrue(GoalEvaluator.tick(new Goal.ConsumeItem("minecraft:bread", 1), night, eat,
				new GoalEvaluator.Tracker()));

		Observation defeat = observation();
		defeat.clockTime = 1000L;
		defeat.defeatedEntitiesAtNight.add("minecraft:zombie");
		assertTrue(GoalEvaluator.tick(new Goal.DefeatEntity("minecraft:zombie", 1), night, defeat,
				new GoalEvaluator.Tracker()));

		Observation beach = observation();
		beach.clockTime = 1000L;
		beach.biome = "minecraft:plains";
		beach.biomeAtNight = "minecraft:beach";
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 5; i++) {
			GoalEvaluator.tick(new Goal.ReachBiome("minecraft:beach", 100), night, beach, tracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.ReachBiome("minecraft:beach", 100), night, beach, tracker));
	}

	@Test
	void nightPlacementAndLookSurviveMorningJump() {
		Observation place = observation();
		place.clockTime = 1000L;
		place.placedBlocksAtNight.add("minecraft:torch");
		assertTrue(GoalEvaluator.tick(new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT)), place, new GoalEvaluator.Tracker()));

		Observation daytimePlace = observation();
		daytimePlace.clockTime = 1000L;
		daytimePlace.placedBlocks.add("minecraft:torch");
		assertFalse(GoalEvaluator.tick(new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT)), daytimePlace,
				new GoalEvaluator.Tracker()));

		GoalEvaluator.Tracker lookTracker = new GoalEvaluator.Tracker();
		Observation look = observation();
		look.clockTime = 1000L;
		look.lookedBlock = "minecraft:torch";
		look.lookedBlockAtNight = "minecraft:torch";
		for (int i = 0; i < 4; i++) {
			GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:torch", 100),
					List.of(new Condition.Time(Condition.Phase.NIGHT)), look, lookTracker);
		}
		assertTrue(GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:torch", 100),
				List.of(new Condition.Time(Condition.Phase.NIGHT)), look, lookTracker));
	}

	@Test
	void sleepingInBedCompletesBedGoalRegardlessOfClock() {
		Observation obs = observation();
		obs.clockTime = 6000L;
		obs.sleeping = true;
		obs.sleepingBed = "minecraft:blue_bed";
		assertTrue(GoalEvaluator.tick(new Goal.UseBlock("minecraft:red_bed"),
				List.of(new Condition.Time(Condition.Phase.NIGHT)), obs, new GoalEvaluator.Tracker()));

		Observation day = observation();
		day.clockTime = 6000L;
		day.sleeping = false;
		day.sleepingBed = "minecraft:red_bed";
		assertFalse(GoalEvaluator.tick(new Goal.UseBlock("minecraft:red_bed"),
				List.of(new Condition.Time(Condition.Phase.NIGHT)), day, new GoalEvaluator.Tracker()));
	}

	@Test
	void bedUseAtNightSurvivesMorningJump() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal bed = new Goal.UseBlock("minecraft:red_bed");
		List<Condition> night = List.of(new Condition.Time(Condition.Phase.NIGHT));

		// Server has already jumped to morning; the bed was used at night.
		Observation afterSleep = observation();
		afterSleep.clockTime = 1000L;
		afterSleep.usedBlocksAtNight.add("minecraft:red_bed");
		assertTrue(GoalEvaluator.tick(bed, night, afterSleep, tracker));

		// A daytime bed use must not count.
		GoalEvaluator.Tracker dayTracker = new GoalEvaluator.Tracker();
		Observation daytime = observation();
		daytime.clockTime = 6000L;
		daytime.usedBlocks.add("minecraft:red_bed");
		assertFalse(GoalEvaluator.tick(bed, night, daytime, dayTracker));

		// Other conditions still gate the latched use.
		GoalEvaluator.Tracker wrongPlace = new GoalEvaluator.Tracker();
		Observation wrongBiome = observation();
		wrongBiome.clockTime = 1000L;
		wrongBiome.usedBlocksAtNight.add("minecraft:red_bed");
		wrongBiome.biome = "minecraft:plains";
		assertFalse(GoalEvaluator.tick(bed,
				List.of(new Condition.Time(Condition.Phase.NIGHT),
						new Condition.InBiome("minecraft:beach")), wrongBiome, wrongPlace));
	}

	@Test
	void biomeTagsMatch() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.ReachBiome("#minecraft:is_forest", 50);
		Observation obs = observation();
		obs.biome = "minecraft:forest";
		obs.biomeTags.add("minecraft:is_forest");
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void reachHeightUsesPlayerY() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.ReachHeight(100, 320);
		Observation obs = observation();
		obs.y = 99;
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.y = 120;
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}
}
