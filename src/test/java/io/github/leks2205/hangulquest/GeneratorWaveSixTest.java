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
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.Tiers;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.NearMiss;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.EntityFact;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertEquals("철을 세 개 모아요.", clue(new Goal.AcquireItem("minecraft:raw_iron", 3), "iron"));
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
				assertTrue(registry.startsWith(Tiers.material(material) + " "), material + "_" + piece + ": " + registry);
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
		assertEquals("철 갑옷을 만들어요.", clue(new Goal.Craft("minecraft:iron_chestplate", 1, null, null), "armor"));
		assertEquals("철 바지를 만들어요.", clue(new Goal.Craft("minecraft:iron_leggings", 1, null, null), "leggings"));
		assertEquals("철 신발을 만들어요.", clue(new Goal.Craft("minecraft:iron_boots", 1, null, null), "shoes"));
		assertEquals("가죽 신발을 만들어요.", clue(new Goal.Craft("minecraft:leather_boots", 1, null, null), "shoes"));
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
		assertEquals("철 갑옷을 입었어요.", SentenceBuilder.ceremony(new Goal.Equip("minecraft:iron_chestplate"), word("armor"), List.of(), names));
		assertEquals("철 투구를 벗었어요.", SentenceBuilder.ceremony(new Goal.Equip("minecraft:iron_helmet", "OFF"), word("helmet"), List.of(), names));
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
}
