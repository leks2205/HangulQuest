package io.github.leks2205.hangulquest;

import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WALL, CROSS and STAIRS as goals: built through the goal type, the status line, the Korean, the JSON, the sizes. */
class NewShapeGoalsTest {
	private static final String STONE = "minecraft:stone";

	private final Catalog catalog = KoreanFixtures.catalog();
	private final SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);

	private static Observation observing(PlacementFact fact) {
		Observation obs = new Observation();
		obs.now = 50_000L;
		obs.clockTime = 1000L;
		obs.addPlacement(fact);
		return obs;
	}

	private static final Goal[] BUILDS = {new Goal.BuildPattern(Pattern.WALL, STONE, 2),
			new Goal.BuildPattern(Pattern.WALL, STONE, 3), new Goal.BuildPattern(Pattern.WALL, STONE, 4),
			new Goal.BuildPattern(Pattern.CROSS, "minecraft:glass", 3),
			new Goal.BuildPattern(Pattern.STAIRS, "minecraft:dirt", 3),
			new Goal.BuildPattern(Pattern.STAIRS, "minecraft:cobblestone", 4)};

	@Test
	void everyNewShapeCanBeBuiltThroughTheGoal() {
		for (Goal goal : BUILDS) {
			assertTrue(GoalEvaluator.tick(goal, List.of(), observing(SpatialWorlds.placementFor(goal, false)),
					new GoalEvaluator.Tracker()), goal.toString());
		}
	}

	@Test
	void everyNewShapeWorksAsTheStructureOfAPlacement() {
		for (Pattern pattern : new Pattern[]{Pattern.WALL, Pattern.CROSS, Pattern.STAIRS}) {
			for (Relation relation : new Relation[]{Relation.ON_TOP_OF, Relation.NEXT_TO, Relation.UNDER}) {
				Goal goal = new Goal.PlaceOnPattern("minecraft:torch", relation, pattern, STONE);
				assertTrue(GoalEvaluator.tick(goal, List.of(), observing(SpatialWorlds.placementFor(goal, false)),
						new GoalEvaluator.Tracker()), relation + " " + pattern);
			}
		}
	}

	// ----------------------------------------------------------------- status line

	private String status(Goal goal, PlacementFact fact) {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = observing(fact);
		GoalEvaluator.tick(goal, List.of(), obs, tracker);
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, null, null, null, null, null);
		return QuestStatus.text(step, obs, tracker, catalog);
	}

	@Test
	void theStatusLineCountsTheBlocksOfTheShapeThatStand() {
		// stairs of three: 1 + 2 + 3 = 6 blocks; four of them stand
		Goal stairs = new Goal.BuildPattern(Pattern.STAIRS, STONE, 3);
		World four = new World().put(100, 64, 100, STONE).put(101, 64, 100, STONE).put(101, 65, 100, STONE);
		assertEquals("4/6", status(stairs, four.put(102, 64, 100, STONE).placed(new BlockPos(102, 64, 100))));
		assertEquals("1/6", status(stairs, new World().put(100, 64, 100, STONE).placed(new BlockPos(100, 64, 100))));

		Goal wall = new Goal.BuildPattern(Pattern.WALL, STONE, 3);
		World row = new World().put(100, 64, 100, STONE).put(101, 64, 100, STONE).put(102, 64, 100, STONE)
				.put(100, 65, 100, STONE);
		assertEquals("4/6", status(wall, row.placed(new BlockPos(100, 65, 100))));

		Goal cross = new Goal.BuildPattern(Pattern.CROSS, STONE, 3);
		World arms = new World().put(101, 64, 100, STONE).put(100, 64, 101, STONE).put(102, 64, 101, STONE);
		assertEquals("4/5", status(cross, arms.put(101, 64, 101, STONE).placed(new BlockPos(101, 64, 101))),
				"four of five stand once the middle is in");
	}

	@Test
	void aFinishedShapeShowsAllItsBlocks() {
		for (Goal goal : BUILDS) {
			Goal.BuildPattern build = (Goal.BuildPattern) goal;
			int total = build.pattern().cells(build.size(), 0).size();
			assertEquals(total + "/" + total, status(goal, SpatialWorlds.placementFor(goal, false)), goal.toString());
		}
	}

	// ----------------------------------------------------------------------- Korean

	private String say(Goal goal, int level) {
		return SentenceBuilder.normal(goal, null, List.of(), level, false, namer);
	}

	@Test
	void theShapeNounsAreInTheSentenceAtEveryLevel() {
		for (int level = 1; level <= 6; level++) {
			assertTrue(say(new Goal.BuildPattern(Pattern.WALL, STONE, 3), level).contains("벽을"), "level " + level);
			assertTrue(say(new Goal.BuildPattern(Pattern.CROSS, STONE, 3), level).contains("십자 모양을"), "level " + level);
			assertTrue(say(new Goal.BuildPattern(Pattern.STAIRS, STONE, 3), level).contains("계단을"), "level " + level);
		}
		assertEquals("돌로 벽을 쌓아요.", say(BUILDS[1], 1));
		assertEquals("유리로 십자 모양을 만들어요.", say(BUILDS[3], 1));
		assertEquals("흙으로 계단을 만들어요.", say(BUILDS[4], 1));
		assertEquals("돌로 벽을 쌓았어요.", SentenceBuilder.ceremony(BUILDS[1], null, List.of(), namer));
	}

	@Test
	void theVerbFitsTheShape() {
		VerbTable table = VerbTable.shared();
		assertEquals(Verb.STACK, GoalTypes.of(BUILDS[0]).verb(BUILDS[0], table), "a wall is piled up: 쌓다");
		assertEquals(Verb.CRAFT, GoalTypes.of(BUILDS[3]).verb(BUILDS[3], table), "a plus is made: 만들다");
		assertEquals(Verb.CRAFT, GoalTypes.of(BUILDS[4]).verb(BUILDS[4], table), "stairs are made: 만들다");
	}

	// ----------------------------------------------------------------- JSON, tasks, sizes

	@Test
	void theShapesRoundTripThroughJson() {
		for (Goal goal : BUILDS) {
			assertEquals(goal, GoalTypes.parse(GoalTypes.toJson(goal)));
		}
		Goal onWall = new Goal.PlaceOnPattern("minecraft:torch", Relation.ON_TOP_OF, Pattern.STAIRS, STONE);
		assertEquals(onWall, GoalTypes.parse(GoalTypes.toJson(onWall)));
		Goal bare = GoalTypes.parse(JsonParser.parseString(
				"{\"type\":\"BUILD_PATTERN\",\"pattern\":\"wall\",\"material\":\"minecraft:stone\"}").getAsJsonObject());
		assertEquals(new Goal.BuildPattern(Pattern.WALL, STONE, 3), bare, "a missing size is the usual size");
	}

	@Test
	void everyShapeAndSizeIsAnotherTask() {
		Set<String> seen = new HashSet<>();
		for (Goal goal : BUILDS) {
			assertTrue(seen.add(GoalTypes.of(goal).taskSignature(goal)), goal.toString());
		}
		Goal cobbleWall = new Goal.BuildPattern(Pattern.WALL, "minecraft:cobblestone", 3);
		assertTrue(seen.add(GoalTypes.of(cobbleWall).taskSignature(cobbleWall)), "another material is another task");
	}

	private List<String> problems(Goal goal) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(), goal, List.of(), null, null, null, null, null, null, null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, catalog, problems));
		return problems;
	}

	@Test
	void sizesOutsideTheSevenCubeAreRejected() {
		assertEquals(List.of(), problems(BUILDS[0]));
		assertEquals(List.of(), problems(BUILDS[2]));
		assertEquals(List.of(), problems(BUILDS[3]));
		assertEquals(List.of(), problems(BUILDS[5]));
		assertFalse(problems(new Goal.BuildPattern(Pattern.WALL, STONE, 5)).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.WALL, STONE, 1)).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.CROSS, STONE, 5)).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.STAIRS, STONE, 2)).isEmpty());
		assertFalse(problems(new Goal.BuildPattern(Pattern.STAIRS, STONE, 5)).isEmpty());
	}
}
