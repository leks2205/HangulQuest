package io.github.leks2205.hangulquest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.Tiers;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Compounds;
import io.github.leks2205.hangulquest.quest.spatial.NearMiss;
import io.github.leks2205.hangulquest.quest.spatial.RelationWords;
import io.github.leks2205.hangulquest.quest.spatial.SpatialRules;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.GiveTracker;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 6 (from BATCH-008, the first level-2 batch): a trail-level grammar list, the iron chain,
 * tool and armour tiers, 밖 as a deciding form, more anchors for 앞 and 뒤, wearing armour, compound pattern
 * names, the German giveaway check and the placing verbs.
 */
class GeneratorWaveSixTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();

	@TempDir
	Path scratch;

	// ------------------------------------------------------------ 1. a trail-level grammar list

	private static Questline parse(String grammarJson) {
		String json = """
				{"schema": 1, "id": "probe", "titleKo": "시험", "topic": "시험", "tier": 2, "order": 1, "koreanLevel": 2,
				 "concepts": ["stone"], "requires": {"minWordsKnown": 0, "dimension": "minecraft:overworld"},
				 "steps": [], "finale": {"step": {"id": "finale", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}, "textKo": "끝"}%s}
				""".formatted(grammarJson.isEmpty() ? "" : ", \"grammar\": " + grammarJson);
		return QuestLoader.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void i1_aTrailMayListItsGrammar() {
		assertEquals(List.of("L2.seyo", "L2.go_held"), parse("[\"L2.seyo\", \"L2.go_held\"]").grammar());
		assertEquals(List.of(), parse("").grammar(), "the list is optional");
	}

	@Test
	void i1_theListSurvivesTheCopiesTheLoaderMakes() {
		Questline trail = parse("[\"L2.seyo\"]");
		assertEquals(List.of("L2.seyo"), trail.withSteps(trail.steps(), trail.finale()).grammar());
		assertEquals(List.of("L2.seyo"), trail.withLevels(2, 3).grammar());
		assertEquals(List.of("L2.seyo"), trail.withTopic("다른").grammar());
		assertEquals(List.of("L2.seyo"), trail.withRequires(Questline.Requires.none()).grammar());
		assertEquals(List.of("L2.seyo"), trail.withEnglish("Probe", "Done.").grammar());
		assertEquals(List.of("L2.seyo"), trail.withAdaptive(true).grammar());
	}

	@Test
	void i1_everyIdIsCheckedAgainstGrammarJsonAliasesIncluded() {
		// L1.eul_reul is a ladder alias of L1.obj_eul
		Questline fine = parse("[\"L2.seyo\", \"L1.eul_reul\"]");
		Questline broken = parse("[\"L2.seyo\", \"L9.nope\"]");
		List<String> problems = new ArrayList<>();
		var ok = QuestValidator.validate(List.of(fine), CATALOG, org.slf4j.LoggerFactory.getLogger("test"));
		assertEquals(List.of(), ok.problems());
		assertEquals(List.of("L2.seyo", "L1.eul_reul"), ok.trails().get(0).grammar());
		var bad = QuestValidator.validate(List.of(broken), CATALOG, org.slf4j.LoggerFactory.getLogger("test"));
		problems.addAll(bad.problems());
		assertEquals(List.of("probe: unknown grammar id L9.nope"), problems);
		assertEquals(1, bad.trails().size(), "the trail still loads");
		assertEquals(List.of("L2.seyo"), bad.trails().get(0).grammar(), "the unknown id is dropped");
	}

	@Test
	void i1_batch008DeclaresTheListsItsReviewersApproved() throws IOException {
		JsonObject manifest = JsonParser.parseString(Files.readString(Path.of("content", "batches", "BATCH-008",
				"manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		int checked = 0;
		for (JsonElement entry : manifest.getAsJsonArray("trails")) {
			JsonObject trail = entry.getAsJsonObject();
			List<String> expected = new ArrayList<>();
			trail.getAsJsonArray("grammar").forEach(id -> expected.add(id.getAsString()));
			Questline shipped = QuestLoader.loadAll().stream()
					.filter(candidate -> candidate.id().equals(trail.get("id").getAsString())).findFirst().orElseThrow();
			assertEquals(expected, shipped.grammar(), shipped.id());
			checked++;
		}
		assertEquals(15, checked);
	}

	@Test
	void i1_theDumpCarriesTheList() throws IOException {
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		ContentFacts.write(trails, scratch.resolve("steps.json"));
		ContentFacts.Trail schmelzpfad = ContentFacts.read(scratch.resolve("steps.json")).stream()
				.filter(trail -> trail.id().equals("schmelzpfad")).findFirst().orElseThrow();
		assertEquals(List.of("L2.seyo", "L2.eseo_activity"), schmelzpfad.grammar());
		assertTrue(trails.stream().anyMatch(trail -> trail.grammar().isEmpty()), "most trails list none yet");
		assertFalse(trails.stream().allMatch(trail -> trail.grammar().isEmpty()));
	}

	// ------------------------------------------------------------ helpers for the sentence tests

	private static final SentenceBuilder.Namer NAMES = KoreanFixtures.namer(CATALOG);

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	/** The generated clue at a Korean level, with the word a step teaches (null: none). */
	private static String clue(Goal goal, String concept, int level) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(), level, false, NAMES);
	}

	private static String clue(Goal goal, String concept) {
		return clue(goal, concept, 1);
	}

	// ------------------------------------------------------------ 2. the iron chain has words of its own

	@Test
	void i2_theIronAndGoldChainUsesTheVanillaNames() {
		String[][] chain = {{"iron_ore", "block.minecraft.iron_ore"}, {"raw_iron", "item.minecraft.raw_iron"},
				{"iron_ingot", "item.minecraft.iron_ingot"}, {"gold_ore", "block.minecraft.gold_ore"},
				{"raw_gold", "item.minecraft.raw_gold"}, {"gold_ingot", "item.minecraft.gold_ingot"}};
		for (String[] row : chain) {
			assertEquals(KoreanFixtures.koNames().get(row[1]), word(row[0]).ko, row[0]);
		}
		assertEquals("철", word("iron").ko, "철 stays the core word");
		assertEquals("금", word("gold").ko);
	}

	@Test
	void i2_aStepSaysTheExactItemWhenNoGeneralWordIsTaught() {
		assertEquals("화로에서 철 주괴를 만들어요.", clue(new Goal.Smelt(null, "minecraft:iron_ingot", 1), null));
		assertEquals("철 광석을 캐요.", clue(new Goal.BreakBlock("minecraft:iron_ore", 1, null), null));
		assertEquals("철 원석을 세 개 모아요.", clue(new Goal.AcquireItem("minecraft:raw_iron", 3), null));
		assertEquals("화로에서 금 주괴를 만들어요.", clue(new Goal.Smelt(null, "minecraft:gold_ingot", 1), null));
		assertEquals("금 원석을 세 개 모아요.", clue(new Goal.AcquireItem("minecraft:raw_gold", 3), null));
	}

	@Test
	void i2_ironStaysTheWordWhenItIsTheOneTaught() {
		assertEquals("철을 캐요.", clue(new Goal.BreakBlock("minecraft:iron_ore", 1, null), "iron"));
		// raw iron is accepted by 철 but keeps its own name (see i2_ironAcceptsRawIronButDoesNotRenameIt)
		assertEquals("철 광석을 캐요.", clue(new Goal.BreakBlock("minecraft:deepslate_iron_ore", 1, null), "iron_ore"),
				"the word covers the deepslate ore");
	}

	// ------------------------------------------------------------ 3. tool and armour tiers

	@Test
	void i3_theMaterialWordsAreTheOnesTheRegistryUses() {
		for (String material : Tiers.materials()) {
			for (String piece : Tiers.pieces()) {
				String registry = KoreanFixtures.koNames().get("item.minecraft." + material + "_" + piece);
				if (registry == null) {
					continue; // no wooden helmet
				}
				assertEquals(Tiers.official(material, piece), registry, material + "_" + piece);
			}
		}
		// the general words are the words of the concepts
		assertEquals("곡괭이", word("pickaxe").ko);
		assertEquals("투구", word("helmet").ko);
		assertEquals("갑옷", word("armor").ko);
		assertEquals("바지", word("leggings").ko);
		assertEquals("신발", word("shoes").ko);
	}

	@Test
	void i3_theTierIsNamedWhenTheGoalAsksForOneAndTheGeneralWordIsTaught() {
		assertEquals("철 곡괭이를 만들어요.", clue(new Goal.Craft("minecraft:iron_pickaxe", 1, null, null), "pickaxe"));
		assertEquals("철 곡괭이를 만드세요.", clue(new Goal.Craft("minecraft:iron_pickaxe", 1, null, null), "pickaxe", 2));
		assertEquals("나무 도끼를 만들어요.", clue(new Goal.Craft("minecraft:wooden_axe", 1, null, null), "axe"));
		assertEquals("돌 삽을 만들어요.", clue(new Goal.Craft("minecraft:stone_shovel", 1, null, null), "shovel"));
		assertEquals("다이아몬드 괭이를 만들어요.", clue(new Goal.Craft("minecraft:diamond_hoe", 1, null, null), "hoe"));
		assertEquals("철 검을 만들어요.", clue(new Goal.Craft("minecraft:iron_sword", 1, null, null), "sword"));
		// armour
		assertEquals("철 투구를 만들어요.", clue(new Goal.Craft("minecraft:iron_helmet", 1, null, null), "helmet"));
		// one exact piece says its vanilla name, not the general word with a material (no "철 갑옷")
		assertEquals("철 흉갑을 만들어요.", clue(new Goal.Craft("minecraft:iron_chestplate", 1, null, null), "armor"));
		assertEquals("철 레깅스를 만들어요.", clue(new Goal.Craft("minecraft:iron_leggings", 1, null, null), "leggings"));
		assertEquals("철 부츠를 만들어요.", clue(new Goal.Craft("minecraft:iron_boots", 1, null, null), "shoes"));
		assertEquals("가죽 장화를 만들어요.", clue(new Goal.Craft("minecraft:leather_boots", 1, null, null), "shoes"));
		assertEquals("가죽 모자를 만들어요.", clue(new Goal.Craft("minecraft:leather_helmet", 1, null, null), "helmet"));
	}

	@Test
	void i3_theHintAndTheFinaleNameTheTierToo() {
		Goal pickaxe = new Goal.Craft("minecraft:iron_pickaxe", 1, null, null);
		assertEquals("철 곡괭이를 만들어요.", SentenceBuilder.hint(pickaxe, word("pickaxe"), List.of(), NAMES));
		assertEquals("철 곡괭이를 만들었어요.", SentenceBuilder.ceremony(pickaxe, word("pickaxe"), List.of(), NAMES));
	}

	@Test
	void i3_withoutATaughtWordTheRegistryTierStillShows() {
		// the namer used to map iron_pickaxe to the concept 곡괭이 before any registry name could be used
		assertEquals("철 곡괭이를 만들어요.", clue(new Goal.Craft("minecraft:iron_pickaxe", 1, null, null), null));
		assertEquals("철 곡괭이를 만들어요.", clue(new Goal.AcquireItem("minecraft:iron_pickaxe", 1), null));
	}

	@Test
	void i3_aWordThatHoldsTheTierIsLeftAlone() {
		assertEquals("철 흉갑을 만들어요.", clue(new Goal.Craft("minecraft:iron_chestplate", 1, null, null), "iron_chestplate"));
		assertEquals("철 투구를 만들어요.", clue(new Goal.Craft("minecraft:iron_helmet", 1, null, null), "adv2_item_iron_helmet"));
	}

	@Test
	void i3_theTierIsExactEvenThoughTheWordCoversEveryTier() {
		// 곡괭이 is a right word for a wooden pickaxe (the validator accepts it) ...
		Goal wooden = new Goal.Craft("minecraft:wooden_pickaxe", 1, null, null);
		assertEquals(List.of(), problems(wooden, "pickaxe"));
		// ... but a sentence that says 철 곡괭이 does not take a wooden one
		Goal iron = new Goal.Craft("minecraft:iron_pickaxe", 1, null, null);
		var accepted = Accepted.of(step(iron, "pickaxe"), CATALOG.concept("pickaxe").orElseThrow());
		assertFalse(accepted.matches("minecraft:iron_pickaxe", "minecraft:wooden_pickaxe"));
		assertFalse(accepted.widens("minecraft:iron_pickaxe"));
		// a word for a thing without tiers still widens (any bed for 침대)
		Goal bed = new Goal.PlaceBlock("minecraft:red_bed", 1);
		assertTrue(Accepted.of(step(bed, "bed"), CATALOG.concept("bed").orElseThrow()).widens("minecraft:red_bed"));
	}

	private static QuestStep step(Goal goal, String concept) {
		return new QuestStep("s1", List.of(concept), goal, List.of(), null, null, "Do it.", null, null, null, null);
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	// ------------------------------------------------------------ 4. 밖 contrasts with 안

	@Test
	void i4_standingOutsideWhenInsideWasAskedIsASlip() {
		Goal inside = new Goal.PlaceRel("minecraft:torch", Relation.INSIDE, "minecraft:oak_fence", 1);
		Goal outside = new Goal.PlaceRel("minecraft:torch", Relation.OUTSIDE, "minecraft:oak_fence", 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(SpatialWorlds.placementFor(outside, false));
		assertFalse(GoalEvaluator.tick(inside, List.of(), obs, tracker));
		assertEquals(1, tracker.formErrors, "the learning side hears about it");
		assertEquals(Relation.OUTSIDE, NearMiss.current(tracker, obs.now));
		// the other way round was a slip already
		GoalEvaluator.Tracker other = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(outside, List.of(), observing(SpatialWorlds.placementFor(inside, false)), other));
		assertEquals(1, other.formErrors);
		// and 밖 stays loose for every other wanted relation: a torch up on a post is not "wrong" for 위
		Goal on = new Goal.PlaceRel("minecraft:torch", Relation.ON_TOP_OF, "minecraft:oak_fence", 1);
		GoalEvaluator.Tracker quiet = new GoalEvaluator.Tracker();
		GoalEvaluator.tick(on, List.of(), observing(SpatialWorlds.placementFor(outside, false)), quiet);
		assertEquals(0, quiet.formErrors);
		// 밖 is a deciding form, 근처 is not
		assertEquals(List.of("L1.loc_bak_e"), GoalTypes.of(outside).decidingForms(outside));
		Goal near = new Goal.PlaceRel("minecraft:torch", Relation.NEAR, "minecraft:oak_fence", 1);
		assertEquals(List.of(), GoalTypes.of(near).decidingForms(near));
	}

	private static Observation observing(PlacementFact fact) {
		Observation obs = new Observation();
		obs.now = 50_000L;
		obs.clockTime = 1000L;
		obs.addPlacement(fact);
		return obs;
	}

	// ------------------------------------------------------------ 5. more anchors for 앞 and 뒤

	@Test
	void i5_everyBlockWithAFrontCanAnchorFrontAndBehind() {
		for (String id : List.of("minecraft:oak_stairs", "minecraft:stone_brick_stairs", "minecraft:crafting_table",
				"minecraft:smithing_table", "minecraft:fletching_table", "minecraft:cartography_table", "minecraft:loom",
				"minecraft:grindstone", "minecraft:stonecutter", "minecraft:lectern", "minecraft:anvil",
				"minecraft:barrel", "minecraft:oak_sign", "minecraft:oak_wall_sign", "minecraft:oak_hanging_sign",
				"minecraft:white_banner", "minecraft:carved_pumpkin", "minecraft:jack_o_lantern",
				"minecraft:oak_door", "minecraft:chest", "minecraft:furnace", "minecraft:red_bed")) {
			assertTrue(BlockSpec.hasFacing(id), id);
			Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.IN_FRONT_OF, id, 1);
			assertEquals(List.of(), problems(goal, "torch").stream().filter(p -> p.contains("front")).toList(), id);
		}
	}

	@Test
	void i5_pillarsAndWallsHaveNoFront() {
		for (String id : List.of("minecraft:oak_fence", "minecraft:cobblestone_wall", "minecraft:stone",
				"minecraft:oak_planks", "minecraft:oak_log", "minecraft:glass", "minecraft:torch")) {
			assertFalse(BlockSpec.hasFacing(id), id);
			Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.BEHIND, id, 1);
			assertTrue(problems(goal, "torch").stream().anyMatch(p -> p.contains("no clear front")), id);
		}
		Goal pillar = new Goal.PlaceOnPattern("minecraft:torch", Relation.BEHIND, Pattern.PILLAR, "minecraft:cobblestone");
		assertFalse(problems(pillar, "torch").isEmpty());
	}

	@Test
	void i5_aPlacementInFrontOfAWorkstationCounts() {
		Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.IN_FRONT_OF, "minecraft:crafting_table", 1);
		assertTrue(GoalEvaluator.tick(goal, List.of(), observing(SpatialWorlds.placementFor(goal, false)),
				new GoalEvaluator.Tracker()));
		Goal behind = new Goal.PlaceRel("minecraft:torch", Relation.BEHIND, "minecraft:oak_stairs", 1);
		assertTrue(GoalEvaluator.tick(behind, List.of(), observing(SpatialWorlds.placementFor(behind, false)),
				new GoalEvaluator.Tracker()));
		assertEquals("제작대 앞에 횃불을 놓아요.", SentenceBuilder.normal(goal, null, List.of(), 1, false, NAMES));
	}

	private static Observation withVillager(float yaw) {
		Observation obs = observing(new PlacementFact("minecraft:torch", new BlockPos(100, 64, 100), 1L, false,
				new Vec3(100.5, 64, 103.5), BlockNeighborhood.capture(100, 64, 100, (x, y, z) -> null)));
		obs.addEntityPosition(new EntityFact("minecraft:villager", UUID.randomUUID(), 100.5, 64, 98.5, yaw, 0f));
		return obs;
	}

	@Test
	void i5_aVillagerHasAFrontToo() {
		Goal front = new Goal.PlaceRel("minecraft:torch", Relation.IN_FRONT_OF, "minecraft:villager", 1);
		Goal behind = new Goal.PlaceRel("minecraft:torch", Relation.BEHIND, "minecraft:villager", 1);
		// yaw 0 looks south (+z); the torch lies two blocks south of the villager
		assertTrue(GoalEvaluator.tick(front, List.of(), withVillager(0f), new GoalEvaluator.Tracker()));
		assertFalse(GoalEvaluator.tick(behind, List.of(), withVillager(0f), new GoalEvaluator.Tracker()));
		assertTrue(GoalEvaluator.tick(behind, List.of(), withVillager(180f), new GoalEvaluator.Tracker()));
		assertFalse(GoalEvaluator.tick(front, List.of(), withVillager(180f), new GoalEvaluator.Tracker()));
		// no villager in sight: nothing holds
		assertFalse(GoalEvaluator.tick(front, List.of(), observing(withVillager(0f).placements.get(0)),
				new GoalEvaluator.Tracker()));
		assertEquals("주민 앞에 횃불을 놓아요.", SentenceBuilder.normal(front, null, List.of(), 1, false, NAMES));
		assertEquals(List.of(), problems(front, "torch"));
		// a creature has no top or bottom to speak of
		assertFalse(problems(new Goal.PlaceRel("minecraft:torch", Relation.ON_TOP_OF, "minecraft:villager", 1), "torch")
				.isEmpty());
	}

	// ------------------------------------------------------------ 6. 들고 is not a deciding form

	@Test
	void i6_holdingIsPracticeNotAChoiceAndTheContributingGuideSaysSo() throws IOException {
		Goal hold = new Goal.HoldWhile("minecraft:torch", new Goal.PlaceBlock("minecraft:stone", 1), null, 0);
		assertEquals(List.of(), GoalTypes.of(hold).decidingForms(hold));
		String guide = Files.readString(Path.of("CONTRIBUTING.md"), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
		assertTrue(guide.contains("들고 (`HOLD_WHILE`, and the `HOLDING` phrase) is not form-dependent"),
				"the decision is written down next to the metric");
	}

	// ------------------------------------------------------------ 7. EQUIP: wearing armour

	private static Observation wearing(java.util.Map<String, String> worn) {
		Observation obs = new Observation();
		obs.now = 10_000L;
		obs.worn = worn;
		return obs;
	}

	private static final java.util.Map<String, String> NOTHING = java.util.Map.of();
	private static final java.util.Map<String, String> IRON_HELMET = java.util.Map.of("head", "minecraft:iron_helmet");

	@Test
	void i7_putOnCountsWhenTheSlotWasSeenBareAndThenHasThePiece() {
		Goal goal = new Goal.Equip("minecraft:iron_helmet");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(NOTHING), tracker));
		assertTrue(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
	}

	@Test
	void i7_aPieceAlreadyWornWhenTheStepBeginsHasToComeOffFirst() {
		Goal goal = new Goal.Equip("minecraft:iron_helmet");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(NOTHING), tracker));
		assertTrue(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
	}

	@Test
	void i7_theTierAndTheSlotHaveToBeRight() {
		Goal goal = new Goal.Equip("minecraft:iron_helmet");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		GoalEvaluator.tick(goal, List.of(), wearing(NOTHING), tracker);
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(java.util.Map.of("head", "minecraft:leather_helmet")),
				tracker), "another tier");
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(java.util.Map.of("chest", "minecraft:iron_helmet")),
				tracker), "not in that slot");
		assertTrue(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
	}

	@Test
	void i7_takeOffCountsWhenThePieceWasSeenAndThenIsGone() {
		Goal goal = new Goal.Equip("minecraft:iron_helmet", "OFF");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(NOTHING), tracker), "bare from the start: nothing to take off");
		assertFalse(GoalEvaluator.tick(goal, List.of(), wearing(IRON_HELMET), tracker));
		assertTrue(GoalEvaluator.tick(goal, List.of(), wearing(NOTHING), tracker));
	}

	@Test
	void i7_theSlotsAreReadFromTheFourArmourSlots() {
		assertEquals(java.util.Map.of(), io.github.leks2205.hangulquest.sense.WornItems.of(null, "", null, null));
		assertEquals(java.util.Map.of("head", "minecraft:iron_helmet", "feet", "minecraft:iron_boots"),
				io.github.leks2205.hangulquest.sense.WornItems.of("minecraft:iron_helmet", null, null, "minecraft:iron_boots"));
	}

	@Test
	void i7_theVerbFollowsTheBodyPart() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		assertEquals("철 투구를 써요.", SentenceBuilder.normal(new Goal.Equip("minecraft:iron_helmet"), null, List.of(), 1, false, names));
		assertEquals("철 흉갑을 입어요.", SentenceBuilder.normal(new Goal.Equip("minecraft:iron_chestplate"), null, List.of(), 1, false, names));
		assertEquals("철 레깅스를 입어요.", SentenceBuilder.normal(new Goal.Equip("minecraft:iron_leggings"), null, List.of(), 1, false, names));
		assertEquals("철 부츠를 신어요.", SentenceBuilder.normal(new Goal.Equip("minecraft:iron_boots"), null, List.of(), 1, false, names));
		assertEquals("철 투구를 벗어요.", SentenceBuilder.normal(new Goal.Equip("minecraft:iron_helmet", "OFF"), null, List.of(), 1, false, names));
		assertEquals("철 흉갑을 입었어요.", SentenceBuilder.ceremony(new Goal.Equip("minecraft:iron_chestplate"), word("armor"), List.of(), names));
		assertEquals("철 투구를 벗었어요.", SentenceBuilder.ceremony(new Goal.Equip("minecraft:iron_helmet", "OFF"), word("helmet"), List.of(), names));
	}

	@Test
	void i7_aGoalOnAnyPieceKeepsTheGeneralWordAndTakesEveryMaterial() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal any = new Goal.Equip("#minecraft:chest_armor");
		assertEquals("갑옷을 입어요.", SentenceBuilder.normal(any, word("armor"), List.of(), 1, false, names));
		assertEquals("갑옷을 입으세요.", SentenceBuilder.normal(any, word("armor"), List.of(), 2, false, names));
		assertEquals("갑옷을 입었어요.", SentenceBuilder.ceremony(any, word("armor"), List.of(), names));
		assertEquals(List.of(), problems(any, "armor"));
		assertFalse(problems(any, "helmet").isEmpty(), "a head word for a chest tag");
		assertFalse(problems(new Goal.Equip("#minecraft:nonsense"), "armor").isEmpty());
		// every material counts, and the first change is still the player's
		for (String worn : List.of("minecraft:leather_chestplate", "minecraft:iron_chestplate", "minecraft:netherite_chestplate")) {
			GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
			assertFalse(GoalEvaluator.tick(any, List.of(), wearing(NOTHING), tracker));
			assertTrue(GoalEvaluator.tick(any, List.of(), wearing(java.util.Map.of("chest", worn)), tracker), worn);
		}
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		GoalEvaluator.tick(any, List.of(), wearing(NOTHING), tracker);
		assertFalse(GoalEvaluator.tick(any, List.of(), wearing(IRON_HELMET), tracker), "a helmet is no chestplate");
	}

	@Test
	void i7_onlyArmourCanBeEquippedAndTheWordMustFit() {
		assertEquals(List.of(), problems(new Goal.Equip("minecraft:iron_helmet"), "helmet"));
		assertEquals(List.of(), problems(new Goal.Equip("minecraft:iron_chestplate"), "armor"));
		assertFalse(problems(new Goal.Equip("minecraft:stick")).isEmpty());
		assertFalse(problems(new Goal.Equip("minecraft:iron_helmet", "SIDEWAYS")).isEmpty());
		assertFalse(problems(new Goal.Equip("minecraft:iron_helmet"), "armor").isEmpty(), "a chest word for a helmet");
	}

	@Test
	void i7_theGoalIsReadAndWrittenAsJson() {
		Goal off = new Goal.Equip("minecraft:iron_boots", "OFF");
		assertEquals(off, GoalTypes.parse(GoalTypes.toJson(off)));
		assertFalse(GoalTypes.toJson(new Goal.Equip("minecraft:iron_boots")).has("change"));
		assertEquals(new Goal.Equip("minecraft:iron_boots", "OFF"),
				QuestLoader.parseGoal(JsonParser.parseString("{\"type\":\"equip\",\"item\":\"minecraft:iron_boots\",\"change\":\"off\"}")
						.getAsJsonObject()));
	}

	// ------------------------------------------------------------ 9. 만드세요 names the craft action

	@Test
	void i9_anAuthoredLevelTwoCraftClueMayUseTheLDropForm() {
		Goal pickaxe = new Goal.Craft("minecraft:iron_pickaxe", 1, null, null);
		assertTrue(QuestlineValidationTest.namesAction(pickaxe, "철 곡괭이를 만드세요."));
		assertTrue(QuestlineValidationTest.namesAction(pickaxe, "제작대에서 철과 막대기로 철 곡괭이를 만드세요."));
		assertTrue(QuestlineValidationTest.namesAction(new Goal.AcquireItem("minecraft:iron_pickaxe", 1), "철 곡괭이를 만드세요."));
		assertTrue(QuestlineValidationTest.namesAction(new Goal.Smelt(null, "minecraft:iron_ingot", 1), "화로에서 철 주괴를 만드세요."));
		assertFalse(QuestlineValidationTest.namesAction(pickaxe, "철 곡괭이를 보세요."));
		// the engine does not reject such a clue either
		String json = "{\"schema\":1,\"id\":\"probe\",\"titleKo\":\"시험\",\"topic\":\"시험\",\"tier\":2,\"order\":1,"
				+ "\"koreanLevel\":2,\"concepts\":[\"pickaxe\"],\"requires\":{\"minWordsKnown\":0,\"dimension\":\"minecraft:overworld\"},"
				+ "\"steps\":[],\"finale\":{\"step\":{\"id\":\"finale\",\"teach\":[\"pickaxe\"],"
				+ "\"goal\":{\"type\":\"CRAFT\",\"item\":\"minecraft:iron_pickaxe\",\"count\":1},"
				+ "\"clue\":{\"ko\":\"철 곡괭이를 만드세요.\"}},\"textKo\":\"끝\"}}";
		var result = QuestValidator.validate(List.of(QuestLoader.parse(JsonParser.parseString(json).getAsJsonObject())),
				CATALOG, org.slf4j.LoggerFactory.getLogger("test"));
		assertEquals(List.of(), result.problems());
		assertEquals(1, result.trails().size());
	}

	// ------------------------------------------------------------ 10. the German giveaway check

	private static String leak(String translation, Relation relation, Condition... conditions) {
		return RelationWords.leak(translation, relation, List.of(conditions));
	}

	@Test
	void i10_aSeparableVerbPrefixIsNoRelationWord() {
		assertNull(leak("Stelle eine Fackel auf.", Relation.ON_TOP_OF));
		assertNull(leak("Stelle neben dem Haus ein Schild auf.", Relation.ON_TOP_OF));
		assertNull(leak("Platziere eine Fackel und stelle sie dann auf, bevor es dunkel wird.", Relation.ON_TOP_OF));
		assertNull(leak("Du kannst die Fackel aufstellen.", Relation.ON_TOP_OF), "joined, not a separate word");
		// the preposition with its noun is the giveaway
		assertEquals("auf", leak("Stelle eine Fackel auf die Kiste.", Relation.ON_TOP_OF));
		assertEquals("neben", leak("Stelle eine Fackel neben die Kiste.", Relation.NEXT_TO));
		assertNull(leak("Tagsüber eine Fackel setzen.", Relation.ON_TOP_OF));
	}

	@Test
	void i10_onlyTheWordsOfTheStepsOwnRelationCount() {
		assertNull(leak("Setze eine Fackel auf die Kiste.", Relation.NEXT_TO));
		assertNull(leak("Place a torch on the chest.", Relation.NEXT_TO));
		assertNull(leak("Setze eine Fackel unter die Kiste.", Relation.BEHIND));
		assertEquals("unter", leak("Setze eine Fackel unter die Kiste.", Relation.UNDER));
		assertEquals("daneben", leak("Setze die Fackel daneben.", Relation.NEXT_TO));
	}

	@Test
	void i10_aPhraseTheConditionProducesIsNotChecked() {
		Condition below = new Condition.YRange(-64, 40);
		assertEquals("unter", leak("Setze unter der Erde eine Fackel unter die Kiste.", Relation.UNDER));
		assertNull(leak("Setze unter der Erde eine Fackel.", Relation.UNDER, below));
		assertNull(leak("Place a torch underground.", Relation.UNDER, below));
		assertEquals("unter", leak("Setze unter der Erde eine Fackel unter die Kiste.", Relation.UNDER, below),
				"the relation of the step itself is still found");
		assertNull(leak("Setze eine Fackel in Wassernähe unter Wasser.", Relation.UNDER, new Condition.InWater()));
		assertNull(leak("Setze draußen eine Fackel.", Relation.OUTSIDE, new Condition.Sky(Condition.SkyState.OPEN)));
		assertEquals("draußen", leak("Setze draußen eine Fackel.", Relation.OUTSIDE));
	}

	@Test
	void i10_theValidatorUsesTheConditionsOfTheStep() {
		Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.UNDER, "minecraft:chest", 1);
		QuestStep step = new QuestStep("s1", List.of("torch"), goal, List.of(new Condition.YRange(-64, 40)), null,
				"Setze unter der Erde eine Fackel.", "Place a torch underground.", null, null, null, null);
		List<String> problems = new ArrayList<>();
		SpatialRules.checkRelationText(goal, Relation.UNDER, new ValidationContext("t/s1", step, CATALOG, problems));
		assertEquals(List.of(), problems.stream().filter(p -> p.contains("gives the relation away")).toList());
		QuestStep plain = new QuestStep("s1", List.of("torch"), goal, List.of(), null,
				"Setze unter der Erde eine Fackel.", "Place a torch.", null, null, null, null);
		problems.clear();
		SpatialRules.checkRelationText(goal, Relation.UNDER, new ValidationContext("t/s1", plain, CATALOG, problems));
		assertFalse(problems.stream().filter(p -> p.contains("gives the relation away")).toList().isEmpty());
	}

	// ------------------------------------------------------------ 11. the placing verbs of level 2

	@Test
	void i11_blocksThatStandHangSpreadOrAreFittedHaveTheirOwnVerb() {
		VerbTable table = VerbTable.shared();
		for (String type : List.of("PLACE_BLOCK", "PLACE_REL", "PLACE_ON_PATTERN")) {
			for (String id : List.of("minecraft:oak_sign", "minecraft:armor_stand", "minecraft:lightning_rod",
					"minecraft:quartz_pillar", "minecraft:purpur_pillar")) {
				assertEquals(Verb.STAND, table.choose(type, id, 1, Verb.PLACE), type + " " + id);
			}
			for (String id : List.of("minecraft:item_frame", "minecraft:painting")) {
				assertEquals(Verb.HANG, table.choose(type, id, 1, Verb.PLACE), type + " " + id);
			}
			// a banner on a wall is hung; one on top of a built structure stands on it
			assertEquals(type.equals("PLACE_ON_PATTERN") ? Verb.STAND : Verb.HANG,
					table.choose(type, "minecraft:white_banner", 1, Verb.PLACE), type + " banner");
			for (String id : List.of("minecraft:rail", "minecraft:powered_rail", "minecraft:white_carpet")) {
				assertEquals(Verb.SPREAD, table.choose(type, id, 1, Verb.PLACE), type + " " + id);
			}
			for (String id : List.of("minecraft:oak_door", "minecraft:oak_trapdoor", "minecraft:oak_fence_gate",
					"minecraft:oak_hanging_sign", "minecraft:iron_door")) {
				assertEquals(Verb.HANG_DOOR, table.choose(type, id, 1, Verb.PLACE), type + " " + id);
			}
			for (String id : List.of("minecraft:torch", "minecraft:chest", "minecraft:oak_pressure_plate",
					"minecraft:lantern", "minecraft:crafting_table")) {
				assertEquals(Verb.PLACE, table.choose(type, id, 1, Verb.PLACE), type + " " + id);
			}
		}
	}

	@Test
	void i11_theSentencesSayIt() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		assertEquals("제작대 옆에 참나무 표지판을 세워요.",
				SentenceBuilder.normal(new Goal.PlaceRel("minecraft:oak_sign", Relation.NEXT_TO, "minecraft:crafting_table", 1),
						null, List.of(), 1, false, names));
		assertEquals("용광로 옆에 갑옷 거치대를 세우세요.",
				SentenceBuilder.normal(new Goal.PlaceRel("minecraft:armor_stand", Relation.NEXT_TO, "minecraft:blast_furnace", 1),
						null, List.of(), 2, false, names));
		assertEquals("상자 옆에 참나무 매다는 표지판을 다세요.",
				SentenceBuilder.normal(new Goal.PlaceRel("minecraft:oak_hanging_sign", Relation.NEXT_TO, "minecraft:chest", 1),
						null, List.of(), 2, false, names));
		assertEquals("석영 기둥을 세웠어요.", SentenceBuilder.ceremony(new Goal.PlaceBlock("minecraft:quartz_pillar", 1), null, List.of(), names));
		assertEquals("제작대 옆에 레일을 깔아요.",
				SentenceBuilder.normal(new Goal.PlaceRel("minecraft:rail", Relation.NEXT_TO, "minecraft:crafting_table", 1),
						null, List.of(), 1, false, names));
		assertEquals("제작대 옆에 참나무 문을 달아요.",
				SentenceBuilder.normal(new Goal.PlaceRel("minecraft:oak_door", Relation.NEXT_TO, "minecraft:crafting_table", 1),
						null, List.of(), 1, false, names));
	}

	// ------------------------------------------------------------ 8. compound pattern names and the shortener

	@Test
	void i8_aStoneRingIsADolDamAsTheDictionarySpellsIt() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal ring = new Goal.PlaceOnPattern("minecraft:torch", Relation.INSIDE, Pattern.RING, "minecraft:stone");
		assertEquals("돌담 안에 횃불을 놓아요.", SentenceBuilder.normal(ring, null, List.of(), 1, false, names));
		// the headwords of 표준국어대사전 close up ...
		assertEquals("돌기둥 위에 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:stone"),
				null, List.of(), 1, false, names));
		assertEquals("흙벽 옆에 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.WALL, "minecraft:dirt"),
				null, List.of(), 1, false, names));
		// ... the others stay two words: 조약돌 is a headword, 조약돌기둥 is not
		assertEquals("조약돌 기둥 위에 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:cobblestone"),
				null, List.of(), 1, false, names));
		assertEquals("석재 벽돌 기둥 위에 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PILLAR, "minecraft:stone_bricks"),
				null, List.of(), 1, false, names));
		assertEquals("돌 피라미드 위에 횃불을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.PYRAMID, "minecraft:stone"),
				null, List.of(), 1, false, names));
		assertEquals("돌담 밖에 횃불을 놓았어요.", SentenceBuilder.ceremony(
				new Goal.PlaceOnPattern("minecraft:torch", Relation.OUTSIDE, Pattern.RING, "minecraft:stone"),
				null, List.of(), names));
	}

	@Test
	void i8_theClosedWordsAreAMaterialAndAStructure() {
		java.util.Set<String> structures = java.util.Arrays.stream(Pattern.values()).map(Pattern::ko)
				.collect(java.util.stream.Collectors.toSet());
		assertFalse(Compounds.words().isEmpty());
		for (String word : Compounds.words()) {
			assertTrue(structures.stream().anyMatch(word::endsWith), word + " ends in a structure word");
		}
		assertEquals("돌담", Compounds.join("돌", "담"));
		assertEquals("조약돌 담", Compounds.join("조약돌", "담"));
		assertEquals("담", Compounds.join("", "담"));
	}

	@Test
	void i8_theMaterialStaysWhenThePillarAndTheBlockShareIt() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal stairs = new Goal.PlaceOnPattern("minecraft:stone_brick_stairs", Relation.NEXT_TO, Pattern.PILLAR,
				"minecraft:stone_bricks");
		assertEquals("석재 벽돌 기둥 옆에 석재 벽돌 계단을 놓아요.", SentenceBuilder.normal(stairs, null, List.of(), 1, false, names));
		assertEquals("석재 벽돌 기둥 옆에 석재 벽돌 계단을 놓았어요.", SentenceBuilder.ceremony(stairs, null, List.of(), names));
		assertEquals("석재 벽돌 기둥 옆에 석재 벽돌 계단을 놓아요.", SentenceBuilder.hint(stairs, null, List.of(), names));
		// a block of plain stone next to a stone pillar says both too
		assertEquals("돌기둥 옆에 돌 계단을 놓아요.", SentenceBuilder.normal(
				new Goal.PlaceOnPattern("minecraft:stone_stairs", Relation.NEXT_TO, Pattern.PILLAR, "minecraft:stone"),
				null, List.of(), 1, false, names));
	}

	// ------------------------------------------------------------ B13. SEQUENCE with -고 at level 2

	private static Goal.Sequence closeThenTorch(boolean ordered, String connector) {
		return new Goal.Sequence(List.of(new Goal.UseBlock("minecraft:oak_door", "CLOSE"),
				new Goal.PlaceBlock("minecraft:torch", 1)), ordered, connector);
	}

	private static List<String> sequenceProblems(Goal.Sequence goal, int level) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems, level));
		return problems.stream().filter(p -> p.contains("SEQUENCE needs koreanLevel")).toList();
	}

	@Test
	void b13_aPlainGoSequenceIsALevelTwoSentence() {
		assertEquals(List.of(), sequenceProblems(closeThenTorch(false, null), 2));
		assertEquals(List.of(), sequenceProblems(closeThenTorch(true, "AND"), 2));
		assertFalse(sequenceProblems(closeThenTorch(false, null), 1).isEmpty());
		assertFalse(sequenceProblems(closeThenTorch(true, "AND"), 1).isEmpty());
		// -기 전에, -(으)ㄴ 후에 and -고 나서 stay at level 3
		assertFalse(sequenceProblems(closeThenTorch(true, "BEFORE"), 2).isEmpty());
		assertFalse(sequenceProblems(closeThenTorch(true, "AFTER"), 2).isEmpty());
		assertFalse(sequenceProblems(closeThenTorch(true, "THEN"), 2).isEmpty());
		assertFalse(sequenceProblems(closeThenTorch(true, null), 2).isEmpty(), "the connective is picked from level 3");
		assertEquals(List.of(), sequenceProblems(closeThenTorch(true, "BEFORE"), 3));
	}

	@Test
	void b13_theSentenceJoinsThePartsWithGo() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal ordered = closeThenTorch(true, "AND");
		assertEquals("참나무 문을 닫고 횃불을 놓으세요.", SentenceBuilder.normal(ordered, null, List.of(), 2, false, names));
		assertEquals("참나무 문을 닫고 횃불을 놓아요.", SentenceBuilder.hint(ordered, null, List.of(), names));
		assertEquals("참나무 문을 닫고 횃불을 놓으세요.", SentenceBuilder.normal(closeThenTorch(false, null), null, List.of(), 2, false,
				names));
		assertTrue(SentenceBuilder.grammar(ordered, null, List.of(), 2, false, names).contains("L2.go_and"));
	}

	@Test
	void b13_onlyAnOrderedGoCountsAsFormDependent() {
		assertEquals(List.of("L2.go_and"), GoalTypes.of(closeThenTorch(true, "AND")).decidingForms(closeThenTorch(true, "AND")));
		assertEquals(List.of(), GoalTypes.of(closeThenTorch(false, null)).decidingForms(closeThenTorch(false, null)));
		// and the order is checked: the torch first does not count
		Goal ordered = closeThenTorch(true, "AND");
		Observation early = new Observation();
		early.placedBlocks.add("minecraft:torch");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(ordered, List.of(), early, tracker));
		assertEquals(1, tracker.formErrors);
		Observation closed = new Observation();
		closed.addClosed("minecraft:oak_door");
		closed.usedBlocks.add("minecraft:oak_door");
		assertFalse(GoalEvaluator.tick(ordered, List.of(), closed, tracker));
		Observation placed = new Observation();
		placed.placedBlocks.add("minecraft:torch");
		assertTrue(GoalEvaluator.tick(ordered, List.of(), placed, tracker));
		assertEquals(ordered, GoalTypes.parse(GoalTypes.toJson(ordered)));
	}

	// ------------------------------------------------------------ B12. 드세요

	@Test
	void b12_theRequestToEatIsDeuseyoAndTheOthersStayPlain() {
		assertEquals("드세요", Conjugator.of("먹다").seyo());
		assertEquals("먹으세요", Conjugator.of("먹다").plainSeyo(), "the regular form is still there");
		assertEquals("마시세요", Conjugator.of("마시다").seyo(), "drinking keeps 마시세요: clearer for a bottle");
		assertEquals("자세요", Conjugator.of("자다").seyo(), "주무세요 is for later");
		assertEquals("드세요", Conjugator.of("들다").seyo(), "to hold: the ordinary ㄹ-drop");
		assertEquals("먹어요", Conjugator.of("먹다").eoyo(), "only the request form has a word of its own");
		assertEquals("빵을 드세요.", clue(new Goal.ConsumeItem("minecraft:bread", 1), "bread", 2));
		assertEquals("빵을 먹어요.", clue(new Goal.ConsumeItem("minecraft:bread", 1), "bread", 1));
		assertEquals("물약을 마시세요.", clue(new Goal.ConsumeItem("minecraft:potion", 1), null, 2));
		// a clue that says it with 먹으세요 or 드세요 still holds the verb 먹다
		assertTrue(Conjugator.contains("빵을 먹으세요.", "먹다"));
		assertTrue(Conjugator.contains("빵을 드세요.", "먹다"));
	}

	// ------------------------------------------------------------ B14. 갈다 is not 가다

	@Test
	void b14_theRequestToTillNamesItsToolSoItIsNotTheRequestToGo() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal till = new Goal.UseBlock("minecraft:dirt");
		assertEquals("흙을 갈아요.", SentenceBuilder.normal(till, null, List.of(), 1, false, names));
		assertEquals("괭이로 흙을 가세요.", SentenceBuilder.normal(till, null, List.of(), 2, false, names));
		assertEquals("괭이로 흙을 가세요.", SentenceBuilder.normal(till, null, List.of(), 3, false, names));
		assertEquals("흙을 갈아야 해요.", SentenceBuilder.normal(till, null, List.of(), 4, false, names));
		assertEquals("흙을 갈았어요.", SentenceBuilder.ceremony(till, null, List.of(), names));
		assertEquals("흙을 갈아요.", SentenceBuilder.hint(till, null, List.of(), names));
		assertTrue(SentenceBuilder.grammar(till, null, List.of(), 2, false, names).contains("L3.euro_instrument"));
		assertFalse(SentenceBuilder.grammar(till, null, List.of(), 1, false, names).contains("L3.euro_instrument"));
		// the other verbs of USE_BLOCK say nothing of the kind
		assertEquals("참나무 문을 열어요.", SentenceBuilder.normal(new Goal.UseBlock("minecraft:oak_door"), null, List.of(), 1,
				false, names));
		assertEquals("참나무 문을 여세요.", SentenceBuilder.normal(new Goal.UseBlock("minecraft:oak_door"), null, List.of(), 2,
				false, names));
	}

	// ------------------------------------------------------------ B15. crop blocks and every tier

	@Test
	void b15_aCropWordCoversItsCropBlock() {
		for (String[] row : new String[][]{{"carrot", "minecraft:carrots"}, {"potato", "minecraft:potatoes"},
				{"gen_beetroot", "minecraft:beetroots"}, {"sweet_berries", "minecraft:sweet_berry_bush"},
				{"cocoa_beans", "minecraft:cocoa"}, {"wheat", "minecraft:wheat"}}) {
			Goal harvest = new Goal.BreakBlock(row[1], 1, null);
			assertEquals(List.of(), problems(harvest, row[0]), row[0] + " teaches " + row[1]);
		}
		assertEquals("당근을 수확해요.", clue(new Goal.BreakBlock("minecraft:carrots", 1, "HARVEST"), "carrot"));
	}

	@Test
	void b15_everyToolWordCoversEveryTier() {
		for (String[] tool : new String[][]{{"pickaxe", "pickaxe"}, {"axe", "axe"}, {"shovel", "shovel"}, {"hoe", "hoe"},
				{"sword", "sword"}}) {
			for (String material : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite", "copper")) {
				String item = "minecraft:" + material + "_" + tool[1];
				assertEquals(List.of(), problems(new Goal.Craft(item, 1, null, null), tool[0]), item);
				assertTrue(io.github.leks2205.hangulquest.quest.goal.SentenceContext.wordNames(word(tool[0]),
						io.github.leks2205.hangulquest.quest.goal.ObjectKind.ITEM, item), item);
			}
		}
		assertEquals("돌 도끼를 만들어요.", clue(new Goal.Craft("minecraft:stone_axe", 1, null, null), "axe"));
		assertEquals("돌 도끼를 만드세요.", clue(new Goal.Craft("minecraft:stone_axe", 1, null, null), "axe", 2));
	}

	// ------------------------------------------------------------ B16. a concept that is dropped silently

	/** The ids of concepts that concepts.json holds but the loader drops, and what they lack. */
	static java.util.Map<String, String> droppedConcepts(String conceptsJson) {
		java.util.Map<String, String> dropped = new java.util.LinkedHashMap<>();
		for (JsonElement element : JsonParser.parseString(conceptsJson).getAsJsonArray()) {
			io.github.leks2205.hangulquest.data.Concept concept = io.github.leks2205.hangulquest.util.JsonIO.GSON
					.fromJson(element, io.github.leks2205.hangulquest.data.Concept.class);
			concept.fillText();
			if (!concept.isValid()) {
				String missing = concept.id == null || concept.id.isBlank() ? "an id" : concept.ko == null || concept.ko.isBlank()
						? "a Korean word" : "a German gloss (hangulquest.word." + concept.id + ".gloss in lang/content/de_de.json)";
				dropped.put(concept.id, missing);
			}
		}
		return dropped;
	}

	/** Every concept id a trail teaches, lists as its words or rewards. */
	static java.util.Set<String> taughtBy(List<Questline> trails) {
		java.util.Set<String> ids = new java.util.TreeSet<>();
		for (Questline trail : trails) {
			ids.addAll(trail.concepts());
			for (QuestStep step : trail.allSteps()) {
				ids.addAll(step.teach());
			}
		}
		return ids;
	}

	@Test
	void b16_noConceptAShippedTrailTeachesIsDroppedSilently() throws IOException {
		String json = new String(GeneratorWaveSixTest.class.getResourceAsStream(
				"/assets/hangulquest/data/concepts.json").readAllBytes(), StandardCharsets.UTF_8);
		java.util.Map<String, String> dropped = droppedConcepts(json);
		java.util.Set<String> taught = taughtBy(QuestLoader.loadAll());
		List<String> problems = new ArrayList<>();
		dropped.forEach((id, missing) -> {
			if (taught.contains(id)) {
				problems.add("concept " + id + " is taught by a shipped trail but dropped by the loader: it has no " + missing);
			}
		});
		assertEquals(List.of(), problems);
		assertEquals(Set.of(), dropped.keySet().stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()),
				"no concept at all is invalid: " + dropped);
	}

	@Test
	void b16_theCheckSeesAConceptWithoutAGermanGloss() {
		String json = "[{\"id\":\"probe_ok\",\"ko\":\"시험\",\"de\":\"Probe\"},{\"id\":\"probe_bad\",\"ko\":\"틀\"}]";
		java.util.Map<String, String> dropped = droppedConcepts(json);
		assertEquals(Set.of("probe_bad"), dropped.keySet());
		assertTrue(dropped.get("probe_bad").contains("German gloss"), dropped.get("probe_bad"));
	}

	// ------------------------------------------------------------ B17. the place is said once

	@Test
	void b17_standingByTheStationAndUsingItIsOnePhrase() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Condition byTable = new Condition.PlayerRel(Relation.NEXT_TO, "minecraft:crafting_table", null);
		Goal craft = new Goal.Craft("minecraft:iron_pickaxe", 1, "crafting_table", null);
		assertEquals("제작대 옆에서 철 곡괭이를 만드세요.", SentenceBuilder.normal(craft, null, List.of(byTable), 2, false, names));
		assertEquals("제작대 옆에서 철 곡괭이를 만들어요.", SentenceBuilder.hint(craft, null, List.of(byTable), names));
		assertEquals("제작대 옆에서 철 곡괭이를 만들었어요.", SentenceBuilder.ceremony(craft, null, List.of(byTable), names));
		Condition byFurnace = new Condition.PlayerRel(Relation.NEAR, "minecraft:furnace", null);
		assertEquals("화로 근처에서 철 주괴를 만드세요.", SentenceBuilder.normal(new Goal.Smelt(null, "minecraft:iron_ingot", 1),
				null, List.of(byFurnace), 2, false, names));
		// an authored clue that names the station gets the same treatment
		assertEquals("제작대 옆에서 철 곡괭이를 만드세요.", SentenceBuilder.authored(craft, "제작대에서 철 곡괭이를 만드세요.",
				List.of(byTable), null, names, 2));
		// another station is another place: the wave 4 rule still makes a modifier of the front
		Condition byTree = new Condition.PlayerRel(Relation.UNDER, "minecraft:oak_log", null);
		String other = SentenceBuilder.normal(craft, null, List.of(byTree), 2, false, names);
		assertTrue(other.contains("제작대에서") && other.contains("아래"), other);
	}

	// ------------------------------------------------------------ B18. giving to a villager

	private static final String VILLAGER = "minecraft:villager";
	private static final java.util.UUID VILLAGER_ID = java.util.UUID.nameUUIDFromBytes("v".getBytes(StandardCharsets.UTF_8));

	private static Observation gave(GiveFact.Method method) {
		Observation obs = new Observation();
		obs.addGave(new GiveFact("minecraft:bread", 1, VILLAGER, VILLAGER_ID, method));
		return obs;
	}

	@Test
	void b18_aVillagerTakesNoItemByRightClick() {
		Goal give = new Goal.Give("minecraft:bread", VILLAGER, null, 1);
		assertFalse(GoalEvaluator.tick(give, List.of(), gave(GiveFact.Method.USE_WITH), new GoalEvaluator.Tracker()));
		assertFalse(GoalEvaluator.tick(give, List.of(), gave(GiveFact.Method.FEED), new GoalEvaluator.Tracker()));
		assertTrue(GoalEvaluator.tick(give, List.of(), gave(GiveFact.Method.DROP_NEAR), new GoalEvaluator.Tracker()));
		assertTrue(GoalEvaluator.tick(give, List.of(), gave(GiveFact.Method.TRADE), new GoalEvaluator.Tracker()));
		// a cow still takes wheat by hand, a golem iron, and nothing changed for them
		Goal cow = new Goal.Give("minecraft:wheat", "minecraft:cow", null, 1);
		Observation fed = new Observation();
		fed.addGave(new GiveFact("minecraft:wheat", 1, "minecraft:cow", VILLAGER_ID, GiveFact.Method.FEED));
		assertTrue(GoalEvaluator.tick(cow, List.of(), fed, new GoalEvaluator.Tracker()));
		// a wandering trader only takes a trade
		Goal trader = new Goal.Give("minecraft:emerald", "minecraft:wandering_trader", null, 1);
		Observation dropped = new Observation();
		dropped.addGave(new GiveFact("minecraft:emerald", 1, "minecraft:wandering_trader", VILLAGER_ID, GiveFact.Method.DROP_NEAR));
		assertFalse(GoalEvaluator.tick(trader, List.of(), dropped, new GoalEvaluator.Tracker()));
		Observation paid = new Observation();
		paid.addGave(new GiveFact("minecraft:emerald", 1, "minecraft:wandering_trader", VILLAGER_ID, GiveFact.Method.TRADE));
		assertTrue(GoalEvaluator.tick(trader, List.of(), paid, new GoalEvaluator.Tracker()));
	}

	@Test
	void b18_theValidatorRefusesAWayThatCannotWork() {
		assertFalse(problems(new Goal.Give("minecraft:bread", VILLAGER, "USE_WITH", 1), "bread").isEmpty());
		assertFalse(problems(new Goal.Give("minecraft:bread", VILLAGER, "FEED", 1), "bread").isEmpty());
		assertEquals(List.of(), problems(new Goal.Give("minecraft:bread", VILLAGER, "DROP_NEAR", 1), "bread"));
		assertEquals(List.of(), problems(new Goal.Give("minecraft:emerald", VILLAGER, "TRADE", 1)));
		assertFalse(problems(new Goal.Give("minecraft:emerald", "minecraft:wandering_trader", "DROP_NEAR", 1)).isEmpty());
		assertEquals(List.of(), problems(new Goal.Give("minecraft:wheat", "minecraft:cow", "FEED", 1), "wheat"));
	}

	@Test
	void b18_theHintHandsItOverWithTheVerbOfTheClue() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal bread = new Goal.Give("minecraft:bread", VILLAGER, null, 1);
		assertEquals("주민에게 빵을 주세요.", SentenceBuilder.normal(bread, null, List.of(), 2, false, names));
		assertEquals("주민에게 빵을 줘요.", SentenceBuilder.hint(bread, null, List.of(), names), "wave 12: 주다 with 에게, as the clue");
		assertEquals("주민에게 빵을 줬어요.", SentenceBuilder.ceremony(bread, null, List.of(), names));
		// an explicit drop is spoken as it was written
		assertEquals("주민 앞에 빵을 떨어뜨려요.", SentenceBuilder.hint(new Goal.Give("minecraft:bread", VILLAGER, "DROP_NEAR", 1),
				null, List.of(), names));
		// a payment is a trade and keeps its sentence; another creature is handed the item as before
		assertEquals("주민에게 에메랄드를 줘요.", SentenceBuilder.hint(new Goal.Give("minecraft:emerald", VILLAGER, null, 1), null,
				List.of(), names));
		assertEquals("소에게 밀을 줘요.", SentenceBuilder.hint(new Goal.Give("minecraft:wheat", "minecraft:cow", null, 1), null,
				List.of(), names));
	}

	// the pure side of the sensor: an item dropped in front of a villager, then gone

	@Test
	void b18_aDropCountsWhenTheVillagerHasTakenIt() {
		GiveTracker gives = new GiveTracker();
		Map<String, Integer> before = Map.of("minecraft:bread", 3);
		Map<String, Integer> after = Map.of("minecraft:bread", 2);
		gives.inventoryChanged(before, after, 1_000L, true);
		GiveTracker.Target villager = new GiveTracker.Target(VILLAGER, VILLAGER_ID, 2.0);
		assertNull(gives.itemSpawned(77, "minecraft:bread", 1, 1.5, List.of(villager), 1_100L), "not yet");
		assertTrue(gives.hasPickups(1_200L));
		// the item lies there
		assertEquals(List.of(), gives.pickupsChecked(Set.of(77), Set.of(VILLAGER_ID), 1_500L));
		// it is gone, and the villager is still near
		assertEquals(List.of(), gives.pickupsChecked(Set.of(), Set.of(VILLAGER_ID), 3_000L), "grace for a take-back");
		List<GiveFact> taken = gives.pickupsChecked(Set.of(), Set.of(VILLAGER_ID), 3_500L);
		assertEquals(1, taken.size());
		assertEquals(new GiveFact("minecraft:bread", 1, VILLAGER, VILLAGER_ID, GiveFact.Method.DROP_NEAR), taken.get(0));
		assertFalse(gives.hasPickups(3_600L));
	}

	@Test
	void b18_aDropThePlayerTakesBackOrThatDespawnsIsNoGift() {
		GiveTracker.Target villager = new GiveTracker.Target(VILLAGER, VILLAGER_ID, 2.0);
		GiveTracker back = new GiveTracker();
		back.inventoryChanged(Map.of("minecraft:bread", 3), Map.of("minecraft:bread", 2), 1_000L, true);
		back.itemSpawned(77, "minecraft:bread", 1, 1.5, List.of(villager), 1_100L);
		back.pickupsChecked(Set.of(), Set.of(VILLAGER_ID), 2_000L);
		back.inventoryChanged(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 3), 2_100L, true);
		assertEquals(List.of(), back.pickupsChecked(Set.of(), Set.of(VILLAGER_ID), 2_600L), "the player took it back");
		// the villager walked off: nobody near when the item vanished
		GiveTracker away = new GiveTracker();
		away.inventoryChanged(Map.of("minecraft:bread", 3), Map.of("minecraft:bread", 2), 1_000L, true);
		away.itemSpawned(78, "minecraft:bread", 1, 1.5, List.of(villager), 1_100L);
		away.pickupsChecked(Set.of(), Set.of(), 2_000L);
		assertEquals(List.of(), away.pickupsChecked(Set.of(), Set.of(), 2_600L));
		// a cow gets the drop at once, as before
		GiveTracker cow = new GiveTracker();
		cow.inventoryChanged(Map.of("minecraft:wheat", 3), Map.of("minecraft:wheat", 2), 1_000L, true);
		GiveFact fact = cow.itemSpawned(79, "minecraft:wheat", 1, 1.5,
				List.of(new GiveTracker.Target("minecraft:cow", VILLAGER_ID, 2.0)), 1_100L);
		assertEquals(GiveFact.Method.DROP_NEAR, fact.method());
		// and a drop lying for half a minute is forgotten
		GiveTracker old = new GiveTracker();
		old.inventoryChanged(Map.of("minecraft:bread", 3), Map.of("minecraft:bread", 2), 1_000L, true);
		old.itemSpawned(80, "minecraft:bread", 1, 1.5, List.of(villager), 1_100L);
		assertFalse(old.hasPickups(1_100L + GiveTracker.PICKUP_WINDOW_MS + 1));
	}

	// ------------------------------------------------------------ B19. coverage.py reads 팔아요, B20. 에게서

	@Test
	void b19_theCoverageToolReadsTheVerbsOfSellingAndBuyingWithTheirParticles() {
		CoverageTest.requirePython();
		QuestScaffoldTest.Run sell = QuestScaffoldTest.python("tools/coverage.py", "--tokens", "주민에게 밀을 팔아요");
		assertEquals(0, sell.exit(), sell.output());
		List<String> sold = List.of(sell.output().split("\\R"));
		assertTrue(sold.contains("팔다"), "팔다 not found in " + sold);
		assertTrue(sold.contains("주민") && sold.contains("밀"), sold.toString());
		QuestScaffoldTest.Run buy = QuestScaffoldTest.python("tools/coverage.py", "--tokens", "주민에게서 빵을 사요. 주민에게서 빵을 사세요.");
		assertEquals(0, buy.exit(), buy.output());
		List<String> bought = List.of(buy.output().split("\\R"));
		assertTrue(bought.contains("사다") && bought.contains("주민") && bought.contains("빵"), bought.toString());
	}

	@Test
	void b20_aBuyerGetsItFromTheSellerAndASellerSellsToThem() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal buy = new Goal.Trade("minecraft:apple", VILLAGER, "BUY", 1);
		Goal sell = new Goal.Trade("minecraft:wheat", VILLAGER, "SELL", 1);
		assertEquals("주민에게서 사과를 사세요.", SentenceBuilder.normal(buy, null, List.of(), 2, false, names));
		assertEquals("주민에게서 사과를 사요.", SentenceBuilder.hint(buy, null, List.of(), names));
		assertEquals("주민에게서 사과를 샀어요.", SentenceBuilder.ceremony(buy, null, List.of(), names));
		assertEquals("주민에게 밀을 파세요.", SentenceBuilder.normal(sell, null, List.of(), 2, false, names));
		assertEquals("주민에게 밀을 팔아요.", SentenceBuilder.hint(sell, null, List.of(), names));
		assertEquals("주민에게 밀을 팔았어요.", SentenceBuilder.ceremony(sell, null, List.of(), names));
		assertEquals("떠돌이 상인에게서 에메랄드를 사요.", SentenceBuilder.normal(
				new Goal.Trade("minecraft:emerald", "minecraft:wandering_trader", "BUY", 1), null, List.of(), 1, false, names));
		// 에게서 has no card of its own: its id stands for L2.ege
		assertTrue(SentenceBuilder.grammar(buy, null, List.of(), 2, false, names).contains("L2.ege_source"));
		assertEquals("L2.ege", io.github.leks2205.hangulquest.quest.grammar.Ladder.shared().resolve("L2.ege_source"));
		assertEquals("주민에게서", io.github.leks2205.hangulquest.quest.KoGrammar.egeseo("주민"));
	}

	// ------------------------------------------------------------ follow-ups of the review of items 2 and B14

	@Test
	void i2_ironAcceptsRawIronButDoesNotRenameIt() {
		// a step that teaches 철 takes the raw iron too (the validator and the evaluator), and says its own name
		Goal raw = new Goal.AcquireItem("minecraft:raw_iron", 3);
		assertEquals(List.of(), problems(raw, "iron"));
		assertEquals("철 원석을 세 개 모아요.", clue(raw, "iron"));
		assertTrue(Accepted.of(step(raw, "iron"), CATALOG.concept("iron").orElseThrow()).widens("minecraft:raw_iron"));
	}

	@Test
	void b14_theHoeInTheHandIsNotNamedTwice() {
		SentenceBuilder.Namer names = KoreanFixtures.registryNamer();
		Goal till = new Goal.UseBlock("minecraft:dirt");
		Condition hoe = new Condition.Holding("minecraft:wooden_hoe");
		assertEquals("나무 괭이를 들고 흙을 가세요.", SentenceBuilder.normal(till, null, List.of(hoe), 2, false, names));
		assertEquals("나무 괭이를 들고 흙을 갈아요.", SentenceBuilder.normal(till, null, List.of(hoe), 1, false, names));
	}
}
