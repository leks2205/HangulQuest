package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Counters;
import io.github.leks2205.hangulquest.quest.Goal;
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
