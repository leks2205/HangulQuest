package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.TradeFact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 3, group B: the limits the batch manifests of BATCH-002 to BATCH-005 named. One test per item, the
 * item number in the name.
 */
class GeneratorWaveThreeTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);
	private static final String TORCH = "minecraft:torch";

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, Condition... conditions) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(conditions), 1, false, NAMER);
	}

	private static String hint(Goal goal, String concept, String... more) {
		List<Word> extra = new ArrayList<>();
		for (String id : more) {
			extra.add(word(id));
		}
		return SentenceBuilder.hint(goal, concept == null ? null : word(concept), null, extra, List.of(), NAMER);
	}

	private static String ceremony(Goal goal, String concept) {
		return SentenceBuilder.ceremony(goal, concept == null ? null : word(concept), List.of(), NAMER);
	}

	private static List<String> problems(Goal goal, List<Condition> conditions, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, conditions, null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static Accepted accepted(Goal goal, String concept) {
		Concept taught = ConceptLoader.load().stream().filter(c -> c.id.equals(concept)).findFirst().orElseThrow();
		QuestStep step = new QuestStep("s1", List.of(concept), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		return Accepted.of(step, taught);
	}

	// ------------------------------------------------------------------------ 3. a biome step names the biome

	@Test
	void b3_aStepThatTeachesAFrogOnASwampSaysSwamp() {
		Goal swamp = new Goal.ReachBiome("minecraft:swamp", 1000);
		Condition frog = new Condition.NearEntity("minecraft:frog", 12);
		assertEquals("개구리 근처에서 늪으로 가요.", clue(swamp, "frog", frog));
		assertEquals("늪으로 가요.", clue(swamp, "frog"));
		assertEquals("늪에 도착했어요.", ceremony(swamp, "frog"));
		// the word of the biome itself is still the biome
		assertEquals("숲으로 가요.", clue(new Goal.ReachBiome("minecraft:forest", 1000), "forest"));
		assertEquals("바다로 가요.", clue(new Goal.ReachBiome("#minecraft:is_ocean", 1000), "ocean"));
		// a tag goal and a word that is no biome
		assertEquals("바다로 가요.", clue(new Goal.ReachBiome("#minecraft:is_ocean", 1000), "squid"));
	}

	// ------------------------------------------------------------------------------ 4. biomes have covers

	@Test
	void b4_theCaveWordCoversTheLushCavesAndAnyCoveredBiomeCounts() {
		Goal lush = new Goal.ReachBiome("minecraft:lush_caves", 1000);
		Goal dripstone = new Goal.ReachBiome("minecraft:dripstone_caves", 1000);
		assertEquals(List.of(), problems(lush, List.of(), "cave"));
		assertEquals("동굴로 들어가요.", clue(lush, "cave"));
		Accepted accepted = accepted(dripstone, "cave");
		assertTrue(accepted.widens("minecraft:dripstone_caves"));

		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = accepted;
		Observation inLush = new Observation();
		inLush.biome = "minecraft:lush_caves";
		inLush.now = 1000;
		assertFalse(GoalEvaluator.tick(dripstone, List.of(), inLush, tracker));
		Observation later = new Observation();
		later.biome = "minecraft:lush_caves";
		later.now = 1100;
		for (long t = 1100; t <= 3000; t += 50) {
			later.now = t;
			if (GoalEvaluator.tick(dripstone, List.of(), later, tracker)) {
				return;
			}
		}
		throw new AssertionError("the lush caves did not count for the word 동굴");
	}

	@Test
	void b4_aBiomeOfAnotherKindIsNotCovered() {
		Goal desert = new Goal.ReachBiome("minecraft:desert", 1000);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = accepted(new Goal.ReachBiome("minecraft:dripstone_caves", 1000), "cave");
		Observation obs = new Observation();
		obs.biome = "minecraft:desert";
		for (long t = 1000; t <= 3000; t += 100) {
			obs.now = t;
			assertFalse(GoalEvaluator.tick(new Goal.ReachBiome("minecraft:dripstone_caves", 1000), List.of(), obs, tracker));
		}
		assertFalse(problems(desert, List.of(), "cave").isEmpty());
	}

	// -------------------------------------------- 5. the ceremony reuses the clue's noun; C16 the word covers

	@Test
	void c16_aWordSpeaksEveryIdItCovers() {
		Goal spruce = new Goal.PlaceBlock("minecraft:spruce_sapling", 1);
		assertEquals("묘목을 놓아요.", clue(spruce, "sapling").replace("심어", "놓아"), "the word, not 가문비나무 묘목");
		assertEquals("묘목을 심어요.", clue(spruce, "sapling"));
		assertEquals("묘목을 심었어요.", ceremony(spruce, "sapling"));
		assertEquals("가문비나무 묘목을 심어요.", clue(spruce, null));
		// the first sapling of the concept is spoken the same
		assertEquals("묘목을 심어요.", clue(new Goal.PlaceBlock("minecraft:oak_sapling", 1), "sapling"));
		// a word that does not cover it says the registry name
		assertEquals("가문비나무 묘목을 심어요.", clue(spruce, "stone"));
	}

	@Test
	void b5_theCeremonyIsTheClueInThePast() {
		Goal beehive = new Goal.PlaceRel("minecraft:beehive", Relation.UNDER, "minecraft:spruce_log", 1);
		assertEquals("통나무 아래에 벌통을 놓아요.", clue(beehive, "log"));
		assertEquals("통나무 아래에 벌통을 놓았어요.", ceremony(beehive, "log"));
		// the finale is not taught a word of its own: the word picking is the one of the clue
		Goal birch = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:birch_log", 1);
		assertEquals(clue(birch, null).replace("놓아요.", "놓았어요."), ceremony(birch, null));
	}

	@Test
	void c15_aConcreteAnchorInTheFinaleReadsLikeInTheClue() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:oak_log", 1);
		String clue = clue(goal, null);
		assertEquals("통나무 옆에 횃불을 놓아요.", clue);
		assertEquals("통나무 옆에 횃불을 놓았어요.", ceremony(goal, null));
		QuestStep finale = new QuestStep("finale", List.of(), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		assertEquals("통나무 옆에 횃불을 놓았어요.", KoreanFixtures.ceremony(finale));
		// and the registry-name namer is what the old fixture used
		assertEquals("참나무 원목 옆에 횃불을 놓았어요.", SentenceBuilder.ceremony(goal, null, List.of(),
				KoreanFixtures.registryNamer()));
	}

	// ------------------------------------------------------------------------ 6. smelting a covered input

	@Test
	void b6_aFishInTheFurnaceAcceptsTheCookedFormOfEveryFish() {
		Goal cod = new Goal.Smelt("minecraft:cod", "minecraft:cooked_cod", 1);
		assertEquals("물고기를 구워요.", clue(cod, "fish"));
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = accepted(cod, "fish");
		Observation salmon = new Observation();
		salmon.addCrafted(new io.github.leks2205.hangulquest.sense.CraftFact("minecraft:cooked_salmon", 1, "furnace"));
		assertTrue(GoalEvaluator.tick(cod, List.of(), salmon, tracker), "cooked salmon is the cooked form of a fish");

		GoalEvaluator.Tracker wrong = new GoalEvaluator.Tracker();
		wrong.accepted = accepted(cod, "fish");
		Observation chicken = new Observation();
		chicken.addCrafted(new io.github.leks2205.hangulquest.sense.CraftFact("minecraft:cooked_chicken", 1, "furnace"));
		assertFalse(GoalEvaluator.tick(cod, List.of(), chicken, wrong));

		// a word that covers no input leaves the output exact
		GoalEvaluator.Tracker exact = new GoalEvaluator.Tracker();
		exact.accepted = accepted(cod, "stone");
		assertFalse(GoalEvaluator.tick(cod, List.of(), salmon, exact));
		// and a word with one referent never widens
		assertFalse(accepted(new Goal.Smelt("minecraft:potato", "minecraft:baked_potato", 1), "potato")
				.matches("minecraft:baked_potato", "minecraft:cooked_cod"));
	}

	// ------------------------------------------------------------------------ 7. the ring is a 담, not a 울타리

	@Test
	void b7_aRingOfStoneIsAWallAndOnlyFencesMakeAFence() {
		assertEquals("돌로 담을 쌓아요.", clue(new Goal.BuildPattern(Pattern.RING, "minecraft:stone", 3), null));
		assertEquals("판자로 담을 쌓아요.", clue(new Goal.BuildPattern(Pattern.RING, "#minecraft:planks", 3), null));
		assertEquals("울타리를 쳐요.", clue(new Goal.BuildPattern(Pattern.RING, "#minecraft:fences", 3), null));
		assertEquals("울타리를 쳐요.", clue(new Goal.BuildPattern(Pattern.RING, "minecraft:oak_fence", 3), null));
		assertEquals("울타리를 쳤어요.", ceremony(new Goal.BuildPattern(Pattern.RING, "minecraft:oak_fence", 3), null));
		assertEquals("가문비나무 울타리를 쳐요.", SentenceBuilder.normal(new Goal.BuildPattern(Pattern.RING,
				"minecraft:spruce_fence", 3), null, List.of(), 1, false, KoreanFixtures.registryNamer()));
		assertEquals(Verb.FENCE, SentenceBuilder.verbFor(new Goal.BuildPattern(Pattern.RING, "minecraft:oak_fence", 3)));
		assertEquals(Verb.STACK, SentenceBuilder.verbFor(new Goal.BuildPattern(Pattern.RING, "minecraft:stone", 3)));
		assertEquals("돌담 안에 횃불을 놓아요.", clue(new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING,
				"minecraft:stone"), null));
		assertEquals("울타리 안에 횃불을 놓아요.", clue(new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING,
				"minecraft:oak_fence"), null));
		assertFalse(Pattern.ringOfFences("minecraft:oak_planks"));
		assertTrue(Pattern.ringOfFences("#minecraft:fences"));
	}

	// -------------------------------------------------------------------------------- 8. 눈 블록 stays 눈 블록

	@Test
	void b8_theSnowBlockIsNeverShortenedToSnow() {
		Goal block = new Goal.PlaceBlock("minecraft:snow_block", 1);
		assertEquals("눈 블록을 놓아요.", clue(block, "snow"));
		assertEquals("눈 블록을 놓아요.", clue(block, "snow_block"));
		assertEquals("눈 블록을 놓았어요.", ceremony(block, "snow"));
		assertEquals("눈을 쌓아요.", clue(new Goal.PlaceBlock("minecraft:snow", 1), "snow"));
		assertEquals("눈 블록 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:snow_block", 1), "snow"));
		assertEquals(List.of(), problems(block, List.of(), "snow"), "the word 눈 still covers the block");
	}

	// ------------------------------------------------------------------ 10. a hint that names every noun

	@Test
	void b10_aMultiWordHintNamesEveryNoun() {
		Goal goal = new Goal.PlaceRel("minecraft:birch_door", Relation.NEXT_TO, "minecraft:spruce_planks", 1);
		String one = hint(goal, "door");
		String all = hint(goal, "door", "planks");
		assertEquals("판자 옆에 문을 달아요.", all);
		assertEquals("가문비나무 판자 옆에 문을 달아요.", one, "alone the anchor keeps its registry name");
		// a noun the goal has no place for cannot be named: it needs an authored hint
		assertEquals(all, hint(goal, "door", "planks", "stone"));
	}

	// -------------------------------------------------------------- 11. a verb can be the first taught word

	@Test
	void b11_aClueHoldsAConjugatedFormOfItsVerb() {
		assertTrue(Conjugator.contains("언덕 위로 올라가요.", "올라가다"));
		assertTrue(Conjugator.contains("산에 올라갔어요.", "올라가다"));
		assertTrue(Conjugator.contains("횃불을 놓으세요.", "놓다"));
		assertTrue(Conjugator.contains("침대에 누워요.", "눕다"), "ㅂ-irregular");
		assertTrue(Conjugator.contains("노래를 들어요.", "듣다"), "ㄷ-irregular");
		assertTrue(Conjugator.contains("빵을 구워요.", "굽다"));
		assertFalse(Conjugator.contains("사람을 봐요.", "사다"), "사람 is no form of 사다");
		assertFalse(Conjugator.contains("언덕 위로 가요.", "올라가다"));
		assertFalse(Conjugator.contains("", "올라가다"));
		assertTrue(Conjugator.contains("올라가다", "올라가다"), "the dictionary form itself");
		// and the validator takes a verb as the first word when the goal can say it
		assertEquals(List.of(), problems(new Goal.ReachHeight(80, 320), List.of(), "climb"));
		assertEquals(List.of(), problems(new Goal.Craft("minecraft:torch", 1, null, List.of()), List.of(), "make"));
		assertFalse(problems(new Goal.Craft("minecraft:torch", 1, null, List.of()), List.of(), "eat").isEmpty());
	}

	// -------------------------------------------------------------------------------- 12. fluids as anchors

	@Test
	void b12_waterIsAnAnchorAndCanBeLookedAt() {
		assertEquals("물 위에 수련잎을 놓아요.", clue(new Goal.PlaceRel("minecraft:lily_pad", Relation.ON_TOP_OF,
				"minecraft:water", 1), null));
		Goal look = new Goal.LookAtBlock("minecraft:water", 800);
		assertEquals("물을 일 초 동안 바라봐요.", clue(look, "water"));
		assertEquals(List.of(), problems(look, List.of(), "water"));
		Observation obs = new Observation();
		assertFalse(GoalEvaluator.tick(look, List.of(), obs, new GoalEvaluator.Tracker()));
		assertTrue(obs.fluidLookWanted, "the sensor looks through the surface only while a step needs it");
		Observation stone = new Observation();
		GoalEvaluator.tick(new Goal.LookAtBlock("minecraft:stone", 800), List.of(), stone, new GoalEvaluator.Tracker());
		assertFalse(stone.fluidLookWanted);
		// water seen for long enough
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i < 30 && !done; i++) {
			Observation water = new Observation();
			water.now = 1000 + i * 50L;
			water.lookedBlock = "minecraft:water";
			done = GoalEvaluator.tick(look, List.of(), water, tracker);
		}
		assertTrue(done);
	}

	// ----------------------------------------------------------------------- 13. a tag as the PLAYER_REL anchor

	@Test
	void b13_aPlayerRelationCanPointAtATag() {
		Goal torch = new Goal.PlaceBlock(TORCH, 1);
		Condition logs = new Condition.PlayerRel(Relation.UNDER, "#minecraft:logs");
		assertEquals("통나무 아래에서 횃불을 놓아요.", clue(torch, null, logs));
		assertEquals("나무 아래에서 횃불을 놓아요.", clue(torch, "tree", logs));
		Condition doors = new Condition.PlayerRel(Relation.NEXT_TO, "#minecraft:doors");
		assertEquals("문 옆에서 횃불을 놓아요.", clue(torch, null, doors));
		assertFalse(clue(torch, null, logs).contains("그것"));
		var type = io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(logs);
		List<String> bad = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of("log"), torch, List.of(logs), null, null, "Do it.", null, null,
				null, null);
		type.validate(logs, new ValidationContext("t/s1", step, CATALOG, bad));
		assertEquals(List.of(), bad);
		type.validate(new Condition.PlayerRel(Relation.UNDER, "#minecraft:nonsense"),
				new ValidationContext("t/s1", step, CATALOG, bad));
		assertFalse(bad.isEmpty());
		// a looked-at oak log is under the tag
		Observation obs = new Observation();
		obs.lookedBlock = "minecraft:birch_log";
		obs.lookedBlockPos = new net.minecraft.core.BlockPos(10, 64, 10);
		obs.position = new net.minecraft.world.phys.Vec3(10.5, 60.5, 10.5);
		assertTrue(GoalEvaluator.conditionMet(logs, obs));
	}

	// -------------------------------------------------------------------- 14. a torch is placed, not lit

	@Test
	void b14_onlyALightIsKindledAndATorchIsPlaced() {
		assertEquals("횃불을 놓아요.", clue(new Goal.PlaceBlock(TORCH, 1), "torch"));
		assertEquals("횃불을 놓았어요.", ceremony(new Goal.PlaceBlock(TORCH, 1), "torch"));
		assertEquals(Verb.PLACE, SentenceBuilder.verbFor(new Goal.PlaceBlock("minecraft:soul_torch", 1)));
		assertEquals(Verb.PLACE, SentenceBuilder.verbFor(new Goal.PlaceBlock("minecraft:redstone_torch", 1)));
		assertEquals(Verb.KINDLE, SentenceBuilder.verbFor(new Goal.PlaceBlock("minecraft:campfire", 1)));
		assertEquals(Verb.KINDLE, SentenceBuilder.verbFor(new Goal.PlaceBlock("minecraft:fire", 1)));
		assertEquals("모닥불을 피워요.", clue(new Goal.PlaceBlock("minecraft:campfire", 1), "campfire"));
		for (String type : List.of("PLACE_BLOCK", "PLACE_REL", "PLACE_ON_PATTERN")) {
			assertFalse(VerbTable.shared().verbsFor(type).contains(Verb.LIGHT), type + " must not choose 밝히다");
		}
	}

	// --------------------------------------------------------------------------------------- 15. pattern anchors

	@Test
	void b15_aPatternMaterialIsSpokenWithTheTaughtWord() {
		Goal pillar = new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:dark_oak_planks");
		assertEquals("판자 기둥 위에 횃불을 놓아요.", clue(pillar, "planks"));
		assertEquals("판자 기둥 위에 횃불을 놓았어요.", ceremony(pillar, "planks"));
		Goal tag = new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PILLAR, "#minecraft:planks");
		assertEquals("판자 기둥 위에 횃불을 놓아요.", clue(tag, null));
		Goal build = new Goal.BuildPattern(Pattern.PILLAR, "minecraft:dark_oak_planks", 3);
		assertEquals("판자 세 개로 기둥을 쌓아요.", clue(build, "planks"));
		// wave 9: the word of the kind that covers the planks is said whether it is taught or not
		assertEquals("판자 기둥 위에 횃불을 놓아요.", clue(pillar, null));
	}

	// ----------------------------------------------------------------- 16. every placing verb names the action

	@Test
	void b16_everyPlacingGoalMayUseEveryVerbOfTheTable() {
		for (String type : List.of("PLACE_BLOCK", "PLACE_REL", "PLACE_ON_PATTERN")) {
			List<String> stems = new ArrayList<>();
			for (Verb verb : VerbTable.shared().verbsFor(type)) {
				stems.add(verb.stem());
			}
			assertTrue(stems.contains("달") || type.equals("PLACE_BLOCK") || type.equals("PLACE_REL")
					|| type.equals("PLACE_ON_PATTERN"), type);
			assertTrue(stems.contains("달"), type + " hangs doors, so 달 names the action: " + stems);
		}
	}

	// ------------------------------------------------------------------------------------ 17. candles

	@Test
	void b17_aCandleIsSwitchedOnAndACampfireKeepsItsVerb() {
		// Lighting a candle is a use of the block (켜다); placing one only stands it up (세우다), because a
		// placing goal never checks the flame.
		assertEquals("초를 켜요.", clue(new Goal.UseBlock("minecraft:candle"), "candle"));
		assertEquals(Verb.SWITCH_ON, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:blue_candle")));
		assertEquals("초를 세워요.", clue(new Goal.PlaceBlock("minecraft:candle", 1), "candle"));
		assertEquals("초를 세웠어요.", ceremony(new Goal.PlaceBlock("minecraft:red_candle", 1), "candle"));
		assertEquals("초 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:candle", 1), "candle"));
		assertEquals("횃불 옆에 초를 세워요.", clue(new Goal.PlaceRel("minecraft:candle", Relation.NEXT_TO, TORCH, 1), "torch"));
		assertEquals("모닥불을 피워요.", clue(new Goal.PlaceBlock("minecraft:soul_campfire", 1), "campfire"));
	}

	// ------------------------------------------------- 18. a covering word replaces only the id it is taught for

	@Test
	void b18_aFlowerWordNamesOneFlowerAndTheOtherGetsItsOwnName() {
		Goal dandelion = new Goal.PlaceRel("minecraft:dandelion", Relation.NEXT_TO, "minecraft:sunflower", 1);
		assertEquals("해바라기 옆에 민들레를 심어요.", clue(dandelion, "sunflower"));
		Goal sunflower = new Goal.PlaceRel("minecraft:sunflower", Relation.NEXT_TO, "minecraft:dandelion", 1);
		assertEquals("민들레 옆에 해바라기를 심어요.", clue(sunflower, "dandelion"));
		// the word for all flowers still names every flower it covers
		assertEquals("꽃을 꺾어요.", clue(new Goal.BreakBlock("minecraft:cornflower", 1, "SNAP"), "flower"));
		assertEquals("꽃을 심어요.", clue(new Goal.PlaceBlock("minecraft:dandelion", 1), "flower"));
		// a door word still names any door (one kind of thing)
		assertEquals("문을 열어요.", clue(new Goal.UseBlock("minecraft:birch_door"), "door"));
	}

	// ------------------------------------------------------------ 19. the German giveaway check is unicode

	@Test
	void b19_tagsueberIsNotTheWordUeber() {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of("torch"), new Goal.PlaceRel(TORCH, Relation.ON_TOP_OF,
				"minecraft:chest", 1), List.of(), null, "Tagsüber eine Fackel setzen.", "Place a torch.", null, null,
				null, null);
		io.github.leks2205.hangulquest.quest.spatial.SpatialRules.checkRelationText(step.goal(), Relation.ON_TOP_OF,
				new ValidationContext("t/s1", step, CATALOG, problems));
		assertEquals(List.of(), problems.stream().filter(p -> p.contains("Tagsüber")).toList());
		QuestStep giveaway = new QuestStep("s1", List.of("torch"), step.goal(), List.of(), null,
				"Eine Fackel über die Kiste setzen.", "Place a torch.", null, null, null, null);
		problems.clear();
		io.github.leks2205.hangulquest.quest.spatial.SpatialRules.checkRelationText(giveaway.goal(), Relation.ON_TOP_OF,
				new ValidationContext("t/s1", giveaway, CATALOG, problems));
		assertFalse(problems.isEmpty(), "a real giveaway is still found");
	}

	// ------------------------------------------------------------ 20. a smelting of something that is not food

	@Test
	void b20_theInputIsSpokenForStoneGlassAndOres() {
		assertEquals("화로에서 조약돌을 구워요.", clue(new Goal.Smelt("minecraft:cobblestone", "minecraft:stone", 1), null));
		assertEquals("화로에서 모래를 구워요.", clue(new Goal.Smelt("minecraft:sand", "minecraft:glass", 1), null));
		assertEquals("화로에서 철 광석을 녹여요.", clue(new Goal.Smelt("minecraft:iron_ore", "minecraft:iron_ingot", 1), null));
		assertEquals("화로에서 조약돌을 구웠어요.", ceremony(new Goal.Smelt("minecraft:cobblestone", "minecraft:stone", 1), null));
		assertEquals("화로에서 철 광석을 녹였어요.", ceremony(new Goal.Smelt("minecraft:iron_ore", "minecraft:iron_ingot", 1), null));
		assertEquals(Verb.BAKE, SentenceBuilder.verbFor(new Goal.Smelt("minecraft:sand", "minecraft:glass", 1)));
		assertEquals(Verb.MELT, SentenceBuilder.verbFor(new Goal.Smelt("minecraft:iron_ore", "minecraft:iron_ingot", 1)));
		// without the input the product is made, as before
		assertEquals(Verb.CRAFT, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:glass", 1)));
		assertEquals(Verb.CRAFT, SentenceBuilder.verbFor(new Goal.Smelt(null, "minecraft:iron_ingot", 1)));
		assertTrue(clue(new Goal.Smelt(null, "minecraft:stone", 1), null).endsWith("만들어요."));
		// food keeps its sentence, bricks and cooked meat have no furnace word
		assertEquals("익히지 않은 닭고기를 구워요.", clue(new Goal.Smelt("minecraft:chicken", "minecraft:cooked_chicken", 1), null));
		// level 4: sand is melted into glass, a cobblestone is baked into stone
		assertEquals("모래를 녹여서 유리를 만들어야 해요.", SentenceBuilder.normal(new Goal.Smelt("minecraft:sand",
				"minecraft:glass", 1), null, List.of(), 4, false, NAMER));
		assertEquals("조약돌을 구워서 돌을 만들어야 해요.", SentenceBuilder.normal(new Goal.Smelt("minecraft:cobblestone",
				"minecraft:stone", 1), null, List.of(), 4, false, NAMER));
	}

	// ------------------------------------------------------------------------------------------ 21. trade

	private static Observation trade(String merchant, String got, int gotCount, String paid, int paidCount) {
		Observation obs = new Observation();
		obs.addTraded(new TradeFact(merchant, got, gotCount, paid, paidCount));
		return obs;
	}

	@Test
	void b21_buyingFromAVillagerAndSellingToOne() {
		Goal buy = new Goal.Trade("minecraft:bread", "minecraft:villager", "BUY", 1);
		Goal sell = new Goal.Trade("minecraft:wheat", "minecraft:villager", "SELL", 3);
		assertEquals("주민에게서 빵을 사요.", clue(buy, null));
		assertEquals("주민에게 밀을 세 개 팔아요.", clue(sell, null));
		assertEquals("주민에게서 빵을 샀어요.", ceremony(buy, null));
		assertEquals("주민에게 밀을 세 개 팔았어요.", ceremony(sell, null));
		assertEquals(Verb.BUY, SentenceBuilder.verbFor(buy));
		assertEquals(Verb.SELL, SentenceBuilder.verbFor(sell));
		assertEquals(List.of(), problems(buy, List.of(), "bread"));
		assertEquals(List.of(), problems(buy, List.of(), "buy"));
		assertEquals(List.of(), problems(sell, List.of(), "sell"));
		assertEquals(List.of(), problems(sell, List.of(), "villager"));
		assertFalse(problems(buy, List.of(), "stone").isEmpty());

		assertTrue(GoalEvaluator.tick(buy, List.of(), trade("minecraft:villager", "minecraft:bread", 1,
				"minecraft:emerald", 1), new GoalEvaluator.Tracker()));
		assertFalse(GoalEvaluator.tick(buy, List.of(), trade("minecraft:villager", "minecraft:emerald", 1,
				"minecraft:bread", 1), new GoalEvaluator.Tracker()), "selling bread is no buying of it");
		assertFalse(GoalEvaluator.tick(buy, List.of(), trade("minecraft:wandering_trader", "minecraft:bread", 1,
				"minecraft:emerald", 1), new GoalEvaluator.Tracker()), "another merchant");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(sell, List.of(), trade("minecraft:villager", "minecraft:emerald", 1,
				"minecraft:wheat", 2), tracker));
		assertTrue(GoalEvaluator.tick(sell, List.of(), trade("minecraft:villager", "minecraft:emerald", 1,
				"minecraft:wheat", 1), tracker));
	}

	@Test
	void b21_theTradeGoalReadsAndWritesItsJson() {
		Goal buy = new Goal.Trade("minecraft:bread", "minecraft:villager", "BUY", 2);
		assertEquals(buy, GoalTypes.parse(GoalTypes.toJson(buy)));
		assertEquals(new Goal.Trade("", "minecraft:villager", "BUY", 1),
				GoalTypes.parse(com.google.gson.JsonParser.parseString("{\"type\":\"TRADE\"}").getAsJsonObject()));
		assertEquals("L2.ege_source", SentenceBuilder.grammar(buy, null, List.of(), 1, false, NAMER).get(2));
	}

	// ---------------------------------------------------------------- the water biome with the water condition

	@Test
	void a2_theIslandStepReadsSwimmingInTheSea() {
		Goal sea = new Goal.ReachBiome("minecraft:ocean", 1000);
		assertEquals("바다에서 수영해요.", clue(sea, "ocean", new Condition.InWater()));
		assertEquals("바다에서 수영해요.", clue(new Goal.ReachBiome("#minecraft:is_ocean", 1000), "ocean",
				new Condition.InWater()));
	}
}
