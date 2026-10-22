package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.DecidingForms;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.grammar.Constructions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 11 (the level 4 to 6 grammar): conditions spoken as -(으)면 / -(으)니까 / -는 동안, the sequence
 * connectives -아/어서 and -(으)러, -는 동안 on a held item, -아/어도 on an avoided action, the forms of
 * 합쇼체 and 반말, and the validator rules that keep the constructions where they belong.
 */
class GeneratorWaveElevenTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();

	private static final String TORCH = "minecraft:torch";
	private static final Goal PLACE_TORCH = new Goal.PlaceBlock(TORCH, 1);
	private static final Condition NIGHT = new Condition.Time(Condition.Phase.NIGHT);
	private static final Condition DUSK = new Condition.Time(Condition.Phase.DUSK);
	private static final Condition RAIN = new Condition.WeatherState(Condition.Weather.RAIN);

	private static String clue(Goal goal, List<Condition> conditions, int level) {
		return SentenceBuilder.normal(goal, null, conditions, level, false, REGISTRY);
	}

	private static String clue(Goal goal, int level) {
		return clue(goal, List.of(), level);
	}

	private static List<String> grammar(Goal goal, List<Condition> conditions, int level) {
		return SentenceBuilder.grammar(goal, null, conditions, level, false, REGISTRY);
	}

	private static List<String> problems(Goal goal, List<Condition> conditions, int koreanLevel) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, conditions, null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems, koreanLevel));
		return problems;
	}

	// ------------------------------------------------------------ 1. -(으)면 and 9. -(으)니까 on a condition

	@Test
	void i1_aTimeConditionIsAnIfClauseAtLevelFour() {
		assertEquals("밤에 횃불을 놓으세요.", clue(PLACE_TORCH, List.of(NIGHT), 3));
		assertEquals("밤이 되면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(NIGHT), 4));
		assertEquals("해가 지면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(DUSK), 4));
		assertEquals("낮이 되면 횃불을 놓아야 해요.",
				clue(PLACE_TORCH, List.of(new Condition.Time(Condition.Phase.DAY)), 4));
		assertEquals("해가 뜨면 횃불을 놓아야 해요.",
				clue(PLACE_TORCH, List.of(new Condition.Time(Condition.Phase.DAWN)), 4));
	}

	@Test
	void i1_weatherIsAnIfClauseToo() {
		assertEquals("비가 올 때 횃불을 놓으세요.", clue(PLACE_TORCH, List.of(RAIN), 3));
		assertEquals("비가 오면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(RAIN), 4));
		assertEquals("천둥이 치면 횃불을 놓아야 해요.",
				clue(PLACE_TORCH, List.of(new Condition.WeatherState(Condition.Weather.THUNDER)), 4));
		assertEquals("날씨가 맑으면 횃불을 놓아야 해요.",
				clue(PLACE_TORCH, List.of(new Condition.WeatherState(Condition.Weather.CLEAR)), 4));
		assertEquals("비가 오면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(RAIN), 6));
	}

	@Test
	void i1_onlyOneConditionTakesTheIfClause() {
		assertEquals("밤에 비가 오면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(RAIN, NIGHT), 4));
	}

	@Test
	void i1_otherConditionsKeepTheirPhrase() {
		Condition beach = new Condition.InBiome("#minecraft:is_beach");
		assertEquals("밤이 되면 해변에서 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(beach, NIGHT), 4));
	}

	@Test
	void i1_theHintAndTheFinaleStayPlain() {
		assertEquals("밤에 횃불을 놓아요.", SentenceBuilder.hint(PLACE_TORCH, null, List.of(NIGHT), REGISTRY));
		assertEquals("밤에 횃불을 놓았어요.", SentenceBuilder.ceremony(PLACE_TORCH, null, List.of(NIGHT), REGISTRY));
	}

	@Test
	void i1_theGrammarPointFollowsTheForm() {
		assertTrue(grammar(PLACE_TORCH, List.of(NIGHT), 4).contains("L4.myeon"), grammar(PLACE_TORCH, List.of(NIGHT), 4).toString());
		assertFalse(grammar(PLACE_TORCH, List.of(NIGHT), 3).contains("L4.myeon"));
		assertTrue(grammar(PLACE_TORCH, List.of(RAIN), 5).contains("L4.myeon"));
	}

	@Test
	void i1_theConditionDecidesWhenToAct() {
		QuestStep step = new QuestStep("s1", List.of(), PLACE_TORCH, List.of(NIGHT), null, null, "Do it.", null, null,
				null, null);
		assertEquals(List.of(), DecidingForms.of(step, 3));
		assertEquals(List.of("L4.myeon"), DecidingForms.of(step, 4));
		assertEquals(List.of("L4.myeon"), DecidingForms.of(step, 6), "the same form at the upper levels");
	}

	@Test
	void i9_aReasonIsNeverGenerated() {
		// -(으)니까 asserts a cause; a condition and an action do not give one, so the clue stays an if clause
		assertEquals("밤이 되면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(NIGHT), 5));
		assertEquals("해가 지면 횃불을 놓아야 해요.", clue(PLACE_TORCH, List.of(DUSK), 6));
		assertFalse(grammar(PLACE_TORCH, List.of(NIGHT), 6).contains("L5.eunikka"));
	}

	// ------------------------------------------------------------ 7. -는 동안 on a wait and a held item

	@Test
	void i7_waitingWhileSomethingHolds() {
		Goal wait = new Goal.Wait(10, null);
		assertEquals("해가 지면 십 초 동안 기다려야 해요.", clue(wait, List.of(DUSK), 4));
		assertEquals("해가 지는 동안 기다려야 해요.", clue(wait, List.of(DUSK), 5));
		assertEquals("비가 오는 동안 기다려야 해요.", clue(wait, List.of(RAIN), 6));
		assertEquals("십 초 동안 기다려야 해요.", clue(wait, 5), "no condition, no clause");
		assertTrue(grammar(wait, List.of(DUSK), 5).contains("L5.neun_dongan"));
		QuestStep step = new QuestStep("s1", List.of(), wait, List.of(DUSK), null, null, "Do it.", null, null, null, null);
		assertEquals(List.of("L5.neun_dongan"), DecidingForms.of(step, 5));
	}

	@Test
	void i7_holdingWhileDoingIt() {
		Goal hold = new Goal.HoldWhile(TORCH, null, NIGHT, 10);
		assertEquals("횃불을 들고 밤에 걸어야 해요.", clue(hold, 4));
		assertEquals("횃불을 들고 밤에 걸어야 해요.", clue(hold, 5), "walking at night stays 들고");
		Goal mine = new Goal.HoldWhile(TORCH, new Goal.BreakBlock("minecraft:stone", 1, null), null, 10);
		assertEquals("횃불을 들고 있는 동안 돌을 캐야 해요.", clue(mine, 6));
		assertEquals("횃불을 들고 돌을 캐요.", SentenceBuilder.hint(mine, null, List.of(), REGISTRY));
		assertEquals("횃불을 들고 돌을 캤어요.", SentenceBuilder.ceremony(mine, null, List.of(), REGISTRY));
		assertTrue(grammar(mine, List.of(), 5).contains("L5.neun_dongan"));
		assertFalse(grammar(mine, List.of(), 4).contains("L5.neun_dongan"));
		QuestStep step = new QuestStep("s1", List.of(), mine, List.of(), null, null, "Do it.", null, null, null, null);
		assertEquals(List.of(), DecidingForms.of(step, 4));
		assertEquals(List.of("L5.neun_dongan"), DecidingForms.of(step, 5));
	}

	// ------------------------------------------------------------ 8. -아/어도 on an avoided action

	@Test
	void i8_avoidEvenWhenItIsNight() {
		Goal torch = new Goal.Avoid(PLACE_TORCH, 60, NIGHT);
		assertEquals("밤에 횃불을 놓지 마세요.", clue(torch, 3));
		assertEquals("밤이 되면 횃불을 놓지 마세요.", clue(torch, 4));
		assertEquals("밤이 되어도 횃불을 놓지 마세요.", clue(torch, 5));
		assertEquals("밤이 되어도 횃불을 놓지 마세요.", clue(torch, 6));
		Goal rain = new Goal.Avoid(new Goal.BreakBlock("minecraft:stone", 1, null), 60, RAIN);
		assertEquals("비가 와도 돌을 캐지 마세요.", clue(rain, 5));
		assertEquals("밤에 횃불을 놓지 않았어요.", SentenceBuilder.ceremony(torch, null, List.of(), REGISTRY));
		assertEquals("밤에 횃불을 놓지 마세요.", SentenceBuilder.hint(torch, null, List.of(), REGISTRY));
		assertTrue(grammar(torch, List.of(), 5).contains("L5.eodo"), grammar(torch, List.of(), 5).toString());
		assertFalse(grammar(torch, List.of(), 4).contains("L5.eodo"));
		assertTrue(grammar(torch, List.of(), 4).contains("L4.myeon"));
		QuestStep step = new QuestStep("s1", List.of(), torch, List.of(), null, null, "Do it.", null, null, null, null);
		assertEquals(List.of("L3.jima_seyo"), DecidingForms.of(step, 5),
				"the 'not' still decides; -아/어도 only adds the concession");
	}

	// ------------------------------------------------------------ 2. -아/어서 and 3. -(으)러 가다 on a sequence

	private static Goal.Sequence viaEoseo() {
		return new Goal.Sequence(List.of(new Goal.BreakBlock("minecraft:stone", 1, null), new Goal.Craft("minecraft:furnace", 1, null, List.of())),
				true, "EOSEO");
	}

	private static Goal.Sequence viaEureo() {
		return new Goal.Sequence(List.of(new Goal.ReachBiome("#minecraft:is_forest", 1000), new Goal.BreakBlock("minecraft:oak_log", 1, null)),
				true, "EUREO");
	}

	private static final Condition IN_FOREST = new Condition.InBiome("#minecraft:is_forest");

	@Test
	void i2_theFirstThingHappensFirstAndTheSecondUsesIt() {
		assertEquals("돌을 캐서 화로를 만들어야 해요.", clue(viaEoseo(), 4));
		assertEquals("돌을 캐서 화로를 만들어요.", clue(viaEoseo(), 1), "a hint is level one and still has the connective");
		assertEquals("돌을 캐서 화로를 만들었어요.", SentenceBuilder.ceremony(viaEoseo(), null, List.of(), REGISTRY));
		assertTrue(grammar(viaEoseo(), List.of(), 4).contains("L4.eoseo"), grammar(viaEoseo(), List.of(), 4).toString());
	}

	@Test
	void i2_theConnectiveDecidesTheOrder() {
		assertEquals(List.of("L4.eoseo"), GoalTypes.of(viaEoseo()).decidingForms(viaEoseo()));
	}

	@Test
	void i2_needsLevelFourAndTwoEvents() {
		assertTrue(problems(viaEoseo(), List.of(), 3).stream().anyMatch(p -> p.contains("koreanLevel 4")), problems(viaEoseo(), List.of(), 3).toString());
		assertTrue(problems(viaEoseo(), List.of(), 4).isEmpty(), problems(viaEoseo(), List.of(), 4).toString());
		Goal.Sequence walking = new Goal.Sequence(List.of(new Goal.ReachBiome("#minecraft:is_forest", 1000),
				new Goal.BreakBlock("minecraft:stone", 1, null)), true, "EOSEO");
		assertFalse(problems(walking, List.of(), 4).isEmpty(), "going somewhere is no -아/어서 step");
	}

	@Test
	void i3_goingThereToDoIt() {
		assertEquals("참나무 원목을 베러 숲으로 가야 해요.", clue(viaEureo(), List.of(IN_FOREST), 4));
		assertEquals("참나무 원목을 베러 숲으로 가요.", SentenceBuilder.hint(viaEureo(), null, List.of(IN_FOREST), REGISTRY));
		assertTrue(grammar(viaEureo(), List.of(IN_FOREST), 4).contains("L4.eureo_gayo"));
		assertEquals(List.of("L4.eureo_gayo"), GoalTypes.of(viaEureo()).decidingForms(viaEureo()));
	}

	@Test
	void i3_theActHappensThereSoTheBiomeIsRequired() {
		assertTrue(problems(viaEureo(), List.of(IN_FOREST), 4).isEmpty(), problems(viaEureo(), List.of(IN_FOREST), 4).toString());
		assertFalse(problems(viaEureo(), List.of(), 4).isEmpty(), "without IN_BIOME the act could happen anywhere");
		assertFalse(problems(viaEureo(), List.of(new Condition.InBiome("minecraft:desert")), 4).isEmpty());
		assertTrue(problems(viaEureo(), List.of(IN_FOREST), 3).stream().anyMatch(p -> p.contains("koreanLevel 4")));
		Goal.Sequence backwards = new Goal.Sequence(List.of(new Goal.BreakBlock("minecraft:oak_log", 1, null),
				new Goal.ReachBiome("#minecraft:is_forest", 1000)), true, "EUREO");
		assertFalse(problems(backwards, List.of(IN_FOREST), 4).isEmpty(), "the trip comes first");
	}

	// ------------------------------------------------------------ 6. a colour as an adjective in front of the noun

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	@Test
	void i6_aColourIsAnAdjectiveFromLevelFive() {
		Goal red = new Goal.PlaceBlock("minecraft:red_bed", 1);
		assertEquals("빨간색 침대를 놓아야 해요.", clue(red, 4));
		assertEquals("빨간 침대를 놓아야 해요.", clue(red, 5));
		assertEquals("빨간 침대를 놓아야 해요.", clue(red, 6));
		assertEquals("파란 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:blue_wool", 1), 5));
		assertEquals("노란 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:yellow_wool", 1), 5));
		assertEquals("하얀 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:white_wool", 1), 5));
		assertEquals("검은 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:black_wool", 1), 5));
	}

	@Test
	void i6_aColourWithoutAnAdjectiveStaysANoun() {
		assertEquals("보라색 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:purple_wool", 1), 5));
		assertEquals("주황색 양털을 놓아야 해요.", clue(new Goal.PlaceBlock("minecraft:orange_wool", 1), 5));
	}

	@Test
	void i6_theTaughtColourWordIsKeptAndOnlyTheClueChanges() {
		Goal red = new Goal.PlaceBlock("minecraft:red_bed", 1);
		assertEquals("빨간색 침대를 놓아야 해요.", SentenceBuilder.normal(red, word("red"), List.of(), 5, false, REGISTRY),
				"a step that teaches 빨간색 says 빨간색");
		assertEquals("빨간색 침대를 놓아요.", SentenceBuilder.hint(red, null, List.of(), REGISTRY));
		assertEquals("빨간색 침대를 놓았어요.", SentenceBuilder.ceremony(red, null, List.of(), REGISTRY));
	}

	@Test
	void i6_theAdjectiveIsAGrammarPointOfTheSentence() {
		Goal red = new Goal.PlaceBlock("minecraft:red_bed", 1);
		assertTrue(grammar(red, List.of(), 5).contains("L5.adn_adj_eun"), grammar(red, List.of(), 5).toString());
		assertFalse(grammar(red, List.of(), 4).contains("L5.adn_adj_eun"));
		assertFalse(grammar(new Goal.PlaceBlock("minecraft:purple_wool", 1), List.of(), 5).contains("L5.adn_adj_eun"));
		assertFalse(grammar(PLACE_TORCH, List.of(), 5).contains("L5.adn_adj_eun"));
	}

	// ------------------------------------------------------------ 11. -(으)ㄹ수록 as a flavour fact

	@Test
	void i11_aHighClimbCarriesTheRisingColdFact() {
		Goal mountain = new Goal.ReachHeight(100, 320);
		assertEquals(java.util.Optional.of("높이 올라갈수록 추워져요."), SentenceBuilder.flavour(mountain, 6));
		assertEquals(List.of("L6.eulsurok"), SentenceBuilder.flavourGrammar(mountain, 6));
		assertEquals(java.util.Optional.of("높이 올라갈수록 추워져요."),
				SentenceBuilder.flavour(new Goal.ReachHeight(80, 320), 6), "the cold starts at height 80");
	}

	@Test
	void i11_onlyWhereTheFactIsTrueAndTheLevelIsSix() {
		Goal mountain = new Goal.ReachHeight(100, 320);
		assertEquals(java.util.Optional.empty(), SentenceBuilder.flavour(mountain, 5));
		assertEquals(java.util.Optional.empty(), SentenceBuilder.flavour(new Goal.ReachHeight(70, 320), 6), "a hill is not cold");
		assertEquals(java.util.Optional.empty(), SentenceBuilder.flavour(new Goal.ReachHeight(-64, 0), 6), "a descent is no climb");
		assertEquals(java.util.Optional.empty(), SentenceBuilder.flavour(PLACE_TORCH, 6));
		assertEquals(List.of(), SentenceBuilder.flavourGrammar(PLACE_TORCH, 6));
	}

	@Test
	void i11_theFlavourIsAFinaleOrAHintNeverTheInstruction() {
		String fact = SentenceBuilder.flavour(new Goal.ReachHeight(100, 320), 6).orElseThrow();
		assertTrue(Constructions.problems("t", fact, Constructions.Role.FINALE, 6).isEmpty());
		assertTrue(Constructions.problems("t", fact, Constructions.Role.HINT, 6).isEmpty());
		assertFalse(Constructions.problems("t", fact, Constructions.Role.CLUE, 6).isEmpty());
	}
}
