package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.PatternMatcher;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 12 (what the first level-4 batches hit): -(으)러 steps and their conditions, a shot without a
 * target, longer pillars and rows, a step that teaches nothing, boats that are ridden, a repeated container,
 * time of day where there is none, slips against a -(으)면 clause, heights read as absolute, the beacon.
 */
class GeneratorWaveTwelveTest {
	private static final Logger LOG = LoggerFactory.getLogger("wave12");
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();

	@TempDir
	Path tempDir;

	private static String clue(Goal goal, List<Condition> conditions, int level) {
		return SentenceBuilder.normal(goal, null, conditions, level, false, REGISTRY);
	}

	private static String clue(Goal goal, int level) {
		return clue(goal, List.of(), level);
	}

	private static List<String> problems(Goal goal, List<Condition> conditions, int level) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, conditions, null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems, level));
		return problems;
	}

	private static QuestStep step(String id, List<String> teach, Goal goal, List<Condition> conditions) {
		return new QuestStep(id, teach, goal, conditions, null, null, null, null, null, null, null);
	}

	private static Questline trail(int level, Questline.Requires requires, QuestStep first) {
		return new Questline("probe", "시험", "Probe", 1, List.of(), requires, List.of(first),
				step("finale", List.of("stone"), new Goal.BreakBlock("minecraft:stone", 1, null), List.of()),
				"끝", "Ende", null, 1, level, false);
	}

	private static List<String> trailProblems(Questline trail) {
		return QuestValidator.validate(List.of(trail), CATALOG, LOG).problems();
	}

	// ------------------------------------------------------------ 1. -(으)러 가다 says its own biome

	private static final Goal.Sequence TO_CHOP = new Goal.Sequence(List.of(
			new Goal.ReachBiome("#minecraft:is_forest", 1000), new Goal.BreakBlock("minecraft:oak_log", 1, null)),
			true, "EUREO");
	private static final Condition IN_FOREST = new Condition.InBiome("#minecraft:is_forest");

	@Test
	void i1_aTripStepWhoseOnlyConditionIsItsBiomeIsAsSpokenAsItCanBe() {
		List<Condition> conditions = List.of(IN_FOREST);
		String plain = clue(TO_CHOP, 4);
		String spoken = clue(TO_CHOP, conditions, 4);
		assertEquals(plain, spoken, "the trip already says 숲으로");
		assertTrue(QuestlineValidationTest.speaksItsConditions(TO_CHOP, conditions, plain, spoken));
	}

	@Test
	void i1_aConditionTheGoalDoesNotSayStillHasToBeSpoken() {
		List<Condition> conditions = List.of(IN_FOREST, new Condition.Time(Condition.Phase.NIGHT));
		assertTrue(QuestlineValidationTest.speaksItsConditions(TO_CHOP, conditions, clue(TO_CHOP, 4),
				clue(TO_CHOP, conditions, 4)));
		assertFalse(QuestlineValidationTest.speaksItsConditions(TO_CHOP, conditions, "같아요.", "같아요."),
				"a night nobody speaks is still caught");
	}

	// ------------------------------------------------------------ 2. a shot without a target

	@Test
	void i2_aTargetlessShootHasNoBadItem() {
		for (String weapon : List.of("minecraft:bow", "minecraft:crossbow", "minecraft:trident", "minecraft:snowball",
				"minecraft:egg")) {
			Goal shoot = new Goal.Shoot(weapon, null, null, 1, 0, 0);
			assertEquals(List.of(), problems(shoot, List.of(), 3), weapon);
		}
		assertEquals("활을 쏘세요.", clue(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0), 2));
	}

	@Test
	void i2_aTargetlessShootStillChecksItsWeaponAndTheTaughtWord() {
		assertFalse(problems(new Goal.Shoot("minecraft:stick", null, null, 1, 0, 0), List.of(), 3).isEmpty());
		var stone = CATALOG.concept("stone").orElseThrow();
		assertFalse(GoalTypes.of(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0))
				.covers(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0), stone), "a word for stone is not the bow");
	}

	// ------------------------------------------------------------ 3. pillars and rows up to seven

	@Test
	void i3_aPillarAndARowMayBeSevenLong() {
		assertTrue(Pattern.PILLAR.validSize(7));
		assertFalse(Pattern.PILLAR.validSize(8));
		assertTrue(Pattern.ROW.validSize(7));
		assertFalse(Pattern.ROW.validSize(8));
		assertFalse(Pattern.WALL.validSize(5), "a wall stays at four");
		assertFalse(Pattern.STAIRS.validSize(5));
	}

	@Test
	void i3_theViewIsWideEnoughForTheLastBlockAtTheEndOfASevenPillar() {
		for (String material : List.of("minecraft:stone", "minecraft:oak_log", "#minecraft:logs", "#minecraft:planks")) {
			for (Pattern pattern : List.of(Pattern.PILLAR, Pattern.ROW)) {
				Goal.BuildPattern build = new Goal.BuildPattern(pattern, material, 7);
				var fact = SpatialWorlds.placementFor(build, false);
				assertFalse(PatternMatcher.builtWith(fact.around(), pattern, 7, material).isEmpty(), pattern + " " + material);
				assertEquals(List.of(), problems(build, List.of(), 3), pattern + " " + material);
			}
		}
	}

	@Test
	void i3_aThingOnTopOfASixPillarIsSeenButNotOnASevenOne() {
		Goal.PlaceOnPattern six = new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR,
				"minecraft:oak_log", 6);
		assertFalse(SpatialWorlds.placementFor(six, false).around().isAir(0, -6, 0));
		Goal.PlaceOnPattern seven = new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR,
				"minecraft:oak_log", 7);
		boolean refused = false;
		try {
			SpatialWorlds.placementFor(seven, false);
		} catch (AssertionError tooBig) {
			refused = true;
		}
		assertTrue(refused, "the block on top sees only six below it");
	}

	@Test
	void i3_relationsStillLookOnlyThreeBlocksAround() {
		World world = new World().put(105, 64, 100, "minecraft:stone").put(100, 64, 100, "minecraft:torch");
		var fact = world.placed(new BlockPos(100, 64, 100));
		assertFalse(io.github.leks2205.hangulquest.quest.spatial.Relations.holds(Relation.NEAR,
				new io.github.leks2205.hangulquest.quest.spatial.Anchor.Block("minecraft:stone"), fact),
				"five blocks away is not 근처, as before the wider view");
		assertEquals(13, BlockNeighborhood.SIZE);
		assertEquals(3, BlockNeighborhood.NEAR);
	}

	// ------------------------------------------------------------ 4. a step that teaches nothing

	@Test
	void i4_aStepWithoutATaughtWordIsReported() {
		String steps = "{\"id\": \"s1\", %s \"goal\": {\"type\": \"BREAK_BLOCK\", \"block\": \"minecraft:stone\", \"count\": 1}}";
		for (String teach : List.of("", "\"teach\": [],")) {
			var root = com.google.gson.JsonParser.parseString("{\"id\": \"probe\", \"steps\": [" + steps.formatted(teach) + "]}")
					.getAsJsonObject();
			List<String> problems = io.github.leks2205.hangulquest.quest.StrictFields.problems(root);
			assertTrue(problems.stream().anyMatch(p -> p.contains("teaches no word")), teach + " " + problems);
		}
		var fine = com.google.gson.JsonParser.parseString("{\"id\": \"probe\", \"steps\": ["
				+ steps.formatted("\"teach\": [\"stone\"],") + "]}").getAsJsonObject();
		assertEquals(List.of(), io.github.leks2205.hangulquest.quest.StrictFields.problems(fine));
	}

	// ------------------------------------------------------------ 5. the container once

	private static final Goal.Sequence SMOKE = new Goal.Sequence(List.of(
			new Goal.PutIn("minecraft:potato", "minecraft:smoker", 1),
			new Goal.TakeOut("minecraft:baked_potato", "minecraft:smoker", 1)), true, "EOSEO");

	@Test
	void i5_theSecondAnchorOfAPutInAndTakeOutIsDropped() {
		assertEquals("훈연기 안에 감자를 넣어서 구운 감자를 꺼내야 해요.", clue(SMOKE, 4));
		assertEquals("훈연기 안에 감자를 넣어서 구운 감자를 꺼냈어요.",
				SentenceBuilder.ceremony(SMOKE, null, List.of(), REGISTRY));
	}

	@Test
	void i5_anotherContainerIsStillNamed() {
		Goal.Sequence other = new Goal.Sequence(List.of(new Goal.PutIn("minecraft:potato", "minecraft:smoker", 1),
				new Goal.TakeOut("minecraft:bread", "minecraft:chest", 1)), true, "EOSEO");
		assertTrue(clue(other, 4).contains("상자에서"), clue(other, 4));
	}

	// ------------------------------------------------------------ 6. a boat is ridden

	@Test
	void i6_aBoatAMinecartAnAnimalAreRidden() {
		for (String[] row : new String[][]{{"minecraft:oak_boat", "참나무 보트를 타세요.", "참나무 보트를 탔어요."},
				{"minecraft:birch_boat", "자작나무 보트를 타세요.", "자작나무 보트를 탔어요."},
				{"minecraft:minecart", "광산 수레를 타세요.", "광산 수레를 탔어요."},
				{"minecraft:horse", "말을 타세요.", "말을 탔어요."},
				{"minecraft:strider", "스트라이더를 타세요.", "스트라이더를 탔어요."},
				{"minecraft:camel", "낙타를 타세요.", "낙타를 탔어요."}}) {
			Goal.Mount mount = new Goal.Mount(row[0], 1500);
			assertEquals(row[1], clue(mount, 2), row[0]);
			assertEquals(row[2], SentenceBuilder.ceremony(mount, null, List.of(), REGISTRY), row[0]);
			assertEquals(Verb.RIDE, GoalTypes.of(mount).verb(mount, VerbTable.shared()), row[0]);
		}
	}

	@Test
	void i6_aStepThatTeachesSittingStillFitsABoat() {
		Goal.Mount boat = new Goal.Mount("minecraft:oak_boat", 1500);
		assertTrue(GoalTypes.of(boat).alternativeVerbs(boat).contains(Verb.SIT));
	}

	// ------------------------------------------------------------ 7. no day in the Nether and the End

	@Test
	void i7_aTimeOfDayInTheNetherOrTheEndIsReported() {
		Goal stone = new Goal.BreakBlock("minecraft:stone", 1, null);
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		for (String dimension : List.of("minecraft:the_nether", "minecraft:the_end")) {
			List<String> viaCondition = trailProblems(trail(2, null,
					step("s1", List.of("stone"), stone, List.of(new Condition.InDimension(dimension), night))));
			assertTrue(viaCondition.stream().anyMatch(p -> p.contains("no day and night")), viaCondition.toString());
			List<String> viaTrail = trailProblems(trail(2, new Questline.Requires(0, dimension, List.of()),
					step("s1", List.of("stone"), stone, List.of(night))));
			assertTrue(viaTrail.stream().anyMatch(p -> p.contains("no day and night")), viaTrail.toString());
		}
	}

	@Test
	void i7_theOverworldAndWeatherAreUntouched() {
		Goal stone = new Goal.BreakBlock("minecraft:stone", 1, null);
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		assertEquals(List.of(), trailProblems(trail(2, new Questline.Requires(0, "minecraft:overworld", List.of()),
				step("s1", List.of("stone"), stone, List.of(night)))));
		assertEquals(List.of(), trailProblems(trail(2, null, step("s1", List.of("stone"), stone, List.of(night)))));
		assertEquals(List.of(), trailProblems(trail(2, null, step("s1", List.of("stone"), stone,
				List.of(new Condition.InDimension("minecraft:the_nether"),
						new Condition.NearBlock("minecraft:lava", 5.0))))));
	}

	// ------------------------------------------------------------ 8. a slip against a -(으)면 clause

	private static final Goal PLACE_TORCH = new Goal.PlaceBlock("minecraft:torch", 1);
	private static final Condition NIGHT = new Condition.Time(Condition.Phase.NIGHT);

	private static Observation placing(long clockTime) {
		Observation obs = new Observation();
		obs.placedBlocks.add("minecraft:torch");
		obs.clockTime = clockTime;
		return obs;
	}

	@Test
	void i8_theActDoneWhileTheClauseDoesNotHoldIsASlipButDoesNotCount() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(PLACE_TORCH, List.of(NIGHT), placing(1000L), tracker));
		assertEquals(1, tracker.conditionSlips);
		assertEquals(0, tracker.count, "the torch placed by day does not count");
		assertEquals(0, tracker.formErrors, "it is not a slip of the goal's own form");
	}

	@Test
	void i8_nothingDoneOrTheRightTimeIsNoSlip() {
		GoalEvaluator.Tracker idle = new GoalEvaluator.Tracker();
		Observation nothing = new Observation();
		nothing.clockTime = 1000L;
		GoalEvaluator.tick(PLACE_TORCH, List.of(NIGHT), nothing, idle);
		assertEquals(0, idle.conditionSlips);
		GoalEvaluator.Tracker right = new GoalEvaluator.Tracker();
		assertTrue(GoalEvaluator.tick(PLACE_TORCH, List.of(NIGHT), placing(14000L), right));
		assertEquals(0, right.conditionSlips);
	}

	@Test
	void i8_aConditionWithoutAClauseFormIsNoSlipOfThatForm() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = placing(14000L);
		GoalEvaluator.tick(PLACE_TORCH, List.of(NIGHT, new Condition.InDimension("minecraft:the_nether")), obs, tracker);
		assertEquals(0, tracker.conditionSlips, "the dimension failed, and it has no -(으)면");
	}

	@Test
	void i8_aDwellOrADistanceIsNeverASlip() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = new Observation();
		obs.clockTime = 1000L;
		GoalEvaluator.tick(new Goal.Walk("NORTH", 20), List.of(NIGHT), obs, tracker);
		assertEquals(0, tracker.conditionSlips);
	}

	private LearningService learning() {
		ProgressStore store = new ProgressStore(tempDir, LOG);
		return new LearningService(store.data(), store, CATALOG);
	}

	private static final class Quiet implements QuestEngine.Feedback {
		@Override
		public void banner(String ko, String de, boolean success) {
		}

		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}
	}

	@Test
	void i8_theLearningSideHearsOfItOnlyWhereTheIfClauseIsWhatThePlayerRead() {
		for (int known : List.of(0, 3)) { // the grammar levels the player has met: the clue reads level known + 1
			LearningService learn = learning();
			for (int g = 1; g <= known; g++) {
				for (int i = 0; i < io.github.leks2205.hangulquest.quest.grammar.Ladder.shared().gateSteps(); i++) {
					learn.grammar().ladderStep(g);
				}
			}
			QuestStep torch = step("s1", List.of("stone"), PLACE_TORCH, List.of(NIGHT));
			Questline trail = new Questline("probe" + known, "시험", "Probe", 1, List.of(), null, List.of(torch),
					step("finale", List.of("stone"), new Goal.PlaceBlock("minecraft:sand", 1), List.of()),
					"끝", "Ende", null, 1, 4, false);
			QuestEngine engine = new QuestEngine(List.of(trail), CATALOG, learn, LOG);
			Quiet feedback = new Quiet();
			Observation start = new Observation();
			start.now = 10_000L;
			engine.startTrail(trail.id(), start, feedback);
			assertEquals(known == 3 ? "밤이 되면 횃불을 놓아야 해요." : "밤에 횃불을 놓아요.",
					engine.clueKo(engine.currentStep()));
			for (int i = 0; i < 6; i++) {
				Observation obs = placing(1000L);
				obs.now = 10_100L + i * 100L;
				engine.tick(obs, feedback);
			}
			assertEquals("s1", engine.currentStep().id());
			assertEquals(known == 3 ? 3 : 0, learn.grammar().answers("L4.myeon"),
					"known " + known + ": three slips at most, none where -(으)면 was not read");
		}
	}

	// ------------------------------------------------------------ 9 and 12. an absolute height

	@Test
	void i9_aHeightIsAtLeastNotUpward() {
		assertEquals("높이 팔십 블록 이상으로 올라가세요.", clue(new Goal.ReachHeight(80, 320), 2));
		assertEquals("높이 구십 블록 이상으로 올라가세요.", clue(new Goal.ReachHeight(90, 320), 2));
		assertEquals("높이 백 블록 이상으로 올라가세요.", clue(new Goal.ReachHeight(100, 320), 2));
		assertEquals("높이 0 이하로 내려가세요.", clue(new Goal.ReachHeight(-64, 0), 2), "zero is a height, not a distance");
	}

	@Test
	void i12_aHeightAboveAHundredStaysInDigitsWithTheSameWord() {
		assertEquals("높이 150블록 이상으로 올라가세요.", clue(new Goal.ReachHeight(150, 320), 2));
		assertEquals("높이 이백 블록 이상으로 올라가세요.", clue(new Goal.ReachHeight(200, 320), 2));
	}

	@Test
	void i9_aHeightIsStillAHillOrAMountainOnTheFirstLevel() {
		assertEquals("언덕 위로 올라가요.", clue(new Goal.ReachHeight(80, 320), 1));
	}

	// ------------------------------------------------------------ 10. the beacon is used, not opened

	@Test
	void i10_aBeaconIsUsedAndTheOthersKeepTheirVerbs() {
		for (String[] row : new String[][]{{"beacon", "신호기를 사용하세요."}, {"lodestone", "자석석을 사용하세요."},
				{"respawn_anchor", "리스폰 정박기를 충전하세요."}, {"bell", "종을 울리세요."},
				{"grindstone", "숫돌을 사용하세요."}, {"stonecutter", "석재 절단기를 사용하세요."}, {"loom", "베틀을 사용하세요."},
				{"smithing_table", "대장장이 작업대를 사용하세요."}, {"cartography_table", "지도 제작대를 사용하세요."},
				{"anvil", "모루를 사용하세요."}, {"enchanting_table", "마법 부여대를 여세요."}, {"crafting_table", "제작대를 여세요."},
				{"brewing_stand", "양조기를 여세요."},
				{"chest", "상자를 여세요."}}) {
			assertEquals(row[1], clue(new Goal.UseBlock("minecraft:" + row[0]), 2), row[0]);
		}
		assertEquals(Verb.OPERATE, VerbTable.shared().choose("USE_BLOCK", "minecraft:beacon", 1, Verb.USE));
	}

	// ------------------------------------------------------------ 13. obsidian is mined

	@Test
	void i13_thePickaxeBlocksTheTableSaysMine() {
		for (String block : List.of("obsidian", "crying_obsidian", "end_stone", "netherrack", "stone", "deepslate")) {
			Goal.BreakBlock mine = new Goal.BreakBlock("minecraft:" + block, 1, null);
			assertEquals(Verb.MINE, GoalTypes.of(mine).verb(mine, VerbTable.shared()), block);
		}
		assertEquals("흑요석을 캐세요.", clue(new Goal.BreakBlock("minecraft:obsidian", 1, null), 2));
	}

	// ------------------------------------------------------------ 16. one 동안 in a sentence

	@Test
	void i16_holdingWhileLookingSaysDongAnOnce() {
		Goal look = new Goal.HoldWhile("minecraft:torch", new Goal.LookAtEntity("minecraft:piglin", 1000), null, 0);
		String five = SentenceBuilder.normal(look, null, List.of(), 5, false, REGISTRY);
		assertEquals("횃불을 들고 있는 동안 피글린을 바라봐야 해요.", five);
		assertEquals(1, five.split("동안", -1).length - 1, five);
		// below the level the item is held with -고, and the look keeps its length
		assertEquals("횃불을 들고 피글린을 일 초 동안 바라보세요.", SentenceBuilder.normal(look, null, List.of(), 2, false, REGISTRY));
		Goal block = new Goal.HoldWhile("minecraft:torch", new Goal.LookAtBlock("minecraft:stone", 3000), null, 0);
		assertEquals("횃불을 들고 있는 동안 돌을 바라봐야 해요.", SentenceBuilder.normal(block, null, List.of(), 5, false, REGISTRY));
	}

	// ------------------------------------------------------------ 17. the colour stays when the object is taught

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	@Test
	void i17_aTaughtObjectKeepsItsColourAsAnAdjective() {
		assertEquals("파란 침대를 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:blue_bed", 1), word("bed"),
				List.of(), 5, false, REGISTRY));
		assertEquals("하얀 침대를 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:white_bed", 1), word("bed"),
				List.of(), 5, false, REGISTRY));
		assertEquals("하얀 양털을 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:white_wool", 1), word("wool"),
				List.of(), 5, false, REGISTRY));
		assertEquals("검은 양털을 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:black_wool", 1), word("wool"),
				List.of(), 6, false, REGISTRY));
	}

	@Test
	void i17_belowLevelFiveTheColourIsANoun() {
		assertEquals("파란색 침대를 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:blue_bed", 1), word("bed"),
				List.of(), 4, false, REGISTRY));
		assertEquals("파란색 침대를 놓으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:blue_bed", 1), word("bed"),
				List.of(), 3, false, REGISTRY));
		assertEquals("노란색 침대를 놓으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:yellow_bed", 1), word("bed"),
				List.of(), 3, false, REGISTRY));
	}

	@Test
	void i23_aTaughtWordKeepsTheColourOfEveryColouredVariantFromLevelThree() {
		assertEquals("빨간색 침대를 놓으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:red_bed", 1), word("bed"),
				List.of(), 3, false, REGISTRY));
		assertTrue(SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:red_candle", 1), word("candle"),
				List.of(), 3, false, REGISTRY).startsWith("빨간색 초"));
		assertEquals("빨간 침대를 놓아야 해요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:red_bed", 1), word("bed"),
				List.of(), 5, false, REGISTRY));
	}

	@Test
	void i24_aVariantWithAnOwnLongerNameKeepsItButAKindWordStandsForItsMembers() {
		assertEquals("영혼 모닥불을 일 초 동안 바라보세요.", SentenceBuilder.normal(new Goal.LookAtBlock("minecraft:soul_campfire", 1000),
				word("campfire"), List.of(), 2, false, REGISTRY));
		assertEquals("영혼 횃불을 놓으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:soul_torch", 1), word("torch"),
				List.of(), 2, false, REGISTRY));
		assertEquals("횃불을 놓으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:torch", 1), word("torch"),
				List.of(), 2, false, REGISTRY));
		assertEquals("울타리 문을 여세요.", SentenceBuilder.normal(new Goal.UseBlock("minecraft:spruce_fence_gate"), word("gate"),
				List.of(), 2, false, REGISTRY), "a spruce gate is any gate");
		assertEquals("묘목을 심으세요.", SentenceBuilder.normal(new Goal.PlaceBlock("minecraft:spruce_sapling", 1), word("sapling"),
				List.of(), 2, false, REGISTRY), "a word for a whole kind stands for each member");
	}

	@Test
	void i17_theGrammarPointOfTheAdjectiveFollowsTheColour() {
		Goal blue = new Goal.PlaceBlock("minecraft:blue_bed", 1);
		assertTrue(SentenceBuilder.grammar(blue, word("bed"), List.of(), 5, false, REGISTRY).contains("L5.adn_adj_eun"));
		assertFalse(SentenceBuilder.grammar(blue, word("bed"), List.of(), 4, false, REGISTRY).contains("L5.adn_adj_eun"));
	}

	@Test
	void i17_aHintSpeaksTheColourAsANounWhenTheClueSaysItAtAll() {
		assertEquals("검은색 양털을 놓아요.", SentenceBuilder.hint(new Goal.PlaceBlock("minecraft:black_wool", 1), null, List.of(), REGISTRY));
		Word bed = word("bed");
		Word wool = word("wool");
		Goal blue = new Goal.PlaceBlock("minecraft:blue_bed", 1);
		Goal white = new Goal.PlaceBlock("minecraft:white_wool", 1);
		assertEquals("파란색 침대를 놓아요.", SentenceBuilder.hint(blue, bed, null, List.of(), REGISTRY, 3));
		assertEquals("파란색 침대를 놓아요.", SentenceBuilder.hint(blue, bed, null, List.of(), REGISTRY, 5), "a hint stays a noun");
		assertEquals("하얀색 양털을 놓아요.", SentenceBuilder.hint(white, wool, null, List.of(), REGISTRY, 4));
		assertEquals("침대를 놓아요.", SentenceBuilder.hint(blue, bed, null, List.of(), REGISTRY, 2), "level 2 leaves the colour out");
	}

	// ------------------------------------------------------------ 18. a chest can be opened

	@Test
	void i18_aChestOrABarrelIsOpenedByUsingItAndMayBeAvoided() {
		Goal.UseBlock chest = new Goal.UseBlock("minecraft:chest", "OPEN");
		assertEquals(List.of(), problems(chest, List.of(), 3));
		Goal.Avoid avoid = new Goal.Avoid(chest, 60, null);
		assertEquals(List.of(), problems(avoid, List.of(), 3));
		assertEquals("상자를 열지 마세요.", clue(avoid, 3));
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation used = new Observation();
		used.usedBlocks.add("minecraft:chest");
		assertTrue(GoalEvaluator.tick(chest, List.of(), used, tracker), "the click on the chest is the opening");
	}

	@Test
	void i18_closingAChestIsStillNotSomethingTheClientSees() {
		assertFalse(problems(new Goal.UseBlock("minecraft:chest", "CLOSE"), List.of(), 3).isEmpty());
		assertFalse(problems(new Goal.UseBlock("minecraft:stone", "OPEN"), List.of(), 3).isEmpty());
	}

	// ------------------------------------------------------------ 19. a wait is told apart by its length and end

	@Test
	void i19_twoWaitsOfDifferentLengthOrEndAreTwoTasks() {
		var type = GoalTypes.of(new Goal.Wait(10, null));
		assertFalse(type.taskSignature(new Goal.Wait(10, null)).equals(type.taskSignature(new Goal.Wait(20, null))));
		assertFalse(type.taskSignature(new Goal.Wait(10, "NIGHT")).equals(type.taskSignature(new Goal.Wait(10, "DAY"))));
		assertFalse(type.taskSignature(new Goal.Wait(10, null)).equals(type.taskSignature(new Goal.Wait(10, "NIGHT"))));
		assertEquals(type.taskSignature(new Goal.Wait(10, null)), type.taskSignature(new Goal.Wait(10, null)));
	}

	// ------------------------------------------------------------ 20. a rule that cannot bite

	@Test
	void i20_aBedIsOnlyAvoidedWhereItCouldBeUsed() {
		Goal.UseBlock bed = new Goal.UseBlock("minecraft:red_bed");
		for (Condition.Phase phase : List.of(Condition.Phase.DUSK, Condition.Phase.DAY, Condition.Phase.DAWN)) {
			List<String> problems = problems(new Goal.Avoid(bed, 60, new Condition.Time(phase)), List.of(), 5);
			assertTrue(problems.stream().anyMatch(p -> p.contains("cannot bite")), phase + " " + problems);
		}
		assertEquals(List.of(), problems(new Goal.Avoid(bed, 60, new Condition.Time(Condition.Phase.NIGHT)), List.of(), 5));
		assertEquals(List.of(), problems(new Goal.Avoid(bed, 60, new Condition.WeatherState(Condition.Weather.THUNDER)), List.of(), 5));
		assertEquals(List.of(), problems(new Goal.Avoid(new Goal.PlaceBlock("minecraft:torch", 1), 60,
				new Condition.Time(Condition.Phase.DUSK)), List.of(), 5), "a torch can be placed at dusk");
	}

	// ------------------------------------------------------------ 21. the gift hint uses the verb of the clue

	@Test
	void i21_aGiftToAVillagerIsGivenInTheHintToo() {
		Goal clock = new Goal.Give("minecraft:clock", "minecraft:villager", null, 1);
		assertEquals("주민에게 시계를 줘요.", SentenceBuilder.hint(clock, null, List.of(), REGISTRY));
		assertEquals("주민에게 시계를 줘야 해요.", clue(clock, 4));
	}

	// ------------------------------------------------------------ 28. heights are bucketed finer

	@Test
	void i28_heightsAboveAHundredAreToldApartByFifty() {
		var type = GoalTypes.of(new Goal.ReachHeight(100, 320));
		String h100 = type.taskSignature(new Goal.ReachHeight(100, 320));
		assertEquals(h100, type.taskSignature(new Goal.ReachHeight(120, 320)));
		assertFalse(h100.equals(type.taskSignature(new Goal.ReachHeight(150, 320))));
		assertFalse(type.taskSignature(new Goal.ReachHeight(150, 320)).equals(type.taskSignature(new Goal.ReachHeight(200, 320))));
		assertFalse(h100.equals(type.taskSignature(new Goal.ReachHeight(90, 320))));
	}

	// ------------------------------------------------------------ 29. a Y range reads 높이 0 이하

	@Test
	void i29_aDeepYRangeKeepsTheHeightWording() {
		Goal breeze = new Goal.DefeatEntity("minecraft:breeze", 1);
		assertEquals("높이 0 이하에서 브리즈를 한 마리 물리쳐야 해요.",
				clue(breeze, List.of(new Condition.YRange(-64, 0)), 4));
		assertEquals("높이 구십 블록 위에서 브리즈를 한 마리 물리쳐야 해요.",
				clue(breeze, List.of(new Condition.YRange(90, 320)), 4));
	}

	// ------------------------------------------------------------ 30. a lamp block is put down

	@Test
	void i30_aCopperBulbIsPlacedNotHung() {
		assertEquals(Verb.PLACE, VerbTable.shared().choose("PLACE_BLOCK", "minecraft:copper_bulb", 1, Verb.PLACE));
		assertEquals(Verb.PLACE, VerbTable.shared().choose("PLACE_BLOCK", "minecraft:waxed_oxidized_copper_bulb", 2, Verb.PLACE));
		assertEquals("구리 전구를 놓으세요.", clue(new Goal.PlaceBlock("minecraft:copper_bulb", 1), 2));
		assertEquals(Verb.HANG_DOOR, VerbTable.shared().choose("PLACE_BLOCK", "minecraft:oak_door", 1, Verb.PLACE),
				"a door is still hung");
	}

	// ------------------------------------------------------------ 31. a friendly creature is not defeated

	@Test
	void i31_aFriendlyCreatureIsHarmedNotDefeatedInAnAvoid() {
		Goal.Avoid villager = new Goal.Avoid(new Goal.DefeatEntity("minecraft:villager", 1), 60, null);
		assertEquals("주민을 해치지 마세요.", clue(villager, 3));
		assertEquals("주민을 해치지 않았어요.", SentenceBuilder.ceremony(villager, null, List.of(), REGISTRY));
		assertEquals("소를 해치지 마세요.", clue(new Goal.Avoid(new Goal.DefeatEntity("minecraft:cow", 1), 60, null), 3));
		// an enemy is still defeated, and a plain task still says 물리치다
		assertEquals("좀비를 물리치지 마세요.", clue(new Goal.Avoid(new Goal.DefeatEntity("minecraft:zombie", 1), 60, null), 3));
		assertEquals("좀비를 한 마리 물리치세요.", clue(new Goal.DefeatEntity("minecraft:zombie", 1), 2));
	}
}
