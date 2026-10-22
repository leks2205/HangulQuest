package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.sense.ContainerPutFact;
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The action-variety goals and conditions, evaluated from fake Observation facts: craft, smelt, give,
 * put in, hold, sequence, avoid, and the four new conditions. Korean output is in
 * {@link ActionSentenceGoldTest}.
 */
class ActionGoalsTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final String BREAD = "minecraft:bread";
	private static final String TORCH = "minecraft:torch";
	private static final String BED = "minecraft:red_bed";

	private static boolean tick(Goal goal, Observation obs, GoalEvaluator.Tracker tracker) {
		return GoalEvaluator.tick(goal, List.of(), obs, tracker);
	}

	private static Observation night() {
		Observation obs = new Observation();
		obs.clockTime = 14000L;
		return obs;
	}

	private static Observation eats(String item) {
		Observation obs = new Observation();
		obs.consumedItems.add(item);
		return obs;
	}

	private static Observation sleeps() {
		Observation obs = new Observation();
		obs.usedBlocks.add(BED);
		return obs;
	}

	private static Goal.Sequence eatThenSleep() {
		return new Goal.Sequence(List.of(new Goal.ConsumeItem(BREAD, 1), new Goal.UseBlock(BED)), true, "BEFORE");
	}

	// ------------------------------------------------------------------ craft

	@Test
	void craftCountsResultSlotTakesAtCraftingScreens() {
		Goal.Craft torches = new Goal.Craft(TORCH, 4, null, List.of());
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation first = new Observation();
		first.addCrafted(new CraftFact(TORCH, 2, "crafting_table"));
		assertFalse(tick(torches, first, tracker));
		assertEquals(2, tracker.count);
		Observation second = new Observation();
		second.addCrafted(new CraftFact(TORCH, 2, "inventory2x2"));
		assertTrue(tick(torches, second, tracker), "2 + 2 takes make 4");
	}

	@Test
	void craftIgnoresFurnacesMerchantsAndOtherItems() {
		Goal.Craft torch = new Goal.Craft(TORCH, 1, null, List.of());
		for (CraftFact fact : List.of(new CraftFact(TORCH, 1, "furnace"), new CraftFact(TORCH, 1, "merchant"),
				new CraftFact(TORCH, 1, "smoker"), new CraftFact(BREAD, 1, "crafting_table"))) {
			Observation obs = new Observation();
			obs.addCrafted(fact);
			assertFalse(tick(torch, obs, new GoalEvaluator.Tracker()), fact.toString());
		}
	}

	@Test
	void craftStationFilterNeedsThatScreen() {
		Goal.Craft cut = new Goal.Craft("minecraft:stone_bricks", 1, "stonecutter", List.of());
		Observation table = new Observation();
		table.addCrafted(new CraftFact("minecraft:stone_bricks", 1, "crafting_table"));
		assertFalse(tick(cut, table, new GoalEvaluator.Tracker()));
		Observation cutter = new Observation();
		cutter.addCrafted(new CraftFact("minecraft:stone_bricks", 1, "stonecutter"));
		assertTrue(tick(cut, cutter, new GoalEvaluator.Tracker()));
	}

	@Test
	void craftIngredientsOnlyShapeTheSentence() {
		Goal.Craft torch = new Goal.Craft(TORCH, 1, null, List.of("minecraft:stick", "minecraft:coal"));
		Observation obs = new Observation();
		obs.addCrafted(new CraftFact(TORCH, 1, "crafting_table"));
		assertTrue(tick(torch, obs, new GoalEvaluator.Tracker()), "what went into the recipe is not checked");
	}

	// ------------------------------------------------------------------ smelt

	@Test
	void smeltCountsFurnaceBlastFurnaceAndSmoker() {
		Goal.Smelt glass = new Goal.Smelt("minecraft:sand", "minecraft:glass", 1);
		for (String station : List.of("furnace", "blast_furnace", "smoker")) {
			Observation obs = new Observation();
			obs.addCrafted(new CraftFact("minecraft:glass", 1, station));
			assertTrue(tick(glass, obs, new GoalEvaluator.Tracker()), station);
		}
		Observation table = new Observation();
		table.addCrafted(new CraftFact("minecraft:glass", 1, "crafting_table"));
		assertFalse(tick(glass, table, new GoalEvaluator.Tracker()), "glass from a crafting table is not smelting");
		Observation other = new Observation();
		other.addCrafted(new CraftFact("minecraft:iron_ingot", 1, "furnace"));
		assertFalse(tick(glass, other, new GoalEvaluator.Tracker()));
	}

	@Test
	void smeltCountsEveryItemTaken() {
		Goal.Smelt ingots = new Goal.Smelt(null, "minecraft:iron_ingot", 3);
		Observation obs = new Observation();
		obs.addCrafted(new CraftFact("minecraft:iron_ingot", 3, "furnace"));
		assertTrue(tick(ingots, obs, new GoalEvaluator.Tracker()));
	}

	@Test
	void ceramicsAndFoodAreBakedTheRestIsMadeInTheFurnace() {
		assertEquals(Verb.BAKE, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:cooked_beef", 1)));
		assertEquals(Verb.BAKE, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:brick", 1)));
		assertEquals(Verb.BAKE, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:white_glazed_terracotta", 1)));
		assertEquals(Verb.CRAFT, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:glass", 1)));
		assertEquals(Verb.CRAFT, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:iron_ingot", 1)));
	}

	// ------------------------------------------------------------------- give

	private static Observation gave(String item, int count, String entity, GiveFact.Method method) {
		Observation obs = new Observation();
		obs.addGave(new GiveFact(item, count, entity, UUID.randomUUID(), method));
		return obs;
	}

	@Test
	void giveNeedsTheRightItemAndTheRightCreature() {
		Goal.Give give = new Goal.Give(BREAD, "minecraft:villager", null, 1);
		assertTrue(tick(give, gave(BREAD, 1, "minecraft:villager", GiveFact.Method.DROP_NEAR), new GoalEvaluator.Tracker()));
		assertFalse(tick(give, gave(BREAD, 1, "minecraft:cow", GiveFact.Method.FEED), new GoalEvaluator.Tracker()));
		assertFalse(tick(give, gave("minecraft:apple", 1, "minecraft:villager", GiveFact.Method.USE_WITH),
				new GoalEvaluator.Tracker()));
		assertTrue(tick(give, gave(BREAD, 1, "minecraft:villager", GiveFact.Method.TRADE), new GoalEvaluator.Tracker()),
				"paying a trader is handing something over");
	}

	@Test
	void giveCanBeLimitedToOneWayOfGiving() {
		Goal.Give drop = new Goal.Give(BREAD, "minecraft:villager", "DROP_NEAR", 1);
		assertFalse(tick(drop, gave(BREAD, 1, "minecraft:villager", GiveFact.Method.USE_WITH), new GoalEvaluator.Tracker()));
		assertTrue(tick(drop, gave(BREAD, 1, "minecraft:villager", GiveFact.Method.DROP_NEAR), new GoalEvaluator.Tracker()));
	}

	@Test
	void giveCountsTheItemsHandedOver() {
		Goal.Give three = new Goal.Give("minecraft:wheat", "minecraft:cow", null, 3);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(three, gave("minecraft:wheat", 2, "minecraft:cow", GiveFact.Method.FEED), tracker));
		assertTrue(tick(three, gave("minecraft:wheat", 1, "minecraft:cow", GiveFact.Method.FEED), tracker));
	}

	// ----------------------------------------------------------------- put in

	private static Observation put(String item, int count, String block) {
		Observation obs = new Observation();
		obs.addPutIn(new ContainerPutFact(item, count, block, block == null ? null : BlockPos.ZERO));
		return obs;
	}

	@Test
	void putInNeedsTheContainerAndTheItem() {
		Goal.PutIn apple = new Goal.PutIn("minecraft:apple", "minecraft:chest", 1);
		assertTrue(tick(apple, put("minecraft:apple", 1, "minecraft:chest"), new GoalEvaluator.Tracker()));
		assertTrue(tick(apple, put("minecraft:apple", 1, "minecraft:trapped_chest"), new GoalEvaluator.Tracker()),
				"a trapped chest is a chest");
		assertFalse(tick(apple, put("minecraft:apple", 1, "minecraft:barrel"), new GoalEvaluator.Tracker()));
		assertFalse(tick(apple, put(BREAD, 1, "minecraft:chest"), new GoalEvaluator.Tracker()));
		assertFalse(tick(apple, put("minecraft:apple", 1, null), new GoalEvaluator.Tracker()),
				"an unknown container (a chest minecart) cannot be the chest");
	}

	@Test
	void putInCountsAcrossTicks() {
		Goal.PutIn three = new Goal.PutIn("minecraft:apple", "minecraft:chest", 3);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(three, put("minecraft:apple", 2, "minecraft:chest"), tracker));
		assertTrue(tick(three, put("minecraft:apple", 1, "minecraft:chest"), tracker));
	}

	// ------------------------------------------------------------- hold while

	private static Observation holding(String main, String off) {
		Observation obs = new Observation();
		obs.mainHand = main;
		obs.offHand = off;
		return obs;
	}

	@Test
	void holdWhileInnerGoalOnlyCountsWithTheItemInHand() {
		Goal.HoldWhile mine = new Goal.HoldWhile(TORCH, new Goal.BreakBlock("minecraft:stone", 2, null), null, 10);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation bare = holding("minecraft:stone_pickaxe", null);
		bare.brokenBlocks.add("minecraft:stone");
		bare.brokenBlocks.add("minecraft:stone");
		assertFalse(tick(mine, bare, tracker), "no torch in either hand");
		Observation torch = holding("minecraft:stone_pickaxe", TORCH);
		torch.brokenBlocks.add("minecraft:stone");
		assertFalse(tick(mine, torch, tracker), "one of two");
		Observation again = holding("minecraft:stone_pickaxe", TORCH);
		again.brokenBlocks.add("minecraft:stone");
		assertTrue(tick(mine, again, tracker), "the torch in the off hand counts");
	}

	@Test
	void holdWhileDwellStartsOverWhenTheItemIsPutAway() {
		Goal.HoldWhile look = new Goal.HoldWhile("minecraft:wheat", new Goal.LookAtEntity("minecraft:cow", 200), null, 10);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation gaze = holding("minecraft:wheat", null);
		gaze.lookedEntity = "minecraft:cow";
		tick(look, gaze, tracker);
		tick(look, gaze, tracker);
		tick(look, gaze, tracker);
		Observation away = holding(null, null);
		away.lookedEntity = "minecraft:cow";
		assertFalse(tick(look, away, tracker));
		assertFalse(tick(look, gaze, tracker), "the look begins again");
	}

	@Test
	void holdWhileConditionNeedsWalkingWithTheItemUnderTheCondition() {
		Goal.HoldWhile walk = new Goal.HoldWhile(TORCH, null, new Condition.Time(Condition.Phase.NIGHT), 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		// 1 s = 20 ticks of 50 ms. Standing still, no torch, or daylight earn nothing.
		for (int i = 0; i < 40; i++) {
			Observation still = night();
			still.mainHand = TORCH;
			still.idle = true;
			assertFalse(tick(walk, still, tracker), "standing still");
			Observation noTorch = night();
			noTorch.idle = false;
			assertFalse(tick(walk, noTorch, tracker), "without the torch");
			Observation day = holding(TORCH, null);
			day.idle = false;
			day.clockTime = 1000L;
			assertFalse(tick(walk, day, tracker), "by day");
		}
		assertEquals(0L, tracker.dwellMs);
		boolean done = false;
		for (int i = 0; i < 19; i++) {
			Observation stroll = night();
			stroll.offHand = TORCH;
			stroll.idle = false;
			done = tick(walk, stroll, tracker);
		}
		assertFalse(done, "19 ticks are not yet a second");
		Observation last = night();
		last.mainHand = TORCH;
		last.idle = false;
		assertTrue(tick(walk, last, tracker));
	}

	// --------------------------------------------------------------- sequence

	@Test
	void sequenceOutOfOrderDoesNotCount() {
		Goal.Sequence goal = eatThenSleep();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(goal, sleeps(), tracker), "sleeping first");
		assertEquals(0, tracker.count);
		assertFalse(tick(goal, sleeps(), tracker));
		assertEquals(0, tracker.count);
		assertFalse(tick(goal, eats(BREAD), tracker));
		assertEquals(1, tracker.count, "now the bread counts");
		assertFalse(tick(goal, eats(BREAD), tracker), "eating again does not finish it");
		assertTrue(tick(goal, sleeps(), tracker));
	}

	@Test
	void sequenceTakesOnePartPerTick() {
		Goal.Sequence goal = eatThenSleep();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation both = eats(BREAD);
		both.usedBlocks.add(BED);
		assertFalse(tick(goal, both, tracker), "the two cannot happen in the same instant");
		assertEquals(1, tracker.count);
		assertTrue(tick(goal, sleeps(), tracker));
	}

	@Test
	void sequenceResumesFromSavedProgress() {
		Goal.Sequence goal = eatThenSleep();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		GoalEvaluator.seed(tracker, goal, 1);
		assertFalse(tick(goal, eats(BREAD), tracker), "the bread was already eaten before the restart");
		assertTrue(tick(goal, sleeps(), tracker));
	}

	@Test
	void unorderedSequenceTakesAnyOrder() {
		Goal.Sequence any = new Goal.Sequence(List.of(new Goal.ConsumeItem(BREAD, 1), new Goal.UseBlock(BED)), false, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(any, sleeps(), tracker));
		assertEquals(1, tracker.count);
		assertTrue(tick(any, eats(BREAD), tracker));
		// Both in one tick is fine when order does not matter.
		Observation both = eats(BREAD);
		both.usedBlocks.add(BED);
		assertTrue(tick(any, both, new GoalEvaluator.Tracker()));
	}

	@Test
	void sequenceIsCountableByParts() {
		GoalType<Goal> type = GoalTypes.of(eatThenSleep());
		assertTrue(type.countable());
		assertEquals(2, type.needed(eatThenSleep()));
	}

	// ------------------------------------------------------------------ avoid

	private static final Goal.Avoid DONT_SLEEP =
			new Goal.Avoid(new Goal.UseBlock(BED), 2, new Condition.Time(Condition.Phase.NIGHT));

	@Test
	void avoidCompletesWhenTheWindowPassesWithoutTheAction() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 39; i++) {
			assertFalse(tick(DONT_SLEEP, night(), tracker), "tick " + i);
		}
		assertTrue(tick(DONT_SLEEP, night(), tracker), "40 ticks of 50 ms are the two seconds");
	}

	@Test
	void avoidRestartsQuietlyWhenTheActionHappens() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 30; i++) {
			assertFalse(tick(DONT_SLEEP, night(), tracker));
		}
		Observation slip = night();
		slip.usedBlocks.add(BED);
		assertFalse(tick(DONT_SLEEP, slip, tracker));
		assertEquals(0L, tracker.dwellMs, "the window starts over");
		assertEquals(1, tracker.count, "one restart");
		assertEquals("다시 해 봐요", status(DONT_SLEEP, night(), tracker));
		for (int i = 0; i < 39; i++) {
			assertFalse(tick(DONT_SLEEP, night(), tracker), "a whole new window, tick " + i);
		}
		assertTrue(tick(DONT_SLEEP, night(), tracker));
	}

	@Test
	void avoidSleepingForSeveralTicksKeepsRestarting() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (int i = 0; i < 100; i++) {
			Observation inBed = night();
			inBed.sleeping = true;
			inBed.sleepingBed = BED;
			assertFalse(tick(DONT_SLEEP, inBed, tracker));
		}
		assertEquals(0L, tracker.dwellMs);
	}

	@Test
	void avoidWindowOnlyRunsWhileTheConditionHolds() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation day = new Observation();
		day.clockTime = 1000L;
		for (int i = 0; i < 200; i++) {
			assertFalse(tick(DONT_SLEEP, day, tracker));
		}
		assertEquals(0L, tracker.dwellMs, "daylight does not run the window");
		Observation napInDaylight = new Observation();
		napInDaylight.clockTime = 1000L;
		napInDaylight.usedBlocks.add(BED);
		assertFalse(tick(DONT_SLEEP, napInDaylight, tracker));
		assertEquals(0, tracker.count, "a nap by day is not what was forbidden");
		assertEquals("지금 낮 · 밤 필요", status(DONT_SLEEP, day, tracker));
	}

	@Test
	void avoidShowsProgressAfterTheRetryNoticeFades() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation slip = night();
		slip.usedBlocks.add(BED);
		tick(DONT_SLEEP, slip, tracker);
		assertEquals("다시 해 봐요", status(DONT_SLEEP, night(), tracker));
		Goal.Avoid longer = new Goal.Avoid(new Goal.UseBlock(BED), 60, new Condition.Time(Condition.Phase.NIGHT));
		GoalEvaluator.Tracker slow = new GoalEvaluator.Tracker();
		tick(longer, slip, slow);
		for (int i = 0; i < 120; i++) {
			tick(longer, night(), slow);
		}
		assertEquals("10%", status(longer, night(), slow));
	}

	@Test
	void avoidDoesNotShowTheRetryNoticeAfterAReload() {
		Goal.Avoid longer = new Goal.Avoid(new Goal.UseBlock(BED), 60, new Condition.Time(Condition.Phase.NIGHT));
		GoalEvaluator.Tracker restored = new GoalEvaluator.Tracker();
		GoalEvaluator.seed(restored, longer, 2); // two restarts were saved
		tick(longer, night(), restored);
		assertEquals("0%", status(longer, night(), restored));
	}

	private static String status(Goal goal, Observation obs, GoalEvaluator.Tracker tracker) {
		return GoalTypes.of(goal).status(goal, obs, tracker, CATALOG, "");
	}

	// ------------------------------------------------------------- conditions

	@Test
	void holdingSkyAndWaterReadTheObservation() {
		Condition torch = new Condition.Holding(TORCH);
		assertFalse(GoalEvaluator.conditionMet(torch, new Observation()));
		assertTrue(GoalEvaluator.conditionMet(torch, holding(TORCH, null)));
		assertTrue(GoalEvaluator.conditionMet(torch, holding(null, TORCH)));
		assertFalse(GoalEvaluator.conditionMet(torch, holding("minecraft:stick", null)));

		Observation outside = new Observation();
		outside.canSeeSky = true;
		Observation indoors = new Observation();
		indoors.roofed = true;
		assertTrue(GoalEvaluator.conditionMet(new Condition.Sky(Condition.SkyState.OPEN), outside));
		assertFalse(GoalEvaluator.conditionMet(new Condition.Sky(Condition.SkyState.OPEN), indoors));
		assertTrue(GoalEvaluator.conditionMet(new Condition.Sky(Condition.SkyState.ROOFED), indoors));
		assertFalse(GoalEvaluator.conditionMet(new Condition.Sky(Condition.SkyState.ROOFED), outside));

		Observation swimming = new Observation();
		swimming.inWater = true;
		assertTrue(GoalEvaluator.conditionMet(new Condition.InWater(), swimming));
		assertFalse(GoalEvaluator.conditionMet(new Condition.InWater(), new Observation()));
	}

	private static Observation atBlock(String looked, double x, double y, double z) {
		Observation obs = new Observation();
		obs.lookedBlock = looked;
		obs.lookedBlockPos = new BlockPos(10, 64, 10);
		obs.position = new Vec3(x, y, z);
		return obs;
	}

	private static boolean rel(Relation relation, String anchor, Observation obs) {
		return GoalEvaluator.conditionMet(new Condition.PlayerRel(relation, anchor), obs);
	}

	@Test
	void playerRelFindsTheStandingPlaceAroundABlock() {
		String log = "minecraft:oak_log";
		assertTrue(rel(Relation.ON_TOP_OF, log, atBlock(log, 10.5, 65.0, 10.5)));
		assertFalse(rel(Relation.UNDER, log, atBlock(log, 10.5, 65.0, 10.5)));
		assertTrue(rel(Relation.UNDER, log, atBlock(log, 10.5, 62.0, 10.5)));
		assertFalse(rel(Relation.ON_TOP_OF, log, atBlock(log, 10.5, 62.0, 10.5)));
		assertTrue(rel(Relation.NEXT_TO, log, atBlock(log, 12.0, 64.0, 10.5)));
		assertFalse(rel(Relation.NEXT_TO, log, atBlock(log, 20.0, 64.0, 10.5)), "too far to be next to it");
		assertTrue(rel(Relation.NEAR, log, atBlock(log, 14.0, 64.0, 10.5)));
		assertFalse(rel(Relation.NEAR, log, atBlock(log, 30.0, 64.0, 10.5)));
		assertTrue(rel(Relation.INSIDE, log, atBlock(log, 10.5, 64.0, 10.5)));
		assertTrue(rel(Relation.OUTSIDE, log, atBlock(log, 14.5, 64.0, 10.5)));
		assertFalse(rel(Relation.OUTSIDE, log, atBlock(log, 10.5, 64.0, 10.5)));
		assertFalse(rel(Relation.BETWEEN, log, atBlock(log, 12.0, 64.0, 10.5)), "needs two anchors");
		assertFalse(rel(Relation.UNDER, "minecraft:stone", atBlock(log, 10.5, 62.0, 10.5)), "another block is looked at");
		assertFalse(rel(Relation.UNDER, log, new Observation()), "nothing looked at");
	}

	@Test
	void playerRelFrontAndBehindUseWhereTheEntityLooks() {
		String villager = "minecraft:villager";
		Observation inFront = new Observation();
		inFront.position = new Vec3(10.0, 64.0, 12.0);
		// yaw 0 is south: the villager looks toward +z, at a player standing two blocks south of it.
		inFront.addEntityPosition(new EntityFact(villager, UUID.randomUUID(), 10.0, 64.0, 10.0, 0.0F, 0.0F));
		assertTrue(rel(Relation.IN_FRONT_OF, villager, inFront));
		assertFalse(rel(Relation.BEHIND, villager, inFront));
		Observation behind = new Observation();
		behind.position = new Vec3(10.0, 64.0, 8.0);
		behind.addEntityPosition(new EntityFact(villager, UUID.randomUUID(), 10.0, 64.0, 10.0, 0.0F, 0.0F));
		assertTrue(rel(Relation.BEHIND, villager, behind));
		assertFalse(rel(Relation.IN_FRONT_OF, villager, behind));
		// A block has no front: looking at one never satisfies it.
		assertFalse(rel(Relation.IN_FRONT_OF, "minecraft:oak_door", atBlock("minecraft:oak_door", 10.5, 64.0, 12.0)));
	}

	// ------------------------------------------------------------- signatures

	private static String signature(Goal goal) {
		return GoalTypes.of(goal).taskSignature(goal);
	}

	@Test
	void signaturesTellTheSameWordsInDifferentTasksApart() {
		Goal bread = new Goal.ConsumeItem(BREAD, 1);
		Goal craft = new Goal.Craft(BREAD, 1, null, List.of());
		Goal give = new Goal.Give(BREAD, "minecraft:villager", null, 1);
		Goal giveCow = new Goal.Give(BREAD, "minecraft:cow", null, 1);
		Goal drop = new Goal.Give(BREAD, "minecraft:villager", "DROP_NEAR", 1);
		Goal putIn = new Goal.PutIn(BREAD, "minecraft:chest", 1);
		Goal putBarrel = new Goal.PutIn(BREAD, "minecraft:barrel", 1);
		List<String> all = new ArrayList<>(List.of(signature(bread), signature(craft), signature(give), signature(giveCow),
				signature(drop), signature(putIn), signature(putBarrel)));
		assertEquals(all.size(), all.stream().distinct().count(), "every one is its own task: " + all);
		assertTrue(signature(give).startsWith("GIVE:minecraft:bread>minecraft:villager:GIVE"), signature(give));
		assertTrue(signature(drop).endsWith("DROP_AT"), signature(drop));
	}

	@Test
	void sequenceSignatureKeepsTheOrderAndTheTargets() {
		Goal eat = new Goal.ConsumeItem(BREAD, 1);
		Goal sleep = new Goal.UseBlock(BED);
		Goal ab = new Goal.Sequence(List.of(eat, sleep), true, null);
		Goal ba = new Goal.Sequence(List.of(sleep, eat), true, null);
		assertNotEquals(signature(ab), signature(ba), "the order is part of the task");
		assertEquals(signature(new Goal.Sequence(List.of(eat, sleep), true, "AFTER")), signature(ab),
				"the connective is only wording");
		assertEquals(signature(new Goal.Sequence(List.of(eat, sleep), false, null)),
				signature(new Goal.Sequence(List.of(sleep, eat), false, null)), "any order is one task");
		assertNotEquals(signature(ab), signature(new Goal.Sequence(List.of(new Goal.ConsumeItem("minecraft:apple", 1), sleep),
				true, null)), "another target is another task");
		assertTrue(signature(ab).startsWith("SEQUENCE:CONSUME_ITEM:minecraft:bread:EAT>USE_BLOCK:"), signature(ab));
		assertNotEquals(signature(ab), signature(eat));
	}

	@Test
	void avoidAndHoldSignaturesContainWhatTheyWrap() {
		Goal sleep = new Goal.UseBlock(BED);
		Goal avoid = new Goal.Avoid(sleep, 60, null);
		assertEquals("AVOID:" + signature(sleep), signature(avoid));
		Goal hold = new Goal.HoldWhile(TORCH, new Goal.ReachBiome("minecraft:forest", 1000), null, 10);
		Goal holdNight = new Goal.HoldWhile(TORCH, null, new Condition.Time(Condition.Phase.NIGHT), 10);
		assertNotEquals(signature(hold), signature(holdNight));
		assertTrue(signature(holdNight).contains("HOLD"), signature(holdNight));
		assertTrue(signature(holdNight).contains("WALK") || signature(holdNight).contains("TIME"), signature(holdNight));
	}

	// ------------------------------------------------------------- validation

	private static QuestStep step(Goal goal, String clueEn, String clueDe) {
		return new QuestStep("s1", List.of(), goal, List.of(), null, clueDe, clueEn, null, null, null, null);
	}

	private static List<String> problems(Goal goal, int level, String clueEn, String clueDe) {
		List<String> problems = new ArrayList<>();
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step(goal, clueEn, clueDe), CATALOG, problems, level));
		return problems;
	}

	private static List<String> problems(Goal goal, int level) {
		return problems(goal, level, "Do it.", "Mach es.");
	}

	@Test
	void sequenceAndAvoidNeedLevelThree() {
		assertTrue(problems(eatThenSleep(), 2).stream().anyMatch(p -> p.contains("SEQUENCE needs koreanLevel 3")));
		assertEquals(List.of(), problems(eatThenSleep(), 3));
		assertEquals(List.of(), problems(eatThenSleep(), 5));
		Goal avoid = new Goal.Avoid(new Goal.UseBlock(BED), 60, new Condition.Time(Condition.Phase.NIGHT));
		assertTrue(problems(avoid, 1).stream().anyMatch(p -> p.contains("AVOID needs koreanLevel 3")));
		assertTrue(problems(avoid, 2).stream().anyMatch(p -> p.contains("AVOID needs koreanLevel 3")));
		assertEquals(List.of(), problems(avoid, 3));
	}

	@Test
	void translationsMustNotGiveAwayTheDecidingGrammar() {
		for (String clue : List.of("Eat bread before you sleep.", "First eat bread, then sleep.", "Sleep after eating.")) {
			assertFalse(problems(eatThenSleep(), 3, clue, "Mach es.").isEmpty(), clue);
		}
		for (String clue : List.of("Iss Brot, bevor du schläfst.", "Schlafe nach dem Essen.", "Erst essen, dann schlafen.")) {
			assertFalse(problems(eatThenSleep(), 3, "Do it.", clue).isEmpty(), clue);
		}
		assertEquals(List.of(), problems(eatThenSleep(), 3, "Do the two things in the order the Korean says.",
				"Mach die zwei Dinge in der Reihenfolge, die der koreanische Satz sagt."));
		Goal avoid = new Goal.Avoid(new Goal.UseBlock(BED), 60, null);
		for (String clue : List.of("Don't sleep at night.", "Do not sleep.", "Never sleep.", "Avoid the bed.")) {
			assertFalse(problems(avoid, 3, clue, "Mach es.").isEmpty(), clue);
		}
		assertFalse(problems(avoid, 3, "Do it.", "Schlafe nicht.").isEmpty());
		assertEquals(List.of(), problems(avoid, 3, "Do what the Korean says about the bed.", "Tu, was der koreanische Satz sagt."));
	}

	@Test
	void sequencePartsAreChecked() {
		Goal.Sequence three = new Goal.Sequence(List.of(new Goal.ConsumeItem(BREAD, 1), new Goal.UseBlock(BED),
				new Goal.PlaceBlock(TORCH, 1)), true, null);
		assertTrue(problems(three, 3).stream().anyMatch(p -> p.contains("exactly 2 parts")));
		Goal.Sequence nested = new Goal.Sequence(List.of(eatThenSleep(), new Goal.UseBlock(BED)), true, null);
		assertTrue(problems(nested, 3).stream().anyMatch(p -> p.contains("cannot be spoken as a clause")));
		Goal.Sequence badId = new Goal.Sequence(List.of(new Goal.ConsumeItem("Bread!", 1), new Goal.UseBlock(BED)), true, null);
		assertTrue(problems(badId, 3).stream().anyMatch(p -> p.contains("bad item")), problems(badId, 3).toString());
		Goal.Sequence badConnector = new Goal.Sequence(eatThenSleep().parts(), true, "WHILE");
		assertTrue(problems(badConnector, 3).stream().anyMatch(p -> p.contains("connector")));
		assertEquals(List.of(), problems(new Goal.Sequence(three.parts(), false, null), 3), "three parts in any order");
		assertTrue(problems(new Goal.Sequence(List.of(new Goal.UseBlock(BED)), true, null), 3).stream()
				.anyMatch(p -> p.contains("2 parts")));
	}

	@Test
	void otherNewGoalsRejectBadData() {
		assertTrue(problems(new Goal.Avoid(null, 60, null), 3).stream().anyMatch(p -> p.contains("simple goal")));
		assertTrue(problems(new Goal.Avoid(new Goal.UseBlock(BED), 0, null), 3).stream().anyMatch(p -> p.contains("windowSeconds")));
		assertTrue(problems(new Goal.Avoid(eatThenSleep(), 60, null), 3).stream().anyMatch(p -> p.contains("simple goal")));
		assertTrue(problems(new Goal.HoldWhile(TORCH, null, null, 10), 3).stream().anyMatch(p -> p.contains("exactly one")));
		assertTrue(problems(new Goal.HoldWhile(TORCH, new Goal.UseBlock(BED), new Condition.InWater(), 10), 3).stream()
				.anyMatch(p -> p.contains("exactly one")));
		assertTrue(problems(new Goal.HoldWhile(TORCH, null, new Condition.InWater(), 0), 3).stream().anyMatch(p -> p.contains("seconds")));
		assertTrue(problems(new Goal.Give(BREAD, "minecraft:villager", "SHOVE", 1), 3).stream().anyMatch(p -> p.contains("give method")));
		assertTrue(problems(new Goal.Give(BREAD, "Villager!", null, 1), 3).stream().anyMatch(p -> p.contains("bad entity")),
				"not a valid id");
		assertTrue(problems(new Goal.Craft(TORCH, 1, "anvil", List.of()), 3).stream().anyMatch(p -> p.contains("station")));
		assertTrue(problems(new Goal.Craft(TORCH, 1, null, List.of("Stick!")), 3).stream().anyMatch(p -> p.contains("ingredient")));
		assertTrue(problems(new Goal.PutIn(BREAD, "Chest!", 1), 3).stream().anyMatch(p -> p.contains("bad block")));
		assertTrue(problems(new Goal.Smelt("Sand!", "minecraft:glass", 1), 3).stream().anyMatch(p -> p.contains("bad item")));
		assertEquals(List.of(), problems(new Goal.Give(BREAD, "minecraft:villager", "DROP_NEAR", 1), 1));
		assertEquals(List.of(), problems(new Goal.HoldWhile(TORCH, null, new Condition.Time(Condition.Phase.NIGHT), 10), 3));
	}

	@Test
	void newConditionsRejectBadAnchors() {
		List<String> problems = new ArrayList<>();
		ValidationContext context = new ValidationContext("t/s1", step(new Goal.UseBlock(BED), "x", "x"), CATALOG, problems, 3);
		validate(new Condition.PlayerRel(Relation.BETWEEN, "minecraft:oak_log"), context);
		assertTrue(problems.stream().anyMatch(p -> p.contains("BETWEEN")));
		problems.clear();
		validate(new Condition.PlayerRel(Relation.IN_FRONT_OF, "minecraft:oak_door"), context);
		assertTrue(problems.stream().anyMatch(p -> p.contains("needs an entity anchor")), problems.toString());
		problems.clear();
		validate(new Condition.PlayerRel(Relation.IN_FRONT_OF, "minecraft:villager"), context);
		validate(new Condition.PlayerRel(Relation.UNDER, "minecraft:oak_log"), context);
		validate(new Condition.Holding(TORCH), context);
		assertEquals(List.of(), problems);
		validate(new Condition.Holding("Torch!"), context);
		validate(new Condition.PlayerRel(Relation.UNDER, "Log!"), context);
		assertEquals(2, problems.size());
	}

	private static void validate(Condition condition, ValidationContext context) {
		ConditionTypes.of(condition).validate(condition, context);
	}

	// ---------------------------------------------------------------- the demo

	private static JsonObject demoJson() throws IOException {
		try (InputStream stream = QuestLoader.class.getResourceAsStream(QuestLoader.BASE + "mill_demo.json")) {
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}

	@Test
	void theDemoTrailIsValidAtLevelThreeAndRefusedBelow() throws IOException {
		JsonObject json = demoJson();
		assertEquals("village_errands_demo", json.get("id").getAsString());
		Questline trail = QuestLoader.parse(json);
		var log = LoggerFactory.getLogger("action-goals");
		assertEquals(List.of(), QuestValidator.validate(List.of(trail), CATALOG, log).problems());
		json.addProperty("koreanLevel", 2);
		QuestValidator.Result low = QuestValidator.validate(List.of(QuestLoader.parse(json)), CATALOG, log);
		assertTrue(low.problems().stream().anyMatch(p -> p.contains("SEQUENCE needs koreanLevel 3")), low.problems().toString());
		assertEquals(List.of("s1", "s2", "s3"), low.trails().get(0).steps().stream().map(QuestStep::id).toList(),
				"the step that cannot be taught at this level is dropped");
	}

	@Test
	void theDemoSaysEachErrandTheWayThePlayerReadsIt() throws IOException {
		Questline trail = QuestLoader.parse(demoJson());
		SentenceBuilder.Namer namer = KoreanFixtures.namer(CATALOG);
		List<String> spoken = new ArrayList<>();
		for (QuestStep step : trail.allSteps()) {
			spoken.add(SentenceBuilder.normal(step.goal(), CATALOG.byConceptId(step.teach().get(0)).orElse(null),
					step.conditions(), trail.koreanLevel(), "finale".equals(step.id()), namer));
		}
		assertEquals(List.of("밀을 모으고 빵을 만드세요.", "주민에게 빵을 주세요.", "상자 안에 사과를 넣으세요.",
				"침대에서 자기 전에 빵을 드세요.", "횃불을 들고 밤에 걸으세요."), spoken);
		// The translations do not give the order away.
		for (QuestStep step : trail.allSteps()) {
			assertFalse(step.clueEn().toLowerCase().contains("before"), step.clueEn());
		}
	}

	// ------------------------------------------------------------------- json

	@Test
	void nestedGoalsAndConditionsSurviveAJsonRoundTrip() {
		List<Goal> goals = List.of(
				new Goal.Craft(TORCH, 2, "crafting_table", List.of("minecraft:stick", "minecraft:coal")),
				new Goal.Smelt("minecraft:sand", "minecraft:glass", 3),
				new Goal.Smelt(null, "minecraft:cooked_beef", 1),
				new Goal.Give(BREAD, "minecraft:villager", "DROP_NEAR", 2),
				new Goal.PutIn("minecraft:apple", "minecraft:chest", 4),
				new Goal.HoldWhile(TORCH, new Goal.BreakBlock("minecraft:stone", 3, null), null, 10),
				new Goal.HoldWhile(TORCH, null, new Condition.Time(Condition.Phase.NIGHT), 15),
				eatThenSleep(),
				new Goal.Sequence(eatThenSleep().parts(), false, null),
				new Goal.Avoid(new Goal.UseBlock(BED), 90, new Condition.Time(Condition.Phase.NIGHT)),
				new Goal.Avoid(new Goal.ConsumeItem(BREAD, 1), 30, null));
		for (Goal goal : goals) {
			assertEquals(goal, GoalTypes.parse(GoalTypes.toJson(goal)), goal.toString());
		}
		for (Condition condition : List.<Condition>of(new Condition.Holding(TORCH), new Condition.Sky(Condition.SkyState.ROOFED),
				new Condition.InWater(), new Condition.PlayerRel(Relation.BEHIND, "minecraft:villager"))) {
			assertEquals(condition, ConditionTypes.parse(ConditionTypes.toJson(condition)), condition.toString());
		}
	}

	@Test
	void theSharedRelationEnumHasTheAgreedValues() {
		assertEquals(List.of("ON_TOP_OF", "UNDER", "NEXT_TO", "IN_FRONT_OF", "BEHIND", "INSIDE", "OUTSIDE", "BETWEEN", "NEAR"),
				Arrays.stream(Relation.values()).map(Enum::name).toList());
	}

	@Test
	void eachProgressKeyNamesItsParts() {
		assertNotEquals(GoalEvaluator.key(eatThenSleep()),
				GoalEvaluator.key(new Goal.Sequence(eatThenSleep().parts(), false, null)));
		assertNotEquals(GoalEvaluator.key(new Goal.Give(BREAD, "minecraft:villager", null, 1)),
				GoalEvaluator.key(new Goal.Give(BREAD, "minecraft:cow", null, 1)));
	}
}
