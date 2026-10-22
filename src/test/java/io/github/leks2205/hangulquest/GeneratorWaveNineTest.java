package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.ClueWords;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Counters;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.quest.QuestFile;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 9 (from the weaknesses BATCH-012 and BATCH-013 reported and the BATCH-012 review): doubled
 * counters, the size of a row, the order of the conditions, seconds as words, falling, the tool that decides the
 * verb, armour on a creature, more creature anchors, strict loading, and the tools around the generator.
 */
class GeneratorWaveNineTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMES = KoreanFixtures.namer(CATALOG);
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, List<Condition> conditions, int level) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), conditions, level, false, REGISTRY);
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static String clue(Goal goal, int level) {
		return clue(goal, null, List.of(), level);
	}

	// ------------------------------------------------------------ 1. a doubled counter

	@Test
	void i1_aNameThatEndsInItsCounterIsCountedWithGae() {
		assertEquals("꿀이 든 병을 네 개 모으세요.", clue(new Goal.AcquireItem("minecraft:honey_bottle", 4), 2));
		assertEquals("꿀이 든 병을 네 개 모았어요.", SentenceBuilder.ceremony(new Goal.AcquireItem("minecraft:honey_bottle", 4),
				null, List.of(), REGISTRY));
		Counters counters = Counters.shared();
		assertEquals("개", counters.counter("minecraft:honey_bottle", "꿀이 든 병"));
		assertEquals("개", counters.counter("minecraft:glass_bottle", "유리병"));
		assertEquals("개", counters.counter("minecraft:bowl", "그릇"));
		// the counter stays where the name does not end in it
		assertEquals("병", counters.counter("minecraft:potion", "물약"));
		assertEquals("그릇", counters.counter("minecraft:mushroom_stew", "버섯 스튜"));
		assertEquals("장", counters.counter("minecraft:map", "지도"));
	}

	// ------------------------------------------------------------ 2. the size of a row

	@Test
	void i2_aRowIsSpokenWithTheSizeThatWasBuilt() {
		Goal four = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel", 4);
		Goal three = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel");
		Goal two = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel", 2);
		assertEquals("자갈 네 개 옆에 횃불을 놓으세요.", clue(four, 2));
		assertEquals("자갈 세 개 옆에 횃불을 놓으세요.", clue(three, 2));
		assertEquals("자갈 두 개 옆에 횃불을 놓으세요.", clue(two, 2));
		assertEquals("자갈 네 개 옆에 횃불을 놓아요.", SentenceBuilder.hint(four, null, List.of(), REGISTRY));
		assertEquals("자갈 네 개 옆에 횃불을 놓았어요.", SentenceBuilder.ceremony(four, null, List.of(), REGISTRY));
	}

	@Test
	void i2_theSizeIsPartOfTheTaskAndKeepsOldKeysAndJson() {
		Goal four = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel", 4);
		Goal three = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel", 0);
		assertEquals(Pattern.ROW.defaultSize(), ((Goal.PlaceOnPattern) three).size());
		GoalType<Goal> type = GoalTypes.of(four);
		assertNotEquals(type.taskSignature(four), type.taskSignature(three));
		assertNotEquals(type.keyPart(four), type.keyPart(three));
		// a goal of the usual size keeps the key it had before the size existed, and its JSON
		assertEquals("minecraft:torch/NEXT_TO/ROW/minecraft:gravel", type.keyPart(three));
		assertEquals(false, type.toJson(three).has("size"));
		assertEquals(4, type.toJson(four).get("size").getAsInt());
		assertEquals(four, type.parse(type.toJson(four)));
		assertEquals(three, type.parse(type.toJson(three)));
	}

	@Test
	void i2_aSizeOutsideTheRangeOfTheShapeIsRefused() {
		Goal five = new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW, "minecraft:gravel", 5);
		assertFalse(problems(five).isEmpty());
		assertEquals(List.of(), problems(new Goal.PlaceOnPattern("minecraft:torch", Relation.NEXT_TO, Pattern.ROW,
				"minecraft:gravel", 4)));
	}

	// ------------------------------------------------------------ 4. a SEQUENCE part teaches its word

	private static final Goal CLIMB_AND_PLANT = new Goal.Sequence(List.of(new Goal.ReachHeight(100, 320),
			new Goal.PlaceBlock("minecraft:spruce_sapling", 1)), true, "AND");

	@Test
	void i4_theClueSaysTheTaughtWordForAPartLikeTheHintDoes() {
		QuestStep step = new QuestStep("s2", List.of("climb", "sapling"), CLIMB_AND_PLANT, List.of(), null, null, "Do it.",
				null, null, null, null);
		SentenceBuilder.Namer clueNamer = ClueWords.namer(REGISTRY, ClueWords.extras(step, CATALOG, id -> false, REGISTRY));
		String clue = SentenceBuilder.normal(CLIMB_AND_PLANT, word("climb"), word("climb"), List.of(), 2, false, clueNamer);
		String hint = SentenceBuilder.hint(CLIMB_AND_PLANT, word("climb"), word("climb"),
				List.of(word("climb"), word("sapling")), List.of(), REGISTRY);
		assertEquals("산 위로 올라가고 묘목을 심으세요.", clue);
		assertEquals("산 위로 올라가고 묘목을 심어요.", hint);
		// without the extra word the exact name shows
		assertEquals("산 위로 올라가고 가문비나무 묘목을 심으세요.",
				SentenceBuilder.normal(CLIMB_AND_PLANT, word("climb"), word("climb"), List.of(), 2, false, REGISTRY));
	}

	@Test
	void i4_aSequenceNeverAddsAWordThatIsNotInTheNameItReplaces() {
		// the word for flowers is no part of 양귀비: the clue keeps to the first noun when it is unknown
		Goal poppy = new Goal.Sequence(List.of(new Goal.ReachHeight(100, 320), new Goal.PlaceBlock("minecraft:poppy", 1)),
				true, "AND");
		QuestStep step = new QuestStep("s2", List.of("climb", "flower"), poppy, List.of(), null, null, "Do it.", null,
				null, null, null);
		assertEquals(List.of(), ClueWords.extras(step, CATALOG, id -> false, REGISTRY).stream().map(w -> w.conceptId)
				.filter("flower"::equals).toList());
	}

	// ------------------------------------------------------------ 5. falling

	private static final Goal FALL_ON_SNOW = new Goal.Fall(6, "minecraft:powder_snow");
	private static final Goal FALL_UNHURT = new Goal.Fall(10, null);

	@Test
	void i5_aJumpOntoASoftBlockIsSpokenAsJumpingDown() {
		assertEquals("높은 곳에서 가루눈 위로 뛰어내려요.", clue(FALL_ON_SNOW, 1));
		assertEquals("높은 곳에서 가루눈 위로 뛰어내리세요.", clue(FALL_ON_SNOW, 2));
		assertEquals("높은 곳에서 가루눈 위로 뛰어내렸어요.", SentenceBuilder.ceremony(FALL_ON_SNOW, null, List.of(), REGISTRY));
		assertEquals("높은 곳에서 건초 더미 위로 뛰어내리세요.", clue(new Goal.Fall(6, "minecraft:hay_block"), 2));
		assertEquals("높은 곳에서 물로 뛰어내리세요.", clue(new Goal.Fall(6, "minecraft:water"), 2));
		assertEquals("높은 곳에서 가루눈 위로 뛰어내려요.", SentenceBuilder.hint(FALL_ON_SNOW, null, List.of(), REGISTRY));
	}

	@Test
	void i5_aFallThatCostsNothingNamesTheHeightFromLevelTwo() {
		assertEquals("높은 곳에서 안 다치고 뛰어내려요.", clue(FALL_UNHURT, 1));
		assertEquals("10블록 높이에서 안 다치고 뛰어내리세요.", clue(FALL_UNHURT, 2));
		assertEquals("10블록 높이에서 다치지 않고 뛰어내리세요.", clue(FALL_UNHURT, 3));
		assertEquals("높은 곳에서 안 다치고 뛰어내려요.", SentenceBuilder.hint(FALL_UNHURT, null, List.of(), REGISTRY));
		assertEquals("높은 곳에서 안 다치고 뛰어내렸어요.", SentenceBuilder.ceremony(FALL_UNHURT, null, List.of(), REGISTRY));
	}

	@Test
	void i5_theVerbConjugates() {
		assertEquals("뛰어내려요", Verb.JUMP_DOWN.polite());
		assertEquals("뛰어내렸어요", Verb.JUMP_DOWN.past());
		assertEquals("뛰어내리세요", io.github.leks2205.hangulquest.quest.grammar.Endings.apply("ending:seyo", "뛰어내리다"));
	}

	/** One jump: ticks in the air with a growing distance, then the ground (the game resets the distance on landing). */
	private static boolean jump(Goal goal, float peak, float healthBefore, float healthAfter, String landedOn,
								boolean inWater) {
		return jump(goal, new GoalEvaluator.Tracker(), new Observation(), new long[] {1000L}, peak, healthBefore,
				healthAfter, landedOn, inWater);
	}

	private static boolean jump(Goal goal, GoalEvaluator.Tracker tracker, Observation obs, long[] clock, float peak,
								float healthBefore, float healthAfter, String landedOn, boolean inWater) {
		long now = clock[0];
		boolean done = false;
		obs.health = healthBefore;
		for (int i = 1; i <= 8; i++) {
			obs.now = now += 50L;
			obs.onGround = false;
			obs.fallDistance = peak * i / 8.0F;
			done |= GoalEvaluator.tick(goal, List.of(), obs, tracker);
		}
		obs.onGround = true;
		obs.fallDistance = 0.0F;
		obs.landedOn = landedOn;
		obs.inWater = inWater;
		for (int i = 0; i < 12; i++) { // the damage reaches the client a little after the landing
			obs.now = now += 50L;
			if (i == 2) {
				obs.health = healthAfter;
			}
			done |= GoalEvaluator.tick(goal, List.of(), obs, tracker);
		}
		clock[0] = now;
		return done;
	}

	@Test
	void i5_landingOnTheSoftBlockAfterEnoughHeightCounts() {
		assertTrue(jump(FALL_ON_SNOW, 12.0F, 20.0F, 20.0F, "minecraft:powder_snow", false));
		assertFalse(jump(FALL_ON_SNOW, 12.0F, 20.0F, 20.0F, "minecraft:grass_block", false), "stone ground is no snow");
		assertFalse(jump(FALL_ON_SNOW, 3.0F, 20.0F, 20.0F, "minecraft:powder_snow", false), "a hop is no jump");
		assertTrue(jump(new Goal.Fall(6, "minecraft:water"), 12.0F, 20.0F, 20.0F, "minecraft:stone", true));
	}

	@Test
	void i5_aFallWithoutALandingBlockMustCostNoHealth() {
		assertTrue(jump(FALL_UNHURT, 14.0F, 20.0F, 20.0F, "minecraft:water", true));
		assertFalse(jump(FALL_UNHURT, 14.0F, 20.0F, 14.0F, "minecraft:stone", false), "ten hearts' worth of bruises");
		assertFalse(jump(FALL_UNHURT, 6.0F, 20.0F, 20.0F, "minecraft:water", true), "too low");
	}

	@Test
	void i5_aFailedJumpDoesNotSpoilTheNextOne() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = new Observation();
		long[] clock = {1000L};
		assertFalse(jump(FALL_UNHURT, tracker, obs, clock, 14.0F, 20.0F, 12.0F, "minecraft:stone", false), "hurt");
		assertTrue(jump(FALL_UNHURT, tracker, obs, clock, 14.0F, 20.0F, 20.0F, "minecraft:water", true),
				"the second, unhurt jump counts");
	}

	@Test
	void i5_theGoalIsReadAndWrittenAsJsonAndChecked() {
		GoalType<Goal> type = GoalTypes.of(FALL_ON_SNOW);
		assertEquals(FALL_ON_SNOW, type.parse(type.toJson(FALL_ON_SNOW)));
		assertEquals(FALL_UNHURT, type.parse(type.toJson(FALL_UNHURT)));
		assertEquals(false, type.toJson(FALL_UNHURT).has("landing"));
		assertEquals(List.of(), problems(FALL_ON_SNOW));
		assertFalse(problems(new Goal.Fall(2, null)).isEmpty(), "a fall of two blocks hurts nobody");
		assertFalse(problems(new Goal.Fall(6, "powder snow")).isEmpty(), "a bad block id");
		assertNotEquals(type.taskSignature(FALL_ON_SNOW), type.taskSignature(FALL_UNHURT));
	}

	@Test
	void i5_theSentenceCarriesItsGrammarPoints() {
		List<String> snow = SentenceBuilder.grammar(FALL_ON_SNOW, null, List.of(), 2, false, REGISTRY);
		assertTrue(snow.containsAll(List.of("L2.seyo", "L2.eseo_activity", "L2.euro_direction")), snow.toString());
		assertTrue(SentenceBuilder.grammar(FALL_UNHURT, null, List.of(), 1, false, REGISTRY).contains("L1.neg_an"));
		assertTrue(SentenceBuilder.grammar(FALL_UNHURT, null, List.of(), 3, false, REGISTRY).contains("L3.ji_anayo"));
	}

	// ------------------------------------------------------------ 7. the gate covers every fence gate

	@Test
	void i7_theWordForGateTeachesEveryFenceGate() {
		for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
				"pale_oak", "bamboo", "crimson", "warped")) {
			Goal open = new Goal.UseBlock("minecraft:" + wood + "_fence_gate");
			assertEquals(List.of(), problems(open, "gate"), wood);
			assertEquals("울타리 문을 여세요.", clue(open, "gate", List.of(), 2), wood);
		}
		assertFalse(problems(new Goal.UseBlock("minecraft:spruce_door"), "gate").isEmpty(), "a door is no fence gate");
	}

	// ------------------------------------------------------------ 11. a held item without a concept

	@Test
	void i11_aHeldItemWithoutAConceptIsSpokenFromTheRegistryNames() {
		assertEquals(null, CATALOG.concept("spyglass").orElse(null) == null ? null : "has a concept",
				"the spyglass is the example of an item without a concept");
		Condition spyglass = new Condition.Holding("minecraft:spyglass");
		Goal wait = new Goal.Wait(10, null);
		assertEquals("망원경을 들고 십 초 동안 기다리세요.", SentenceBuilder.normal(wait, null, List.of(spyglass), 2, false, NAMES));
		assertEquals("망원경을 들고 십 초 동안 기다렸어요.", SentenceBuilder.ceremony(wait, null, List.of(spyglass), NAMES));
		// the same for HOLD_WHILE, whose item is part of the goal
		Goal hold = new Goal.HoldWhile("minecraft:slime_ball", new Goal.Wait(10, null), null, 10);
		assertEquals("슬라임볼을 들고 십 초 동안 기다리세요.", SentenceBuilder.normal(hold, null, List.of(), 2, false, NAMES));
	}

	@Test
	void i11_theValidationTestSpeaksConditionsWithTheRegistryNames() throws Exception {
		String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
				"src/test/java/io/github/leks2205/hangulquest/QuestlineValidationTest.java")),
				java.nio.charset.StandardCharsets.UTF_8);
		String test = source.substring(source.indexOf("void everyConditionAppearsInTheKoreanClue"));
		test = test.substring(0, test.indexOf("@Test"));
		assertTrue(test.contains("KoreanFixtures.namer("), "the namer of the validation test must fall back on names.json");
	}

	// ------------------------------------------------------------ 12. the loader is strict about fields

	private static String trailWith(String goal, String conditions) {
		return "{\"id\":\"probe\",\"steps\":[{\"id\":\"s1\",\"teach\":[\"stone\"],\"goal\":" + goal + ",\"conditions\":"
				+ conditions + "}],\"finale\":{\"step\":{\"id\":\"finale\",\"teach\":[\"stone\"],\"goal\":"
				+ "{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\",\"count\":1}},\"textKo\":\"끝\"}}";
	}

	private static QuestFile read(String json) {
		return QuestFile.read("probe.json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static final String STONE = "{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\",\"count\":1}";

	@Test
	void i12_aWeatherConditionWithTheWrongFieldIsRefusedWithTrailAndStep() {
		QuestFile file = read(trailWith(STONE, "[{\"type\":\"WEATHER\",\"phase\":\"CLEAR\"}]"));
		assertEquals(null, file.trail());
		assertTrue(file.error().contains("probe/s1"), file.error());
		assertTrue(file.error().contains("WEATHER"), file.error());
		assertTrue(file.error().contains("unknown field 'phase'"), file.error());
		assertTrue(file.error().contains("missing field 'weather'"), file.error());
	}

	@Test
	void i12_aMisspeltFieldOfAGoalAndOfAPartOfASequenceIsRefused() {
		QuestFile goal = read(trailWith("{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\",\"cnt\":3}", "[]"));
		assertTrue(goal.error().contains("probe/s1 goal BREAK_BLOCK: unknown field 'cnt'"), goal.error());
		QuestFile part = read(trailWith("{\"type\":\"SEQUENCE\",\"parts\":[" + STONE
				+ ",{\"type\":\"PLACE_BLOCK\",\"blok\":\"minecraft:torch\"}]}", "[]"));
		assertTrue(part.error().contains("parts[1]"), part.error());
		assertTrue(part.error().contains("unknown field 'blok'"), part.error());
		assertTrue(part.error().contains("missing field 'block'"), part.error());
	}

	@Test
	void i12_aFieldMissingInTheFinaleIsFoundToo() {
		String json = "{\"id\":\"probe\",\"steps\":[],\"finale\":{\"step\":{\"id\":\"finale\",\"teach\":[],"
				+ "\"goal\":{\"type\":\"BREAK_BLOCK\",\"count\":1}},\"textKo\":\"끝\"}}";
		assertTrue(read(json).error().contains("probe/finale goal BREAK_BLOCK: missing field 'block'"));
	}

	@Test
	void i12_aRightFileStillLoads() {
		QuestFile file = read(trailWith(STONE, "[{\"type\":\"WEATHER\",\"weather\":\"CLEAR\"},{\"type\":\"TIME\","
				+ "\"phase\":\"NIGHT\"}]"));
		assertEquals(null, file.error());
		assertEquals("probe", file.trail().id());
	}

	// ------------------------------------------------------------ 6. seconds as Sino-Korean numbers

	@Test
	void i6_aLookLastsSamSeconds() {
		Goal block = new Goal.LookAtBlock("minecraft:stone", 3000);
		assertEquals("돌을 삼 초 동안 바라봐요.", clue(block, 1));
		assertEquals("돌을 삼 초 동안 바라보세요.", clue(block, 2));
		assertEquals("돌을 삼 초 동안 바라봐요.", SentenceBuilder.hint(block, null, List.of(), REGISTRY));
		Goal entity = new Goal.LookAtEntity("minecraft:cow", 10_000);
		assertEquals("소를 십 초 동안 바라보세요.", clue(entity, 2));
		assertEquals("소를 일 초 동안 바라봐요.", clue(new Goal.LookAtEntity("minecraft:cow", 800), 1));
		assertEquals("소를 십이 초 동안 바라봐요.", clue(new Goal.LookAtEntity("minecraft:cow", 12_000), 1));
	}

	@Test
	void i6_theGrammarPointOfTheNumeralIsListedFromLevelTwo() {
		Goal block = new Goal.LookAtBlock("minecraft:stone", 3000);
		assertTrue(SentenceBuilder.grammar(block, null, List.of(), 2, false, REGISTRY).contains("L2.num_sino"));
		assertFalse(SentenceBuilder.grammar(block, null, List.of(), 1, false, REGISTRY).contains("L2.num_sino"));
	}

	@Test
	void i6_theLiveCountdownStaysInDigits() {
		assertEquals("10초 남았어요", GoalTypes.of(new Goal.Wait(10, null)).status(new Goal.Wait(10, null),
				new io.github.leks2205.hangulquest.sense.Observation(),
				new io.github.leks2205.hangulquest.quest.GoalEvaluator.Tracker(), CATALOG, null).split(" · ")[0]);
	}

	// ------------------------------------------------------------ 3. the order of the conditions

	private static final Condition NIGHT = new Condition.Time(Condition.Phase.NIGHT);
	private static final Condition RAIN = new Condition.WeatherState(Condition.Weather.RAIN);
	private static final Condition WOLVES = new Condition.NearEntity("minecraft:wolf", 8, 3);
	private static final Condition SHEARS = new Condition.Holding("minecraft:shears");
	private static final Condition BEACH = new Condition.InBiome("#minecraft:is_beach");

	@Test
	void i3_timeComesFirstWhateverTheOrderInTheJson() {
		Goal wait = new Goal.Wait(10, null);
		assertEquals("밤에 늑대 세 마리 근처에서 십 초 동안 기다리세요.", clue(wait, null, List.of(NIGHT, WOLVES), 2));
		assertEquals("밤에 늑대 세 마리 근처에서 십 초 동안 기다리세요.", clue(wait, null, List.of(WOLVES, NIGHT), 2));
	}

	@Test
	void i3_timeThenWeatherThenPlaceThenWhatIsHeld() {
		Goal wait = new Goal.Wait(10, null);
		String expected = "밤에 비가 올 때 해변에서 가위를 들고 십 초 동안 기다리세요.";
		assertEquals(expected, clue(wait, null, List.of(NIGHT, RAIN, BEACH, SHEARS), 2));
		for (List<Condition> shuffled : List.of(List.of(SHEARS, BEACH, RAIN, NIGHT), List.of(BEACH, SHEARS, NIGHT, RAIN),
				List.of(RAIN, SHEARS, NIGHT, BEACH))) {
			assertEquals(expected, clue(wait, null, shuffled, 2), shuffled.toString());
		}
	}

	@Test
	void i3_anAuthoredClueGetsTheMissingPhrasesInTheSameOrder() {
		String authored = SentenceBuilder.authored("늑대를 지켜보세요.", List.of(WOLVES, SHEARS, NIGHT), null, REGISTRY, 2);
		assertEquals("밤에 늑대 세 마리 근처에서 가위를 들고 늑대를 지켜보세요.", authored);
	}
}
