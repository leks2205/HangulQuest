package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.ToggleTracker;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 4: the limits BATCH-005 and BATCH-006 named. One test per item, the item number in the name.
 */
class GeneratorWaveFourTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);
	private static final long T0 = 100_000L;

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, Condition... conditions) {
		return clue(goal, concept, 1, conditions);
	}

	private static String clue(Goal goal, String concept, int level, Condition... conditions) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(conditions), level, false,
				NAMER);
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static Observation at(long now) {
		Observation obs = new Observation();
		obs.now = now;
		return obs;
	}

	// ------------------------------------------------------------------------------ 1. the candle flame

	@Test
	void i1_aCandleHasToBeLitNotJustClicked() {
		Goal candle = new Goal.UseBlock("minecraft:candle");
		Goal lit = new Goal.UseBlock("minecraft:candle", "LIT");
		Observation clicked = at(T0);
		clicked.usedBlocks.add("minecraft:candle"); // a click with the hand puts the flame out, or does nothing
		assertFalse(GoalEvaluator.tick(candle, List.of(), clicked, new GoalEvaluator.Tracker()));
		assertFalse(GoalEvaluator.tick(lit, List.of(), clicked, new GoalEvaluator.Tracker()));

		Observation flame = at(T0);
		flame.addLit("minecraft:candle");
		assertTrue(GoalEvaluator.tick(candle, List.of(), flame, new GoalEvaluator.Tracker()), "lit is the default");
		assertTrue(GoalEvaluator.tick(lit, List.of(), flame, new GoalEvaluator.Tracker()));
		Observation out = at(T0);
		out.addSnuffed("minecraft:candle");
		assertFalse(GoalEvaluator.tick(lit, List.of(), out, new GoalEvaluator.Tracker()),
				"putting it out is no lighting");
		Observation other = at(T0);
		other.addLit("minecraft:campfire");
		assertFalse(GoalEvaluator.tick(lit, List.of(), other, new GoalEvaluator.Tracker()));
	}

	@Test
	void i1_aCampfireOrAnyOtherBlockKeepsTheOldUse() {
		Observation clicked = at(T0);
		clicked.usedBlocks.add("minecraft:campfire");
		assertTrue(GoalEvaluator.tick(new Goal.UseBlock("minecraft:campfire"), List.of(), clicked,
				new GoalEvaluator.Tracker()), "cooking on a campfire is a use");
		assertFalse(GoalEvaluator.tick(new Goal.UseBlock("minecraft:campfire", "LIT"), List.of(), clicked,
				new GoalEvaluator.Tracker()));
		Observation lit = at(T0);
		lit.addLit("minecraft:soul_campfire");
		assertTrue(GoalEvaluator.tick(new Goal.UseBlock("minecraft:campfire", "LIT"), List.of(), lit,
				new GoalEvaluator.Tracker()), "the campfire family");
	}

	@Test
	void i1_litSpeaksTheVerbOfTheFlame() {
		assertEquals("초를 켜요.", clue(new Goal.UseBlock("minecraft:candle", "LIT"), "candle"));
		assertEquals("초를 켜요.", clue(new Goal.UseBlock("minecraft:candle"), "candle"));
		assertEquals("모닥불을 피워요.", clue(new Goal.UseBlock("minecraft:campfire", "LIT"), "campfire"));
		assertEquals(Verb.SWITCH_ON, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:candle", "LIT")));
		assertEquals(Verb.KINDLE, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:campfire", "LIT")));
		assertEquals("UseBlock:minecraft:candle", GoalEvaluator.key(new Goal.UseBlock("minecraft:candle")));
		assertEquals("UseBlock:minecraft:candle/LIT", GoalEvaluator.key(new Goal.UseBlock("minecraft:candle", "LIT")));
	}

	@Test
	void i1_onlyBlocksWithAFlameCanBeLit() {
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:candle", "LIT"), "candle"));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:blue_candle", "LIT")));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:soul_campfire", "LIT")));
		assertFalse(problems(new Goal.UseBlock("minecraft:oak_door", "LIT")).isEmpty());
		assertFalse(problems(new Goal.UseBlock("minecraft:torch", "LIT")).isEmpty());
	}

	@Test
	void i1_theLitStateIsWatchedLikeTheDoorState() {
		ToggleTracker lights = new ToggleTracker();
		BlockPos wick = new BlockPos(3, 64, 3);
		Map<BlockPos, Boolean> world = new HashMap<>();
		world.put(wick, false);
		List<String> lit = new ArrayList<>();
		List<String> out = new ArrayList<>();
		lights.clicked(wick, "minecraft:candle", false, T0);
		lights.poll(world::get, T0 + 50, lit::add, out::add);
		assertTrue(lit.isEmpty(), "the server has not answered");
		world.put(wick, true);
		lights.poll(world::get, T0 + 100, lit::add, out::add);
		assertEquals(List.of("minecraft:candle"), lit);
		lights.clicked(wick, "minecraft:candle", true, T0 + 2000);
		world.put(wick, false);
		lights.poll(world::get, T0 + 2050, lit::add, out::add);
		assertEquals(List.of("minecraft:candle"), out);
	}

	// ------------------------------------------------------------------------------------- 2. BREED

	private static Observation scene(long now, UUID calf, double calfX, UUID... adults) {
		Observation obs = at(now);
		double x = 0;
		for (UUID adult : adults) {
			obs.addEntityPosition(new EntityFact("minecraft:cow", adult, x, 64, 0, 0F, 0F, false));
			x += 2;
		}
		if (calf != null) {
			obs.addEntityPosition(new EntityFact("minecraft:cow", calf, calfX, 64, 0, 0F, 0F, true));
		}
		return obs;
	}

	private static Observation fed(long now, UUID... adults) {
		Observation obs = at(now);
		for (UUID adult : adults) {
			obs.addGave(new GiveFact("minecraft:wheat", 1, "minecraft:cow", adult, GiveFact.Method.FEED));
		}
		return obs;
	}

	@Test
	void i2_aBabyBornFarFromTheFedPairIsNotTheirs() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(cow, List.of(), fed(T0, a, b), tracker));
		// a calf next to the player but 10 blocks from the fed pair: born in the next pen
		assertFalse(GoalEvaluator.tick(cow, List.of(), scene(T0 + 500, UUID.randomUUID(), 12, a, b), tracker));
		// and it stays uncounted when the pair walks over to it later
		UUID late = UUID.randomUUID();
		assertFalse(GoalEvaluator.tick(cow, List.of(), scene(T0 + 600, late, 12, a, b), tracker));
		assertFalse(GoalEvaluator.tick(cow, List.of(), scene(T0 + 700, late, 1, a, b), tracker));
	}

	@Test
	void i2_aBabyBetweenTheFedPairCounts() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(cow, List.of(), fed(T0, a, b), tracker));
		assertTrue(GoalEvaluator.tick(cow, List.of(), scene(T0 + 500, UUID.randomUUID(), 1, a, b), tracker));
	}

	@Test
	void i2_oneFedAnimalIsNoPair() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		UUID a = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(cow, List.of(), fed(T0, a), tracker));
		assertFalse(GoalEvaluator.tick(cow, List.of(), scene(T0 + 500, UUID.randomUUID(), 1, a, stranger), tracker),
				"the other cow was not fed by the player");
	}

	// ------------------------------------------------------------------------------ 3. SKY OPEN at level 1

	@Test
	void i3_openSkyNamesOnlyConstructionsThatExistAndTheLongFormWaitsForLevelFive() throws java.io.IOException {
		Condition open = new Condition.Sky(Condition.SkyState.OPEN);
		SentenceBuilder.Namer registry = KoreanFixtures.registryNamer();
		int checked = 0;
		for (ActionSentenceGoldTest.Row row : ActionSentenceGoldTest.rows()) {
			for (int level = 1; level <= 6; level++) {
				List<Condition> conditions = new ArrayList<>(row.conditions());
				conditions.removeIf(c -> c instanceof Condition.Sky);
				conditions.add(open);
				String taught = row.word().split("\\+")[0];
				Word noun = taught.equals("-") ? null : word(taught);
				SentenceBuilder.Spoken spoken = SentenceBuilder.spoken(row.goal(), noun, conditions, level, false, registry);
				if (!spoken.ko().contains("밖") && !spoken.ko().contains("하늘이 보이")) {
					continue; // the goal says the place itself (or the clause is dropped)
				}
				checked++;
				assertFalse(spoken.grammar().contains("L3.neun_attr"),
						row.id() + " at level " + level + ": " + spoken.ko() + " " + spoken.grammar());
				for (String id : spoken.grammar()) {
					assertTrue(GrammarBook.shared().has(Ladder.shared().resolve(id)),
							row.id() + " at level " + level + " emits " + id + " which has no construction");
				}
			}
		}
		assertTrue(checked > 10, "only " + checked + " sentences with 밖에서 were checked");
		for (int level = 3; level <= 4; level++) {
			assertTrue(SentenceBuilder.normal(new Goal.ConsumeItem("minecraft:bread", 1), word("bread"), List.of(open),
					level, false, registry).startsWith("밖에서 "), "level " + level);
		}
		// the adnominal form (보이는) is a level 5 construction, and its id resolves
		SentenceBuilder.Spoken five = SentenceBuilder.spoken(new Goal.ConsumeItem("minecraft:bread", 1), word("bread"),
				List.of(open), 5, false, registry);
		assertTrue(five.ko().contains("하늘이 보이는 곳에서"), five.ko());
		assertTrue(five.grammar().contains("L5.adn_present_neun"), five.grammar().toString());
	}

	// ---------------------------------------------------------------------------------- 4. doubled 에서

	private static final Condition HIGH = new Condition.YRange(90, 320);
	private static final Condition NIGHT = new Condition.Time(Condition.Phase.NIGHT);
	private static final Goal BED = new Goal.UseBlock("minecraft:red_bed");

	@Test
	void i4_aHeightBeforeAPlaceBecomesItsModifier() {
		// levels 1-2 have no 의: the height stands in front of the bed as a noun
		assertEquals("높이 구십 블록 위 침대에서 자요.", clue(BED, "bed", 1, HIGH));
		assertEquals("밤에 높이 구십 블록 위 침대에서 자요.", clue(BED, "bed", 1, NIGHT, HIGH));
		assertEquals("밤에 높이 구십 블록 위 침대에서 자세요.", clue(BED, "bed", 2, NIGHT, HIGH));
		// from level 3 with 의
		assertEquals("밤에 높이 구십 블록 위의 빨간색 침대에서 자세요.", clue(BED, "bed", 3, NIGHT, HIGH));
		// the height first in the list still ends up next to the place
		assertEquals("밤에 높이 구십 블록 위 침대에서 자요.", clue(BED, "bed", 1, HIGH, NIGHT));
		// the hint and the finale line say it the same way
		assertEquals("밤에 높이 구십 블록 위 침대에서 자요.",
				SentenceBuilder.hint(BED, word("bed"), List.of(NIGHT, HIGH), NAMER));
		assertEquals("밤에 높이 구십 블록 위 침대에서 잤어요.",
				SentenceBuilder.ceremony(BED, word("bed"), List.of(NIGHT, HIGH), NAMER));
	}

	@Test
	void i4_nothingIsMergedWhenThereIsNoPlaceToMergeWith() {
		Goal bread = new Goal.ConsumeItem("minecraft:bread", 1);
		assertEquals("높이 구십 블록 위에서 빵을 먹어요.", clue(bread, "bread", 1, HIGH));
		// 밖에서 and 물속에서 are no places a noun can modify
		Goal torch = new Goal.HoldWhile("minecraft:torch", null, new Condition.Sky(Condition.SkyState.OPEN), 10);
		assertFalse(clue(torch, "torch", 1, HIGH).contains("위 밖"), clue(torch, "torch", 1, HIGH));
		assertEquals("높이 구십 블록 위에서 밖에서 빵을 먹어요.",
				clue(bread, "bread", 1, HIGH, new Condition.Sky(Condition.SkyState.OPEN)));
	}

	@Test
	void i4_otherPlaceConditionsMergeLikewise() {
		Goal apple = new Goal.TakeOut("minecraft:apple", "minecraft:chest", 1);
		assertEquals("해변 상자에서 사과를 꺼내요.", clue(apple, null, 1, new Condition.InBiome("minecraft:beach")));
		assertEquals("네더의 빨간색 침대에서 자세요.", clue(BED, "bed", 3, new Condition.InDimension("minecraft:the_nether")));
		assertEquals("북극곰 근처 침대에서 자요.", clue(BED, "bed", 1, new Condition.NearEntity("minecraft:polar_bear", 8)));
	}

	@Test
	void i4_anAuthoredClueGetsTheHeightAsAModifierToo() {
		List<Condition> high = List.of(HIGH);
		assertEquals("높이 구십 블록 위 침대에서 자요.", SentenceBuilder.authored(BED, "침대에서 자요.", high, word("bed"), NAMER, 1));
		assertEquals("높이 구십 블록 위의 침대에서 자세요.", SentenceBuilder.authored(BED, "침대에서 자세요.", high, word("bed"), NAMER, 3));
		// a clue that says it already is left alone
		assertEquals("높이 구십 블록 위 침대에서 자요.",
				SentenceBuilder.authored(BED, "높이 구십 블록 위 침대에서 자요.", high, word("bed"), NAMER, 1));
		assertEquals("높이 구십 블록 위의 침대에서 자요.",
				SentenceBuilder.authored(BED, "높이 구십 블록 위의 침대에서 자요.", high, word("bed"), NAMER, 3));
	}

	// ------------------------------------------------------------------ 5. a kind word for two items

	@Test
	void i5_aKindWordDoesNotNameBothItemsOfARelation() {
		Goal beside = new Goal.PlaceRel("minecraft:dandelion", Relation.NEXT_TO, "minecraft:poppy", 1);
		assertEquals("꽃 옆에 민들레를 심어요.", clue(beside, "flower"));
		assertEquals("꽃 옆에 민들레를 심었어요.", SentenceBuilder.ceremony(beside, word("flower"), List.of(), NAMER));
		assertEquals("꽃 옆에 민들레를 심어요.", SentenceBuilder.hint(beside, word("flower"), List.of(), NAMER));
		// the same flower on both sides is the same thing
		assertEquals("꽃 옆에 꽃을 심어요.", clue(new Goal.PlaceRel("minecraft:poppy", Relation.NEXT_TO,
				"minecraft:poppy", 1), "flower"));
		// a word for one flower names that one; the other keeps its registry name
		assertEquals("해바라기 옆에 민들레를 심어요.", clue(new Goal.PlaceRel("minecraft:dandelion", Relation.NEXT_TO,
				"minecraft:sunflower", 1), "sunflower"));
		// a tag anchor is a kind too: any flower, and a dandelion beside it
		assertEquals("꽃 옆에 민들레를 심어요.", clue(new Goal.PlaceRel("minecraft:dandelion", Relation.NEXT_TO,
				"#minecraft:flowers", 1), "flower"));
		// BETWEEN: two anchors of the kind and one different object
		assertEquals("꽃과 꽃 사이에 민들레를 심어요.", clue(new Goal.PlaceRel("minecraft:dandelion", Relation.BETWEEN,
				"minecraft:poppy", 1, "minecraft:allium"), "flower"));
	}

	// --------------------------------------------------------- 7. a verb as the first taught word, authored

	@Test
	void i7_anAuthoredClueMentionsItsVerbInAnyConjugatedForm() {
		Word climb = word("climb");
		assertEquals("올라가다", climb.ko);
		assertTrue(SentenceBuilder.mentions("언덕 위로 올라가요.", climb));
		assertTrue(SentenceBuilder.mentions("산에 올라갔어요.", climb));
		assertTrue(SentenceBuilder.mentions("언덕에 올라가세요.", climb));
		assertFalse(SentenceBuilder.mentions("언덕 위로 가요.", climb));
		assertFalse(SentenceBuilder.mentions("", climb));
		assertFalse(SentenceBuilder.mentions(null, climb));
		// a noun still has to stand as it is
		assertTrue(SentenceBuilder.mentions("문을 열어요.", word("door")));
		assertFalse(SentenceBuilder.mentions("창문을 열어요.", word("chest")));
		// a verb the clue does not hold in any form
		Word cook = word("cook");
		assertTrue(SentenceBuilder.mentions("닭고기를 요리해요.", cook));
		assertFalse(SentenceBuilder.mentions("닭고기를 먹어요.", cook));
	}

	// ---------------------------------------------------------------------- 8. "bei" is no relation word

	private static List<String> relationProblems(String german) {
		List<String> problems = new ArrayList<>();
		Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.ON_TOP_OF, "minecraft:chest", 1);
		QuestStep step = new QuestStep("s1", List.of("torch"), goal, List.of(), null, german, "Place a torch.", null,
				null, null, null);
		io.github.leks2205.hangulquest.quest.spatial.SpatialRules.checkRelationText(goal, Relation.ON_TOP_OF,
				new ValidationContext("t/s1", step, CATALOG, problems));
		return problems.stream().filter(p -> p.contains("gives the relation away")).toList();
	}

	@Test
	void i8_beiRegenIsNotAGiveaway() {
		assertEquals(List.of(), relationProblems("Bei Regen eine Fackel setzen."));
		assertEquals(List.of(), relationProblems("Setze bei Regen eine Fackel."));
		// the step asks for ON_TOP_OF: only the words for "on top of" give it away
		for (String real : List.of("Setze eine Fackel auf die Kiste.", "Setze eine Fackel über die Kiste.",
				"Setze eine Fackel obendrauf.")) {
			assertFalse(relationProblems(real).isEmpty(), real);
		}
	}

	// --------------------------------------------------------------------- 9. the band of a height

	private static String signature(Goal goal) {
		return GoalTypes.of(goal).taskSignature(goal);
	}

	@Test
	void i9_aHeightHasABandInItsSignature() {
		String hill = signature(new Goal.ReachHeight(70, 320));
		String eighty = signature(new Goal.ReachHeight(80, 320));
		String ninety = signature(new Goal.ReachHeight(90, 320));
		String mountain = signature(new Goal.ReachHeight(100, 320));
		assertEquals(4, java.util.Set.of(hill, eighty, ninety, mountain).size());
		// a taller mountain is the same band, and the ceiling does not matter
		assertEquals(mountain, signature(new Goal.ReachHeight(140, 320)));
		assertEquals(eighty, signature(new Goal.ReachHeight(85, 200)));
		// a descent and the ground are their own tasks
		assertEquals(3, java.util.Set.of(signature(new Goal.ReachHeight(-64, 30)), signature(new Goal.ReachHeight(0, 320)),
				hill).size());
	}

	// ------------------------------------------------------------------------- 10. the verb of a used block

	/** Block, and the sentence: the choices of the linguist check (see the commit message). */
	private static final String[][] USED = {
		{"crafting_table", "제작대를 열어요."}, {"anvil", "모루를 사용해요."}, {"stonecutter", "석재 절단기를 사용해요."},
		{"grindstone", "숫돌을 사용해요."}, {"enchanting_table", "마법 부여대를 열어요."}, {"beacon", "신호기를 사용해요."},
		{"smithing_table", "대장장이 작업대를 사용해요."}, {"cartography_table", "지도 제작대를 사용해요."},
		{"jukebox", "주크박스를 틀어요."}, {"respawn_anchor", "리스폰 정박기를 충전해요."},
		{"copper_bulb", "구리 전구를 켜요."}, {"beehive", "벌통을 수확해요."}, {"bee_nest", "벌집을 수확해요."},
		{"composter", "퇴비통을 채워요."}, {"cauldron", "가마솥을 채워요."}, {"flower_pot", "화분을 채워요."},
		{"grass_block", "잔디를 갈아요."},
		// no everyday verb for the bare click: operate
		{"lectern", "독서대를 사용해요."}, {"lodestone", "자석석을 사용해요."}, {"water_cauldron", "물 가마솥을 사용해요."},
		// unchanged
		{"candle", "초를 켜요."}, {"bell", "종을 울려요."}, {"note_block", "소리 블록을 쳐요."}, {"red_bed", "침대에서 자요."},
		{"stone_button", "돌 버튼을 눌러요."}, {"lever", "레버를 당겨요."}, {"oak_door", "문을 열어요."},
		{"chest", "상자를 열어요."}, {"furnace", "화로를 열어요."}};

	@Test
	void i10_everyUsedBlockHasItsOwnVerb() {
		for (String[] row : USED) {
			assertEquals(row[1], clue(new Goal.UseBlock("minecraft:" + row[0]), null), row[0]);
		}
		// 쓰다 is no default any more: it means write, wear or use, and "초를 써요" read as "write a candle"
		assertEquals(Verb.OPERATE, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:target")));
		assertEquals("과녁을 사용해요.", clue(new Goal.UseBlock("minecraft:target"), null));
	}

	@Test
	void i10_noUsedBlockOfTheContentIsUsedWithTheOldDefault() {
		// no floor on how many USE_BLOCK steps the content has: a trail that drops one must not fail this check
		List<String> offenders = new java.util.ArrayList<>();
		for (io.github.leks2205.hangulquest.quest.Questline trail : io.github.leks2205.hangulquest.quest.QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				if (step.goal() instanceof Goal.UseBlock use && use.change() == null
						&& SentenceBuilder.verbFor(step.goal()) == Verb.USE) {
					offenders.add(trail.id() + "/" + step.id() + " " + use.block());
				}
			}
		}
		assertEquals(List.of(), offenders);
	}

	// -------------------------------------------------------------------- 12. the form-dependent steps

	private static boolean formDependent(Goal goal) {
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, "Do it.", null, null, null, null);
		return ContentFacts.step(step, 1, false, CATALOG, NAMER, "", "").formDependent();
	}

	@Test
	void i12_aStepIsFormDependentWhenTheKoreanFormDecidesTheAction() {
		Goal place = new Goal.PlaceBlock("minecraft:torch", 1);
		assertTrue(formDependent(new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "minecraft:chest", 1)));
		assertTrue(formDependent(new Goal.PlaceRel("minecraft:torch", Relation.BETWEEN, "minecraft:chest", 1,
				"minecraft:furnace")));
		assertTrue(formDependent(new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF,
				io.github.leks2205.hangulquest.quest.spatial.Pattern.PILLAR, "minecraft:cobblestone")));
		assertTrue(formDependent(new Goal.Give("minecraft:bread", "minecraft:villager", "GIVE", 1)));
		assertTrue(formDependent(new Goal.Avoid(new Goal.UseBlock("minecraft:red_bed"), 60, null)));
		assertTrue(formDependent(new Goal.Sequence(List.of(place, new Goal.UseBlock("minecraft:oak_door")), true,
				"BEFORE")));
		// a plain placement, an unordered sequence and a craft say the same whatever the form
		assertFalse(formDependent(place));
		assertFalse(formDependent(new Goal.Craft("minecraft:torch", 1, null, List.of())));
		assertFalse(formDependent(new Goal.Sequence(List.of(place, new Goal.UseBlock("minecraft:oak_door")), false,
				null)));
	}

	@Test
	void i2_theBufferKeepsTheUuidOfEveryInteraction() {
		io.github.leks2205.hangulquest.sense.EventBuffer buffer = new io.github.leks2205.hangulquest.sense.EventBuffer();
		UUID cow = UUID.randomUUID();
		buffer.entityInteracted("minecraft:cow", cow);
		buffer.entityInteracted("minecraft:pig"); // an old caller without a uuid
		Observation obs = at(T0);
		buffer.drainInto(obs);
		assertEquals(List.of("minecraft:cow", "minecraft:pig"), obs.interactedEntities);
		assertEquals(java.util.Arrays.asList(cow, null), obs.interactedUuids);
		buffer.drainInto(obs);
		assertEquals(2, obs.interactedUuids.size(), "drained once");
	}
}
