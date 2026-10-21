package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.spatial.NearMiss;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.StepNamer;
import io.github.leks2205.hangulquest.quest.spatial.StepRef;
import io.github.leks2205.hangulquest.quest.spatial.World;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "아까 놓은 횃불 옆에": a step that points at the block placed to finish an earlier step. The sentence,
 * the memory that survives the step change and a restart, the check against that spot and the validation.
 */
class PlacedAnchorTest {
	private static final Logger LOG = LoggerFactory.getLogger("placed-anchor");
	private static final String TORCH = "minecraft:torch";
	private static final String CHEST = "minecraft:chest";

	private final Catalog catalog = KoreanFixtures.catalog();
	private final SentenceBuilder.Namer namer = KoreanFixtures.namer(catalog);

	@TempDir
	Path tempDir;

	// ------------------------------------------------------------------ helpers

	private static QuestStep step(String id, Goal goal) {
		return new QuestStep(id, List.of(), goal, List.of(), null, null, null, null, null, null, null);
	}

	/** Steps s1.. from the goals, then a finale; the trail asks for Korean level {@code level}. */
	private static Questline trail(int level, Goal... goals) {
		List<QuestStep> steps = new java.util.ArrayList<>();
		for (int i = 0; i < goals.length; i++) {
			steps.add(step("s" + (i + 1), goals[i]));
		}
		return new Questline("probe", "시험", "Probe", 1, List.of(), null, steps,
				step("finale", new Goal.PlaceBlock(CHEST, 1)), "끝", "Ende", null, 1, level, false);
	}

	/** A torch beside a chest: placed with 놓다, like most blocks (a bare torch is 밝히다). */
	private static Goal torchFirst() {
		return new Goal.PlaceRel(TORCH, Relation.NEXT_TO, CHEST, 1);
	}

	private static Goal placeTorch() {
		return new Goal.PlaceBlock(TORCH, 1);
	}

	private static Goal chestBesideIt() {
		return new Goal.PlaceRel(CHEST, Relation.NEXT_TO, "@s1", 1);
	}

	private String say(Questline trail, String stepId, int level) {
		QuestStep step = trail.step(stepId);
		return SentenceBuilder.normal(step.goal(), null, List.of(), level, false, StepNamer.of(namer, step));
	}

	private static PlacementFact placed(String block, int x, int y, int z) {
		return new World().put(x, y, z, block).placed(new BlockPos(x, y, z));
	}

	private static Observation placing(long now, PlacementFact... facts) {
		Observation obs = new Observation();
		obs.now = now;
		obs.clockTime = 1000L;
		for (PlacementFact fact : facts) {
			obs.addPlacement(fact);
			obs.placedBlocks.add(fact.block());
		}
		return obs;
	}

	/** A chest at (x, y, z) with a torch at (tx, ty, tz) in the world, the chest being the block just placed. */
	private static PlacementFact chestNear(int x, int y, int z, int tx, int ty, int tz) {
		return new World().put(tx, ty, tz, TORCH).put(x, y, z, CHEST).placed(new BlockPos(x, y, z));
	}

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

	// ----------------------------------------------------------------- the Korean

	@Test
	void theSentenceNamesTheBlockOfTheEarlierStepInThePastAttributive() {
		Questline trail = trail(5, torchFirst(), chestBesideIt());
		assertEquals("아까 놓은 횃불 옆에 상자를 놓아야 해요.", say(trail, "s2", 5));
		QuestStep s2 = trail.step("s2");
		assertEquals("아까 놓은 횃불 옆에 상자를 놓아요.",
				SentenceBuilder.hint(s2.goal(), null, List.of(), StepNamer.of(namer, s2)));
		assertEquals("아까 놓은 횃불 옆에 상자를 놓았어요.",
				SentenceBuilder.ceremony(s2.goal(), null, List.of(), StepNamer.of(namer, s2)));
	}

	@Test
	void theAdnominalFollowsTheVerbTheEarlierStepUsed() {
		for (Object[] row : new Object[][]{{Verb.PLACE, "minecraft:torch", "아까 놓은 횃불 옆에"},
				{Verb.STACK, "minecraft:stone", "아까 쌓은 돌 옆에"},
				{Verb.HANG_DOOR, "minecraft:bell", "아까 단 종 옆에"},
				{Verb.HANG, "minecraft:painting", "아까 건 그림 옆에"},
				{Verb.PLANT, "minecraft:oak_sapling", "아까 심은 묘목 옆에"},
				{Verb.STAND, "minecraft:torch", "아까 세운 횃불 옆에"}}) {
			SentenceBuilder.Namer.Placed placed = new SentenceBuilder.Namer.Placed((String) row[1], (Verb) row[0]);
			SentenceBuilder.Namer remembering = new SentenceBuilder.Namer() {
				@Override
				public String biome(String id) {
					return namer.biome(id);
				}

				@Override
				public String entity(String id) {
					return namer.entity(id);
				}

				@Override
				public String block(String id) {
					return namer.block(id);
				}

				@Override
				public Placed placedBy(String stepId) {
					return stepId.equals("s1") ? placed : null;
				}
			};
			String ko = SentenceBuilder.normal(chestBesideIt(), null, List.of(), 5, false, remembering);
			assertTrue(ko.startsWith((String) row[2] + " 상자를 "), ko);
		}
	}

	@Test
	void aBareTorchPlacementIsTheLightingVerb() {
		// the verb table says 밝히다 for a torch placed on its own, and the earlier-step phrase follows it
		Questline trail = trail(5, placeTorch(), chestBesideIt());
		assertEquals("아까 밝힌 횃불 옆에 상자를 놓아야 해요.", say(trail, "s2", 5));
	}

	@Test
	void aStepThatIsNotKnownIsJustAThing() {
		// the generator has no trail around it (a dump, a gold table): a neutral word instead of a failure
		assertEquals("아까 놓은 것 옆에 상자를 놓아요.", SentenceBuilder.normal(chestBesideIt(), null, List.of(), 1, false, namer));
	}

	@Test
	void theSentenceCarriesTheAdnominalAndTheRelationAsGrammarPoints() {
		Questline trail = trail(5, torchFirst(), chestBesideIt());
		QuestStep s2 = trail.step("s2");
		List<String> ids = SentenceBuilder.grammar(s2.goal(), null, List.of(), 5, false, StepNamer.of(namer, s2));
		assertTrue(ids.containsAll(List.of("L5.adn_past_eun", "L1.loc_yeop_e", "L1.obj_eul", "L4.eoya_haeyo")), ids.toString());
	}

	@Test
	void theTaughtWordNamesTheEarlierBlockToo() {
		// a step that teaches 횃불 says 횃불 for the earlier torch, like it does for any other anchor
		Word torch = catalog.byConceptId("torch").orElseThrow();
		Questline trail = trail(5, torchFirst(), chestBesideIt());
		QuestStep s2 = trail.step("s2");
		String ko = SentenceBuilder.normal(s2.goal(), torch, List.of(), 5, false, StepNamer.of(namer, s2));
		assertTrue(ko.startsWith("아까 놓은 횃불 옆에 "), ko);
	}

	// ------------------------------------------------------------ the JSON and the schema

	@Test
	void theAnchorIsWrittenAndReadBack() {
		Goal goal = chestBesideIt();
		JsonObject json = GoalTypes.toJson(goal);
		assertEquals("@s1", json.get("anchor").getAsString());
		assertFalse(json.has("anchor2"));
		assertEquals(goal, GoalTypes.parse(json));
		assertTrue(StepRef.wellFormed("@s1") && StepRef.wellFormed("@step-2"));
		assertFalse(StepRef.wellFormed("@") || StepRef.wellFormed("s1") || StepRef.wellFormed("@s 1"));
		assertEquals("s1", StepRef.stepId("@s1"));
	}

	// -------------------------------------------------------- checking against the spot

	@Test
	void theRememberedSpotIsWhatCounts() {
		Goal goal = chestBesideIt();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.placed.put("s1", new int[]{100, 64, 100});
		// next to the remembered torch
		assertTrue(GoalEvaluator.tick(goal, List.of(), placing(1000, chestNear(101, 64, 100, 100, 64, 100)), tracker));
		// next to another torch, not that one
		GoalEvaluator.Tracker elsewhere = new GoalEvaluator.Tracker();
		elsewhere.placed.put("s1", new int[]{100, 64, 100});
		assertFalse(GoalEvaluator.tick(goal, List.of(), placing(1000, chestNear(201, 64, 100, 200, 64, 100)), elsewhere));
		assertEquals(0, elsewhere.count);
		assertEquals(0, elsewhere.formErrors, "being nowhere near is not a wrong relation");
		// the other side of the same torch works as well
		GoalEvaluator.Tracker behind = new GoalEvaluator.Tracker();
		behind.placed.put("s1", new int[]{100, 64, 100});
		assertTrue(GoalEvaluator.tick(goal, List.of(), placing(1000, chestNear(100, 64, 99, 100, 64, 100)), behind));
	}

	@Test
	void aRightBlockInTheWrongRelationToTheSpotIsAMiss() {
		Goal goal = chestBesideIt();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.placed.put("s1", new int[]{100, 64, 100});
		Observation obs = placing(1000, chestNear(100, 65, 100, 100, 64, 100)); // on top of it, not beside it
		assertFalse(GoalEvaluator.tick(goal, List.of(), obs, tracker));
		assertEquals(Relation.ON_TOP_OF, NearMiss.current(tracker, obs.now));
		assertEquals(1, tracker.formErrors);
	}

	@Test
	void withoutTheMemoryAnyBlockOfThatKindWillDo() {
		// an old save, or a step renamed under the player: the kind of block the step placed is still known
		Goal goal = chestBesideIt();
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		tracker.refBlocks = Map.of("s1", TORCH);
		assertTrue(GoalEvaluator.tick(goal, List.of(), placing(1000, chestNear(201, 64, 100, 200, 64, 100)), tracker));
		// and with nothing known at all, nothing can match (the validator keeps such a goal out of the game)
		GoalEvaluator.Tracker blind = new GoalEvaluator.Tracker();
		assertFalse(GoalEvaluator.tick(goal, List.of(), placing(1000, chestNear(201, 64, 100, 200, 64, 100)), blind));
	}

	// ----------------------------------------------------- the engine remembers and saves

	private QuestEngine engine(ProgressStore store, Questline trail) {
		return new QuestEngine(List.of(trail), catalog, new LearningService(store.data(), store, catalog), LOG);
	}

	private static void tick(QuestEngine engine, Observation obs) {
		engine.tick(obs, new Quiet());
	}

	@Test
	void theBlockThatFinishedAStepIsRememberedSavedAndRestored() {
		Questline trail = trail(5, placeTorch(), chestBesideIt());
		ProgressStore store = new ProgressStore(tempDir.resolve("progress"), LOG);
		QuestEngine engine = engine(store, trail);
		Observation start = new Observation();
		start.now = 10_000L;
		engine.startTrail("probe", start, new Quiet());
		assertEquals("s1", engine.currentStep().id());

		tick(engine, placing(11_000L, placed(TORCH, 100, 64, 100)));
		assertEquals("s2", engine.currentStep().id());
		Map<String, int[]> saved = store.data().quests.active.placed;
		assertNotNull(saved, "the spot is saved with the trail");
		assertArrayEquals(new int[]{100, 64, 100}, saved.get("s1"));

		// after a restart the chest still has to go beside that torch
		store.flush();
		ProgressStore reopened = new ProgressStore(tempDir.resolve("progress"), LOG);
		QuestEngine again = engine(reopened, trail);
		assertEquals("s2", again.currentStep().id());
		tick(again, placing(12_000L, chestNear(201, 64, 100, 200, 64, 100)));
		assertEquals("s2", again.currentStep().id(), "beside some other torch");
		tick(again, placing(13_000L, chestNear(101, 64, 100, 100, 64, 100)));
		assertEquals("finale", again.currentStep().id(), "beside the torch from step 1");
	}

	@Test
	void onlyASpotThatALaterStepNeedsIsKept() {
		// nothing refers back to s1, so nothing is stored: no saved noise for every placing step
		Questline trail = trail(5, placeTorch(), new Goal.PlaceBlock(CHEST, 1));
		ProgressStore store = new ProgressStore(tempDir.resolve("quiet"), LOG);
		QuestEngine engine = engine(store, trail);
		Observation start = new Observation();
		start.now = 10_000L;
		engine.startTrail("probe", start, new Quiet());
		tick(engine, placing(11_000L, placed(TORCH, 100, 64, 100)));
		assertEquals("s2", engine.currentStep().id());
		assertNull(store.data().quests.active.placed);
	}

	@Test
	void aRelationStepRemembersItsBlockForTheNextOne() {
		Questline trail = trail(5, new Goal.PlaceRel(TORCH, Relation.NEXT_TO, "minecraft:chest", 1),
				new Goal.PlaceRel(CHEST, Relation.ON_TOP_OF, "@s1", 1));
		ProgressStore store = new ProgressStore(tempDir.resolve("rel"), LOG);
		QuestEngine engine = engine(store, trail);
		Observation start = new Observation();
		start.now = 10_000L;
		engine.startTrail("probe", start, new Quiet());
		PlacementFact torchBesideChest = new World().put(100, 64, 100, CHEST).put(101, 64, 100, TORCH)
				.placed(new BlockPos(101, 64, 100));
		tick(engine, placing(11_000L, torchBesideChest));
		assertEquals("s2", engine.currentStep().id());
		assertArrayEquals(new int[]{101, 64, 100}, store.data().quests.active.placed.get("s1"));
	}

	// ------------------------------------------------------------------ validation

	private List<String> problems(Questline trail) {
		return QuestValidator.validate(List.of(trail), catalog, LOG).problems();
	}

	@Test
	void aGoodReferenceHasNoProblems() {
		assertEquals(List.of(), problems(trail(5, torchFirst(), chestBesideIt())));
		assertEquals(List.of(), problems(trail(6, torchFirst(), chestBesideIt())));
	}

	@Test
	void theReferenceHasToPointAtAnEarlierPlacingStep() {
		assertTrue(problems(trail(5, new Goal.PlaceRel(CHEST, Relation.NEXT_TO, "@s7", 1), torchFirst()))
				.stream().anyMatch(p -> p.contains("@s7")), "no such step");
		assertTrue(problems(trail(5, chestBesideIt(), torchFirst())).stream().anyMatch(p -> p.contains("@s1")),
				"a step cannot point at itself or at a later one");
		assertTrue(problems(trail(5, new Goal.BreakBlock("minecraft:stone", 1, null), chestBesideIt()))
				.stream().anyMatch(p -> p.contains("@s1") && p.contains("places a block")), "s1 breaks a block");
		assertTrue(problems(trail(5, torchFirst(), new Goal.PlaceRel(CHEST, Relation.NEXT_TO, "@", 1)))
				.stream().anyMatch(p -> p.contains("step reference")));
	}

	@Test
	void theAdnominalIsALevelFiveConstruction() {
		for (int level = 1; level < StepRef.MIN_LEVEL; level++) {
			int shown = level;
			assertTrue(problems(trail(level, torchFirst(), chestBesideIt())).stream()
					.anyMatch(p -> p.contains("koreanLevel " + StepRef.MIN_LEVEL)), "level " + shown);
		}
	}

	@Test
	void onlyRelationsThatMakeSenseForASpotFit() {
		for (Relation relation : Relation.values()) {
			boolean fits = relation == Relation.ON_TOP_OF || relation == Relation.UNDER || relation == Relation.NEXT_TO
					|| relation == Relation.NEAR;
			List<String> found = problems(trail(5, torchFirst(), new Goal.PlaceRel(CHEST, relation, "@s1", 1)));
			assertEquals(fits, found.stream().noneMatch(p -> p.contains("does not fit")), relation + " " + found);
		}
	}

	@Test
	void aReferenceCannotBePairedWithASecondAnchor() {
		List<String> found = problems(trail(5, torchFirst(),
				new Goal.PlaceRel(CHEST, Relation.BETWEEN, "@s1", 1, "minecraft:furnace")));
		assertTrue(found.stream().anyMatch(p -> p.contains("BETWEEN needs two blocks")), found.toString());
	}

	@Test
	void theTrailRebuiltByTheValidatorStillKnowsItsEarlierSteps() {
		Questline checked = QuestValidator.validate(List.of(trail(5, torchFirst(), chestBesideIt())), catalog, LOG)
				.trails().get(0);
		assertEquals(Map.of("s1", TORCH), checked.step("s2").placedBefore());
		assertEquals(List.of("s1"), checked.step("s2").earlier().stream().map(QuestStep::id).toList());
		assertEquals(Map.of(), checked.step("s1").placedBefore());
	}

	// ----------------------------------------------------------- the saved file's keys

	@Test
	void aRenamedStepKeepsItsSpot() throws IOException {
		Path dir = tempDir.resolve("renamed");
		Files.createDirectories(dir);
		try (var in = PlacedAnchorTest.class.getResourceAsStream("/progress-fixtures/v4-placed.json")) {
			Files.write(dir.resolve("progress.json"), in.readAllBytes());
		}
		var aliases = io.github.leks2205.hangulquest.learn.migrate.Aliases.parse(JsonParser.parseString(
				"{\"steps\": {\"druckerei/s1\": \"druckerei/anfang\"}}").getAsJsonObject());
		ProgressData data = new ProgressStore(dir, LOG, aliases).data();
		assertArrayEquals(new int[]{100, 64, 100}, data.quests.active.placed.get("anfang"));
		assertNull(data.quests.active.placed.get("s1"));
		// the other trail's steps were not renamed
		assertTrue(data.quests.saved.get("rotwerk").placed.containsKey("s1"));
	}
}
