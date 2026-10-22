package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Counters;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sentence generator's naming rules (the weaknesses BATCH-001 ran into): the gift is named by its
 * own name, an anchor by its declared kind, the finale by the taught word, relation sentences without
 * repeated material, a status line for every goal, the verbs 올라가다 and 들어가다, relaxed teaching
 * and the counters of counters.json.
 */
class GeneratorFixesTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, Condition... conditions) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(conditions), 1, false,
				NAMER);
	}

	private static String ceremony(Goal goal, String concept, Condition... conditions) {
		return SentenceBuilder.ceremony(goal, concept == null ? null : word(concept), List.of(conditions),
				KoreanFixtures.registryNamer());
	}

	// ------------------------------------------------------------------ 1. GIVE names the gift itself

	@Test
	void theCatDoesNotGetACatWhenTheCodIsItsPicture() {
		Goal cod = new Goal.Give("minecraft:cod", "minecraft:cat", null, 1);
		assertEquals("고양이에게 물고기를 줘요.", clue(cod, "cat"));
		assertEquals("고양이에게 물고기를 줘요.", SentenceBuilder.hint(cod, word("cat"), List.of(), NAMER));
		assertEquals("고양이에게 물고기를 줘요.", clue(new Goal.Give("minecraft:salmon", "minecraft:cat", null, 1), "cat"));
		// a wolf shown by a bone gets the bone by its own name
		assertEquals("늑대에게 뼈를 줘요.", clue(new Goal.Give("minecraft:bone", "minecraft:wolf", null, 1), "wolf"));
		// the same gift when the step teaches the gift or nothing
		assertEquals("고양이에게 물고기를 줘요.", clue(cod, "fish"));
		assertEquals("고양이에게 물고기를 줘요.", clue(cod, null));
	}

	@Test
	void theFinaleOfAGiftIsNamedTheSameWay() {
		Goal salmon = new Goal.Give("minecraft:salmon", "minecraft:cat", null, 1);
		assertEquals("고양이에게 익히지 않은 연어를 줬어요.", ceremony(salmon, "cat"));
	}

	@Test
	void aWordsPictureNeverNamesAnItem() {
		List<String> leaks = new ArrayList<>();
		for (Word word : CATALOG.trailWords()) {
			String icon = word.iconItem;
			if (icon == null || word.referents.contains(icon) || word.covers.contains(icon)) {
				continue;
			}
			if (SentenceContext.wordNames(word, ObjectKind.ITEM, icon)
					&& word.referents.stream().noneMatch(ref -> io.github.leks2205.hangulquest.quest.goal.Matching
							.matchesBlock(icon, ref))
					&& !"tree".equals(word.conceptId)) {
				leaks.add(word.ko + " names its picture " + icon);
			}
		}
		assertEquals(List.of(), leaks);
		// and the lookup the namers use resolves referents only
		assertNotEquals("고양이", NAMER.item("minecraft:cod"));
		assertNotEquals("소", NAMER.item("minecraft:beef"));
	}

	// ------------------------------------------------------- 2. an anchor is an entity when it can be

	@Test
	void chickenCodAndSalmonAreTheAnimalsNotTheMeat() {
		Condition.PlayerRel chicken = new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken");
		Goal torch = new Goal.PlaceBlock("minecraft:torch", 1);
		assertEquals("닭 옆에서 횃불을 놓아요.", clue(torch, "torch", chicken));
		for (String id : new String[]{"cod", "salmon"}) {
			String entity = KoreanFixtures.koNames().get("entity.minecraft." + id);
			String item = KoreanFixtures.koNames().get("item.minecraft." + id);
			String said = clue(torch, "torch", new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:" + id));
			assertTrue(said.startsWith(entity + " 옆에서"), said);
			if (!entity.equals(item)) {
				assertFalse(said.contains(item), said);
			}
		}
		// a declared kind wins over the guess
		String raw = clue(torch, "torch", new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken", "item"));
		assertFalse(raw.startsWith("닭 옆에서"), raw);
		assertEquals(raw, clue(torch, "torch", new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken", "block")));
		// a block stays a block
		assertEquals("통나무 옆에서 횃불을 놓아요.", clue(torch, "torch",
				new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:oak_log")));
	}

	@Test
	void theKindOfAnAnchorIsReadAndWrittenBack() {
		var json = io.github.leks2205.hangulquest.quest.condition.ConditionTypes
				.toJson(new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken", "entity"));
		assertEquals("entity", json.get("kind").getAsString());
		assertEquals(new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken", "entity"),
				io.github.leks2205.hangulquest.quest.condition.ConditionTypes.parse(json));
		var plain = io.github.leks2205.hangulquest.quest.condition.ConditionTypes
				.toJson(new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:oak_log"));
		assertFalse(plain.has("kind"));
	}

	// ---------------------------------------------------------------- 3. the finale says the taught word

	@Test
	void theFinaleNamesTheWordTheStepTeaches() {
		Goal milk = new Goal.ConsumeItem("minecraft:milk_bucket", 1);
		assertEquals("우유를 마셨어요.", ceremony(milk, "milk"));
		assertEquals("우유 양동이를 마셨어요.", ceremony(milk, null));
		assertEquals("우유를 마셔요.", clue(milk, "milk"));
		assertEquals("꽃을 세 송이 심었어요.", ceremony(new Goal.PlaceBlock("minecraft:poppy", 3), "flower"));
	}

	@Test
	void theFinaleLeavesOutTheMaterialItRepeats() {
		Goal gate = new Goal.PlaceRel("minecraft:oak_fence_gate", Relation.NEXT_TO, "minecraft:oak_fence", 1);
		assertEquals("울타리 옆에 문을 달았어요.", ceremony(gate, null));
		assertEquals("울타리 옆에 문을 달아요.", SentenceBuilder.normal(gate, null, List.of(), 1, false,
				KoreanFixtures.registryNamer()));
		// the word for the door; the anchor keeps its name
		Goal door = new Goal.PlaceRel("minecraft:oak_door", Relation.NEXT_TO, "minecraft:oak_fence", 1);
		assertEquals("참나무 울타리 옆에 문을 달았어요.", ceremony(door, "door"));
		assertEquals("울타리 옆에 문을 달았어요.", ceremony(door, null));
	}

	@Test
	void sharedWordsAreCutKeepingOneWordEach() {
		assertEquals(List.of("울타리", "문"), List.of(SpatialSentences.shorten("참나무 울타리", "참나무 울타리 문")));
		assertEquals(List.of("울타리", "판자"), List.of(SpatialSentences.shorten("참나무 울타리", "참나무 판자")));
		assertEquals(List.of("돌", "계단"), List.of(SpatialSentences.shorten("돌", "돌 계단")));
		assertEquals(List.of("문", "문"), List.of(SpatialSentences.shorten("문", "문")));
		assertEquals(List.of("횃불", "문"), List.of(SpatialSentences.shorten("횃불", "문")));
	}

	// ----------------------------------------------- 4. an anchor is named by the word the step teaches

	@Test
	void aLogAnchorIsATreeWhenTheStepTeachesTheTree() {
		Goal torch = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:logs", 1);
		assertEquals("나무 옆에 횃불을 놓아요.", clue(torch, "tree"));
		assertEquals("통나무 옆에 횃불을 놓아요.", clue(torch, null));
		assertEquals("통나무 옆에 횃불을 놓아요.", clue(torch, "log"));
		assertEquals("통나무 옆에 횃불을 놓았어요.", ceremony(torch, null));
		// any other block family is not a tree
		assertEquals("판자 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO,
				"minecraft:oak_planks", 1), "planks"));
		assertEquals("나무 옆에 횃불을 놓았어요.", ceremony(torch, "tree"));
	}

	@Test
	void theWordForTheAnchorTeachesAndTheObjectKeepsItsName() {
		Goal torch = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:doors", 1);
		String said = clue(torch, "door");
		assertEquals("문 옆에 횃불을 놓아요.", said);
	}

	// -------------------------------------------------------------------- 5. a status for every goal

	private static String status(Goal goal, Observation obs, GoalEvaluator.Tracker tracker) {
		QuestStep step = new QuestStep("s1", List.of("stone"), goal, List.of(), null, null, null, null, null);
		return QuestStatus.text(step, obs, tracker, CATALOG);
	}

	@Test
	void buildingShowsHowMuchStands() {
		Goal.BuildPattern pillar = new Goal.BuildPattern(Pattern.PILLAR, "minecraft:cobblestone", 3);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = new Observation();
		assertEquals("0/3", status(pillar, obs, tracker));

		World world = new World().put(100, 64, 100, "minecraft:cobblestone");
		obs.addPlacement(world.placed(new BlockPos(100, 64, 100)));
		assertFalse(GoalEvaluator.tick(pillar, List.of(), obs, tracker));
		assertEquals("1/3", status(pillar, obs, tracker));

		world.put(100, 65, 100, "minecraft:cobblestone");
		obs = new Observation();
		obs.addPlacement(world.placed(new BlockPos(100, 65, 100)));
		assertFalse(GoalEvaluator.tick(pillar, List.of(), obs, tracker));
		assertEquals("2/3", status(pillar, obs, tracker));

		world.put(100, 66, 100, "minecraft:cobblestone");
		obs = new Observation();
		obs.addPlacement(world.placed(new BlockPos(100, 66, 100)));
		assertTrue(GoalEvaluator.tick(pillar, List.of(), obs, tracker));
		assertEquals("3/3", status(pillar, obs, tracker));
	}

	@Test
	void aWrongBlockDoesNotCountAsProgress() {
		Goal.BuildPattern pillar = new Goal.BuildPattern(Pattern.PILLAR, "minecraft:cobblestone", 3);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		World world = new World().put(100, 64, 100, "minecraft:dirt");
		Observation obs = new Observation();
		obs.addPlacement(world.placed(new BlockPos(100, 64, 100)));
		GoalEvaluator.tick(pillar, List.of(), obs, tracker);
		assertEquals("0/3", status(pillar, obs, tracker));
	}

	@Test
	void placingOnAStructureShowsTheCounter() {
		Goal.PlaceOnPattern onPillar = new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR,
				"minecraft:cobblestone");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = new Observation();
		assertEquals("0/1", status(onPillar, obs, tracker));
		obs.addPlacement(SpatialWorlds.placementFor(onPillar, false));
		assertTrue(GoalEvaluator.tick(onPillar, List.of(), obs, tracker));
		assertEquals("1/1", status(onPillar, obs, tracker));
	}

	@Test
	void everyGoalTypeWithoutConditionsHasAStatusLine() {
		List<Goal> goals = List.of(new Goal.BuildPattern(Pattern.RING, "minecraft:stone", 3),
				new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:stone"),
				new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "minecraft:oak_door", 1),
				new Goal.Give("minecraft:cod", "minecraft:cat", null, 1),
				new Goal.PutIn("minecraft:bread", "minecraft:chest", 1),
				new Goal.Craft("minecraft:torch", 1, null, List.of()),
				new Goal.ReachHeight(80, 320));
		for (Goal goal : goals) {
			assertFalse(status(goal, new Observation(), new GoalEvaluator.Tracker()).isBlank(),
					goal.getClass().getSimpleName() + " has no status line");
		}
	}

	// ----------------------------------------------------------- 6. 올라가다 and 들어가다 have a goal

	@Test
	void climbingHasALevelOneSentence() {
		assertEquals("언덕 위로 올라가요.", clue(new Goal.ReachHeight(70, 320), "mountain"));
		assertEquals("산 위로 올라가요.", clue(new Goal.ReachHeight(110, 320), "mountain"));
		assertEquals("언덕 위로 올라갔어요.", ceremony(new Goal.ReachHeight(70, 320), "mountain"));
		// numbers belong to the next level
		assertEquals("높이 팔십 블록 이상으로 올라가세요.", SentenceBuilder.normal(new Goal.ReachHeight(80, 320),
				word("mountain"), List.of(), 2, false, NAMER).replace("올라가요", "올라가세요"));
	}

	@Test
	void aCaveIsEntered() {
		Goal lush = new Goal.ReachBiome("minecraft:lush_caves", 1000);
		assertEquals("동굴로 들어가요.", clue(lush, "cave"));
		assertEquals("동굴로 들어갔어요.", ceremony(lush, "cave"));
		assertEquals(io.github.leks2205.hangulquest.quest.Verb.ENTER, SentenceBuilder.verbFor(lush));
		// every other biome is still gone to
		assertEquals(io.github.leks2205.hangulquest.quest.Verb.GO,
				SentenceBuilder.verbFor(new Goal.ReachBiome("minecraft:forest", 1000)));
		assertEquals("숲으로 가요.", clue(new Goal.ReachBiome("minecraft:forest", 1000), "forest"));
	}

	// ------------------------------------------------------ 7. a step may teach any role of its goal

	private static List<String> problems(Goal goal, List<Condition> conditions, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, conditions, null, null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	@Test
	void aStepMayTeachTheAnchorTheRecipientOrTheHeldItem() {
		Goal torch = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:doors", 1);
		assertEquals(List.of(), problems(torch, List.of(), "torch"));
		assertEquals(List.of(), problems(torch, List.of(), "door"));
		assertEquals(1, problems(torch, List.of(), "cow").size());

		Goal give = new Goal.Give("minecraft:wheat", "minecraft:cow", null, 1);
		assertEquals(List.of(), problems(give, List.of(), "cow"));
		assertEquals(List.of(), problems(give, List.of(), "wheat"));
		assertEquals(1, problems(give, List.of(), "door").size());

		Goal hold = new Goal.HoldWhile("minecraft:water_bucket", new Goal.PlaceRel("minecraft:torch",
				Relation.NEXT_TO, "#minecraft:logs", 1), null, 5);
		assertEquals(List.of(), problems(hold, List.of(), "water"));
		assertEquals(List.of(), problems(hold, List.of(), "torch"));

		Goal bread = new Goal.ConsumeItem("minecraft:bread", 1);
		assertEquals(List.of(), problems(bread, List.of(new Condition.PlayerRel(Relation.UNDER, "minecraft:oak_log")),
				"log"));
		assertEquals(List.of(), problems(bread, List.of(new Condition.Holding("minecraft:torch")), "torch"));
		assertEquals(List.of(), problems(bread, List.of(new Condition.PlayerRel(Relation.NEXT_TO,
				"minecraft:chicken")), "chicken"));
		assertEquals(1, problems(bread, List.of(new Condition.Holding("minecraft:torch")), "door").size());
	}

	@Test
	void theTaughtWordStillHasToBeInTheSentence() {
		List<String> missing = new ArrayList<>();
		for (Object[] row : new Object[][]{
				{new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:doors", 1), "door"},
				{new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:doors", 1), "torch"},
				{new Goal.Give("minecraft:wheat", "minecraft:cow", null, 1), "cow"},
				{new Goal.Give("minecraft:wheat", "minecraft:cow", null, 1), "wheat"},
				{new Goal.PutIn("minecraft:bread", "minecraft:chest", 1), "chest"},
				{new Goal.PutIn("minecraft:bread", "minecraft:chest", 1), "bread"},
				{new Goal.HoldWhile("minecraft:water_bucket", new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO,
						"#minecraft:logs", 1), null, 5), "water"},
				{new Goal.HoldWhile("minecraft:water_bucket", new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO,
						"#minecraft:logs", 1), null, 5), "torch"}}) {
			Goal goal = (Goal) row[0];
			Word word = word((String) row[1]);
			String clue = SentenceBuilder.normal(goal, word, List.of(), 1, false, NAMER);
			String hint = SentenceBuilder.hint(goal, word, List.of(), NAMER);
			if (!clue.contains(word.ko) || !hint.contains(word.ko)) {
				missing.add(word.ko + " missing: " + clue + " / " + hint);
			}
		}
		assertEquals(List.of(), missing);
		// a word that is only in a condition is spoken in the condition
		String said = clue(new Goal.ConsumeItem("minecraft:bread", 1), "chicken",
				new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:chicken"));
		assertTrue(said.contains("닭 옆에서"), said);
	}

	// ------------------------------------------------------------------------------------ counters

	@Test
	void theCounterFitsTheThingCounted() {
		assertEquals("종이를 세 장 만들어요.", clue(new Goal.Craft("minecraft:paper", 3, null, List.of()), "paper"));
		assertEquals("책을 세 권 만들어요.", clue(new Goal.Craft("minecraft:book", 3, null, List.of()), "book"));
		assertEquals("꽃을 세 송이 심어요.", clue(new Goal.PlaceBlock("minecraft:poppy", 3), "flower"));
		assertEquals("횃불을 세 개 놓아요.", clue(new Goal.PlaceBlock("minecraft:torch", 3), "torch"));
		assertEquals("우유를 두 잔 마셔요.", clue(new Goal.ConsumeItem("minecraft:milk_bucket", 2), "milk"));
		assertEquals("연어를 두 마리 모아요.", clue(new Goal.AcquireItem("minecraft:salmon", 2), "salmon"));
	}

	@Test
	void theCounterTableDecidesById() {
		Counters counters = Counters.shared();
		assertEquals("송이", counters.counter("minecraft:dandelion", null));
		assertEquals("장", counters.counter("minecraft:red_carpet", "양탄자"));
		assertEquals("그루", counters.counter("minecraft:oak_sapling", "묘목"));
		assertEquals("개", counters.counter("minecraft:stone", "돌"));
		assertEquals("개", counters.counter(null, null));
		// a house is counted 채 only as a word; the block under it is not
		assertEquals("개", counters.counter("minecraft:oak_planks", "집"));
	}

	@Test
	void buildingCountsItsMaterialWithTheRightCounter() {
		Goal.BuildPattern row = new Goal.BuildPattern(Pattern.ROW, "minecraft:red_carpet", 3);
		assertEquals("빨간색 양탄자 세 장을 나란히 깔아요.", SentenceBuilder.normal(row, null, List.of(), 1, false, NAMER));
		Goal.BuildPattern stoneRow = new Goal.BuildPattern(Pattern.ROW, "minecraft:stone", 3);
		assertEquals("돌 세 개를 나란히 놓아요.", SentenceBuilder.normal(stoneRow, word("stone"), List.of(), 1, false, NAMER));
	}
}
