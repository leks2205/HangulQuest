package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.sense.ContainerPutFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clue names the taught word (꽃, 물고기) while the goal checks one id (poppy, cod). When the word has
 * several referents, any of them completes the step; a word with one referent never widens the goal.
 */
class AcceptedIdsTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("accepted-ids-test");
	private static final String COD = "minecraft:cod";
	private static final String SALMON = "minecraft:salmon";
	private static final String POPPY = "minecraft:poppy";
	// not in the flower family of Matching, so only the word can make it a flower
	private static final String ROSE = "minecraft:pink_petals";

	@TempDir
	Path tempDir;

	private static Concept concept(String id, String ko, String... items) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = ko;
		concept.category = "ANIMALS";
		concept.items = List.of(items);
		return concept;
	}

	private static final Concept FISH = concept("fish", "물고기", COD, SALMON, "minecraft:tropical_fish");
	private static final Concept FLOWER = concept("flower", "꽃", POPPY, ROSE, "minecraft:lily_pad");
	private static final Concept STONE = concept("stone", "돌", "minecraft:stone");

	private static QuestStep step(Goal goal, Concept taught) {
		return new QuestStep("s1", List.of(taught.id), goal, List.of(), null, null, null, null, null);
	}

	private static GoalEvaluator.Tracker tracker(Goal goal, Concept taught) {
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.accepted = Accepted.of(step(goal, taught), taught);
		return tracker;
	}

	private static boolean done(Goal goal, Concept taught, Observation obs) {
		return GoalEvaluator.tick(goal, List.of(), obs, tracker(goal, taught));
	}

	// -------------------------------------------------------------------------- the set itself

	@Test
	void aWordWithSeveralReferentsWidensTheGoalIdsItCovers() {
		Accepted accepted = Accepted.of(step(new Goal.ConsumeItem(COD, 1), FISH), FISH);
		assertTrue(accepted.widens(COD));
		assertTrue(accepted.matches(COD, SALMON));
		assertFalse(accepted.matches(COD, "minecraft:bread"));
		assertFalse(accepted.matches("minecraft:bread", SALMON), "only the ids the word covers are widened");
	}

	@Test
	void aWordWithOneReferentNeverWidens() {
		Accepted accepted = Accepted.of(step(new Goal.PlaceBlock("minecraft:stone", 1), STONE), STONE);
		assertSame(Accepted.NONE, accepted);
		assertSame(Accepted.NONE, Accepted.of(step(new Goal.PlaceBlock("minecraft:stone", 1), FISH), FISH),
				"a word that does not cover the goal widens nothing");
		assertSame(Accepted.NONE, Accepted.of(step(new Goal.PlaceBlock("minecraft:stone", 1), STONE), null));
	}

	// ----------------------------------------------------------------- every goal that names an item

	@Test
	void anyFlowerPlantsTheFlowerStep() {
		Goal poppy = new Goal.PlaceBlock(POPPY, 1);
		Observation rose = new Observation();
		rose.placedBlocks.add(ROSE);
		assertTrue(done(poppy, FLOWER, rose), "pink petals plant the 꽃 step");
		assertFalse(done(poppy, concept("flower", "꽃", POPPY), rose), "a one-referent word is exact");
		Observation stone = new Observation();
		stone.placedBlocks.add("minecraft:stone");
		assertFalse(done(poppy, FLOWER, stone));
	}

	@Test
	void anyBedSleepsInTheBedStep() {
		Concept bed = concept("bed", "침대", "minecraft:red_bed");
		Goal place = new Goal.PlaceBlock("minecraft:red_bed", 1);
		Observation white = new Observation();
		white.placedBlocks.add("minecraft:white_bed");
		assertTrue(done(place, bed, white), "the bed family was always one thing");
	}

	@Test
	void salmonBreaksAndFeedsAndFillsTheFishStep() {
		Observation salmon = new Observation();
		salmon.consumedItems.add(SALMON);
		assertTrue(done(new Goal.ConsumeItem(COD, 1), FISH, salmon));
		assertFalse(done(new Goal.ConsumeItem(COD, 1), concept("cod", "대구", COD), salmon));

		Observation give = new Observation();
		give.addGave(new GiveFact(SALMON, 1, "minecraft:cat", UUID.randomUUID(), GiveFact.Method.FEED));
		assertTrue(done(new Goal.Give(COD, "minecraft:cat", null, 1), FISH, give));
		assertFalse(done(new Goal.Give(COD, "minecraft:cat", null, 1), concept("cod", "대구", COD), give));

		Observation put = new Observation();
		put.addPutIn(new ContainerPutFact(SALMON, 1, "minecraft:chest", new BlockPos(0, 64, 0)));
		assertTrue(done(new Goal.PutIn(COD, "minecraft:chest", 1), FISH, put));

		Observation broken = new Observation();
		broken.brokenBlocks.add(SALMON);
		assertTrue(done(new Goal.BreakBlock(COD, 1, null), FISH, broken));

		Observation looking = new Observation();
		looking.lookedBlock = SALMON;
		Goal.LookAtBlock look = new Goal.LookAtBlock(COD, 0);
		GoalEvaluator.Tracker wide = tracker(look, FISH);
		GoalEvaluator.tick(look, List.of(), looking, wide); // the first tick starts the look
		assertTrue(GoalEvaluator.tick(look, List.of(), looking, wide));
		GoalEvaluator.Tracker exact = tracker(look, concept("cod", "대구", COD));
		GoalEvaluator.tick(look, List.of(), looking, exact);
		assertFalse(GoalEvaluator.tick(look, List.of(), looking, exact));
	}

	@Test
	void anyReferentInTheInventoryCountsForAnAcquireStep() {
		Goal.AcquireItem two = new Goal.AcquireItem(POPPY, 2);
		GoalEvaluator.Tracker tracker = tracker(two, FLOWER);
		assertFalse(GoalEvaluator.tick(two, List.of(), new Observation(), tracker));
		Observation after = new Observation();
		after.inventory.put(POPPY, 1);
		after.inventory.put(ROSE, 1);
		assertTrue(GoalEvaluator.tick(two, List.of(), after, tracker), "a poppy and pink petals are two flowers");

		GoalEvaluator.Tracker exact = tracker(two, concept("flower", "꽃", POPPY));
		GoalEvaluator.tick(two, List.of(), new Observation(), exact);
		assertFalse(GoalEvaluator.tick(two, List.of(), after, exact), "one poppy is one flower for an exact word");
	}

	@Test
	void theObjectAndTheAnchorOfARelationAreBothWidened() {
		Goal.PlaceRel torchOnFlower = new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, POPPY, 1);
		Observation next = new Observation();
		next.addPlacement(SpatialWorlds.placementFor(new Goal.PlaceRel("minecraft:torch", Relation.NEXT_TO, ROSE, 1),
				false));
		assertTrue(done(torchOnFlower, FLOWER, next), "the word for the anchor accepts any of its referents");
		assertFalse(done(torchOnFlower, concept("flower", "꽃", POPPY), next));

		Goal.PlaceRel flowerByDoor = new Goal.PlaceRel(POPPY, Relation.NEXT_TO, "minecraft:oak_door", 1);
		Observation object = new Observation();
		object.addPlacement(SpatialWorlds.placementFor(new Goal.PlaceRel(ROSE, Relation.NEXT_TO,
				"minecraft:oak_door", 1), false));
		assertTrue(done(flowerByDoor, FLOWER, object), "the word for the object accepts any of its referents");
		assertFalse(done(flowerByDoor, concept("flower", "꽃", POPPY), object));
	}

	@Test
	void aStructureMayBeMadeOfAnyReferent() {
		Concept stones = concept("stones", "돌", "minecraft:stone", "minecraft:cobblestone");
		Goal.BuildPattern pillar = new Goal.BuildPattern(Pattern.PILLAR, "minecraft:stone", 2);
		Observation cobble = new Observation();
		cobble.addPlacement(SpatialWorlds.placementFor(new Goal.BuildPattern(Pattern.PILLAR, "minecraft:cobblestone",
				2), false));
		assertTrue(done(pillar, stones, cobble));
		assertFalse(done(pillar, STONE, cobble));
	}

	// -------------------------------------------------------------------- through the engine

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

	private QuestEngine engine(Concept taught) {
		QuestStep first = new QuestStep("s1", List.of(taught.id), new Goal.ConsumeItem(COD, 1), List.of(),
				null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of(taught.id), new Goal.ConsumeItem(COD, 1), List.of(),
				null, null, null, null, null);
		Questline trail = new Questline("t", "길", "Pfad", 1, List.of(taught.id), Questline.Requires.none(),
				List.of(first), finale, "끝났어요.", "Fertig.", "t-badge", 10, 1, false);
		ProgressStore store = new ProgressStore(tempDir.resolve(taught.id + taught.items.size()), LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(taught));
		return new QuestEngine(List.of(trail), catalog, new LearningService(store.data(), store, catalog), LOG);
	}

	private static Observation ate(String item, long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.dimension = "minecraft:overworld";
		obs.consumedItems.add(item);
		return obs;
	}

	@Test
	void theEngineHandsTheTaughtWordToTheGoal() {
		QuestEngine wide = engine(FISH);
		wide.startTrail("t", ate(SALMON, 1_000L), new Quiet());
		assertEquals("s1", wide.currentStep().id());
		wide.tick(ate(SALMON, 1_050L), new Quiet());
		assertEquals("finale", wide.currentStep().id(), "a salmon completes a 물고기 step whose goal is the cod");

		QuestEngine exact = engine(concept("cod", "대구", COD));
		exact.startTrail("t", ate(SALMON, 1_000L), new Quiet());
		exact.tick(ate(SALMON, 1_050L), new Quiet());
		assertEquals("s1", exact.currentStep().id(), "a word with one referent stays exact");
		exact.tick(ate(COD, 1_100L), new Quiet());
		assertEquals("finale", exact.currentStep().id());
	}

	@Test
	void coversWidenTheGoalWithoutRenamingTheItems() {
		// 침대 lists one bed as its own item and the other colours as covers: any bed fulfils the step.
		Concept bed = concept("bed", "침대", "minecraft:red_bed");
		bed.covers = java.util.List.of("minecraft:blue_bed", "minecraft:white_bed");
		Accepted accepted = Accepted.of(step(new Goal.PlaceBlock("minecraft:red_bed", 1), bed), bed);
		assertTrue(accepted.matches("minecraft:red_bed", "minecraft:blue_bed"));
		assertFalse(accepted.matches("minecraft:red_bed", "minecraft:red_wool"));
		// Covers are not referents: a birch log is never named 통나무 (it keeps its own vanilla name in game).
		Catalog catalog = Catalog.ofConcepts(io.github.leks2205.hangulquest.data.ConceptLoader.load());
		assertTrue(catalog.byItemId("minecraft:birch_log").map(word -> !"통나무".equals(word.ko)).orElse(true));
		assertEquals("통나무", catalog.byItemId("minecraft:oak_log").orElseThrow().ko);
	}
}
