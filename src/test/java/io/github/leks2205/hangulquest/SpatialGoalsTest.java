package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.NearMiss;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialRules;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three spatial goal types: evaluation, the wrong-relation signal, signatures, validation and the demo trail. */
class SpatialGoalsTest {
	private static final String TORCH = "minecraft:torch";
	private static final String DOORS = "#minecraft:doors";
	private static final String COBBLE = "minecraft:cobblestone";
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("spatial-goals");

	private final Catalog catalog = KoreanFixtures.catalog();

	@TempDir
	Path tempDir;

	@BeforeEach
	void english() {
		Lang.set("EN");
	}

	@AfterEach
	void restoreLanguage() {
		Lang.set("EN");
	}

	// ------------------------------------------------------------------ helpers

	private static Observation observing(PlacementFact... facts) {
		Observation obs = new Observation();
		obs.now = 50_000L;
		obs.clockTime = 1000L;
		for (PlacementFact fact : facts) {
			obs.addPlacement(fact);
		}
		return obs;
	}

	private static boolean tick(Goal goal, Observation obs, GoalEvaluator.Tracker tracker) {
		return GoalEvaluator.tick(goal, List.of(), obs, tracker);
	}

	/** A torch over, beside or below one door block. */
	private static PlacementFact torchAndDoor(int doorDx, int doorDy, int doorDz) {
		return new World().put(100, 64, 100, TORCH).put(100 + doorDx, 64 + doorDy, 100 + doorDz, "minecraft:oak_door")
				.placed(new BlockPos(100, 64, 100));
	}

	private QuestStep stepOf(Goal goal) {
		return new QuestStep("s1", List.of(), goal, List.of(), null, null, null, null, null, null, null);
	}

	private String statusOf(Goal goal, Observation obs, GoalEvaluator.Tracker tracker) {
		return QuestStatus.text(stepOf(goal), obs, tracker, catalog);
	}

	// ------------------------------------------------------------- place relative

	@Test
	void placeRelCountsOnlyTheRightBlockInTheRightPlace() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertTrue(tick(goal, observing(torchAndDoor(1, 0, 0)), tracker));
		assertEquals(1, tracker.count);

		// a chest next to a door is the wrong block
		PlacementFact chest = new World().put(100, 64, 100, "minecraft:chest").put(101, 64, 100, "minecraft:oak_door")
				.placed(new BlockPos(100, 64, 100));
		GoalEvaluator.Tracker other = new GoalEvaluator.Tracker();
		assertFalse(tick(goal, observing(chest), other));
		assertEquals(0, other.count);
		assertNull(NearMiss.current(other, 50_000L), "the wrong block is not a near miss");

		// a torch far from every door is nothing at all
		GoalEvaluator.Tracker far = new GoalEvaluator.Tracker();
		assertFalse(tick(goal, observing(torchAndDoor(3, 0, 3)), far));
		assertNull(NearMiss.current(far, 50_000L));
	}

	@Test
	void aCountGoalNeedsThatManyPlacements() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 2);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(goal, observing(torchAndDoor(1, 0, 0)), tracker));
		assertEquals("1/2", statusOf(goal, observing(), tracker));
		assertTrue(tick(goal, observing(torchAndDoor(0, 0, 1)), tracker));
	}

	@Test
	void aWrongRelationIsAHintNotProgress() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.ON_TOP_OF, DOORS, 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(torchAndDoor(0, 1, 0)); // the door is above: the torch is under it
		assertFalse(tick(goal, obs, tracker));
		assertEquals(0, tracker.count, "a near miss never counts");
		assertEquals(Relation.UNDER, NearMiss.current(tracker, obs.now));
		assertEquals("위 = on top · 다시 해 봐요", statusOf(goal, obs, tracker));
		assertFalse(tracker.assisted);
	}

	@Test
	void theNearMissLineFollowsTheInterfaceLanguageAndFades() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(torchAndDoor(0, -1, 0)); // the torch is on top of the door
		tick(goal, obs, tracker);
		assertEquals("옆 = next to · 다시 해 봐요", statusOf(goal, obs, tracker));
		Lang.set("DE");
		assertEquals("옆 = daneben · 다시 해 봐요", statusOf(goal, obs, tracker));
		Lang.set("EN");
		Observation later = observing();
		later.now = obs.now + NearMiss.SHOW_MS + 1;
		assertEquals("0/1", statusOf(goal, later, tracker), "the hint fades and the counter returns");
	}

	@Test
	void doingItRightClearsTheHint() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.ON_TOP_OF, DOORS, 2);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation wrong = observing(torchAndDoor(1, 0, 0));
		tick(goal, wrong, tracker);
		assertEquals(Relation.NEXT_TO, NearMiss.current(tracker, wrong.now));
		tick(goal, observing(torchAndDoor(0, -1, 0)), tracker);
		assertNull(NearMiss.current(tracker, wrong.now));
		assertEquals(1, tracker.count);
	}

	@Test
	void placementsAtNightCountAfterTheMorningCame() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		List<Condition> night = List.of(new Condition.Time(Condition.Phase.NIGHT));
		PlacementFact atNight = new World().put(100, 64, 100, TORCH).put(101, 64, 100, "minecraft:oak_door")
				.placed(new BlockPos(100, 64, 100), true, Vec3.ZERO);
		PlacementFact byDay = new World().put(100, 64, 100, TORCH).put(101, 64, 100, "minecraft:oak_door")
				.placed(new BlockPos(100, 64, 100), false, Vec3.ZERO);
		assertTrue(GoalEvaluator.tick(goal, night, observing(atNight), new GoalEvaluator.Tracker()),
				"placed at night, checked in the morning");
		assertFalse(GoalEvaluator.tick(goal, night, observing(byDay), new GoalEvaluator.Tracker()),
				"placed by day, never counts for a night goal");
	}

	// ---------------------------------------------------------- build / place on

	@Test
	void buildPatternNeedsTheWholeShape() {
		Goal goal = new Goal.BuildPattern(Pattern.PILLAR, COBBLE, 3);
		assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()));
		PlacementFact twoHigh = new World().put(100, 63, 100, COBBLE).put(100, 64, 100, COBBLE)
				.placed(new BlockPos(100, 64, 100));
		assertFalse(tick(goal, observing(twoHigh), new GoalEvaluator.Tracker()));
		PlacementFact wrongStone = new World().put(100, 62, 100, COBBLE).put(100, 63, 100, "minecraft:stone")
				.put(100, 64, 100, COBBLE).placed(new BlockPos(100, 64, 100));
		assertFalse(tick(goal, observing(wrongStone), new GoalEvaluator.Tracker()));
	}

	@Test
	void everyShapeCanBeBuiltThroughTheGoal() {
		for (Goal goal : new Goal[]{new Goal.BuildPattern(Pattern.PILLAR, COBBLE, 4),
				new Goal.BuildPattern(Pattern.PYRAMID, "minecraft:stone", 3),
				new Goal.BuildPattern(Pattern.RING, "#minecraft:planks", 3),
				new Goal.BuildPattern(Pattern.ROW, "minecraft:sand", 2)}) {
			assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()),
					goal.toString());
		}
	}

	@Test
	void placeOnPatternWantsTheStructureAndTheRelation() {
		Goal goal = new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PILLAR, COBBLE);
		assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()));

		// under the pillar instead of on top of it: a near miss
		PlacementFact under = new World().put(100, 64, 100, TORCH).put(100, 65, 100, COBBLE).put(100, 66, 100, COBBLE)
				.put(100, 67, 100, COBBLE).placed(new BlockPos(100, 64, 100));
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(under);
		assertFalse(tick(goal, obs, tracker));
		assertEquals(Relation.UNDER, NearMiss.current(tracker, obs.now));
		assertEquals("위 = on top · 다시 해 봐요", statusOf(goal, obs, tracker));

		// a pillar of another stone is not the pillar the task asks for
		PlacementFact other = new World().put(100, 64, 100, TORCH).put(100, 63, 100, "minecraft:stone")
				.put(100, 62, 100, "minecraft:stone").put(100, 61, 100, "minecraft:stone")
				.placed(new BlockPos(100, 64, 100));
		GoalEvaluator.Tracker none = new GoalEvaluator.Tracker();
		assertFalse(tick(goal, observing(other), none));
		assertNull(NearMiss.current(none, 50_000L));
	}

	@Test
	void aTorchBesideTheRingIsNotInsideIt() {
		Goal goal = new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING, COBBLE);
		assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()));
		World world = new World().put(100, 64, 100, TORCH);
		for (int x = 101; x <= 103; x++) {
			for (int z = 99; z <= 101; z++) {
				if (x != 102 || z != 100) {
					world.put(x, 64, z, COBBLE);
				}
			}
		}
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(world.placed(new BlockPos(100, 64, 100)));
		assertFalse(tick(goal, obs, tracker));
		assertEquals(Relation.NEXT_TO, NearMiss.current(tracker, obs.now));
		assertEquals("안 = inside · 다시 해 봐요", statusOf(goal, obs, tracker));
	}

	@Test
	void everyRelationCanBeSatisfiedThroughItsGoalType() {
		for (Relation relation : Relation.values()) {
			String anchor = relation.needsFacing() ? "minecraft:chest"
					: relation == Relation.INSIDE || relation == Relation.OUTSIDE ? "minecraft:oak_fence"
					: relation == Relation.BETWEEN ? TORCH : DOORS;
			Goal goal = new Goal.PlaceRel(relation == Relation.BETWEEN ? "minecraft:chest" : TORCH, relation, anchor, 1);
			assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()),
					relation.name());
		}
		for (Relation relation : new Relation[]{Relation.ON_TOP_OF, Relation.UNDER, Relation.NEXT_TO, Relation.NEAR}) {
			for (Pattern pattern : Pattern.values()) {
				Goal goal = new Goal.PlaceOnPattern(TORCH, relation, pattern, COBBLE);
				assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()),
						relation + " " + pattern);
			}
		}
		for (Relation relation : new Relation[]{Relation.INSIDE, Relation.OUTSIDE}) {
			Goal goal = new Goal.PlaceOnPattern(TORCH, relation, Pattern.RING, COBBLE);
			assertTrue(tick(goal, observing(SpatialWorlds.placementFor(goal, false)), new GoalEvaluator.Tracker()),
					relation.name());
		}
	}

	// ------------------------------------------------------------------ the JSON

	@Test
	void goalsRoundTripThroughJson() {
		for (Goal goal : new Goal[]{new Goal.PlaceRel(TORCH, Relation.BETWEEN, TORCH, 2),
				new Goal.BuildPattern(Pattern.RING, "#minecraft:planks", 3),
				new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING, COBBLE)}) {
			assertEquals(goal, GoalTypes.parse(GoalTypes.toJson(goal)));
		}
		JsonObject json = JsonParser.parseString(
				"{\"type\":\"place_rel\",\"block\":\"minecraft:torch\",\"relation\":\"under\",\"anchor\":\"#minecraft:doors\"}")
				.getAsJsonObject();
		assertEquals(new Goal.PlaceRel(TORCH, Relation.UNDER, DOORS, 1), GoalTypes.parse(json));
		JsonObject bare = JsonParser.parseString("{\"type\":\"BUILD_PATTERN\",\"pattern\":\"ROW\",\"material\":\"minecraft:sand\"}")
				.getAsJsonObject();
		assertEquals(new Goal.BuildPattern(Pattern.ROW, "minecraft:sand", 3), GoalTypes.parse(bare),
				"a missing size is the shape's usual size");
	}

	// --------------------------------------------------------------- signatures

	private static String signature(Goal goal) {
		return GoalTypes.of(goal).taskSignature(goal);
	}

	@Test
	void theSignatureNamesTheRelationAndTheAnchor() {
		Goal nextToDoor = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		Goal onPyramid = new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PYRAMID, COBBLE);
		assertTrue(signature(nextToDoor).contains("NEXT_TO") && signature(nextToDoor).contains(DOORS),
				signature(nextToDoor));
		assertTrue(signature(onPyramid).contains("ON_TOP_OF") && signature(onPyramid).contains("PYRAMID"),
				signature(onPyramid));

		List<Goal> different = List.of(nextToDoor, onPyramid,
				new Goal.PlaceRel(TORCH, Relation.ON_TOP_OF, DOORS, 1),
				new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:chest", 1),
				new Goal.PlaceRel("minecraft:chest", Relation.NEXT_TO, DOORS, 1),
				new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PILLAR, COBBLE),
				new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PYRAMID, "minecraft:stone"),
				new Goal.PlaceOnPattern(TORCH, Relation.NEXT_TO, Pattern.PYRAMID, COBBLE),
				new Goal.BuildPattern(Pattern.PYRAMID, COBBLE, 3),
				new Goal.BuildPattern(Pattern.PYRAMID, "minecraft:stone", 3),
				new Goal.BuildPattern(Pattern.PILLAR, COBBLE, 3),
				new Goal.BuildPattern(Pattern.PILLAR, COBBLE, 4),
				new Goal.BuildPattern(Pattern.RING, COBBLE, 3),
				new Goal.PlaceBlock(TORCH, 1));
		Set<String> seen = new HashSet<>();
		for (Goal goal : different) {
			assertTrue(seen.add(signature(goal)), "same task signature for different tasks: " + signature(goal));
		}
		assertEquals(signature(nextToDoor), signature(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 5)),
				"the count is not part of the task");
	}

	// ----------------------------------------------------------------- validation

	private static final String CLEAN_EN = "Place a torch — read the Korean to see where.";
	private static final String CLEAN_DE = "Platziere eine Fackel — lies den koreanischen Satz, um zu sehen, wo.";

	private List<String> problems(Goal goal, List<String> teach, String clueKo, String en, String de) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", teach, goal, List.of(), clueKo, de, en, null, de, en, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, catalog, problems));
		return problems;
	}

	private List<String> problems(Goal goal) {
		return problems(goal, List.of("torch"), null, CLEAN_EN, CLEAN_DE);
	}

	@Test
	void aWellFormedGoalHasNoProblems() {
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1)));
		assertEquals(List.of(), problems(new Goal.PlaceRel("minecraft:chest", Relation.BETWEEN, TORCH, 1),
				List.of("chest"), null, CLEAN_EN, CLEAN_DE));
		assertEquals(List.of(), problems(new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING, COBBLE)));
		assertEquals(List.of(), problems(new Goal.BuildPattern(Pattern.PILLAR, COBBLE, 3), List.of("cobblestone"),
				null, "Build a structure — read the Korean to see what.", CLEAN_DE));
	}

	@Test
	void inFrontAndBehindNeedAnAnchorWithAFront() {
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.IN_FRONT_OF, "minecraft:chest", 1)));
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.BEHIND, DOORS, 1)));
		for (Relation relation : new Relation[]{Relation.IN_FRONT_OF, Relation.BEHIND}) {
			for (String plain : new String[]{COBBLE, "minecraft:oak_fence", "#minecraft:planks", TORCH}) {
				List<String> found = problems(new Goal.PlaceRel(TORCH, relation, plain, 1));
				assertTrue(found.stream().anyMatch(p -> p.contains("no clear front")), relation + " " + plain + ": " + found);
			}
		}
		assertFalse(problems(new Goal.PlaceOnPattern(TORCH, Relation.BEHIND, Pattern.PILLAR, COBBLE)).isEmpty());
	}

	@Test
	void insideAndOutsideNeedSomethingThatEncloses() {
		assertEquals(List.of(), problems(new Goal.PlaceRel(TORCH, Relation.INSIDE, "minecraft:oak_fence", 1)));
		assertFalse(problems(new Goal.PlaceRel(TORCH, Relation.INSIDE, "minecraft:chest", 1)).isEmpty());
		assertFalse(problems(new Goal.PlaceRel(TORCH, Relation.OUTSIDE, DOORS, 1)).isEmpty());
		assertFalse(problems(new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.PYRAMID, COBBLE)).isEmpty());
		assertFalse(problems(new Goal.PlaceOnPattern(TORCH, Relation.OUTSIDE, Pattern.PILLAR, COBBLE)).isEmpty());
		assertFalse(problems(new Goal.PlaceOnPattern(TORCH, Relation.BETWEEN, Pattern.ROW, COBBLE)).isEmpty());
	}

	@Test
	void theTranslationMustNotGiveTheRelationAway() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		for (String giveaway : new String[]{"Place a torch next to the door.", "Put a torch beside the door."}) {
			List<String> found = problems(goal, List.of("torch"), null, giveaway, CLEAN_DE);
			assertTrue(found.stream().anyMatch(p -> p.contains("gives the relation away")), giveaway + ": " + found);
		}
		for (String giveaway : new String[]{"Platziere eine Fackel neben der Tür.", "Platziere eine Fackel daneben."}) {
			List<String> found = problems(goal, List.of("torch"), null, CLEAN_EN, giveaway);
			assertTrue(found.stream().anyMatch(p -> p.contains("gives the relation away")), giveaway + ": " + found);
		}
		// every relation has its words: a translation gives away the relation of its own step only
		Object[][] others = {
				{Relation.ON_TOP_OF, "Place a torch on top of it.", "Platziere eine Fackel auf der Tür."},
				{Relation.UNDER, "Place a torch under it.", "Platziere eine Fackel unter der Treppe."},
				{Relation.IN_FRONT_OF, "Place a torch in front of the door.", "Platziere eine Fackel vor der Tür."},
				{Relation.BEHIND, "Place a torch behind the chest.", "Platziere eine Fackel hinter der Truhe."},
				{Relation.BETWEEN, "Place a torch between two torches.", "Platziere eine Fackel zwischen zwei Fackeln."},
				{Relation.NEAR, "Place a torch near the door.", "Platziere eine Fackel in der Nähe."},
				{Relation.OUTSIDE, "Place a torch outside.", "Platziere eine Fackel draußen."}};
		for (Object[] row : others) {
			Relation relation = (Relation) row[0];
			String anchor = relation == Relation.OUTSIDE ? "minecraft:oak_fence" : DOORS;
			Goal other = new Goal.PlaceRel(TORCH, relation, relation == Relation.IN_FRONT_OF || relation == Relation.BEHIND
					? DOORS : anchor, 1);
			assertTrue(problems(other, List.of("torch"), null, (String) row[1], CLEAN_DE).stream()
					.anyMatch(p -> p.contains("gives the relation away")), relation + " en");
			assertTrue(problems(other, List.of("torch"), null, CLEAN_EN, (String) row[2]).stream()
					.anyMatch(p -> p.contains("gives the relation away")), relation + " de");
			// ... and the words of another relation say nothing about this one
			assertNull(io.github.leks2205.hangulquest.quest.spatial.RelationWords.leak((String) row[1],
					relation == Relation.NEXT_TO ? Relation.UNDER : Relation.NEXT_TO, List.of()), relation + " en other");
		}
		assertNull(SpatialRules.leak(CLEAN_EN));
		assertNull(SpatialRules.leak(CLEAN_DE));
		assertNull(SpatialRules.leak("Place a torch at night — read the Korean to see where."));
		// without a relation every relation's words are looked for
		assertEquals("on top", SpatialRules.leak("Place a torch on top of it."));
	}

	@Test
	void theKoreanMustNameTheRelation() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		assertEquals(List.of(), problems(goal, List.of("torch"), "문 옆에 횃불을 놓아요.", CLEAN_EN, CLEAN_DE));
		List<String> found = problems(goal, List.of("torch"), "문 위에 횃불을 놓아요.", CLEAN_EN, CLEAN_DE);
		assertTrue(found.stream().anyMatch(p -> p.contains("does not name the relation")), found.toString());
		found = problems(goal, List.of("torch"), "횃불을 놓아요.", CLEAN_EN, CLEAN_DE);
		assertTrue(found.stream().anyMatch(p -> p.contains("does not name the relation")), found.toString());
	}

	@Test
	void theTaughtWordAndTheNamesMustFit() {
		Goal goal = new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 1);
		List<String> found = problems(goal, List.of("chest"), null, CLEAN_EN, CLEAN_DE);
		assertTrue(found.stream().anyMatch(p -> p.contains("does not describe the goal")), found.toString());
		found = problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "mod:unnamed_thing", 1));
		assertTrue(found.stream().anyMatch(p -> p.contains("has no Korean name")), found.toString());
		found = problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "#minecraft:nonsense", 1));
		assertTrue(found.stream().anyMatch(p -> p.contains("bad anchor")), found.toString());
		found = problems(new Goal.PlaceRel("not an id", Relation.NEXT_TO, DOORS, 1));
		assertFalse(found.isEmpty());
		found = problems(new Goal.PlaceRel(TORCH, Relation.NEXT_TO, DOORS, 0));
		assertTrue(found.stream().anyMatch(p -> p.contains("count")), found.toString());
	}

	@Test
	void patternSizesMustFitTheSevenCube() {
		for (int size : new int[]{1, 5, 9}) {
			assertFalse(problems(new Goal.BuildPattern(Pattern.PILLAR, COBBLE, size), List.of("cobblestone"), null,
					CLEAN_EN, CLEAN_DE).isEmpty(), "pillar " + size);
		}
		assertFalse(problems(new Goal.BuildPattern(Pattern.PYRAMID, COBBLE, 5), List.of("cobblestone"), null,
				CLEAN_EN, CLEAN_DE).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.RING, COBBLE, 4), List.of("cobblestone"), null,
				CLEAN_EN, CLEAN_DE).isEmpty());
		assertEquals(List.of(), problems(new Goal.BuildPattern(Pattern.ROW, COBBLE, 4), List.of("cobblestone"), null,
				CLEAN_EN, CLEAN_DE));
		assertFalse(problems(new Goal.BuildPattern(Pattern.PILLAR, "#minecraft:nonsense", 3), List.of(), null,
				CLEAN_EN, CLEAN_DE).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.PILLAR, "minecraft:stone", 3), List.of("cobblestone"), null,
				CLEAN_EN, CLEAN_DE).isEmpty(), "the word must name the material");
	}

	@Test
	void netherMaterialsAreOutOfReachBeforeLevelThree() {
		assertFalse(SpatialRules.reachable("minecraft:netherrack", 1));
		assertFalse(SpatialRules.reachable("minecraft:crimson_planks", 2));
		assertFalse(SpatialRules.reachable("minecraft:blackstone", 1));
		assertFalse(SpatialRules.reachable("minecraft:end_stone", 2));
		assertTrue(SpatialRules.reachable("minecraft:netherrack", 3));
		assertTrue(SpatialRules.reachable("minecraft:cobblestone", 1));
		assertTrue(SpatialRules.reachable("#minecraft:planks", 1));

		String trail = """
				{"id":"far_away","titleKo":"먼 길","titleDe":"x","koreanLevel":%d,"concepts":["torch"],
				"steps":[{"id":"s1","teach":["torch"],"goal":{"type":"BUILD_PATTERN","pattern":"PILLAR","material":"minecraft:netherrack","size":3}}],
				"finale":{"step":{"id":"finale","teach":["torch"],"goal":{"type":"PLACE_ON_PATTERN","block":"minecraft:torch","relation":"ON_TOP_OF","pattern":"PILLAR","material":"minecraft:netherrack"}}}}
				""";
		Questline early = QuestLoader.parse(JsonParser.parseString(trail.formatted(2)).getAsJsonObject());
		assertEquals(2, SpatialRules.reachabilityProblems(early).size());
		Questline later = QuestLoader.parse(JsonParser.parseString(trail.formatted(3)).getAsJsonObject());
		assertEquals(List.of(), SpatialRules.reachabilityProblems(later));
	}

	@Test
	void everyShippedTrailKeepsItsSpatialMaterialsInReach() {
		List<String> problems = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			problems.addAll(SpatialRules.reachabilityProblems(trail));
		}
		assertEquals(List.of(), problems);
	}

	// ------------------------------------------------------------------ demo trail

	private Questline demo() {
		return QuestLoader.loadAll().stream().filter(t -> t.id().equals("lighthouse_demo")).findFirst().orElseThrow();
	}

	@Test
	void theLighthouseDemoUsesOnlyTheNewTypesAndIsMarkedAsADemo() throws IOException {
		Questline trail = demo();
		assertEquals(1, trail.koreanLevel());
		assertEquals("등대", trail.titleKo());
		assertEquals(4, trail.steps().size(), "four steps and the finale");
		Set<Class<?>> allowed = Set.of(Goal.PlaceRel.class, Goal.BuildPattern.class, Goal.PlaceOnPattern.class);
		Set<String> signatures = new HashSet<>();
		for (QuestStep step : trail.allSteps()) {
			assertTrue(allowed.contains(step.goal().getClass()), step.id() + " " + step.goal());
			assertTrue(signatures.add(signature(step.goal())), step.id() + " repeats a task");
		}
		try (InputStream stream = QuestLoader.class.getResourceAsStream(QuestLoader.BASE + "lighthouse_demo.json");
			 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			assertTrue(JsonParser.parseReader(reader).getAsJsonObject().get("demo").getAsBoolean());
		}
	}

	@Test
	void theLighthouseDemoValidatesAndSpeaksTheExpectedKorean() {
		Questline trail = demo();
		QuestValidator.Result result = QuestValidator.validate(List.of(trail), catalog, LOG);
		assertEquals(List.of(), result.problems());
		assertEquals(List.of(), SpatialRules.reachabilityProblems(trail));

		SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);
		List<String> spoken = new ArrayList<>();
		for (QuestStep step : trail.allSteps()) {
			Word word = catalog.byConceptId(step.teach().get(0)).orElseThrow();
			spoken.add(SentenceBuilder.normal(step.goal(), word, step.conditions(), trail.koreanLevel(),
					step.id().equals("finale"), namer));
		}
		assertEquals(List.of("조약돌 세 개로 기둥을 쌓아요.", "조약돌 기둥 위에 횃불을 놓아요.", "문 옆에 횃불을 놓아요.",
				"조약돌로 담을 쌓아요.", "밤에 조약돌 담 안에 횃불을 놓아요."), spoken);
		assertEquals(KoreanFixtures.ceremony(trail), trail.finaleKo(), "the finale line is the generated ceremony");
		for (QuestStep step : trail.allSteps()) {
			assertNull(SpatialRules.leak(step.clueEn()), step.id());
			assertNull(SpatialRules.leak(step.clueDe()), step.id());
		}
	}

	private static final class Feedback implements QuestEngine.Feedback {
		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void banner(String korean, String german, boolean side) {
		}

		@Override
		public void chooseNext(List<Questline> options) {
		}
	}

	/** Feeds the same observation for a few ticks, like the game does while the player stands there. */
	private static long feed(QuestEngine engine, Observation obs, long now, Feedback feedback) {
		for (int tick = 0; tick < 5; tick++) {
			now += 60L;
			obs.now = now;
			engine.tick(obs, feedback);
		}
		return now;
	}

	@Test
	void theLighthouseDemoOnlyAdvancesOnTheRightPlacements() {
		Questline trail = demo();
		ProgressStore store = new ProgressStore(tempDir.resolve("progress"), LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn, LOG);
		Feedback feedback = new Feedback();
		Observation start = new Observation();
		start.now = 10_000L;
		engine.startTrail(trail.id(), start, feedback);

		long now = 10_100L;
		// s1: two cobblestones are not a pillar of three
		now = feed(engine, observing(new World().put(100, 63, 100, COBBLE).put(100, 64, 100, COBBLE)
				.placed(new BlockPos(100, 64, 100))), now, feedback);
		assertEquals("s1", engine.currentStep().id());
		now = feed(engine, observing(SpatialWorlds.placementFor(trail.steps().get(0).goal(), false)), now, feedback);
		assertEquals("s2", engine.currentStep().id());

		// s2: the torch under the pillar is the wrong relation: no progress, and the status says what 위 means
		PlacementFact under = new World().put(100, 64, 100, TORCH).put(100, 65, 100, COBBLE).put(100, 66, 100, COBBLE)
				.put(100, 67, 100, COBBLE).placed(new BlockPos(100, 64, 100));
		now = feed(engine, observing(under), now, feedback);
		assertEquals("s2", engine.currentStep().id());
		assertEquals("위 = on top · 다시 해 봐요", engine.statusText());
		now = feed(engine, observing(SpatialWorlds.placementFor(engine.currentStep().goal(), false)), now, feedback);
		assertEquals("s3", engine.currentStep().id());

		// s3 and s4
		for (String expected : new String[]{"s4", "finale"}) {
			now = feed(engine, observing(SpatialWorlds.placementFor(engine.currentStep().goal(), false)), now, feedback);
			assertEquals(expected, engine.currentStep().id());
		}
		// the finale is a night task: a torch placed by day in the ring does not finish it
		now = feed(engine, observing(SpatialWorlds.placementFor(engine.currentStep().goal(), false)), now, feedback);
		assertEquals("finale", engine.currentStep().id());
		Observation night = observing(SpatialWorlds.placementFor(engine.currentStep().goal(), true));
		night.clockTime = 14_000L;
		feed(engine, night, now, feedback);
		assertTrue(engine.activeTrail() == null && engine.currentStep() == null, "trail finished");
	}
}
