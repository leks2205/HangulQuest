package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Colours;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.DecidingForms;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A colour is said only where the game checks it: a taught general word leaves the colour of a coloured block out
 * below level 3 (any colour of the family does), and from level 3 the clue names it and the check requires that very
 * colour, for the object, an anchor, a held item and a sleeping bed. A dye is its colour, at every level.
 */
class ColourStrictTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("colour-strict-test");
	private static final String RED = "minecraft:red_bed";
	private static final String BLUE = "minecraft:blue_bed";

	@TempDir
	Path tempDir;

	private int serial;

	private static final class Quiet implements QuestEngine.Feedback {

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void banner(String korean, String german, boolean side) {
		}
	}

	/** An engine with one trail of {@code level} whose first step has {@code goal} and teaches the concept. */
	private QuestEngine engine(int level, boolean knowsTheLadder, Goal goal, List<Condition> conditions, String concept) {
		Catalog catalog = KoreanFixtures.catalog();
		QuestStep first = new QuestStep("s1", List.of(concept), goal, conditions, null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of(concept), new Goal.BreakBlock("minecraft:stone", 1, null),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("t", "길", "Pfad", 1, List.of(concept), Questline.Requires.none(),
				List.of(first), finale, "끝났어요.", "Fertig.", "t-badge", 10, level, false);
		ProgressStore store = new ProgressStore(tempDir.resolve("p" + serial++), LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		if (knowsTheLadder) {
			learn.grammar().unlockAll();
		}
		return new QuestEngine(List.of(trail), catalog, learn, LOG);
	}

	private static Observation placed(String... blocks) {
		Observation obs = new Observation();
		obs.now = 1_000L;
		obs.dimension = "minecraft:overworld";
		obs.placedBlocks.addAll(List.of(blocks));
		return obs;
	}

	private static boolean completes(QuestEngine engine, Observation obs) {
		Observation start = new Observation();
		start.now = 500L;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, new Quiet());
		engine.tick(obs, new Quiet());
		obs.now += 100L; // a dwell needs a second tick
		engine.tick(obs, new Quiet());
		return "finale".equals(engine.currentStep().id());
	}

	private static Goal bed(String id) {
		return new Goal.PlaceBlock(id, 1);
	}

	private String clue(QuestEngine engine) {
		Observation start = new Observation();
		start.now = 500L;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, new Quiet());
		return engine.clueKo(engine.currentStep());
	}

	// ---------------------------------------------------------------- the clue and the check agree

	@Test
	void levelTwoLeavesTheColourOutAndAnyBedCompletes() {
		QuestEngine engine = engine(2, true, bed(RED), List.of(), "bed");
		assertEquals("침대를 놓으세요.", clue(engine));
		assertTrue(completes(engine(2, true, bed(RED), List.of(), "bed"), placed(BLUE)), "no colour is said, none is checked");
	}

	@Test
	void levelThreeSaysTheColourAndOnlyThatBedCompletes() {
		assertEquals("빨간색 침대를 놓으세요.", clue(engine(3, true, bed(RED), List.of(), "bed")));
		assertFalse(completes(engine(3, true, bed(RED), List.of(), "bed"), placed(BLUE)), "a blue bed is not the red one");
		assertTrue(completes(engine(3, true, bed(RED), List.of(), "bed"), placed(RED)));
	}

	@Test
	void levelFiveSaysTheAdjectiveAndChecksTheSameColour() {
		assertEquals("빨간 침대를 놓아야 해요.", clue(engine(5, true, bed(RED), List.of(), "bed")));
		assertFalse(completes(engine(5, true, bed(RED), List.of(), "bed"), placed(BLUE)));
		assertTrue(completes(engine(5, true, bed(RED), List.of(), "bed"), placed(RED)));
	}

	@Test
	void aPlayerWhoReadsTheEasierClueIsNotCheckedOnAColourTheClueLeftOut() {
		// a new player on a level-5 trail reads the level-1 sentence
		QuestEngine fresh = engine(5, false, bed(RED), List.of(), "bed");
		assertEquals("침대를 놓아요.", clue(fresh));
		assertTrue(completes(engine(5, false, bed(RED), List.of(), "bed"), placed(BLUE)));
	}

	@Test
	void everyCheckedKindFollowsTheClue() {
		// break
		assertFalse(completes(engine(4, true, new Goal.BreakBlock("minecraft:red_wool", 1, null), List.of(), "wool"),
				brokenBy("minecraft:blue_wool")));
		assertTrue(completes(engine(4, true, new Goal.BreakBlock("minecraft:red_wool", 1, null), List.of(), "wool"),
				brokenBy("minecraft:red_wool")));
		assertTrue(completes(engine(2, true, new Goal.BreakBlock("minecraft:red_wool", 1, null), List.of(), "wool"),
				brokenBy("minecraft:blue_wool")), "below level 3 any wool");
		// lie down in a bed
		assertFalse(completes(engine(3, true, new Goal.LieDown(RED, 0), List.of(), "bed"), sleeping(BLUE)));
		assertTrue(completes(engine(3, true, new Goal.LieDown(RED, 0), List.of(), "bed"), sleeping(RED)));
		assertTrue(completes(engine(1, true, new Goal.LieDown(RED, 0), List.of(), "bed"), sleeping(BLUE)));
		// use (sleep in) a bed
		assertFalse(completes(engine(3, true, new Goal.UseBlock(RED), List.of(), "bed"), sleeping(BLUE)));
		assertTrue(completes(engine(3, true, new Goal.UseBlock(RED), List.of(), "bed"), sleeping(RED)));
	}

	private static Observation brokenBy(String block) {
		Observation obs = placed();
		obs.brokenBlocks.add(block);
		return obs;
	}

	private static Observation sleeping(String bed) {
		Observation obs = placed();
		obs.sleeping = true;
		obs.sleepingBed = bed;
		return obs;
	}

	@Test
	void anAnchorKeepsItsColourFromLevelFive() {
		Goal.PlaceRel byBlue = new Goal.PlaceRel("minecraft:soul_torch", Relation.NEXT_TO, BLUE, 1);
		Goal.PlaceRel byRed = new Goal.PlaceRel("minecraft:soul_torch", Relation.NEXT_TO, RED, 1);
		assertEquals("파란 침대 옆에 영혼 횃불을 놓아야 해요.", clue(engine(5, true, byBlue, List.of(), "bed")));
		Observation beside = new Observation();
		beside.now = 1_000L;
		beside.dimension = "minecraft:overworld";
		beside.addPlacement(SpatialWorlds.placementFor(byRed, false));
		assertFalse(completes(engine(5, true, byBlue, List.of(), "bed"), beside), "a torch by a red bed");
		Observation right = new Observation();
		right.now = 1_000L;
		right.dimension = "minecraft:overworld";
		right.addPlacement(SpatialWorlds.placementFor(byBlue, false));
		assertTrue(completes(engine(5, true, byBlue, List.of(), "bed"), right));
		assertTrue(completes(engine(2, true, byBlue, List.of(), "bed"), beside), "level 2 does not name the bed's colour");
	}

	@Test
	void aHeldItemOfAConditionKeepsItsColour() {
		List<Condition> holding = List.of(new Condition.Holding("minecraft:red_carpet"));
		Goal goal = new Goal.PlaceBlock("minecraft:stone", 1);
		Observation wrong = placed("minecraft:stone");
		wrong.mainHand = "minecraft:blue_carpet";
		Observation right = placed("minecraft:stone");
		right.mainHand = "minecraft:red_carpet";
		assertFalse(completes(engine(4, true, goal, holding, "carpet"), wrong));
		assertTrue(completes(engine(4, true, goal, holding, "carpet"), right));
	}

	@Test
	void aDyeIsItsColourAtEveryLevelAndNeverMatchesAnother() {
		Goal dye = new Goal.AcquireItem("minecraft:red_dye", 1);
		for (int level : new int[]{1, 2, 3}) {
			QuestEngine engine = engine(level, true, dye, List.of(), "dye");
			assertTrue(clue(engine).startsWith("빨간색 염료"), "level " + level + ": " + clue(engine));
			Observation blue = placed();
			blue.inventory.put("minecraft:blue_dye", 1);
			assertFalse(completes(engine(level, true, dye, List.of(), "dye"), blue), "level " + level);
		}
	}

	@Test
	void aCraftedIngredientsKeepTheirColourInTheSentence() {
		Goal.Craft purple = new Goal.Craft("minecraft:purple_dye", 1, null, List.of("minecraft:blue_dye", "minecraft:red_dye"));
		assertEquals("파란색 염료와 빨간색 염료로 보라색 염료를 만드세요.",
				clue(engine(2, true, purple, List.of(), "dye")));
		assertEquals("파란 염료와 빨간 염료로 보라색 염료를 만들어야 해요.",
				clue(engine(6, true, purple, List.of(), "gen_purple_dye")));
		Observation wrong = placed();
		wrong.addCrafted(new CraftFact("minecraft:orange_dye", 1, "crafting"));
		assertFalse(completes(engine(2, true, purple, List.of(), "dye"), wrong), "another dye is another dye");
	}

	@Test
	void progressMadeBeforeIsKept() {
		// two beds are to be placed; the first one (any colour, under the old rule) is already counted
		Goal two = new Goal.PlaceBlock(RED, 2);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = Accepted.NONE;
		GoalEvaluator.tick(two, List.of(), placed(BLUE), tracker);
		assertEquals(1, tracker.count);
		tracker.accepted = Accepted.NONE.withExact(Set.of(RED));
		GoalEvaluator.tick(two, List.of(), placed(BLUE), tracker);
		assertEquals(1, tracker.count, "the rule only judges new events");
		assertTrue(GoalEvaluator.tick(two, List.of(), placed(RED), tracker));
	}

	@Test
	void anExactSpecIsOnlyThatBlock() {
		assertTrue(io.github.leks2205.hangulquest.quest.spatial.BlockSpec.matches("=" + RED, RED));
		assertFalse(io.github.leks2205.hangulquest.quest.spatial.BlockSpec.matches("=" + RED, BLUE));
		assertTrue(io.github.leks2205.hangulquest.quest.spatial.BlockSpec.matches(RED, BLUE), "a bare id is its family");
		assertEquals(RED, io.github.leks2205.hangulquest.quest.spatial.BlockSpec.representative("=" + RED));
	}

	// ---------------------------------------------------------------- the content

	/** Every coloured thing a goal points at, as {@code kind:id}. */
	private static List<String> colouredMentions(QuestStep step) {
		List<String> ids = new ArrayList<>();
		for (GoalType.Mention mention : GoalTypes.of(step.goal()).mentions(step.goal())) {
			if ((mention.kind() == ObjectKind.BLOCK || mention.kind() == ObjectKind.ITEM)
					&& Colours.isColoured(mention.id()) && !ids.contains(mention.id())) {
				ids.add(mention.id());
			}
		}
		for (Condition condition : step.conditions()) {
			for (GoalType.Mention mention : io.github.leks2205.hangulquest.quest.condition.ConditionTypes.of(condition)
					.mentions(condition)) {
				if ((mention.kind() == ObjectKind.BLOCK || mention.kind() == ObjectKind.ITEM)
						&& Colours.isColoured(mention.id()) && !ids.contains(mention.id())) {
					ids.add(mention.id());
				}
			}
		}
		return ids;
	}

	@Test
	void everyTrailSaysTheColourItChecksAndItsHintAgrees() {
		Catalog catalog = KoreanFixtures.catalog();
		List<Questline> trails = QuestLoader.loadAll();
		ProgressStore store = new ProgressStore(tempDir.resolve("all"), LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.grammar().unlockAll();
		QuestEngine engine = new QuestEngine(trails, catalog, learn, LOG);
		Observation start = new Observation();
		start.now = 1000L;
		start.dimension = "minecraft:overworld";
		List<String> wrong = new ArrayList<>();
		int seen = 0;
		for (Questline trail : trails) {
			engine.startTrail(trail.id(), start, new Quiet());
			for (QuestStep step : trail.allSteps()) {
				for (String id : colouredMentions(step)) {
					seen++;
					String clue = engine.clueKo(step);
					String hint = engine.hintKo(step);
					boolean clueSays = Colours.saysColourOf(clue, id);
					boolean hintSays = Colours.saysColourOf(hint, id);
					if (clueSays != hintSays) {
						wrong.add(trail.id() + "/" + step.id() + " " + id + ": clue \"" + clue + "\" hint \"" + hint + "\"");
					}
					if (trail.koreanLevel() >= 3 && !clueSays && !step.hasClueKo()) {
						wrong.add(trail.id() + "/" + step.id() + " " + id + ": level " + trail.koreanLevel()
								+ " clue leaves the colour out: " + clue);
					}
				}
			}
		}
		assertTrue(seen > 50, "the content has coloured goals: " + seen);
		assertTrue(wrong.isEmpty(), String.join("\n", wrong));
	}

	@Test
	void theAdjectiveDecidesOnlyWhereTheColourIsChecked() {
		QuestStep red = new QuestStep("s1", List.of("bed"), bed(RED), List.of(), null, null, null, null, null);
		assertEquals(List.of("L5.adn_adj_eun"), DecidingForms.ofColour(red, List.of("L5.adn_adj_eun", "L1.eul_reul")));
		assertEquals(List.of(), DecidingForms.ofColour(red, List.of("L1.eul_reul")), "no adjective spoken");
		QuestStep stone = new QuestStep("s1", List.of("stone"), new Goal.PlaceBlock("minecraft:stone", 1), List.of(), null,
				null, null, null, null);
		assertEquals(List.of(), DecidingForms.ofColour(stone, List.of("L5.adn_adj_eun")), "nothing coloured is checked");
	}
}
