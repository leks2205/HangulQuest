package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator wave 13 (the last generator round before 3.0.0): a slip against a colour adjective, and the other items
 * of the wave. Each section names its item of content/briefs.
 */
class GeneratorWaveThirteenTest {
	private static final Logger LOG = LoggerFactory.getLogger("wave13");
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final String RED = "minecraft:red_bed";
	private static final String BLUE = "minecraft:blue_bed";

	@TempDir
	Path tempDir;

	private int serial;

	private static final class Quiet implements QuestEngine.Feedback {
		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void banner(String korean, String german, boolean side) {
		}
	}

	private static Observation placed(String... blocks) {
		Observation obs = new Observation();
		obs.now = 1_000L;
		obs.dimension = "minecraft:overworld";
		obs.placedBlocks.addAll(List.of(blocks));
		return obs;
	}

	// ------------------------------------------------------------ 6. a slip against the colour adjective

	@Test
	void i6_aWrongColourIsASlipWhereTheClueNamesTheColourButDoesNotCount() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = Accepted.NONE.withExact(Set.of(RED));
		assertFalse(GoalEvaluator.tick(new Goal.PlaceBlock(RED, 1), List.of(), placed(BLUE), tracker));
		assertEquals(1, tracker.colourSlips);
		assertEquals(0, tracker.count);
		assertEquals(0, tracker.formErrors, "a colour is not the form of the goal");
		assertEquals(0, tracker.conditionSlips);
	}

	@Test
	void i6_theRightColourOrNoColourInTheClueIsNoSlip() {
		GoalEvaluator.Tracker right = new GoalEvaluator.Tracker();
		right.accepted = Accepted.NONE.withExact(Set.of(RED));
		assertTrue(GoalEvaluator.tick(new Goal.PlaceBlock(RED, 1), List.of(), placed(RED), right));
		assertEquals(0, right.colourSlips);
		GoalEvaluator.Tracker unnamed = new GoalEvaluator.Tracker();
		assertTrue(GoalEvaluator.tick(new Goal.PlaceBlock(RED, 1), List.of(), placed(BLUE), unnamed),
				"the clue left the colour out, so any bed does");
		assertEquals(0, unnamed.colourSlips);
		GoalEvaluator.Tracker nothing = new GoalEvaluator.Tracker();
		nothing.accepted = Accepted.NONE.withExact(Set.of(RED));
		GoalEvaluator.tick(new Goal.PlaceBlock(RED, 1), List.of(), placed(), nothing);
		assertEquals(0, nothing.colourSlips, "nothing was done");
	}

	@Test
	void i6_anotherBlockIsNoColourSlip() {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = Accepted.NONE.withExact(Set.of(RED));
		GoalEvaluator.tick(new Goal.PlaceBlock(RED, 1), List.of(), placed("minecraft:stone"), tracker);
		assertEquals(0, tracker.colourSlips);
	}

	private QuestEngine engine(int level, boolean knowsTheLadder, Goal goal) {
		QuestStep first = new QuestStep("s1", List.of("bed"), goal, List.of(), null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("bed"), new Goal.BreakBlock("minecraft:stone", 1, null),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("t", "길", "Pfad", 1, List.of("bed"), Questline.Requires.none(),
				List.of(first), finale, "끝났어요.", "Fertig.", "t-badge", 10, level, false);
		ProgressStore store = new ProgressStore(tempDir.resolve("p" + serial++), LOG);
		LearningService learn = new LearningService(store.data(), store, CATALOG);
		if (knowsTheLadder) {
			learn.grammar().unlockAll();
		}
		learns.add(learn);
		return new QuestEngine(List.of(trail), CATALOG, learn, LOG);
	}

	private final java.util.List<LearningService> learns = new java.util.ArrayList<>();

	private int slips(int level, boolean knowsTheLadder) {
		QuestEngine engine = engine(level, knowsTheLadder, new Goal.PlaceBlock(RED, 1));
		Observation start = new Observation();
		start.now = 500L;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, new Quiet());
		for (int i = 0; i < 6; i++) {
			Observation obs = placed(BLUE);
			obs.now = 1_000L + i * 100L;
			engine.tick(obs, new Quiet());
		}
		return learns.get(learns.size() - 1).grammar().answers("L5.adn_adj_eun");
	}

	@Test
	void i6_theLearningSideHearsOfItOnlyWhereTheAdjectiveIsWhatThePlayerRead() {
		assertEquals(3, slips(5, true), "level 5 reads 빨간 침대: three slips at most");
		assertEquals(0, slips(5, false), "a new player reads the level-1 sentence, which names no colour");
		assertEquals(0, slips(3, true), "level 3 says 빨간색 침대, a noun: the adjective was not read");
	}

	// ------------------------------------------------------------ 7. the stored finale and the colour the clue left out

	private static final class Captured implements QuestEngine.Feedback {
		String finale;

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
		public void trailCompleted(Questline trail, String finaleKo) {
			finale = finaleKo;
		}
	}

	/** A level-5 trail with one step that places a red bed; {@code stored} is the finale line it ships with. */
	private String finaleShown(boolean knowsTheLadder, String stored) {
		QuestStep finale = new QuestStep("finale", List.of("bed"), new Goal.PlaceBlock(RED, 1), List.of(), null, null,
				null, null, null);
		Questline trail = new Questline("t", "길", "Pfad", 1, List.of("bed"), Questline.Requires.none(),
				List.of(), finale, stored == null ? KoreanFixtures.ceremony(finale, 5) : stored, "Fertig.", "t-badge",
				10, 5, false);
		ProgressStore store = new ProgressStore(tempDir.resolve("f" + serial++), LOG);
		LearningService learn = new LearningService(store.data(), store, CATALOG);
		if (knowsTheLadder) {
			learn.grammar().unlockAll();
		}
		QuestEngine engine = new QuestEngine(List.of(trail), CATALOG, learn, LOG);
		Captured feedback = new Captured();
		Observation start = new Observation();
		start.now = 500L;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, feedback);
		engine.tick(placed(RED), feedback);
		assertTrue(feedback.finale != null, "the trail finished");
		return feedback.finale;
	}

	@Test
	void i7_aFinaleStoredAtTrailLevelDoesNotNameAColourTheLearnersClueLeftOut() {
		String stored = KoreanFixtures.ceremony(new QuestStep("finale", List.of("bed"), new Goal.PlaceBlock(RED, 1),
				List.of(), null, null, null, null, null), 5);
		assertEquals("빨간색 침대를 놓았어요.", stored);
		assertEquals("빨간색 침대를 놓았어요.", finaleShown(true, null), "the learner who read the colour is told it");
		assertEquals("침대를 놓았어요.", finaleShown(false, null), "the clue left the colour out, so does the finale");
	}

	@Test
	void i7_anAuthoredFinaleIsNeverRewritten() {
		assertEquals("침대는 포근해요. 빨간색 침대를 놓았어요.", finaleShown(false, "침대는 포근해요. 빨간색 침대를 놓았어요."));
	}
}
