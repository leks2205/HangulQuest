package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
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
		// Doing the steps taught both words, but nobody was quizzed: they are seen, not learned.
		for (String id : List.of("concept:stone", "concept:torch")) {
			assertTrue(learn.introduced(id), id + " should be introduced by its step");
			assertEquals(0, learn.level(id), id + " must not be promoted without recall");
			assertFalse(learn.isKnown(id));
			assertEquals(1, learn.data().words.get(id).seen);
			assertEquals(0, learn.data().words.get(id).correct);
		}
		assertEquals(0, learn.totalReviews());
	}

	@Test
	void completingAStepNeverMovesTheSchedule() {
		Path dir = tempDir.resolve("schedule");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		// The player already reviewed the stone twice and marked the torch as known.
		learn.answer("concept:stone", true, 1_000L);
		learn.answer("concept:stone", true, 2_000L);
		learn.markKnown("concept:torch", true, 3_000L);
		int level = learn.level("concept:stone");
		long dueAt = learn.data().words.get("concept:stone").dueAt;
		int torchLevel = learn.level("concept:torch");
		long torchDueAt = learn.data().words.get("concept:torch").dueAt;
		int reviews = learn.totalReviews();

		QuestEngine engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 10_000L;
		engine.startTrail("t", start, recorder);
		engine.tick(obsFor(10_050L, "minecraft:stone"), recorder);
		engine.tick(obsFor(10_100L, "minecraft:stone"), recorder);
		assertEquals("finale", engine.currentStep().id(), "the stone step is done");
		Observation finale = night();
		finale.now = 10_150L;
		finale.placedBlocks.add("minecraft:torch");
		engine.tick(finale, recorder);
		assertEquals(1, recorder.trails, "the finale is done too");

		assertEquals(level, learn.level("concept:stone"));
		assertEquals(dueAt, learn.data().words.get("concept:stone").dueAt);
		assertEquals(3, learn.data().words.get("concept:stone").seen, "two answers and one encounter");
		assertEquals(2, learn.data().words.get("concept:stone").correct);
		assertEquals(torchLevel, learn.level("concept:torch"));
		assertEquals(torchDueAt, learn.data().words.get("concept:torch").dueAt);
		assertTrue(learn.isKnown("concept:torch"));
		assertEquals(reviews, learn.totalReviews());
	}

	@Test
	void sidequestAnswersAreOnlyPractice() {
		Path dir = tempDir.resolve("practice");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = sidequestCatalog();
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.answer("concept:stone", true, 1_000L);
		int level = learn.level("concept:stone");
		long dueAt = learn.data().words.get("concept:stone").dueAt;
		int reviews = learn.totalReviews();
		QuestEngine engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation obs = night();
		obs.now = 10_000L;
		engine.startTrail("t", obs, recorder);
		assertTrue(engine.hint(obs, recorder).sidequestStarted());

		int wrong = (engine.sideQuest().correctIndex() + 1) % engine.sideQuest().options().size();
		assertFalse(engine.answerSidequest(wrong, recorder));
		assertEquals(level, learn.level("concept:stone"), "a wrong guess changes nothing");
		assertEquals(1, learn.data().words.get("concept:stone").wrong);
		assertTrue(engine.answerSidequest(engine.sideQuest().correctIndex(), recorder));
		assertTrue(engine.answerSidequest(engine.sideQuest().correctIndex(), recorder));

		assertFalse(engine.sidequestActive());
		assertEquals(1, recorder.sides);
		assertEquals(level, learn.level("concept:stone"), "two right sidequest answers must not promote");
		assertEquals(dueAt, learn.data().words.get("concept:stone").dueAt);
		assertEquals(3, learn.data().words.get("concept:stone").correct, "the quiz answer plus two practice answers");
		assertEquals(reviews, learn.totalReviews(), "practice is not a review");
		assertFalse(learn.isKnown("concept:stone"));
	}

	private static Catalog sidequestCatalog() {
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		catalog.add(new Word("mc:item:minecraft:dirt", "흙", "Erde", "Erde", Category.BLOCKS, 1,
				null, "minecraft:dirt", null, null, null, Origin.MINECRAFT, false, null));
		catalog.add(new Word("mc:item:minecraft:sand", "모래", "Sand", "Sand", Category.BLOCKS, 1,
				null, "minecraft:sand", null, null, null, Origin.MINECRAFT, false, null));
		return catalog;
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
	void untrackedQuestDoesNotStealTheAction() {
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
		Questline torches = new Questline("torches", "횃불", "Fackeln", 1,
				List.of("torch"), Questline.Requires.none(), List.of(torchStep), torchFinale,
				"끝", "Ende", "torches", 10, 1, false);
		QuestStep coalStep = new QuestStep("s1", List.of("coal"),
				new Goal.BreakBlock("minecraft:coal_ore", 2, null), List.of(),
				null, null, null, null, null);
		QuestStep coalFinale = new QuestStep("finale", List.of("coal"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		Questline coal = new Questline("coal", "석탄", "Kohle", 2,
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

		assertEquals("torches", engine.activeTrail().id(), "an untracked quest must not take over");
		assertFalse(engine.isTracked("coal"));
		assertEquals(1, engine.trackedCount());
	}

	@Test
	void questLogTracksSeveralQuestsAndProgressesThemTogether() {
		Path dir = tempDir.resolve("log");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline stones = simpleTrail("stones", "minecraft:stone", 2, 10);
		Questline ores = simpleTrail("ores", "minecraft:coal_ore", 2, 20);
		QuestEngine engine = new QuestEngine(List.of(stones, ores), catalog, learn, LOG);
		Recorder recorder = new Recorder();

		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("stones", start, recorder);
		engine.tick(obsFor(1_100L, "minecraft:stone"), recorder);
		assertEquals(1, engine.goalProgress());

		engine.startTrail("ores", start, recorder);
		assertEquals(2, engine.trackedCount());
		assertEquals("ores", engine.activeTrail().id());
		assertTrue(engine.focus("stones"));

		engine.tick(obsFor(1_200L, "minecraft:coal_ore"), recorder);

		assertEquals("stones", engine.activeTrail().id(), "focus does not follow the action");
		assertEquals(1, engine.goalProgress(), "the focused quest keeps its own counter");
		QuestEngine.TrackView oreView = engine.trackedViews().stream()
				.filter(view -> view.id().equals("ores")).findFirst().orElseThrow();
		assertEquals(1, oreView.progress(), "a background quest counts the same action");
		assertFalse(oreView.focused());
		assertEquals("stones", engine.trackedViews().get(0).id(), "the focused quest is listed first");
	}

	@Test
	void restoresEveryTrackedQuest() {
		Path dir = tempDir.resolve("log-restore");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline stones = simpleTrail("stones", "minecraft:stone", 2, 10);
		Questline ores = simpleTrail("ores", "minecraft:coal_ore", 2, 20);
		QuestEngine first = new QuestEngine(List.of(stones, ores), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		first.startTrail("stones", start, recorder);
		first.tick(obsFor(1_100L, "minecraft:stone"), recorder);
		first.startTrail("ores", start, recorder);
		first.tick(obsFor(1_200L, "minecraft:coal_ore"), recorder);
		store.flush();

		ProgressStore reloaded = new ProgressStore(dir, LOG);
		LearningService learnAgain = new LearningService(reloaded.data(), reloaded, catalog);
		QuestEngine second = new QuestEngine(List.of(stones, ores), catalog, learnAgain, LOG);

		assertEquals(2, second.trackedCount());
		assertEquals("ores", second.activeTrail().id());
		QuestEngine.TrackView stoneView = second.trackedViews().stream()
				.filter(view -> view.id().equals("stones")).findFirst().orElseThrow();
		assertEquals(1, stoneView.progress(), "a background quest survives a restart");
	}

	@Test
	void questLogKeepsAtMostSixQuests() {
		Path dir = tempDir.resolve("log-cap");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		List<Questline> many = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			many.add(simpleTrail("trail" + i, "minecraft:stone", 2, 10 + i));
		}
		QuestEngine engine = new QuestEngine(many, catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		for (int i = 0; i < 8; i++) {
			engine.startTrail("trail" + i, start, recorder);
		}

		assertEquals(QuestEngine.MAX_TRACKED, engine.trackedCount());
		assertFalse(engine.isTracked("trail0"), "the oldest background quest leaves the log first");
		assertTrue(engine.hasProgress("trail0"), "leaving the log keeps the saved progress");
		assertTrue(engine.isTracked("trail7"));
		assertEquals("trail7", engine.activeTrail().id());
	}

	@Test
	void untrackedQuestKeepsProgressButDoesNotAdvance() {
		Path dir = tempDir.resolve("log-checkbox");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline stones = simpleTrail("stones", "minecraft:stone", 3, 10);
		Questline ores = simpleTrail("ores", "minecraft:coal_ore", 3, 20);
		QuestEngine engine = new QuestEngine(List.of(stones, ores), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("stones", start, recorder);
		engine.startTrail("ores", start, recorder);
		engine.tick(obsFor(1_100L, "minecraft:coal_ore"), recorder);
		assertEquals(1, engine.progressOf("ores"));

		assertTrue(engine.setTracked("ores", false), "a quest can be taken out of the log");
		assertFalse(engine.isTracked("ores"));
		assertTrue(engine.hasProgress("ores"));
		assertEquals("stones", engine.activeTrail().id(), "the next tracked quest takes the focus");

		engine.tick(obsFor(1_200L, "minecraft:coal_ore"), recorder);
		assertEquals(1, engine.progressOf("ores"), "an unchecked quest does not advance");
		assertEquals(1, engine.trackedViews().size(), "only tracked quests are listed");
		assertTrue(engine.trackedViews().stream().noneMatch(view -> view.id().equals("ores")));

		assertTrue(engine.setTracked("ores", true));
		assertEquals(1, engine.progressOf("ores"), "checking it again resumes where it stopped");
		assertEquals("ores", engine.activeTrail().id());
	}

	@Test
	void questLogOrderIsStableSoFocusCanCycleThroughEveryQuest() {
		Path dir = tempDir.resolve("log-order");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline first = simpleTrail("first", "minecraft:stone", 3, 10);
		Questline second = simpleTrail("second", "minecraft:coal_ore", 3, 20);
		Questline third = simpleTrail("third", "minecraft:stone", 3, 30);
		QuestEngine engine = new QuestEngine(List.of(first, second, third), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("first", start, recorder);
		engine.startTrail("second", start, recorder);
		engine.startTrail("third", start, recorder);

		List<String> order = engine.trackedViews().stream().map(QuestEngine.TrackView::id).toList();
		assertEquals(List.of("first", "second", "third"), order);

		assertTrue(engine.focus("first"));
		assertEquals(List.of("first", "second", "third"), engine.trackedViews().stream()
				.map(QuestEngine.TrackView::id).toList(), "focusing must not reorder the log");
		assertTrue(engine.focus("second"));
		assertTrue(engine.focus("third"));
		assertEquals("third", engine.activeTrail().id());
	}

	@Test
	void focusFollowsTheLogAndNotTheWorld() {
		Path dir = tempDir.resolve("log-focus");
		ProgressStore store = new ProgressStore(dir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"),
				concept("torch", "횃불", "Fackel")));
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline stones = simpleTrail("stones", "minecraft:stone", 3, 10);
		Questline ores = simpleTrail("ores", "minecraft:coal_ore", 3, 20);
		QuestEngine engine = new QuestEngine(List.of(stones, ores), catalog, learn, LOG);
		Recorder recorder = new Recorder();
		Observation start = night();
		start.now = 1_000L;
		engine.startTrail("stones", start, recorder);
		engine.startTrail("ores", start, recorder);

		engine.tick(obsFor(1_100L, "minecraft:stone"), recorder);

		assertEquals("ores", engine.activeTrail().id(), "focus does not follow the action");
		assertEquals(1, engine.progressOf("stones"), "the other checked quest still advances");
		assertTrue(engine.focus("stones"));
		assertEquals("stones", engine.activeTrail().id());
	}

	private static Questline simpleTrail(String id, String block, int count, int order) {
		QuestStep step = new QuestStep("s1", List.of("stone"),
				new Goal.BreakBlock(block, count, null), List.of(),
				null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		return new Questline(id, id, id + "-de", 1, List.of("stone"),
				Questline.Requires.none(), List.of(step), finale,
				"끝", "Ende", id, order, 1, false);
	}

}
