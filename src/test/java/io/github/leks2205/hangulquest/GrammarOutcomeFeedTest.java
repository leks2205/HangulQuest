package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steps whose deciding form is a construction tell the grammar tracking how the player did: a right finish is
 * a right outcome for that construction (the relation word, 에게, the connective, 지 마세요), a slip in the form
 * is a wrong one. The outcomes reach the knowledge tracing of {@link GrammarProgress} and the review log.
 */
class GrammarOutcomeFeedTest {
	private static final Logger LOG = LoggerFactory.getLogger("grammar-outcomes");
	private static final String TORCH = "minecraft:torch";
	private static final String CHEST = "minecraft:chest";
	private static final String STONE = "minecraft:stone";
	private static final String SAND = "minecraft:sand";
	private static final String BEFORE_SLEEP = "L3.gi_jeone";
	private static final double PRIOR = GrammarProgress.P_L0;

	private final Catalog catalog = KoreanFixtures.catalog();

	@TempDir
	Path tempDir;

	private LearningService learn;
	private QuestEngine engine;
	private long now = 10_000L;

	private static final class Quiet implements QuestEngine.Feedback {
		@Override
		public void banner(String ko, String de, boolean success) {
		}

		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}
	}

	private final Quiet feedback = new Quiet();

	@BeforeEach
	void freshProfile() {
		ProgressStore store = new ProgressStore(tempDir, LOG);
		learn = new LearningService(store.data(), store, catalog);
	}

	private static QuestStep step(String id, Goal goal, String... teach) {
		return new QuestStep(id, List.of(teach), goal, List.of(), null, null, null, null, null, null, null);
	}

	private void play(int level, Goal first, Goal... more) {
		List<QuestStep> steps = new java.util.ArrayList<>(List.of(step("s1", first)));
		for (int i = 0; i < more.length; i++) {
			steps.add(step("s" + (i + 2), more[i]));
		}
		play(new Questline("probe", "시험", "Probe", 1, List.of(), null, steps, step("finale", new Goal.PlaceBlock(SAND, 1)),
				"끝", "Ende", null, 1, level, false));
	}

	private void play(Questline trail) {
		engine = new QuestEngine(List.of(trail), catalog, learn, LOG);
		Observation start = new Observation();
		start.now = now;
		engine.startTrail(trail.id(), start, feedback);
	}

	private void tick(Observation obs) {
		now += 100L;
		obs.now = now;
		obs.clockTime = 1000L;
		engine.tick(obs, feedback);
	}

	private static PlacementFact placement(String block, World world, int x, int y, int z) {
		return world.put(x, y, z, block).placed(new BlockPos(x, y, z));
	}

	private static Observation placing(PlacementFact... facts) {
		Observation obs = new Observation();
		for (PlacementFact fact : facts) {
			obs.addPlacement(fact);
			obs.placedBlocks.add(fact.block());
		}
		return obs;
	}

	/** A chest placed beside a door: the right answer to "문 옆에 상자를". */
	private static Observation chestBesideDoor() {
		return placing(placement(CHEST, new World().put(101, 64, 100, "minecraft:oak_door"), 100, 64, 100));
	}

	/** A chest placed on top of a door: the right block in the wrong relation. */
	private static Observation chestOnDoor() {
		return placing(placement(CHEST, new World().put(100, 63, 100, "minecraft:oak_door"), 100, 64, 100));
	}

	private static final Goal CHEST_BESIDE_DOOR = new Goal.PlaceRel(CHEST, Relation.NEXT_TO, "#minecraft:doors", 1);

	private GrammarProgress replayed() {
		learn.reviewLog().flush();
		return GrammarProgress.fromEvents(ReviewLog.readAll(tempDir));
	}

	// ------------------------------------------------------------------ right finishes

	@Test
	void aRelationDoneRightIsARightOutcomeForItsLocationNoun() {
		play(1, CHEST_BESIDE_DOOR);
		tick(chestBesideDoor());
		assertEquals("finale", engine.currentStep().id());
		GrammarProgress grammar = learn.grammar();
		assertEquals(1, grammar.answers("L1.loc_yeop_e"));
		assertEquals(GrammarProgress.update(PRIOR, true), grammar.pL("L1.loc_yeop_e"), 1e-9);
		assertEquals(0, grammar.answers("L1.loc_wi_e"), "the other location nouns are not touched");
		assertEquals(0, grammar.answers("L1.eoyo"), "only the form that decided the step is graded");
	}

	@Test
	void everyRelationFeedsItsOwnConstruction() {
		for (Relation relation : Relation.values()) {
			if (relation.grammarId() == null) {
				continue;
			}
			Goal goal = new Goal.PlaceRel(CHEST, relation, "minecraft:chest", 1);
			assertEquals(List.of(relation.grammarId()), GoalTypes.of(goal).decidingForms(goal), relation.name());
		}
		Goal near = new Goal.PlaceRel(CHEST, Relation.NEAR, "minecraft:chest", 1);
		assertEquals(List.of(), GoalTypes.of(near).decidingForms(near), "근처 has no construction of its own");
		Goal onPattern = new Goal.PlaceOnPattern(TORCH, Relation.ON_TOP_OF, Pattern.PILLAR, STONE);
		assertEquals(List.of("L1.loc_wi_e"), GoalTypes.of(onPattern).decidingForms(onPattern));
	}

	@Test
	void aRecipientIsDecidedByEge() {
		play(2, new Goal.Give("minecraft:bread", "minecraft:villager", null, 1));
		Observation gave = new Observation();
		gave.addGave(new GiveFact("minecraft:bread", 1, "minecraft:villager", UUID.randomUUID(), GiveFact.Method.DROP_NEAR));
		tick(gave);
		assertEquals(1, learn.grammar().answers("L2.ege"));
		assertEquals(GrammarProgress.update(PRIOR, true), learn.grammar().pL("L2.ege"), 1e-9);
	}

	@Test
	void anAvoidKeptIsARightOutcomeForJimaSeyo() {
		play(3, new Goal.Avoid(new Goal.PlaceBlock(TORCH, 1), 1, null));
		for (int i = 0; i < 40; i++) {
			tick(new Observation());
		}
		assertEquals("finale", engine.currentStep().id(), "the window ran out");
		assertEquals(1, learn.grammar().answers("L3.jima_seyo"), "L3.jima resolves to the construction in grammar.json");
		assertEquals(GrammarProgress.update(PRIOR, true), learn.grammar().pL("L3.jima_seyo"), 1e-9);
	}

	@Test
	void anOrderedSequenceDoneInOrderFeedsItsConnective() {
		Goal before = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), true, "BEFORE");
		play(3, before);
		tick(placing(placement(STONE, new World(), 100, 64, 100)));
		tick(placing(placement(TORCH, new World(), 102, 64, 100)));
		assertEquals("finale", engine.currentStep().id());
		assertEquals(1, learn.grammar().answers(BEFORE_SLEEP));
		assertEquals(GrammarProgress.update(PRIOR, true), learn.grammar().pL(BEFORE_SLEEP), 1e-9);
		// the other connectives are theirs alone
		Goal after = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), true, "AFTER");
		assertEquals(List.of("L3.eun_hue"), GoalTypes.of(after).decidingForms(after));
		Goal then = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), true, "THEN");
		assertEquals(List.of("L2.go_sequence"),
				GrammarBook.shared().resolve(GoalTypes.of(then).decidingForms(then), Ladder.shared()));
		Goal any = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), false, null);
		assertEquals(List.of(), GoalTypes.of(any).decidingForms(any), "any order: no form decides");
	}

	@Test
	void aStepWithoutADecidingFormGradesNothing() {
		play(1, new Goal.PlaceBlock(STONE, 1));
		tick(placing(placement(STONE, new World(), 100, 64, 100)));
		assertEquals("finale", engine.currentStep().id());
		assertEquals(0, learn.grammar().answers("L1.obj_eul"));
		assertEquals(0, learn.grammar().answers("L1.eoyo"));
		// but it is still a step: the constructions of its sentence were met
		assertTrue(learn.grammar().met("L1.eoyo"));
	}

	// ------------------------------------------------------------------ slips

	@Test
	void aRightBlockInTheWrongRelationCountsAgainstTheConstruction() {
		play(1, CHEST_BESIDE_DOOR);
		tick(chestOnDoor());
		assertEquals("s1", engine.currentStep().id(), "no progress");
		GrammarProgress grammar = learn.grammar();
		assertEquals(1, grammar.answers("L1.loc_yeop_e"), "the construction the task asked for");
		assertEquals(GrammarProgress.update(PRIOR, false), grammar.pL("L1.loc_yeop_e"), 1e-9);
		assertTrue(grammar.pL("L1.loc_yeop_e") < GrammarProgress.update(PRIOR, true));
		assertEquals(0, grammar.answers("L1.loc_wi_e"), "what the player did instead is not graded");

		tick(chestBesideDoor());
		assertEquals("finale", engine.currentStep().id());
		assertEquals(2, grammar.answers("L1.loc_yeop_e"));
		assertEquals(GrammarProgress.update(GrammarProgress.update(PRIOR, false), true), grammar.pL("L1.loc_yeop_e"), 1e-9);
	}

	@Test
	void aSlipIsReportedOnceAndOnlyAFewTimesPerStep() {
		play(1, CHEST_BESIDE_DOOR);
		for (int i = 0; i < 8; i++) {
			tick(chestOnDoor());
		}
		assertEquals(3, learn.grammar().answers("L1.loc_yeop_e"), "a step stops counting after three slips");
		// a tick with no placement adds nothing
		tick(new Observation());
		assertEquals(3, learn.grammar().answers("L1.loc_yeop_e"));
	}

	@Test
	void aNearbyOrUnrelatedPlacementIsNotASlip() {
		play(1, CHEST_BESIDE_DOOR);
		tick(placing(placement(CHEST, new World().put(110, 64, 100, "minecraft:oak_door"), 100, 64, 100)));
		tick(placing(placement(STONE, new World().put(101, 64, 100, "minecraft:oak_door"), 100, 64, 100)));
		assertEquals(0, learn.grammar().answers("L1.loc_yeop_e"));
	}

	@Test
	void aSequenceDoneOutOfOrderCountsAgainstItsConnective() {
		Goal before = new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), true, "BEFORE");
		play(3, before);
		tick(placing(placement(TORCH, new World(), 100, 64, 100)));
		assertEquals("s1", engine.currentStep().id(), "the torch before its turn does not count");
		assertEquals(1, learn.grammar().answers(BEFORE_SLEEP));
		assertEquals(GrammarProgress.update(PRIOR, false), learn.grammar().pL(BEFORE_SLEEP), 1e-9);
		// in order from here on: stone, then torch
		tick(placing(placement(STONE, new World(), 102, 64, 100)));
		tick(placing(placement(TORCH, new World(), 104, 64, 100)));
		assertEquals("finale", engine.currentStep().id());
		assertEquals(2, learn.grammar().answers(BEFORE_SLEEP));
		assertEquals(GrammarProgress.update(GrammarProgress.update(PRIOR, false), true),
				learn.grammar().pL(BEFORE_SLEEP), 1e-9);
	}

	@Test
	void twoPartsThatAreTheSameGoalAreNeverSlips() {
		Goal twice = new Goal.Sequence(List.of(new Goal.PlaceBlock(TORCH, 1), new Goal.PlaceBlock(TORCH, 1)), true, "BEFORE");
		play(3, twice);
		tick(placing(placement(TORCH, new World(), 100, 64, 100)));
		assertEquals(0, learn.grammar().answers(BEFORE_SLEEP), "one torch cannot be told apart from the other");
	}

	// ----------------------------------------------------- what is not evidence

	@Test
	void progressMadeInCreativeModeProvesNothing() {
		play(1, CHEST_BESIDE_DOOR);
		Observation wrong = chestOnDoor();
		wrong.creative = true;
		tick(wrong);
		Observation right = chestBesideDoor();
		right.creative = true;
		tick(right);
		assertEquals("finale", engine.currentStep().id());
		assertEquals(0, learn.grammar().answers("L1.loc_yeop_e"));
	}

	@Test
	void aStepDoneAfterAHintIsStillRightButOnlyHard() {
		play(new Questline("probe", "시험", "Probe", 1, List.of(), null,
				List.of(step("s1", new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "#minecraft:doors", 1), "torch")),
				step("finale", new Goal.PlaceBlock(SAND, 1)), "끝", "Ende", null, 1, 1, false));
		Observation hintObs = new Observation();
		hintObs.now = now;
		assertTrue(engine.hint(hintObs, feedback).sidequestStarted());
		engine.answerSidequest(engine.sideQuest().correctIndex(), feedback);
		engine.answerSidequest(engine.sideQuest().correctIndex(), feedback);
		assertTrue(engine.hintUsed());
		tick(placing(placement(TORCH, new World().put(101, 64, 100, "minecraft:oak_door"), 100, 64, 100)));
		assertEquals("finale", engine.currentStep().id());
		assertEquals(1, learn.grammar().answers("L1.loc_yeop_e"));
		assertEquals(GrammarProgress.update(PRIOR, true), learn.grammar().pL("L1.loc_yeop_e"), 1e-9);
		learn.reviewLog().flush();
		ReviewLog.Event outcome = ReviewLog.readAll(tempDir).stream()
				.filter(e -> e.kind() == ReviewKind.GRAMMAR && e.grade() != null).findFirst().orElseThrow();
		assertEquals(ReviewMode.QUEST_HINTED, outcome.mode());
		assertEquals(Fsrs.HARD, outcome.grade());
	}

	// ------------------------------------------------------------ the log and the replay

	@Test
	void theOutcomesAreInTheReviewLogAndReplayToTheSameEstimate() {
		play(1, CHEST_BESIDE_DOOR);
		tick(chestOnDoor());
		tick(chestBesideDoor());
		learn.reviewLog().flush();
		List<ReviewLog.Event> outcomes = ReviewLog.readAll(tempDir).stream()
				.filter(e -> e.kind() == ReviewKind.GRAMMAR && e.grade() != null).toList();
		assertEquals(2, outcomes.size());
		assertEquals(List.of("L1.loc_yeop_e", "L1.loc_yeop_e"), outcomes.stream().map(ReviewLog.Event::item).toList());
		assertEquals(Fsrs.AGAIN, outcomes.get(0).grade());
		assertEquals(Fsrs.GOOD, outcomes.get(1).grade());
		assertEquals("quest:probe/s1", outcomes.get(0).ctx());
		assertEquals(learn.grammar().pL("L1.loc_yeop_e"), replayed().pL("L1.loc_yeop_e"), 1e-12);
		assertEquals(2, replayed().answers("L1.loc_yeop_e"));
	}

	@Test
	void everyDecidingFormIsAConstructionOfTheLadder() {
		List<Goal> goals = List.of(CHEST_BESIDE_DOOR, new Goal.PlaceRel(CHEST, Relation.BETWEEN, CHEST, 1, TORCH),
				new Goal.PlaceOnPattern(TORCH, Relation.INSIDE, Pattern.RING, STONE),
				new Goal.Give("minecraft:bread", "minecraft:villager", null, 1),
				new Goal.Avoid(new Goal.PlaceBlock(TORCH, 1), 5, null),
				new Goal.Sequence(List.of(new Goal.PlaceBlock(STONE, 1), new Goal.PlaceBlock(TORCH, 1)), true, "AFTER"));
		for (Goal goal : goals) {
			List<String> forms = GoalTypes.of(goal).decidingForms(goal);
			assertFalse(forms.isEmpty(), goal.toString());
			assertEquals(forms.size(), GrammarBook.shared().resolve(forms, Ladder.shared()).size(),
					goal + " names a form grammar.json does not know: " + forms);
		}
	}
}
