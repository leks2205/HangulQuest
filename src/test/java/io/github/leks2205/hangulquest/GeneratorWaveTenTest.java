package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.Directions;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.ShotFact;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 10 (from the weaknesses BATCH-016 to BATCH-021 reported): shooting and throwing, compass
 * directions, AVOID on creatures, Sino-Korean heights and distances, native counts up to 99, and the sentence
 * fixes of the sequence, the sea swim, the hint of a climb and the armour words.
 */
class GeneratorWaveTenTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();
	private static final SentenceBuilder.Namer NAMES = KoreanFixtures.namer(CATALOG);

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, List<Condition> conditions, int level) {
		return SentenceBuilder.normal(goal, null, conditions, level, false, REGISTRY);
	}

	private static String clue(Goal goal, int level) {
		return clue(goal, List.of(), level);
	}

	private static String hint(Goal goal, List<Condition> conditions) {
		return SentenceBuilder.hint(goal, null, conditions, REGISTRY);
	}

	private static String finale(Goal goal, List<Condition> conditions) {
		return SentenceBuilder.ceremony(goal, null, conditions, REGISTRY);
	}

	private static List<String> problems(Goal goal) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	// ------------------------------------------------------------ 1. shoot and throw

	private static final Goal BOW_TARGET = new Goal.Shoot("minecraft:bow", null, "minecraft:target", 1, 0, 0);
	private static final Goal CROSSBOW_ZOMBIE = new Goal.Shoot("minecraft:crossbow", "minecraft:zombie", null, 1, 0, 0);

	@Test
	void i1_aShotAtATargetHits() {
		assertEquals("활로 과녁을 맞혀요.", clue(BOW_TARGET, 1));
		assertEquals("활로 과녁을 맞히세요.", clue(BOW_TARGET, 2));
		assertEquals("활로 과녁을 맞혔어요.", finale(BOW_TARGET, List.of()));
		assertEquals("쇠뇌로 좀비를 맞히세요.", clue(CROSSBOW_ZOMBIE, 2));
		assertEquals("쇠뇌로 좀비를 맞혔어요.", finale(CROSSBOW_ZOMBIE, List.of()));
		assertEquals("눈덩이로 좀비를 맞히세요.", clue(new Goal.Shoot("minecraft:snowball", "minecraft:zombie", null, 1, 0, 0), 2));
		assertEquals("삼지창으로 좀비를 맞히세요.", clue(new Goal.Shoot("minecraft:trident", "minecraft:zombie", null, 1, 0, 0), 2));
		assertEquals("달걀로 과녁을 맞히세요.", clue(new Goal.Shoot("minecraft:egg", null, "minecraft:target", 1, 0, 0), 2));
	}

	@Test
	void i1_withoutATargetItIsShotOrThrown() {
		assertEquals("삼지창을 던지세요.", clue(new Goal.Shoot("minecraft:trident", null, null, 1, 0, 0), 2));
		assertEquals("삼지창을 던져요.", clue(new Goal.Shoot("minecraft:trident", null, null, 1, 0, 0), 1));
		assertEquals("눈덩이를 세 개 던지세요.", clue(new Goal.Shoot("minecraft:snowball", null, null, 3, 0, 0), 2));
		assertEquals("활을 쏴요.", clue(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0), 1));
		assertEquals("활을 쏘세요.", clue(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0), 2));
		assertEquals("활을 쏘았어요.".replace("쏘았", "쐈"), finale(new Goal.Shoot("minecraft:bow", null, null, 1, 0, 0), List.of()));
	}

	@Test
	void i1_aCountADistanceAndAPierceAreSpoken() {
		assertEquals("좀비를 세 마리 쇠뇌로 맞히세요.".replace("좀비를 세 마리 쇠뇌로", "쇠뇌로 좀비를 세 마리"),
				clue(new Goal.Shoot("minecraft:crossbow", "minecraft:zombie", null, 3, 0, 0), 2));
		assertEquals("활로 과녁을 세 번 맞히세요.", clue(new Goal.Shoot("minecraft:bow", null, "minecraft:target", 3, 0, 0), 2));
		assertEquals("화살 하나로 좀비를 두 마리 맞히세요.", clue(new Goal.Shoot("minecraft:crossbow", "minecraft:zombie", null, 1, 0, 2), 2));
		Goal far = new Goal.Shoot("minecraft:bow", null, "minecraft:target", 1, 20, 0);
		assertEquals("이십 블록 이상 떨어진 곳에서 활로 과녁을 맞히세요.", clue(far, 2));
		assertEquals("멀리서 활로 과녁을 맞혀요.", clue(far, 1));
		assertEquals("멀리서 활로 과녁을 맞혀요.", hint(far, List.of()), "the hint names no number");
		assertEquals("멀리서 활로 과녁을 맞혔어요.", finale(far, List.of()), "a finale keeps to the plain wording");
	}

	@Test
	void i1_theVerbsConjugate() {
		assertEquals("맞혀요", Verb.HIT.polite());
		assertEquals("맞혔어요", Verb.HIT.past());
		assertEquals("맞히세요", io.github.leks2205.hangulquest.quest.grammar.Endings.apply("ending:seyo", "맞히다"));
		assertEquals("쏴요", Verb.SHOOT.polite());
		assertEquals("쐈어요", Verb.SHOOT.past());
		assertEquals("쏘세요", io.github.leks2205.hangulquest.quest.grammar.Endings.apply("ending:seyo", "쏘다"));
	}

	private static Observation shots(ShotFact... facts) {
		Observation obs = new Observation();
		for (ShotFact fact : facts) {
			obs.addShot(fact);
		}
		return obs;
	}

	private static ShotFact hit(String weapon, String entity, double distance) {
		return new ShotFact(weapon, List.of(new ShotFact.Hit(entity, UUID.randomUUID().toString(), distance)), null, 0.0);
	}

	private static boolean met(Goal goal, GoalEvaluator.Tracker tracker, ShotFact... facts) {
		return GoalEvaluator.tick(goal, List.of(), shots(facts), tracker);
	}

	@Test
	void i1_aCreatureStruckWithTheRightWeaponCounts() {
		assertTrue(met(CROSSBOW_ZOMBIE, new GoalEvaluator.Tracker(), hit("minecraft:crossbow", "minecraft:zombie", 5.0)));
		assertFalse(met(CROSSBOW_ZOMBIE, new GoalEvaluator.Tracker(), hit("minecraft:bow", "minecraft:zombie", 5.0)),
				"another weapon");
		assertFalse(met(CROSSBOW_ZOMBIE, new GoalEvaluator.Tracker(), hit("minecraft:crossbow", "minecraft:skeleton", 5.0)),
				"another creature");
		assertFalse(met(CROSSBOW_ZOMBIE, new GoalEvaluator.Tracker(),
				new ShotFact("minecraft:crossbow", List.of(), null, 0.0)), "a miss");
	}

	@Test
	void i1_theSameCreatureTwiceCountsOnce() {
		Goal three = new Goal.Shoot("minecraft:bow", "minecraft:zombie", null, 3, 0, 0);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		ShotFact same = hit("minecraft:bow", "minecraft:zombie", 5.0);
		assertFalse(met(three, tracker, same));
		assertFalse(met(three, tracker, same, same));
		assertEquals(1, tracker.count);
		assertFalse(met(three, tracker, hit("minecraft:bow", "minecraft:zombie", 5.0)));
		assertTrue(met(three, tracker, hit("minecraft:bow", "minecraft:zombie", 5.0)));
	}

	@Test
	void i1_theDistanceIsAMinimum() {
		Goal far = new Goal.Shoot("minecraft:bow", "minecraft:zombie", null, 1, 20, 0);
		assertFalse(met(far, new GoalEvaluator.Tracker(), hit("minecraft:bow", "minecraft:zombie", 19.0)));
		assertTrue(met(far, new GoalEvaluator.Tracker(), hit("minecraft:bow", "minecraft:zombie", 21.0)));
		Goal farBlock = new Goal.Shoot("minecraft:bow", null, "minecraft:target", 1, 20, 0);
		assertFalse(met(farBlock, new GoalEvaluator.Tracker(), new ShotFact("minecraft:bow", List.of(), "minecraft:target", 5.0)));
		assertTrue(met(farBlock, new GoalEvaluator.Tracker(), new ShotFact("minecraft:bow", List.of(), "minecraft:target", 25.0)));
	}

	@Test
	void i1_oneShotMustStrikeAsManyAsThePierceSays() {
		Goal two = new Goal.Shoot("minecraft:crossbow", "minecraft:zombie", null, 1, 0, 2);
		ShotFact one = hit("minecraft:crossbow", "minecraft:zombie", 5.0);
		assertFalse(met(two, new GoalEvaluator.Tracker(), one), "one creature");
		// two separate shots do not pierce
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(met(two, tracker, one, hit("minecraft:crossbow", "minecraft:zombie", 5.0)));
		ShotFact both = new ShotFact("minecraft:crossbow", List.of(
				new ShotFact.Hit("minecraft:zombie", "a", 4.0), new ShotFact.Hit("minecraft:zombie", "b", 6.0)), null, 0.0);
		assertTrue(met(two, new GoalEvaluator.Tracker(), both));
	}

	@Test
	void i1_aTargetBlockIsHitWhenItShowsPower() {
		assertTrue(met(BOW_TARGET, new GoalEvaluator.Tracker(), new ShotFact("minecraft:bow", List.of(), "minecraft:target", 8.0)));
		assertFalse(met(BOW_TARGET, new GoalEvaluator.Tracker(), new ShotFact("minecraft:bow", List.of(), null, 0.0)));
	}

	@Test
	void i1_withoutATargetTheShotItselfCounts() {
		Goal throwTrident = new Goal.Shoot("minecraft:trident", null, null, 2, 0, 0);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(met(throwTrident, tracker, new ShotFact("minecraft:trident", List.of(), null, 0.0)));
		assertTrue(met(throwTrident, tracker, new ShotFact("minecraft:trident", List.of(), null, 0.0)));
	}

	@Test
	void i1_theGoalIsReadWrittenAndChecked() {
		Goal far = new Goal.Shoot("minecraft:crossbow", "minecraft:zombie", null, 2, 15, 2);
		JsonObject json = GoalTypes.toJson(far);
		assertEquals(far, GoalTypes.parse(json));
		assertEquals(List.of(), problems(far));
		assertEquals(List.of(), problems(BOW_TARGET));
		assertFalse(problems(new Goal.Shoot("minecraft:stick", null, null, 1, 0, 0)).isEmpty(), "not a weapon");
		assertFalse(problems(new Goal.Shoot("minecraft:bow", "minecraft:zombie", "minecraft:target", 1, 0, 0)).isEmpty(),
				"one target");
		assertFalse(problems(new Goal.Shoot("minecraft:bow", null, "minecraft:stone", 1, 0, 0)).isEmpty(),
				"only the target block shows a hit");
		assertFalse(problems(new Goal.Shoot("minecraft:snowball", "minecraft:zombie", null, 1, 0, 2)).isEmpty(),
				"only an arrow pierces");
	}

	@Test
	void i1_theTaskIsToldApartByWeaponTargetAndDistance() {
		GoalType<Goal> type = GoalTypes.of(BOW_TARGET);
		assertNotEquals(type.taskSignature(BOW_TARGET),
				type.taskSignature(new Goal.Shoot("minecraft:crossbow", null, "minecraft:target", 1, 0, 0)));
		assertNotEquals(type.taskSignature(BOW_TARGET),
				type.taskSignature(new Goal.Shoot("minecraft:bow", null, "minecraft:target", 1, 20, 0)));
	}

	// ------------------------------------------------------------ 2. directions

	private static final Goal NORTH_20 = new Goal.Walk("NORTH", 20);

	@Test
	void i2_walkingInADirectionIsSpoken() {
		assertEquals("북쪽으로 가요.", clue(NORTH_20, 1));
		assertEquals("북쪽으로 이십 블록 가세요.", clue(NORTH_20, 2));
		assertEquals("북쪽으로 가요.", hint(NORTH_20, List.of()));
		assertEquals("북쪽으로 이십 블록 갔어요.", finale(NORTH_20, List.of()));
		assertEquals("남쪽으로 십 블록 가세요.", clue(new Goal.Walk("SOUTH", 10), 2));
		assertEquals("동쪽으로 삼십 블록 가세요.", clue(new Goal.Walk("EAST", 30), 2));
		assertEquals("서쪽으로 오십 블록 가세요.", clue(new Goal.Walk("WEST", 50), 2));
	}

	private static boolean walk(Goal goal, GoalEvaluator.Tracker tracker, double x, double z) {
		Observation obs = new Observation();
		obs.position = new Vec3(x, 64.0, z);
		return GoalEvaluator.tick(goal, List.of(), obs, tracker);
	}

	@Test
	void i2_theDistanceIsMeasuredAlongTheDirection() {
		// north is -Z
		GoalEvaluator.Tracker north = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int z = 0; z >= -25 && !done; z--) {
			done = walk(NORTH_20, north, 100.0, z);
		}
		assertTrue(done);
		GoalEvaluator.Tracker wrong = new GoalEvaluator.Tracker(); // south, east and west do not count
		for (int i = 0; i <= 40; i++) {
			assertFalse(walk(NORTH_20, wrong, i, i), "south and east");
		}
		GoalEvaluator.Tracker east = new GoalEvaluator.Tracker();
		boolean got = false;
		for (int x = 0; x <= 25; x++) {
			got |= walk(new Goal.Walk("EAST", 20), east, x, 0.0);
		}
		assertTrue(got);
		GoalEvaluator.Tracker west = new GoalEvaluator.Tracker();
		got = false;
		for (int x = 0; x >= -25; x--) {
			got |= walk(new Goal.Walk("WEST", 20), west, x, 0.0);
		}
		assertTrue(got);
		GoalEvaluator.Tracker south = new GoalEvaluator.Tracker();
		got = false;
		for (int z = 0; z <= 25; z++) {
			got |= walk(new Goal.Walk("SOUTH", 20), south, 0.0, z);
		}
		assertTrue(got);
	}

	@Test
	void i2_aStepBackAndATeleportDoNotCount() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		walk(NORTH_20, tracker, 0, 0);
		for (int z = -1; z >= -10; z--) {
			walk(NORTH_20, tracker, 0, z);
		}
		for (int z = -9; z <= 0; z++) { // back again
			walk(NORTH_20, tracker, 0, z);
		}
		assertEquals(0, tracker.count);
		assertFalse(walk(NORTH_20, tracker, 0, -500), "a teleport is no walk");
	}

	@Test
	void i2_theWalkIsReadWrittenAndChecked() {
		assertEquals(NORTH_20, GoalTypes.parse(GoalTypes.toJson(NORTH_20)));
		assertEquals(List.of(), problems(NORTH_20));
		assertFalse(problems(new Goal.Walk("UP", 20)).isEmpty());
		assertNotEquals(GoalTypes.of(NORTH_20).taskSignature(NORTH_20),
				GoalTypes.of(NORTH_20).taskSignature(new Goal.Walk("SOUTH", 20)));
	}

	@Test
	void i2_aFacingConditionIsReadFromTheYaw() {
		Condition north = new Condition.Facing("NORTH");
		Observation obs = new Observation();
		obs.yaw = 180.0F;
		assertTrue(ConditionTypes.of(north).met(north, obs));
		obs.yaw = -170.0F;
		assertTrue(ConditionTypes.of(north).met(north, obs), "wraps around");
		obs.yaw = 150.0F;
		assertTrue(ConditionTypes.of(north).met(north, obs));
		obs.yaw = 130.0F;
		assertFalse(ConditionTypes.of(north).met(north, obs));
		obs.yaw = 0.0F;
		assertFalse(ConditionTypes.of(north).met(north, obs), "south");
		for (String way : Directions.ALL) {
			Condition facing = new Condition.Facing(way);
			obs.yaw = Directions.yaw(way);
			assertTrue(ConditionTypes.of(facing).met(facing, obs), way);
			assertEquals(facing, ConditionTypes.parse(ConditionTypes.toJson(facing)));
		}
	}

	@Test
	void i2_theFacingIsSpokenInFrontOfTheAction() {
		Goal torch = new Goal.PlaceBlock("minecraft:torch", 1);
		List<Condition> north = List.of(new Condition.Facing("NORTH"));
		assertEquals("북쪽을 보고 횃불을 놓아요.", clue(torch, north, 1));
		assertEquals("북쪽을 보고 횃불을 놓으세요.", clue(torch, north, 2));
		assertEquals("북쪽을 보고 횃불을 놓았어요.", finale(torch, north));
	}

	@Test
	void i2_theDirectionWordsAreNamed() {
		assertEquals("북쪽", Directions.ko("NORTH"));
		assertEquals("남쪽", Directions.ko("SOUTH"));
		assertEquals("동쪽", Directions.ko("EAST"));
		assertEquals("서쪽", Directions.ko("WEST"));
	}

	// ------------------------------------------------------------ 3. AVOID on creatures

	@Test
	void i3_anAvoidCanForbidAnActionOnACreature() {
		Goal hit = new Goal.Avoid(new Goal.DefeatEntity("minecraft:villager", 1), 30, null);
		assertEquals("주민을 물리치지 마세요.", clue(hit, 3));
		assertEquals("주민을 물리치지 않았어요.", finale(hit, List.of()));
		assertEquals(List.of(), problems(hit));
		Goal touch = new Goal.Avoid(new Goal.InteractEntity("minecraft:villager"), 30, null);
		assertEquals(List.of(), problems(touch));
		Goal look = new Goal.Avoid(new Goal.LookAtEntity("minecraft:villager", 1000L), 30, null);
		assertEquals(List.of(), problems(look));
	}

	@Test
	void i3_theWindowOnlyBreaksWhenTheCreatureIsHit() {
		Goal avoid = new Goal.Avoid(new Goal.DefeatEntity("minecraft:villager", 1), 2, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = new Observation();
		boolean done = false;
		for (int i = 0; i < 100 && !done; i++) {
			done = GoalEvaluator.tick(avoid, List.of(), obs, tracker);
		}
		assertTrue(done);
		GoalEvaluator.Tracker slip = new GoalEvaluator.Tracker();
		Observation killed = new Observation();
		killed.defeatedEntities.add("minecraft:villager");
		GoalEvaluator.tick(avoid, List.of(), killed, slip);
		assertEquals(1, slip.count, "the window started over");
	}

	@Test
	void i3_theSameAvoidUnderAnotherConditionIsAnotherTask() {
		Goal action = new Goal.DefeatEntity("minecraft:villager", 1);
		Goal plain = new Goal.Avoid(action, 30, null);
		Goal night = new Goal.Avoid(action, 30, new Condition.Time(Condition.Phase.NIGHT));
		Goal rain = new Goal.Avoid(action, 30, new Condition.WeatherState(Condition.Weather.RAIN));
		GoalType<Goal> type = GoalTypes.of(plain);
		assertNotEquals(type.taskSignature(plain), type.taskSignature(night));
		assertNotEquals(type.taskSignature(night), type.taskSignature(rain));
		assertEquals(type.taskSignature(night), type.taskSignature(
				new Goal.Avoid(action, 60, new Condition.Time(Condition.Phase.NIGHT))), "the window is not the task");
	}

	// ------------------------------------------------------------ 7. Sino-Korean heights and distances

	@Test
	void i7_heightsAndDistancesAreSpelledInSinoKorean() {
		assertEquals("높이 백 블록 위로 올라가세요.", clue(new Goal.ReachHeight(100, 320), 2));
		assertEquals("높이 구십 블록 위로 올라가세요.", clue(new Goal.ReachHeight(90, 320), 2));
		assertEquals("높이 사십 블록 이하로 내려가세요.", clue(new Goal.ReachHeight(-64, 40), 2));
		assertEquals("물에서 이십 블록 수영하세요.", clue(new Goal.Swim(20), 2));
		assertEquals("십 블록 높이에서 안 다치고 뛰어내리세요.", clue(new Goal.Fall(10, null), 2));
		assertEquals("높이 구십 블록 위에서 자세요.".replace("자세요", "물리치세요"),
				clue(new Goal.DefeatEntity("minecraft:zombie", 1), List.of(new Condition.YRange(90, 320)), 2)
						.replace("좀비를 한 마리 ", "").replace("좀비를 ", ""));
	}

	@Test
	void i7_largeNumbersKeepToHundredsOrFallBackToDigits() {
		assertEquals("백 블록", KoGrammar.blocks(100));
		assertEquals("이백 블록", KoGrammar.blocks(200));
		assertEquals("구백 블록", KoGrammar.blocks(900));
		assertEquals("320블록", KoGrammar.blocks(320));
		assertEquals("1000블록", KoGrammar.blocks(1000));
		assertEquals("일 블록", KoGrammar.blocks(1));
	}

	// ------------------------------------------------------------ 8. the hint of a climb

	@Test
	void i8_theHintSpeaksThePlaceFromTheConditions() {
		Goal climb = new Goal.ReachHeight(120, 320);
		assertEquals("산 위로 올라가요.", hint(climb, List.of()));
		assertEquals("산에서 높은 곳으로 올라가요.", hint(climb, List.of(new Condition.InBiome("#minecraft:is_mountain"))),
				"the condition already says 산");
		assertEquals("네더에서 높은 곳으로 올라가요.", hint(climb, List.of(new Condition.InDimension("minecraft:the_nether"))),
				"the Nether has no mountain");
		assertEquals("오버월드에서 산 위로 올라가요.", hint(climb, List.of(new Condition.InDimension("minecraft:overworld"))));
		assertEquals("산에서 높은 곳으로 올라가요.", clue(climb, List.of(new Condition.InBiome("#minecraft:is_mountain")), 1));
		assertEquals("네더에서 높은 곳으로 올라갔어요.", finale(climb, List.of(new Condition.InDimension("minecraft:the_nether"))));
	}

	// ------------------------------------------------------------ 9. the sea

	@Test
	void i9_aSwimInTheSeaIsSwumInTheSea() {
		List<Condition> sea = List.of(new Condition.InBiome("#minecraft:is_ocean"));
		assertEquals("바다에서 이십 블록 수영하세요.", clue(new Goal.Swim(20), sea, 2));
		assertEquals("바다에서 이십 블록 수영하세요.", clue(new Goal.Swim(20), sea, 3));
		assertEquals("바다에서 수영해요.", clue(new Goal.Swim(20), sea, 1));
		assertEquals("바다에서 수영해요.", hint(new Goal.Swim(20), sea));
		assertEquals("바다에서 수영했어요.", finale(new Goal.Swim(20), sea));
		assertEquals("강에서 이십 블록 수영하세요.", clue(new Goal.Swim(20), List.of(new Condition.InBiome("#minecraft:is_river")), 2));
		// another biome keeps its phrase in front
		assertEquals("해변에서 물에서 이십 블록 수영하세요.".replace("해변에서 물에서", "해변 물에서"),
				clue(new Goal.Swim(20), List.of(new Condition.InBiome("#minecraft:is_beach")), 2));
	}

	// ------------------------------------------------------------ 12. counts above twenty

	@Test
	void i12_nativeNumeralsGoUpToNinetyNine() {
		assertEquals("스무", KoGrammar.nativeNumeral(20));
		assertEquals("스물한", KoGrammar.nativeNumeral(21));
		assertEquals("스물네", KoGrammar.nativeNumeral(24));
		assertEquals("서른", KoGrammar.nativeNumeral(30));
		assertEquals("마흔여덟", KoGrammar.nativeNumeral(48));
		assertEquals("쉰", KoGrammar.nativeNumeral(50));
		assertEquals("예순네", KoGrammar.nativeNumeral(64));
		assertEquals("일흔", KoGrammar.nativeNumeral(70));
		assertEquals("여든세", KoGrammar.nativeNumeral(83));
		assertEquals("아흔아홉", KoGrammar.nativeNumeral(99));
		assertEquals("스물네 개", KoGrammar.counter(24, "개"));
		assertEquals("예순네 개", KoGrammar.counter(64, "개"));
		assertEquals("100개", KoGrammar.counter(100, "개"));
		assertEquals("24병".replace("24병", "스물네 병"), KoGrammar.counter(24, "병"));
		assertEquals("24번", KoGrammar.counter(24, "번"), "a counter that is not native keeps digits");
	}

	@Test
	void i12_aCountOfTwentyFourIsSpoken() {
		assertEquals("종이를 스물네 장 만드세요.", clue(new Goal.AcquireItem("minecraft:paper", 24), 2));
		assertEquals("좀비를 서른 마리 물리치세요.", clue(new Goal.DefeatEntity("minecraft:zombie", 30), 2));
	}

	// ------------------------------------------------------------ 10. a sequence says its object once

	private static Goal sequence(String connector, Goal... parts) {
		return new Goal.Sequence(List.of(parts), true, connector);
	}

	@Test
	void i10_aSequenceOnOneObjectSaysItOnce() {
		Goal craft = new Goal.Craft("minecraft:diamond_helmet", 1, null, List.of());
		Goal wear = new Goal.Equip("minecraft:diamond_helmet");
		assertEquals("다이아몬드 투구를 만든 후에 쓰세요.", clue(sequence("AFTER", craft, wear), 3));
		assertEquals("다이아몬드 투구를 쓰기 전에 만드세요.", clue(sequence("BEFORE", craft, wear), 3));
		assertEquals("다이아몬드 투구를 만들고 나서 쓰세요.", clue(sequence("THEN", craft, wear), 3));
		assertEquals("다이아몬드 투구를 만들고 쓰세요.", clue(sequence("AND", craft, wear), 3));
		assertEquals("다이아몬드 투구를 만든 후에 썼어요.", finale(sequence("AFTER", craft, wear), List.of()));
	}

	@Test
	void i10_aSequenceKeepsTheObjectWhenThePartsDiffer() {
		Goal craft = new Goal.Craft("minecraft:diamond_helmet", 1, null, List.of());
		Goal chest = new Goal.PutIn("minecraft:diamond_helmet", "minecraft:chest", 1);
		assertTrue(clue(sequence("AFTER", craft, chest), 3).contains("상자"), clue(sequence("AFTER", craft, chest), 3));
		Goal two = new Goal.Craft("minecraft:diamond_helmet", 2, null, List.of());
		assertEquals(2, clue(sequence("AFTER", two, new Goal.Equip("minecraft:diamond_helmet")), 3).split("투구를").length - 1,
				"a count stays in its part");
	}

	@Test
	void i10_twoLooksOfTheSameLengthSayTheSecondsOnce() {
		Goal gold = new Goal.LookAtBlock("minecraft:gold_block", 1000L);
		Goal piglin = new Goal.LookAtEntity("minecraft:piglin", 1000L);
		String text = clue(sequence("BEFORE", gold, piglin), 3);
		assertEquals(1, text.split("일 초 동안", -1).length - 1, text);
		assertEquals("피글린을 바라보기 전에 금 블록을 일 초 동안 바라보세요.", text);
		// different lengths keep both
		Goal longer = new Goal.LookAtEntity("minecraft:piglin", 3000L);
		assertEquals(2, clue(sequence("BEFORE", gold, longer), 3).split("초 동안", -1).length - 1);
	}

	// ------------------------------------------------------------ 11. a taught word renames no other object

	@Test
	void i11_aWordDrawnWithAnItemDoesNotRenameTheItemInASequence() {
		Goal kill = new Goal.DefeatEntity("minecraft:enderman", 1);
		Goal pearl = new Goal.AcquireItem("minecraft:ender_pearl", 1);
		Goal both = sequence("AFTER", kill, pearl);
		String text = SentenceBuilder.normal(both, word("enderman"), List.of(), 3, false, NAMES);
		assertTrue(text.contains("엔더 진주"), text);
		assertEquals(1, text.split("엔더맨", -1).length - 1, text);
		// a step that teaches the item alone still says the word for it
		assertEquals("엔더맨을 구하세요.", SentenceBuilder.normal(pearl, word("enderman"), List.of(), 2, false, NAMES));
	}

	// ------------------------------------------------------------ 6. armour words in the hint

	@Test
	void i6_aTaughtGeneralArmourWordStandsInTheHint() {
		Goal boots = new Goal.Equip("minecraft:iron_boots");
		String hint = SentenceBuilder.hint(boots, word("shoes"), List.of(), REGISTRY);
		assertTrue(hint.contains(word("shoes").ko), hint);
		// the clue still names the exact piece
		String clue = SentenceBuilder.normal(boots, word("shoes"), List.of(), 2, false, REGISTRY);
		assertFalse(clue.contains(word("shoes").ko + "을"), clue);
		assertTrue(clue.contains("철 부츠"), clue);
		Goal chest = new Goal.Equip("minecraft:iron_chestplate");
		assertTrue(SentenceBuilder.hint(chest, word("armor"), List.of(), REGISTRY).contains(word("armor").ko));
		// a tool keeps its exact name in the hint
		Goal pick = new Goal.Craft("minecraft:iron_pickaxe", 1, null, List.of());
		assertTrue(SentenceBuilder.hint(pick, word("pickaxe"), List.of(), REGISTRY).contains("철 곡괭이"));
	}

	// ------------------------------------------------------------ 14. the distinctTypes ratchet

	@Test
	void i14_aLevelMayLoseARarelyUsedTypeWhileItMeetsItsTarget() {
		com.google.gson.JsonObject baseline = MetricsBaseline.load();
		// level 3 fell from 28 to 27 goal types when a batch dropped its only REACH_DIMENSION step: still far above 6
		assertEquals(List.of(), MetricsBaseline.regressions(java.util.Map.of("variety.distinctTypes.3", 27.0), baseline,
				"variety.distinctTypes.3"));
		assertEquals(List.of(), MetricsBaseline.regressions(java.util.Map.of("variety.distinctTypes.3", 6.0), baseline,
				"variety.distinctTypes.3"));
		// below the target of 6 it is a regression
		assertEquals(1, MetricsBaseline.regressions(java.util.Map.of("variety.distinctTypes.3", 5.0), baseline,
				"variety.distinctTypes.3").size());
	}

	// ------------------------------------------------------------ 15. the review packet

	@Test
	void i15_theGoalColumnNamesEveryAnchorAndTheVerbTheSentenceUses() {
		Goal between = new Goal.PlaceRel("minecraft:torch", io.github.leks2205.hangulquest.quest.spatial.Relation.BETWEEN,
				"minecraft:chest", 1, "minecraft:furnace");
		String described = KoreanFixtures.describe(between);
		assertTrue(described.contains("minecraft:chest") && described.contains("minecraft:furnace"), described);
		assertTrue(KoreanFixtures.describe(new Goal.PutIn("minecraft:bread", "minecraft:chest", 2))
				.contains("minecraft:chest"));
		// the verb of the sentence is the one the conditions choose, not the one the goal has alone
		Goal cow = new Goal.InteractEntity("minecraft:cow");
		Verb alone = SentenceBuilder.verbFor(cow);
		Verb leashed = SentenceBuilder.verbOf(cow, null, List.of(new Condition.Holding("minecraft:lead")));
		assertNotEquals(alone, leashed);
		assertEquals(Verb.LEASH, leashed);
	}

	// ------------------------------------------------------------ every grammar id a type emits is a construction

	@Test
	void everyGrammarIdAGoalOrConditionEmitsIsAConstruction() {
		var book = io.github.leks2205.hangulquest.quest.grammar.GrammarBook.shared();
		var ladder = io.github.leks2205.hangulquest.quest.grammar.Ladder.shared();
		java.util.Set<String> unknown = new java.util.TreeSet<>();
		// every goal and condition of the shipped trails, at every level, as the generator speaks them
		for (var trail : io.github.leks2205.hangulquest.quest.QuestLoader.loadAll()) {
			for (var step : trail.allSteps()) {
				for (int level = 1; level <= 6; level++) {
					for (String id : SentenceBuilder.grammar(step.goal(), null, step.conditions(), level, false, REGISTRY)) {
						if (!book.has(ladder.resolve(id))) {
							unknown.add(id + " (" + trail.id() + "/" + step.id() + ")");
						}
					}
				}
			}
		}
		// and the goals the shipped trails do not use yet
		for (Goal goal : List.of(BOW_TARGET, CROSSBOW_ZOMBIE, NORTH_20, new Goal.Shoot("minecraft:snowball", null, null, 3, 20, 0),
				new Goal.PutIn("minecraft:bread", "minecraft:chest", 2))) {
			for (int level = 1; level <= 6; level++) {
				for (String id : SentenceBuilder.grammar(goal, null, List.of(new Condition.Facing("NORTH")), level, false, REGISTRY)) {
					if (!book.has(ladder.resolve(id))) {
						unknown.add(id + " (" + goal.getClass().getSimpleName() + ")");
					}
				}
			}
		}
		assertEquals(java.util.Set.of(), unknown);
	}

	// ------------------------------------------------------------ the linter knows TNT and counts up to 99

	private static int lint(java.nio.file.Path dir, String... lines) throws java.io.IOException {
		java.nio.file.Path tsv = dir.resolve("lines.tsv");
		java.nio.file.Files.writeString(tsv, "id\tlevel\tsource\tko\tgoal\n" + String.join("\n", lines) + "\n");
		return QuestScaffoldTest.python("tools/lint_korean.py", tsv.toString(), "--fail-on", "error").exit();
	}

	@org.junit.jupiter.api.io.TempDir
	java.nio.file.Path scratch;

	@Test
	void theLinterAcceptsTntAndNativeCountsUpToNinetyNine() throws java.io.IOException {
		org.junit.jupiter.api.Assumptions.assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0);
		assertEquals(0, lint(scratch, "trail.x.s1.clue\t3\tgenerated:clue/Avoid/BREAK\t사막에서 TNT를 부수지 마세요.\t"));
		assertEquals(1, lint(scratch, "trail.x.s1.clue\t3\tgenerated:clue/Avoid/BREAK\t사막에서 minecraft:tnt를 부수지 마세요.\t"),
				"a registry id is still a leak");
		assertEquals(1, lint(scratch, "trail.x.s1.clue\t3\tgenerated:clue/Avoid/BREAK\t사막에서 TNTX를 부수지 마세요.\t"),
				"only TNT is the exception");
		assertEquals(0, lint(scratch, "trail.x.s1.clue\t6\tgenerated:clue/AcquireItem/COLLECT\t돌을 서른두 개 모아요.\tAcquireItem minecraft:stone x32"));
		assertEquals(0, lint(scratch, "trail.x.s1.clue\t6\tgenerated:clue/AcquireItem/COLLECT\t돌을 예순네 개 모아요.\tAcquireItem minecraft:stone x64"));
	}
}
