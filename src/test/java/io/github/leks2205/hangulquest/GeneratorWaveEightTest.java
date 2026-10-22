package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 8 (from the weaknesses BATCH-010 and BATCH-011 reported): creature anchors and sequences in the
 * playtest, tag words, writing on a sign, the bell, trading in a direction, counting people, fish and boats,
 * missing words, seconds in the ceremony, the finale rule and BETWEEN with water.
 */
class GeneratorWaveEightTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMES = KoreanFixtures.namer(CATALOG);
	private static final SentenceBuilder.Namer REGISTRY = KoreanFixtures.registryNamer();

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, int level) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(), level, false, NAMES);
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static QuestStep step(Goal goal, String concept) {
		return new QuestStep("s1", List.of(concept), goal, List.of(), null, null, "Do it.", null, null, null, null);
	}

	private static GoalEvaluator.Tracker trackerFor(Goal goal, String concept) {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = Accepted.of(step(goal, concept), CATALOG.concept(concept).orElseThrow());
		return tracker;
	}

	// ------------------------------------------------------------ 9. MOUNT on any boat

	/** The fixture namer of WaveQaTest: every creature is 동물, so a name the word does not give shows. */
	private static final SentenceBuilder.Namer ANIMALS = new SentenceBuilder.Namer() {
		@Override
		public String biome(String id) {
			return "강";
		}

		@Override
		public String entity(String id) {
			return "동물";
		}

		@Override
		public String block(String id) {
			return "블록";
		}
	};

	@Test
	void i9_everyBoatAndRaftIsCalledBae() {
		for (String boat : List.of("minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat",
				"minecraft:bamboo_raft", "minecraft:oak_chest_boat", "minecraft:bamboo_chest_raft")) {
			Goal mount = new Goal.Mount(boat, 1500);
			assertEquals("배에 앉아요.", clue(mount, "boat", 1), boat);
			assertEquals("배에 앉으세요.", clue(mount, "boat", 2), boat);
			assertEquals("배에 앉아요.", SentenceBuilder.hint(mount, word("boat"), List.of(), ANIMALS), boat + " (fixture)");
			assertEquals("배에 앉았어요.", SentenceBuilder.ceremony(mount, word("boat"), List.of(), ANIMALS), boat);
			assertEquals(List.of(), problems(mount, "boat"), boat);
		}
	}

	@Test
	void i9_aHorseIsNoBoatAndAnUntaughtBoatKeepsItsOwnName() {
		assertEquals("가문비나무 보트에 앉아요.", clue(new Goal.Mount("minecraft:spruce_boat", 1500), null, 1));
		assertEquals("동물을 타요.", SentenceBuilder.normal(new Goal.Mount("minecraft:horse", 1500), word("boat"), List.of(), 1,
				false, ANIMALS), "the boat word does not name a horse");
		assertFalse(problems(new Goal.Mount("minecraft:horse", 1500), "boat").isEmpty());
	}

	// ------------------------------------------------------------ 8. fish entities

	@Test
	void i8_theWordForFishNamesTheCodTheSalmonAndTheTropicalFish() {
		for (String fish : List.of("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish")) {
			Goal look = new Goal.LookAtEntity(fish, 3000);
			assertEquals("물고기를 삼 초 동안 바라봐요.", clue(look, "fish", 1), fish);
			assertEquals(List.of(), problems(look, "fish"), fish + " needs no biome condition");
			assertEquals("물고기를 바라봤어요.", SentenceBuilder.ceremony(look, word("fish"), List.of(), NAMES), fish);
		}
		assertFalse(problems(new Goal.LookAtEntity("minecraft:cow", 3000), "fish").isEmpty(), "a cow is no fish");
		assertFalse(problems(new Goal.LookAtEntity("minecraft:squid", 3000), "fish").isEmpty(), "a squid is no fish");
	}

	@Test
	void i8_aStepForTheWordTakesEveryFishButOnlyThatWord() {
		Goal look = new Goal.LookAtEntity("minecraft:cod", 1000);
		GoalEvaluator.Tracker taught = trackerFor(look, "fish");
		Observation salmon = new Observation();
		salmon.lookedEntity = "minecraft:salmon";
		boolean done = false;
		for (int i = 0; i < 40 && !done; i++) {
			salmon.now = 1000L + i * 50L;
			done = GoalEvaluator.tick(look, List.of(), salmon, taught);
		}
		assertTrue(done, "looking at a salmon is looking at a fish");
		// without the word the goal is the cod and nothing else
		GoalEvaluator.Tracker plain = new GoalEvaluator.Tracker();
		for (int i = 0; i < 100; i++) {
			salmon.now = 1000L + i * 50L;
			assertFalse(GoalEvaluator.tick(look, List.of(), salmon, plain));
		}
		// a cow is not a fish
		Observation cow = new Observation();
		cow.lookedEntity = "minecraft:cow";
		GoalEvaluator.Tracker other = trackerFor(look, "fish");
		for (int i = 0; i < 100; i++) {
			cow.now = 1000L + i * 50L;
			assertFalse(GoalEvaluator.tick(look, List.of(), cow, other));
		}
	}

	@Test
	void i8_defeatingAndFeedingWidenTheSameWay() {
		Goal defeat = new Goal.DefeatEntity("minecraft:cod", 1);
		Observation killed = new Observation();
		killed.defeatedEntities.add("minecraft:salmon");
		assertTrue(GoalEvaluator.tick(defeat, List.of(), killed, trackerFor(defeat, "fish")));
		assertFalse(GoalEvaluator.tick(defeat, List.of(), killed, new GoalEvaluator.Tracker()));
		Goal bucket = new Goal.InteractEntity("minecraft:tropical_fish");
		Observation clicked = new Observation();
		clicked.interactedEntities.add("minecraft:cod");
		assertTrue(GoalEvaluator.tick(bucket, List.of(), clicked, trackerFor(bucket, "fish")));
		assertFalse(GoalEvaluator.tick(bucket, List.of(), clicked, new GoalEvaluator.Tracker()));
	}

	// ------------------------------------------------------------ 3. a tag is spoken with its word

	@Test
	void i3_everyTagHasAWord() {
		for (String path : BlockSpec.tagPaths()) {
			assertNotNull(BlockSpec.tagWord("#minecraft:" + path), "#minecraft:" + path + " is spoken 그것");
		}
	}

	@Test
	void i3_theTagsTheContentNamesAreSpokenWithTheirWord() {
		Map<String, String> words = Map.of("signs", "표지판", "candles", "초", "wool_carpets", "양탄자", "beds", "침대",
				"doors", "문", "windows", "창문", "logs", "통나무", "planks", "판자", "fences", "울타리", "flowers", "꽃");
		words.forEach((tag, word) -> assertEquals(word, BlockSpec.tagWord("#minecraft:" + tag), tag));
		// ... and the placement says it
		assertEquals("표지판 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO,
				"#minecraft:signs", 1), null, 1));
		assertEquals("초 옆에 횃불을 놓아요.", clue(new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO,
				"#minecraft:candles", 1), null, 1));
		assertEquals("양탄자 위에 횃불을 놓아요.", clue(new Goal.PlaceRel("minecraft:torch", Relation.ON_TOP_OF,
				"#minecraft:wool_carpets", 1), null, 1));
	}

	@Test
	void i3_everySignCandleAndCarpetMatchesItsTag() {
		assertTrue(BlockSpec.matches("#minecraft:signs", "minecraft:birch_sign"));
		assertTrue(BlockSpec.matches("#minecraft:signs", "minecraft:oak_hanging_sign"));
		assertFalse(BlockSpec.matches("#minecraft:signs", "minecraft:oak_door"));
		assertTrue(BlockSpec.matches("#minecraft:candles", "minecraft:candle"));
		assertTrue(BlockSpec.matches("#minecraft:candles", "minecraft:orange_candle"));
		assertFalse(BlockSpec.matches("#minecraft:candles", "minecraft:candle_cake"));
		assertTrue(BlockSpec.matches("#minecraft:wool_carpets", "minecraft:light_gray_carpet"));
		assertFalse(BlockSpec.matches("#minecraft:wool_carpets", "minecraft:white_wool"));
	}

	@Test
	void i3_aTagWithoutAWordIsAValidatorErrorNotAThatOne() {
		// a catalog that lacks the concept: the tag has blocks but no word to say them with
		Catalog bare = Catalog.ofConcepts(List.of());
		Goal goal = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, "#minecraft:signs", 1);
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, "Do it.", null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, bare, problems));
		assertTrue(problems.stream().anyMatch(p -> p.contains("#minecraft:signs") && p.contains("no word")), problems.toString());
		// the real catalog has it
		assertEquals(List.of(), problems(goal));
		// a tag as the object of a goal and as the anchor of a condition are held to the same rule
		Goal flowers = new Goal.BreakBlock("#minecraft:flowers", 1, null);
		List<String> flowerProblems = new ArrayList<>();
		GoalTypes.of(flowers).validate(flowers, new ValidationContext("t/s1", step, bare, flowerProblems));
		assertTrue(flowerProblems.stream().anyMatch(p -> p.contains("no word")), flowerProblems.toString());
	}

	// ------------------------------------------------------------ 10. missing concepts

	@Test
	void i10_aWindowIsAPaneAStainedPaneOrTheGlassOfAWall() {
		assertEquals("창문", word("window").ko);
		for (String block : List.of("minecraft:glass_pane", "minecraft:red_stained_glass_pane", "minecraft:glass",
				"minecraft:lime_stained_glass")) {
			Goal place = new Goal.PlaceBlock(block, 1);
			assertEquals("창문을 놓아요.", clue(place, "window", 1), block);
			assertEquals(List.of(), problems(place, "window"), block);
		}
		assertFalse(problems(new Goal.PlaceBlock("minecraft:stone", 1), "window").isEmpty(), "stone is no window");
		// the panes keep their own word where the window is not the one taught
		assertEquals("유리판을 놓아요.", clue(new Goal.PlaceBlock("minecraft:glass_pane", 1), "glass_pane", 1));
		assertEquals("창문", BlockSpec.tagWord("#minecraft:windows"));
		assertTrue(BlockSpec.matches("#minecraft:windows", "minecraft:blue_stained_glass_pane"));
		assertFalse(BlockSpec.matches("#minecraft:windows", "minecraft:glowstone"));
	}

	@Test
	void i10_theFamilyIsAWordWithNoItem() {
		Word family = word("family");
		assertEquals("가족", family.ko);
		assertTrue(family.referents.isEmpty());
		assertTrue(family.hasDescKo());
		assertFalse(CATALOG.concept("family").orElseThrow().sentenceKo.isBlank());
		assertEquals("Familie", family.gloss(io.github.leks2205.hangulquest.util.Lang.DE));
	}

	// ------------------------------------------------------------ 4. writing on a sign

	private static final Goal SIGN = new Goal.UseBlock("minecraft:oak_sign");

	@Test
	void i4_aSignIsWrittenOnNotUsed() {
		assertEquals("표지판에 글을 써요.", clue(SIGN, "sign", 1));
		assertEquals("표지판에 글을 쓰세요.", clue(SIGN, "sign", 2));
		assertEquals("표지판에 글을 써요.", SentenceBuilder.hint(SIGN, word("sign"), List.of(), NAMES));
		assertEquals("표지판에 글을 썼어요.", SentenceBuilder.ceremony(SIGN, word("sign"), List.of(), NAMES));
		assertEquals(Verb.WRITE, SentenceBuilder.verbFor(SIGN));
		assertEquals(Verb.WRITE, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:birch_hanging_sign", "WRITE")));
		// any sign will do, so it is the sign and not the wood
		assertEquals("표지판에 글을 써요.", clue(new Goal.UseBlock("minecraft:birch_sign"), null, 1));
		// the other uses of a block keep their verbs
		assertEquals(Verb.OPEN, SentenceBuilder.verbFor(new Goal.UseBlock("minecraft:oak_door", "OPEN")));
	}

	@Test
	void i4_theStepThatTeachesNameAsksForTheName() {
		assertEquals("표지판에 이름을 써요.", clue(SIGN, "name", 1));
		assertEquals("표지판에 이름을 쓰세요.", clue(SIGN, "name", 2));
		assertEquals("표지판에 이름을 써요.", SentenceBuilder.hint(SIGN, word("name"), List.of(), NAMES));
		assertEquals("표지판에 이름을 썼어요.", SentenceBuilder.ceremony(SIGN, word("name"), List.of(), NAMES));
		assertEquals(List.of(), problems(SIGN, "name"), "이름 describes the goal");
		assertEquals(List.of(), problems(SIGN, "sign"));
		assertTrue(SentenceBuilder.mentions(clue(SIGN, "name", 1), word("name")));
		assertFalse(problems(new Goal.UseBlock("minecraft:oak_door"), "name").isEmpty(), "a door has no name to write");
	}

	@Test
	void i4_onlyASignCanBeWrittenOn() {
		assertEquals(List.of("t/s1: a minecraft:oak_door has no text to write; use a sign"),
				problems(new Goal.UseBlock("minecraft:oak_door", "WRITE"), "door"));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:oak_sign", "WRITE"), "sign"));
	}

	@Test
	void i4_theGoalCountsASignCloseWithTextAndNotTheClick() {
		GoalEvaluator.Tracker clicked = new GoalEvaluator.Tracker();
		Observation click = new Observation();
		click.usedBlocks.add("minecraft:oak_sign");
		assertFalse(GoalEvaluator.tick(SIGN, List.of(), click, clicked), "opening the editor is not writing");
		Observation written = new Observation();
		written.addSignWritten("minecraft:birch_sign");
		assertTrue(GoalEvaluator.tick(SIGN, List.of(), written, new GoalEvaluator.Tracker()), "any sign is a sign");
		Observation door = new Observation();
		door.addSignWritten("minecraft:oak_door");
		assertFalse(GoalEvaluator.tick(SIGN, List.of(), door, new GoalEvaluator.Tracker()));
	}

	@Test
	void i4_theChangeIsReadAndWrittenAsJson() {
		JsonObject json = JsonParser.parseString("{\"type\": \"USE_BLOCK\", \"block\": \"minecraft:oak_sign\", \"change\": \"WRITE\"}")
				.getAsJsonObject();
		Goal goal = io.github.leks2205.hangulquest.quest.QuestLoader.parseGoal(json);
		assertEquals(new Goal.UseBlock("minecraft:oak_sign", "WRITE"), goal);
		assertEquals(json, GoalTypes.toJson(goal));
	}

	// ------------------------------------------------------------ 5. HEAR the bell

	private static final Goal BELL = new Goal.Hear("minecraft:bell", 10, 3000L);

	@Test
	void i5_aBellIsHeardNotRung() {
		assertEquals("종소리를 들어요.", clue(BELL, "bell", 1));
		assertEquals("종소리를 들으세요.", clue(BELL, "bell", 2));
		assertEquals("종소리를 들었어요.", SentenceBuilder.ceremony(BELL, word("bell"), List.of(), NAMES));
		assertEquals("종소리를 들어요.", clue(BELL, null, 1));
		assertEquals(Verb.HEAR, SentenceBuilder.verbFor(BELL));
		assertEquals(List.of(), problems(BELL, "bell"));
		assertTrue(SentenceBuilder.mentions(clue(BELL, "bell", 1), word("bell")));
		assertFalse(problems(new Goal.Hear("minecraft:chest", 10, 3000L)).isEmpty());
	}

	@Test
	void i5_aRingNearbyCountsOnceItIsInRange() {
		Goal near = new Goal.Hear("minecraft:bell", 10, 3000L);
		Observation ringing = new Observation();
		ringing.bellDistance = 6.0;
		assertTrue(GoalEvaluator.tick(near, List.of(), ringing, new GoalEvaluator.Tracker()));
		Observation far = new Observation();
		far.bellDistance = 14.0;
		assertFalse(GoalEvaluator.tick(near, List.of(), far, new GoalEvaluator.Tracker()), "too far to hear");
		assertFalse(GoalEvaluator.tick(near, List.of(), new Observation(), new GoalEvaluator.Tracker()), "none ringing");
		// the jukebox goal ignores a bell
		Goal jukebox = new Goal.Hear("minecraft:jukebox", 10, 500L);
		assertFalse(GoalEvaluator.tick(jukebox, List.of(), ringing, new GoalEvaluator.Tracker()));
	}

	@Test
	void i5_theSensorIsToldToScanForBells() {
		Observation obs = new Observation();
		GoalEvaluator.tick(BELL, List.of(), obs, new GoalEvaluator.Tracker());
		assertTrue(obs.musicScanWanted);
		assertEquals("종소리가 안 들려요", io.github.leks2205.hangulquest.quest.QuestStatus.text(
				new QuestStep("s1", List.of("bell"), BELL, List.of(), null, null, "Do it.", null, null, null, null),
				new Observation(), new GoalEvaluator.Tracker(), CATALOG));
	}

	// ------------------------------------------------------------ 6. TRADE has a direction

	private static final String VILLAGER = "minecraft:villager";

	private static Observation traded(String got, String paid) {
		Observation obs = new Observation();
		obs.now = 5_000L;
		obs.addTraded(new io.github.leks2205.hangulquest.sense.TradeFact(VILLAGER, got, 1, paid, 1));
		return obs;
	}

	@Test
	void i6_theParticleAndTheVerbDecideTheDirection() {
		Goal buy = new Goal.Trade("minecraft:bread", VILLAGER, "BUY", 1);
		Goal sell = new Goal.Trade("minecraft:wheat", VILLAGER, "SELL", 1);
		assertEquals(List.of("L2.ege_source"), GoalTypes.of(buy).decidingForms(buy));
		assertEquals(List.of("L2.ege"), GoalTypes.of(sell).decidingForms(sell));
		assertEquals("주민에게서 빵을 사세요.", SentenceBuilder.normal(buy, null, List.of(), 2, false, REGISTRY));
		assertEquals("주민에게 밀을 파세요.", SentenceBuilder.normal(sell, null, List.of(), 2, false, REGISTRY));
		assertTrue(io.github.leks2205.hangulquest.quest.goal.GoalTypes.of(buy).decidingForms(buy).size() == 1);
	}

	@Test
	void i6_aTradeTheWrongWayRoundIsAFormSlipWithAHint() {
		Goal buy = new Goal.Trade("minecraft:bread", VILLAGER, "BUY", 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation sold = traded("minecraft:emerald", "minecraft:bread"); // bread went over the counter
		assertFalse(GoalEvaluator.tick(buy, List.of(), sold, tracker));
		assertEquals(1, tracker.formErrors);
		String status = io.github.leks2205.hangulquest.quest.QuestStatus.text(
				step(buy, "bread"), sold, tracker, CATALOG);
		assertEquals("에게서 = from · 다시 해 봐요", status);
		// the right way round counts and clears the hint
		Observation bought = traded("minecraft:bread", "minecraft:emerald");
		bought.now = 6_000L;
		assertTrue(GoalEvaluator.tick(buy, List.of(), bought, tracker));
		assertEquals(1, tracker.formErrors, "no further slip");
		// and the other direction
		Goal sell = new Goal.Trade("minecraft:wheat", VILLAGER, "SELL", 1);
		GoalEvaluator.Tracker seller = new GoalEvaluator.Tracker();
		Observation wrong = traded("minecraft:wheat", "minecraft:emerald");
		assertFalse(GoalEvaluator.tick(sell, List.of(), wrong, seller));
		assertEquals(1, seller.formErrors);
		assertEquals("에게 = to · 다시 해 봐요", io.github.leks2205.hangulquest.quest.QuestStatus.text(
				step(sell, "wheat"), wrong, seller, CATALOG));
		// an unrelated trade is no slip
		GoalEvaluator.Tracker other = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(buy, List.of(), traded("minecraft:apple", "minecraft:emerald"), other));
		assertEquals(0, other.formErrors);
	}

	// ------------------------------------------------------------ 7. counting people (명) and animals (마리)

	private static Condition near(String entity, int count) {
		return new Condition.NearEntity(entity, 8.0, count);
	}

	@Test
	void i7_peopleAreCountedWithMyeongAndAnimalsWithMari() {
		Goal wait = new Goal.Wait(10, null);
		assertEquals("주민 두 명 근처에서 십 초 동안 기다리세요.",
				SentenceBuilder.normal(wait, null, List.of(near(VILLAGER, 2)), 2, false, REGISTRY));
		assertEquals("주민 세 명 근처에서 십 초 동안 기다렸어요.",
				SentenceBuilder.ceremony(wait, null, List.of(near(VILLAGER, 3)), REGISTRY));
		assertEquals("소 두 마리 근처에서 십 초 동안 기다리세요.",
				SentenceBuilder.normal(wait, null, List.of(near("minecraft:cow", 2)), 2, false, REGISTRY));
		assertEquals("닭 다섯 마리 근처에서 잠깐 기다려요.",
				SentenceBuilder.hint(new Goal.Wait(10, null), null, List.of(near("minecraft:chicken", 5)), REGISTRY));
		// one is the plain 근처, as before
		assertEquals("주민 근처에서 십 초 동안 기다리세요.",
				SentenceBuilder.normal(wait, null, List.of(near(VILLAGER, 1)), 2, false, REGISTRY));
	}

	@Test
	void i7_theCountIsPartOfThePlaceOfAPlace() {
		Goal bed = new Goal.UseBlock("minecraft:red_bed");
		assertEquals("주민 두 명 근처 침대에서 자세요.",
				SentenceBuilder.normal(bed, word("bed"), List.of(near(VILLAGER, 2)), 2, false, NAMES));
	}

	@Test
	void i7_theConditionNeedsThatManyWithinTheRadius() {
		Condition two = near(VILLAGER, 2);
		io.github.leks2205.hangulquest.quest.condition.ConditionType<Condition> type =
				io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(two);
		Observation obs = new Observation();
		obs.position = new net.minecraft.world.phys.Vec3(0, 64, 0);
		obs.addNearby(VILLAGER, 3.0);
		obs.addEntityPosition(new io.github.leks2205.hangulquest.sense.EntityFact(VILLAGER, java.util.UUID.randomUUID(),
				3, 64, 0, 0F, 0F));
		assertFalse(type.met(two, obs), "one is not two");
		obs.addNearby(VILLAGER, 5.0);
		obs.addEntityPosition(new io.github.leks2205.hangulquest.sense.EntityFact(VILLAGER, java.util.UUID.randomUUID(),
				5, 64, 0, 0F, 0F));
		assertTrue(type.met(two, obs));
		assertTrue(type.met(near(VILLAGER, 1), obs));
		// one of the two is out of reach
		Observation spread = new Observation();
		spread.position = new net.minecraft.world.phys.Vec3(0, 64, 0);
		spread.addNearby(VILLAGER, 3.0);
		spread.addNearby(VILLAGER, 20.0);
		spread.addEntityPosition(new io.github.leks2205.hangulquest.sense.EntityFact(VILLAGER, java.util.UUID.randomUUID(),
				3, 64, 0, 0F, 0F));
		spread.addEntityPosition(new io.github.leks2205.hangulquest.sense.EntityFact(VILLAGER, java.util.UUID.randomUUID(),
				20, 64, 0, 0F, 0F));
		assertFalse(type.met(two, spread));
		// an observation that only has counts (hand-built) is judged by them
		Observation counts = new Observation();
		counts.addNearby("minecraft:cow", 2.0);
		counts.addNearby("minecraft:cow", 4.0);
		assertTrue(io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(near("minecraft:cow", 2))
				.met(near("minecraft:cow", 2), counts));
		assertFalse(io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(near("minecraft:cow", 3))
				.met(near("minecraft:cow", 3), counts));
	}

	@Test
	void i7_theCountIsReadWrittenAndOptional() {
		JsonObject json = JsonParser.parseString("{\"type\": \"NEAR_ENTITY\", \"entity\": \"minecraft:villager\", \"radius\": 8, \"count\": 2}")
				.getAsJsonObject();
		Condition condition = io.github.leks2205.hangulquest.quest.QuestLoader.parseCondition(json);
		assertEquals(new Condition.NearEntity(VILLAGER, 8.0, 2), condition);
		assertEquals(json, io.github.leks2205.hangulquest.quest.condition.ConditionTypes.toJson(condition));
		JsonObject plain = JsonParser.parseString("{\"type\": \"NEAR_ENTITY\", \"entity\": \"minecraft:cow\"}").getAsJsonObject();
		assertEquals(new Condition.NearEntity("minecraft:cow", 16.0, 1),
				io.github.leks2205.hangulquest.quest.QuestLoader.parseCondition(plain));
		assertFalse(io.github.leks2205.hangulquest.quest.condition.ConditionTypes.toJson(
				io.github.leks2205.hangulquest.quest.QuestLoader.parseCondition(plain)).has("count"));
	}

	@Test
	void i7_theCountersAreGradedConstructions() {
		Goal wait = new Goal.Wait(10, null);
		List<String> people = SentenceBuilder.grammar(wait, null, List.of(near(VILLAGER, 2)), 2, false, REGISTRY);
		assertTrue(people.contains("L2.counter_more") && people.contains("L1.num_native"), people.toString());
		List<String> animals = SentenceBuilder.grammar(wait, null, List.of(near("minecraft:cow", 2)), 2, false, REGISTRY);
		assertTrue(animals.contains("L1.counter_mari"), animals.toString());
		assertFalse(SentenceBuilder.grammar(wait, null, List.of(near(VILLAGER, 1)), 2, false, REGISTRY)
				.contains("L2.counter_more"));
	}

	// ------------------------------------------------------------ 11. seconds are Sino-Korean words

	@Test
	void i11_sinoKoreanNumeralsForTheUnitsOfTime() {
		Map<Integer, String> words = new java.util.LinkedHashMap<>();
		words.put(1, "일");
		words.put(5, "오");
		words.put(10, "십");
		words.put(11, "십일");
		words.put(20, "이십");
		words.put(30, "삼십");
		words.put(45, "사십오");
		words.put(60, "육십");
		words.put(100, "백");
		words.put(101, "백일");
		words.put(120, "백이십");
		words.put(300, "삼백");
		words.put(600, "육백");
		words.forEach((n, ko) -> assertEquals(ko, io.github.leks2205.hangulquest.quest.KoGrammar.sino(n), "" + n));
	}

	@Test
	void i11_theClueAndTheCeremonySaySecondsTheSameWay() {
		for (int seconds : new int[]{5, 10, 15, 30, 60, 90, 120}) {
			Goal wait = new Goal.Wait(seconds, null);
			String sino = io.github.leks2205.hangulquest.quest.KoGrammar.sino(seconds);
			assertEquals(sino + " 초 동안 기다리세요.", clue(wait, null, 2), "" + seconds);
			assertEquals(sino + " 초 동안 기다렸어요.", SentenceBuilder.ceremony(wait, null, List.of(), NAMES), "" + seconds);
		}
		assertTrue(SentenceBuilder.grammar(new Goal.Wait(10, null), null, List.of(), 2, false, NAMES).contains("L2.num_sino"));
		assertFalse(SentenceBuilder.grammar(new Goal.Wait(10, null), null, List.of(), 1, false, NAMES).contains("L2.num_sino"));
	}

	@Test
	void i11_noShippedWaitSpeaksSecondsInDigitsAnywhere() {
		java.util.regex.Pattern digits = java.util.regex.Pattern.compile("[0-9]+ ?초 동안");
		List<String> found = new ArrayList<>();
		for (io.github.leks2205.hangulquest.quest.Questline trail : io.github.leks2205.hangulquest.quest.QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				if (!(step.goal() instanceof Goal.Wait)) {
					continue;
				}
				for (String text : new String[]{step.clueKo(), step.hintKo(), "finale".equals(step.id()) ? trail.finaleKo() : null}) {
					if (text != null && digits.matcher(text).find()) {
						found.add(trail.id() + "/" + step.id() + ": " + text);
					}
				}
			}
		}
		assertEquals(List.of(), found);
	}

	// ------------------------------------------------------------ 13. BETWEEN with water

	private static final String SAND = "minecraft:sand";
	private static final String WATER = "minecraft:water";

	private static io.github.leks2205.hangulquest.sense.PlacementFact placedWith(String block, Object... around) {
		io.github.leks2205.hangulquest.quest.spatial.World world = new io.github.leks2205.hangulquest.quest.spatial.World()
				.put(100, 64, 100, block);
		for (int i = 0; i < around.length; i += 2) {
			int[] at = (int[]) around[i];
			world.put(100 + at[0], 64 + at[1], 100 + at[2], (String) around[i + 1]);
		}
		return world.placed(new net.minecraft.core.BlockPos(100, 64, 100));
	}

	private static boolean met(Goal goal, io.github.leks2205.hangulquest.sense.PlacementFact fact) {
		Observation obs = new Observation();
		obs.now = 1000L;
		obs.addPlacement(fact);
		obs.placedBlocks.add(fact.block());
		return GoalEvaluator.tick(goal, List.of(), obs, new GoalEvaluator.Tracker());
	}

	@Test
	void i13_aPlantOnTheShoreIsBetweenTheSandAndTheWater() {
		Goal cane = new Goal.PlaceRel("minecraft:sugar_cane", Relation.BETWEEN, SAND, 1, WATER);
		Goal reversed = new Goal.PlaceRel("minecraft:sugar_cane", Relation.BETWEEN, WATER, 1, SAND);
		// sugar cane on sand with the water next to the sand
		var shore = placedWith("minecraft:sugar_cane", new int[]{0, -1, 0}, SAND, new int[]{1, -1, 0}, WATER);
		assertTrue(met(cane, shore));
		assertTrue(met(reversed, shore));
		// a sapling on sand, the water two blocks along
		var sapling = placedWith("minecraft:oak_sapling", new int[]{0, -1, 0}, SAND, new int[]{0, -1, -2}, WATER);
		assertTrue(met(new Goal.PlaceRel("minecraft:oak_sapling", Relation.BETWEEN, SAND, 1, WATER), sapling));
		// the water at the level of the plant (a pond that is full) is next to it as well
		assertTrue(met(cane, placedWith("minecraft:sugar_cane", new int[]{0, -1, 0}, SAND, new int[]{-1, 0, 0}, WATER)));
		// the old reading still holds: the sand on one side, the water on the other
		assertTrue(met(cane, placedWith("minecraft:sugar_cane", new int[]{-1, 0, 0}, SAND, new int[]{2, 0, 0}, WATER)));
	}

	@Test
	void i13_noWaterNoSandOrADifferentGroundIsNotBetween() {
		Goal cane = new Goal.PlaceRel("minecraft:sugar_cane", Relation.BETWEEN, SAND, 1, WATER);
		assertFalse(met(cane, placedWith("minecraft:sugar_cane", new int[]{0, -1, 0}, SAND)), "no water");
		assertFalse(met(cane, placedWith("minecraft:sugar_cane", new int[]{0, -1, 0}, "minecraft:dirt", new int[]{1, -1, 0}, WATER)),
				"dirt is no sand");
		assertFalse(met(cane, placedWith("minecraft:sugar_cane", new int[]{1, -1, 0}, SAND, new int[]{2, -1, 0}, WATER)),
				"not standing on the sand");
		assertFalse(met(cane, placedWith("minecraft:sugar_cane", new int[]{0, -1, 0}, SAND, new int[]{5, -1, 0}, WATER)),
				"the water is too far away");
		// two solid anchors are not read this way: a chest under a torch with a furnace beside is not BETWEEN
		Goal solids = new Goal.PlaceRel("minecraft:torch", Relation.BETWEEN, "minecraft:chest", 1, "minecraft:furnace");
		assertFalse(met(solids, placedWith("minecraft:torch", new int[]{0, -1, 0}, "minecraft:chest", new int[]{1, 0, 0}, "minecraft:furnace")));
	}

	@Test
	void i13_theSentenceIsSpokenAndValid() {
		Goal cane = new Goal.PlaceRel("minecraft:sugar_cane", Relation.BETWEEN, SAND, 1, WATER);
		assertEquals("모래와 물 사이에 사탕수수를 심어요.", SentenceBuilder.hint(cane, null, List.of(), REGISTRY));
		assertEquals("모래와 물 사이에 사탕수수를 심으세요.", SentenceBuilder.normal(cane, null, List.of(), 2, false, REGISTRY));
		assertEquals(List.of(), problems(cane));
	}
}
