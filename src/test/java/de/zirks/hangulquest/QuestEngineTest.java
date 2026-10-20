package de.zirks.hangulquest;

import de.zirks.hangulquest.data.Catalog;
import de.zirks.hangulquest.data.Category;
import de.zirks.hangulquest.data.Concept;
import de.zirks.hangulquest.data.Origin;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.learn.LearningService;
import de.zirks.hangulquest.learn.ProgressStore;
import de.zirks.hangulquest.quest.Condition;
import de.zirks.hangulquest.quest.Goal;
import de.zirks.hangulquest.quest.QuestEngine;
import de.zirks.hangulquest.quest.QuestStep;
import de.zirks.hangulquest.quest.Questline;
import de.zirks.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the clue -> goal -> hint -> sidequest -> restore state machine. */
class QuestEngineTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("quest-engine-test");

	@TempDir
	Path tempDir;

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	private static Questline trail() {
		QuestStep step = new QuestStep("s1", List.of("stone"),
				new Goal.BreakBlock("minecraft:stone", 2, null), List.of(),
				"돌을 두 개 캐요.", "Baue zwei Steine ab.", "돌을 캐요.", "Baue Stein ab.", null);
		QuestStep finale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT)),
				null, null, null, null, null);
		return new Questline("t", "길", "Pfad", 1, List.of("stone", "torch"),
				Questline.Requires.none(), List.of(step), finale, "끝났어요.", "Fertig.", "t-badge", 10, 1, false);
	}

	private static Observation night() {
		Observation obs = new Observation();
		obs.clockTime = 14000L;
		obs.dimension = "minecraft:overworld";
		return obs;
	}

	@Test
	void completesStepsAndFinale() {
		Path dir = tempDir.resolve("run");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		Observation start = night();
		start.now = 10_000L;
		engine.startTrail("t", start, recorder);
		assertEquals("s1", engine.currentStep().id());

		Observation first = night();
		first.now = 10_050L;
		first.brokenBlocks.add("minecraft:stone");
		engine.tick(first, recorder);
		assertEquals(1, engine.goalProgress());

		Observation second = night();
		second.now = 10_100L;
		second.brokenBlocks.add("minecraft:stone");
		engine.tick(second, recorder);
		assertEquals("finale", engine.currentStep().id());

		Observation finale = night();
		finale.now = 10_150L;
		finale.placedBlocks.add("minecraft:torch");
		engine.tick(finale, recorder);

		assertTrue(store.data().quests.completed.containsKey("t"));
		assertTrue(store.data().quests.badges.contains("t-badge"));
		assertFalse(engine.activeTrail() != null);
		assertEquals(1, recorder.steps);
		assertEquals(1, recorder.trails);
		assertEquals(0, recorder.sides);
		assertTrue(learn.isKnown("concept:stone") || learn.data().words.containsKey("concept:stone"));
	}

	@Test
	void switchingTrailsPreservesStepAndCounter() {
		Path dir = tempDir.resolve("switch");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep otherStep = new QuestStep("s1", List.of("torch"),
				new Goal.AcquireItem("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		QuestStep otherFinale = new QuestStep("finale", List.of(),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline other = new Questline("u", "다른 길", "Anderer Pfad", 1, List.of("torch"),
				Questline.Requires.none(), List.of(otherStep), otherFinale, "끝", "Ende", "u", 20, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail(), other), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("t", start, recorder);
		Observation partial = night();
		partial.now = 1_100L;
		partial.brokenBlocks.add("minecraft:stone");
		engine.tick(partial, recorder);
		assertEquals("s1", engine.currentStep().id());
		assertEquals(1, engine.goalProgress());

		Observation switchObs = night();
		switchObs.now = 1_200L;
		engine.startTrail("u", switchObs, recorder);
		assertTrue(store.data().quests.saved.containsKey("t"));
		engine.startTrail("t", switchObs, recorder);

		assertEquals("s1", engine.currentStep().id());
		assertEquals(1, engine.goalProgress());
		assertFalse(store.data().quests.saved.containsKey("t"));

		Observation finishStep = night();
		finishStep.now = 1_300L;
		finishStep.brokenBlocks.add("minecraft:stone");
		engine.tick(finishStep, recorder);
		assertEquals("finale", engine.currentStep().id());
	}

	@Test
	void hotSwapKeepsActiveTrailAndStep() {
		Path dir = tempDir.resolve("hotswap");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline original = trail();
		QuestEngine engine = new QuestEngine(List.of(original), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("t", start, recorder);
		Observation partial = night();
		partial.now = 1_050L;
		partial.brokenBlocks.add("minecraft:stone");
		engine.tick(partial, recorder);
		assertEquals(1, engine.goalProgress());

		Questline edited = new Questline("t", "길", "Neuer Pfad", 1, List.of("stone", "torch"),
				Questline.Requires.none(), original.steps(), original.finale(),
				"끝났어요.", "Fertig.", "t-badge", 10, 1, false);
		engine.replaceTrails(List.of(edited));

		assertEquals(1, engine.trails().size());
		assertEquals("Neuer Pfad", engine.activeTrail().titleDe());
		assertEquals("s1", engine.currentStep().id());
		assertEquals(1, engine.goalProgress());
	}


	private static Questline childTrail(String id, List<String> parents) {
		QuestStep step = new QuestStep("s1", List.of("torch"),
				new Goal.AcquireItem("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of(),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		return new Questline(id, "아이", "Kind", 2, List.of("torch"),
				new Questline.Requires(0, null, parents), List.of(step), finale,
				"끝", "Ende", id, 100, 2, false);
	}

	private static void completeTrail(QuestEngine engine, Recorder recorder) {
		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("t", start, recorder);
		Observation first = night();
		first.now = 1_100L;
		first.brokenBlocks.add("minecraft:stone");
		engine.tick(first, recorder);
		Observation second = night();
		second.now = 1_200L;
		second.brokenBlocks.add("minecraft:stone");
		engine.tick(second, recorder);
		Observation finale = night();
		finale.now = 1_300L;
		finale.placedBlocks.add("minecraft:torch");
		engine.tick(finale, recorder);
	}

	@Test
	void completedTrailStartsItsOnlyChild() {
		Path dir = tempDir.resolve("auto");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail(), childTrail("c", List.of("t"))),
				catalog, learn, LOG);
		Recorder recorder = new Recorder();

		completeTrail(engine, recorder);

		assertEquals("c", engine.activeTrail().id());
		assertEquals("s1", engine.currentStep().id());
		assertTrue(recorder.choices.isEmpty());
	}

	@Test
	void sameLevelSameTopicIsPreferredOverTheChild() {
		Path dir = tempDir.resolve("topic");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep side = new QuestStep("s1", List.of("stone"),
				new Goal.PlaceBlock("minecraft:stone", 1), List.of(),
				null, null, null, null, null);
		Questline sibling = new Questline("sib", "옆길", "Nebenpfad", "Pfad", 1,
				List.of("stone"), Questline.Requires.none(), List.of(side), side,
				"끝", "Ende", "sib", 90, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail(),
				childTrail("c", List.of("t")), sibling), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		completeTrail(engine, recorder);

		assertEquals("sib", engine.activeTrail().id());
	}

	@Test
	void multipleChildrenAskForSelection() {
		Path dir = tempDir.resolve("choice");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail(),
				childTrail("c1", List.of("t")), childTrail("c2", List.of("t"))),
				catalog, learn, LOG);
		Recorder recorder = new Recorder();

		completeTrail(engine, recorder);

		assertNull(engine.activeTrail());
		assertEquals(1, recorder.choices.size());
		assertEquals(2, recorder.choices.get(0).size());
	}

	@Test
	void lockedChildStartsItsMissingPrerequisite() {
		Path dir = tempDir.resolve("prereq");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline other = childTrail("u", List.of());
		QuestEngine engine = new QuestEngine(List.of(trail(), other,
				childTrail("c", List.of("t", "u"))), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		completeTrail(engine, recorder);

		assertEquals("u", engine.activeTrail().id());
		assertTrue(recorder.choices.isEmpty());
	}

	@Test
	void hintStartsSidequestAndRestoresTheStep() {
		Path dir = tempDir.resolve("hint");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		catalog.add(new Word("mc:item:minecraft:dirt", "흙", "Erde", "Erde", Category.BLOCKS, 1,
				null, "minecraft:dirt", null, null, null, Origin.MINECRAFT, false, null));
		catalog.add(new Word("mc:item:minecraft:sand", "모래", "Sand", "Sand", Category.BLOCKS, 1,
				null, "minecraft:sand", null, null, null, Origin.MINECRAFT, false, null));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		Observation obs = night();
		obs.now = 10_000L;
		engine.startTrail("t", obs, recorder);

		QuestEngine.HintView view = engine.hint(obs, recorder);
		assertNotNull(view);
		assertTrue(view.sidequestStarted());
		assertTrue(engine.sidequestActive());
		assertEquals("s1", engine.currentStep().id());
		assertEquals("돌을 캐요.", view.simplifiedKo());

		assertTrue(engine.answerSidequest(engine.sideQuest().correctIndex(), recorder));
		assertTrue(engine.sidequestActive());
		assertTrue(engine.answerSidequest(engine.sideQuest().correctIndex(), recorder));
		assertFalse(engine.sidequestActive());
		assertEquals("s1", engine.currentStep().id());
		assertNotNull(store.data().quests.active);
		assertEquals(null, store.data().quests.active.sidequest);
		assertTrue(store.data().words.containsKey("concept:stone"));
		assertEquals(1, recorder.sides);
	}

	@Test
	void dependentTrailNeedsParentCompletion() {
		Path dir = tempDir.resolve("dependent");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline parent = trail();
		QuestStep childStep = new QuestStep("s1", List.of("stone"),
				new Goal.LookAtBlock("minecraft:stone", 100), List.of(),
				null, null, null, null, null);
		QuestStep childFinale = new QuestStep("finale", List.of(),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline child = new Questline("child", "아이", "Kind", 2, List.of("stone"),
				new Questline.Requires(0, null, List.of("t")), List.of(childStep), childFinale,
				"끝", "Ende", "child", 110, 2, false);
		QuestEngine engine = new QuestEngine(List.of(parent, child), catalog, learn, LOG);

		assertTrue(engine.unlocked(parent));
		assertFalse(engine.unlocked(child));

		store.data().quests.completed.put("t", 1000L);
		assertTrue(engine.unlocked(child));
	}

	@Test
	void restoresActiveStepFromDisk() {
		Path dir = tempDir.resolve("restore");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine first = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation obs = night();
		obs.now = 10_000L;
		first.startTrail("t", obs, recorder);
		first.tick(obsFor(obs.now + 50, "minecraft:stone"), recorder);
		store.flush();

		ProgressStore reloaded = new ProgressStore(dir, LOG);
		LearningService learnAgain = new LearningService(reloaded.data(), reloaded, catalog);
		QuestEngine second = new QuestEngine(List.of(trail()), catalog, learnAgain, LOG);
		assertNotNull(second.activeTrail());
		assertEquals("s1", second.currentStep().id());
		assertEquals(1, second.goalProgress());
	}

	private static Observation obsFor(long now, String broken) {
		Observation obs = night();
		obs.now = now;
		obs.brokenBlocks.add(broken);
		return obs;
	}

	private static final class Recorder implements QuestEngine.Feedback {
		final List<String> banners = new ArrayList<>();
		final List<String> cards = new ArrayList<>();
		final List<List<Questline>> choices = new ArrayList<>();
		int steps;
		int sides;
		int trails;

		@Override
		public void banner(String ko, String de, boolean success) {
			banners.add(ko);
		}

		@Override
		public void wordCard(Word word) {
			cards.add(word.ko);
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
			steps++;
		}

		@Override
		public void sidequestCompleted(Word word) {
			sides++;
		}

		@Override
		public void trailCompleted(Questline trail) {
			trails++;
		}

		@Override
		public void chooseNext(List<Questline> options) {
			choices.add(options);
		}
	}

	@Test
	void authoredHintWinsWithConditions() {
		Path dir = tempDir.resolve("hintcond");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep step = new QuestStep("s1", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.Time(Condition.Phase.NIGHT)),
				"밤에 횃불을 놓아요.", "Nachts eine Fackel setzen.",
				"밤에 횃불을 놓아요. HINDE", "Nachts eine Fackel setzen.", null);
		QuestStep finale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline trail = new Questline("hint", "힌트", "Hinweis", 1,
				List.of("torch"), Questline.Requires.none(), List.of(step), finale,
				"끝", "Ende", "hint", 5, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation obs = night();
		obs.now = 10_000L;
		engine.startTrail("hint", obs, recorder);
		QuestEngine.HintView hint = engine.hint(obs, recorder);
		assertEquals("밤에 횃불을 놓아요. HINDE", hint.simplifiedKo());
	}


	@Test
	void unrelatedSameLevelDoesNotBlockClimbing() {
		Path dir = tempDir.resolve("climb");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep side = new QuestStep("s1", List.of("stone"),
				new Goal.PlaceBlock("minecraft:stone", 1), List.of(),
				null, null, null, null, null);
		Questline other = new Questline("other", "다른 길", "Anderer Pfad", "Kind", 1,
				List.of("stone"), Questline.Requires.none(), List.of(side), side,
				"끝", "Ende", "other", 90, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail(),
				childTrail("c", List.of("t")), other), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		completeTrail(engine, recorder);

		assertEquals("c", engine.activeTrail().id());
	}


	@Test
	void switchesToAnOpenQuestWhoseGoalMatchesTheAction() {
		Path dir = tempDir.resolve("opportunistic");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel"), concept("coal", "석탄", "Kohle")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep torchStep = new QuestStep("s1", List.of("torch"),
				new Goal.AcquireItem("minecraft:torch", 4), List.of(),
				null, null, null, null, null);
		QuestStep torchFinale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline torches = new Questline("torches", "횃불", "Fackeln", "불", 1,
				List.of("torch"), Questline.Requires.none(), List.of(torchStep), torchFinale,
				"끝", "Ende", "torches", 10, 1, false);
		QuestStep coalStep = new QuestStep("s1", List.of("coal"),
				new Goal.BreakBlock("minecraft:coal_ore", 2, null), List.of(),
				null, null, null, null, null);
		QuestStep coalFinale = new QuestStep("finale", List.of("coal"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline coal = new Questline("coal", "석탄", "Kohle", "돌", 2,
				List.of("coal"), Questline.Requires.none(), List.of(coalStep), coalFinale,
				"끝", "Ende", "coal", 20, 1, false);
		QuestEngine engine = new QuestEngine(List.of(torches, coal), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("torches", start, recorder);

		Observation mining = night();
		mining.now = 1_100L;
		mining.brokenBlocks.add("minecraft:coal_ore");
		engine.tick(mining, recorder);

		assertEquals("coal", engine.activeTrail().id());
		assertEquals(1, engine.goalProgress());
	}

}
