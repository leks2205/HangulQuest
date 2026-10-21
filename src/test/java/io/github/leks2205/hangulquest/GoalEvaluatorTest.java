package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	/** Starts a step with an empty bag, then gains one of {@code gained}. */
	private static boolean acquiredLater(String wanted, String gained) {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem(wanted, 1);
		GoalEvaluator.tick(goal, List.of(), observation(), tracker);
		Observation later = observation();
		later.inventory.put(gained, 1);
		return GoalEvaluator.tick(goal, List.of(), later, tracker);
	}

	@Test
	void acquireCountsItemsGainedSinceTheStepStarted() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 2);
		Observation obs = observation();
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 1);
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 2);
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertEquals(2, tracker.count);
	}

	@Test
	void acquireIgnoresItemsAlreadyHeldWhenTheStepBegins() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 2);
		Observation obs = observation();
		obs.inventory.put("minecraft:apple", 30);
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker), "held items must not finish the step");
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 31);
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 32);
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void acquireNeedsARiseAboveTheBaselineNotAShuffle() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 1);
		Observation obs = observation();
		obs.inventory.put("minecraft:apple", 5);
		GoalEvaluator.tick(goal, List.of(), obs, tracker);
		obs.inventory.remove("minecraft:apple"); // stack picked up with the cursor
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		obs.inventory.put("minecraft:apple", 5); // and put back
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertEquals(0, tracker.count);
	}

	@Test
	void acquireResumesRestoredProgressWithoutCountingTheHeldStack() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 3);
		GoalEvaluator.seed(tracker, goal, 2);
		Observation obs = observation();
		obs.inventory.put("minecraft:apple", 9); // already in the bag after the restart
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertEquals(2, tracker.count, "the re-baseline must neither add nor lose progress");
		obs.inventory.put("minecraft:apple", 10);
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, tracker));
	}

	@Test
	void acquireRebaselinesWhileTheInventoryIsStillSyncing() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 1);
		GoalEvaluator.tick(goal, List.of(), observation(), tracker);
		Observation syncing = observation();
		syncing.inventorySettling = true;
		syncing.inventory.put("minecraft:apple", 12); // the new world's inventory arrives
		assertFalse(GoalEvaluator.tick(goal, List.of(), syncing, tracker));
		Observation settled = observation();
		settled.inventory.put("minecraft:apple", 12);
		assertFalse(GoalEvaluator.tick(goal, List.of(), settled, tracker));
		settled.inventory.put("minecraft:apple", 13);
		assertTrue(GoalEvaluator.tick(goal, List.of(), settled, tracker));
	}

	@Test
	void acquireBaselineIsTakenEvenWhileConditionsFail() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Goal goal = new Goal.AcquireItem("minecraft:apple", 1);
		List<Condition> night = List.of(new Condition.Time(Condition.Phase.NIGHT));
		assertFalse(GoalEvaluator.tick(goal, night, observation(), tracker));
		Observation dayWithApple = observation();
		dayWithApple.inventory.put("minecraft:apple", 1);
		assertFalse(GoalEvaluator.tick(goal, night, dayWithApple, tracker));
		Observation nightWithApple = observation();
		nightWithApple.clockTime = 14000L;
		nightWithApple.inventory.put("minecraft:apple", 1);
		assertTrue(GoalEvaluator.tick(goal, night, nightWithApple, tracker),
				"an apple picked up before dark is a gain once the conditions hold");
	}

	@Test
	void creativeProgressCountsButIsFlaggedAssisted() {
		Goal goal = new Goal.BreakBlock("minecraft:stone", 1, null);
		GoalEvaluator.Tracker creative = new GoalEvaluator.Tracker();
		Observation obs = observation();
		obs.creative = true;
		obs.brokenBlocks.add("minecraft:stone");
		assertTrue(GoalEvaluator.tick(goal, List.of(), obs, creative), "breaking in creative still counts");
		assertTrue(creative.assisted);

		GoalEvaluator.Tracker survival = new GoalEvaluator.Tracker();
		Observation plain = observation();
		plain.brokenBlocks.add("minecraft:stone");
		assertTrue(GoalEvaluator.tick(goal, List.of(), plain, survival));
		assertFalse(survival.assisted);
	}

	@Test
	void assistedStaysSetForTheWholeStepAndResetsWithTheNextOne() {
		Goal goal = new Goal.BreakBlock("minecraft:stone", 2, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation spectator = observation();
		spectator.spectator = true;
		spectator.brokenBlocks.add("minecraft:stone");
		GoalEvaluator.tick(goal, List.of(), spectator, tracker);
		Observation survival = observation();
		survival.brokenBlocks.add("minecraft:stone");
		assertTrue(GoalEvaluator.tick(goal, List.of(), survival, tracker));
		assertTrue(tracker.assisted, "one assisted tick taints the step");
		tracker.clear();
		assertFalse(tracker.assisted);
	}

	@Test
	void idleCreativeTicksDoNotTaintAStep() {
		Goal goal = new Goal.BreakBlock("minecraft:stone", 1, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observation();
		obs.creative = true;
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertFalse(tracker.assisted);
	}

	@Test
	void lookAtEntityAsksTheSensorForALongRangePick() {
		Observation look = observation();
		GoalEvaluator.tick(new Goal.LookAtEntity("minecraft:ghast", 100), List.of(), look,
				new GoalEvaluator.Tracker());
		assertTrue(look.farEntityLookWanted);

		Observation other = observation();
		GoalEvaluator.tick(new Goal.BreakBlock("minecraft:stone", 1, null), List.of(), other,
				new GoalEvaluator.Tracker());
		assertFalse(other.farEntityLookWanted, "nothing else needs the extra work");
	}

	@Test
	void sleepingDoesNotBypassDimensionOrBiome() {
		Goal goal = new Goal.UseBlock("minecraft:red_bed");
		List<Condition> conditions = List.of(new Condition.Time(Condition.Phase.NIGHT),
				new Condition.InDimension("minecraft:the_nether"));
		Observation overworld = observation();
		overworld.sleeping = true;
		overworld.sleepingBed = "minecraft:red_bed";
		assertFalse(GoalEvaluator.tick(goal, conditions, overworld, new GoalEvaluator.Tracker()),
				"sleeping in the wrong dimension must not count");

		Observation nether = observation();
		nether.dimension = "minecraft:the_nether";
		nether.sleeping = true;
		nether.sleepingBed = "minecraft:red_bed";
		assertTrue(GoalEvaluator.tick(goal, conditions, nether, new GoalEvaluator.Tracker()),
				"in bed it is night enough even after the morning jump");

		List<Condition> biome = List.of(new Condition.Time(Condition.Phase.NIGHT),
				new Condition.InBiome("minecraft:desert"));
		Observation plains = observation();
		plains.biome = "minecraft:plains";
		plains.sleeping = true;
		plains.sleepingBed = "minecraft:red_bed";
		assertFalse(GoalEvaluator.tick(goal, biome, plains, new GoalEvaluator.Tracker()));
	}

	@Test
	void nearEntityHonoursItsRadius() {
		Condition near = new Condition.NearEntity("minecraft:cow", 8.0);
		Observation far = observation();
		far.addNearby("minecraft:cow", 12.0);
		assertFalse(GoalEvaluator.conditionMet(near, far));
		Observation close = observation();
		close.addNearby("minecraft:cow", 12.0);
		close.addNearby("minecraft:cow", 5.0);
		assertTrue(GoalEvaluator.conditionMet(near, close), "the nearest one counts");
	}

	@Test
	void nearBlockHonoursItsRadius() {
		Condition near = new Condition.NearBlock("minecraft:furnace", 4.0);
		Observation far = observation();
		far.position = new Vec3(0.5, 64, 0.5);
		far.lookedBlock = "minecraft:furnace";
		far.lookedBlockPos = new BlockPos(10, 64, 0);
		assertFalse(GoalEvaluator.conditionMet(near, far));
		Observation close = observation();
		close.position = new Vec3(0.5, 64, 0.5);
		close.lookedBlock = "minecraft:furnace";
		close.lookedBlockPos = new BlockPos(2, 64, 0);
		assertTrue(GoalEvaluator.conditionMet(near, close));
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

		assertTrue(acquiredLater("minecraft:cod", "minecraft:salmon"));
		assertTrue(acquiredLater("minecraft:red_bed", "minecraft:blue_bed"));
		assertTrue(acquiredLater("minecraft:oak_door", "minecraft:spruce_door"));
		assertTrue(acquiredLater("minecraft:white_wool", "minecraft:blue_wool"));
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
