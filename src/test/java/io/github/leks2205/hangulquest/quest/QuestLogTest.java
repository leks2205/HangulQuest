package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest log: focus, eviction when it is full, and the "pick the next quest" popup. */
class QuestLogTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("quest-log-test");

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;
	private final Recorder recorder = new Recorder();

	@BeforeEach
	void setUp() {
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		ProgressStore store = new ProgressStore(tempDir.resolve("run"), LOG);
		learn = new LearningService(store.data(), store, catalog);
	}

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

	/** One step breaking {@code count} of a block, then a torch finale. */
	private static Questline trail(String id, String block, int count, int order, List<String> parents) {
		QuestStep step = new QuestStep("s1", List.of("stone"),
				new Goal.BreakBlock(block, count, null), List.of(), null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(), null, null, null, null, null);
		return new Questline(id, id, id + "-de", 1, List.of("stone"),
				new Questline.Requires(0, null, parents), List.of(step), finale,
				"끝", "Ende", id, order, 1, false);
	}

	private static Questline trail(String id, String block, int count, int order) {
		return trail(id, block, count, order, List.of());
	}

	private static Observation obs(long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.clockTime = 6000L;
		obs.dimension = "minecraft:overworld";
		return obs;
	}

	private static Observation breaking(long now, String block) {
		Observation obs = obs(now);
		obs.brokenBlocks.add(block);
		return obs;
	}

	private static Observation placing(long now) {
		Observation obs = obs(now);
		obs.placedBlocks.add("minecraft:torch");
		return obs;
	}

	/** Finishes a one-block trail on its own. */
	private void finish(QuestEngine engine, String block, long now) {
		engine.tick(breaking(now, block), recorder);
		engine.tick(placing(now + 50), recorder);
	}

	// ---------------------------------------------------------------- focus

	@Test
	void focusNeverFallsBackToAnUntrackedTrail() {
		QuestEngine engine = new QuestEngine(List.of(trail("a", "minecraft:a", 1, 10),
				trail("b", "minecraft:b", 1, 20)), catalog, learn, LOG);
		engine.startTrail("a", obs(1_000), recorder);
		engine.startTrail("b", obs(1_000), recorder);
		engine.setTracked("a", false);
		assertTrue(engine.hasProgress("a"), "the paused quest keeps its state");

		finish(engine, "minecraft:b", 2_000);

		assertTrue(learn.data().quests.completed.containsKey("b"));
		assertNull(engine.activeTrail(), "the paused quest must not become the focused one");
		assertNull(engine.focusedId());
		assertEquals(0, engine.trackedCount());
		assertNull(learn.data().quests.active);
	}

	@Test
	void untrackingTheFocusedTrailFocusesAnotherCheckedOne() {
		QuestEngine engine = new QuestEngine(List.of(trail("a", "minecraft:a", 1, 10),
				trail("b", "minecraft:b", 1, 20), trail("c", "minecraft:c", 1, 30)), catalog, learn, LOG);
		engine.startTrail("a", obs(1_000), recorder);
		engine.startTrail("b", obs(1_000), recorder);
		engine.startTrail("c", obs(1_000), recorder);
		engine.setTracked("b", false);

		engine.setTracked("c", false);

		assertEquals("a", engine.focusedId());
	}

	// -------------------------------------------------------------- eviction

	private QuestEngine fullLog(int trails) {
		List<Questline> many = new ArrayList<>();
		for (int i = 0; i < trails; i++) {
			many.add(trail("t" + i, "minecraft:b" + i, 2, 10 + i));
		}
		QuestEngine engine = new QuestEngine(many, catalog, learn, LOG);
		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			engine.startTrail("t" + i, obs(1_000), recorder);
		}
		return engine;
	}

	@Test
	void fullLogDropsTheOldestQuestWithoutProgressAndKeepsWorkedOnOnes() {
		QuestEngine engine = fullLog(8);
		engine.tick(breaking(2_000, "minecraft:b0"), recorder);
		engine.tick(breaking(2_050, "minecraft:b1"), recorder);

		assertTrue(engine.startTrail("t6", obs(3_000), recorder));

		assertTrue(engine.isTracked("t0"), "t0 has progress and stays");
		assertTrue(engine.isTracked("t1"), "t1 has progress and stays");
		assertFalse(engine.isTracked("t2"), "the oldest quest without progress makes room");
		assertTrue(engine.isTracked("t6"));
		assertEquals(QuestEngine.MAX_TRACKED, engine.trackedCount());
	}

	@Test
	void fullLogOfWorkedOnQuestsDeclinesInsteadOfEvicting() {
		QuestEngine engine = fullLog(8);
		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			engine.tick(breaking(2_000 + i * 50L, "minecraft:b" + i), recorder);
		}

		assertFalse(engine.startTrail("t6", obs(3_000), recorder), "no quest with progress is dropped");
		assertFalse(engine.setTracked("t7", true));

		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			assertTrue(engine.isTracked("t" + i), "t" + i + " keeps its place");
		}
		assertFalse(engine.isTracked("t6"));
		assertFalse(engine.hasProgress("t6"), "a declined quest leaves no state behind");
		assertEquals("t5", engine.focusedId());
	}

	// ------------------------------------------------------------- next quest

	@Test
	void popupCountsTheQuestLogNotEveryStartedTrail() {
		List<Questline> all = new ArrayList<>();
		all.add(trail("t", "minecraft:t", 1, 5));
		all.add(trail("c1", "minecraft:c1", 1, 100, List.of("t")));
		all.add(trail("c2", "minecraft:c2", 1, 101, List.of("t")));
		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			all.add(trail("p" + i, "minecraft:p" + i, 2, 50 + i));
		}
		QuestEngine engine = new QuestEngine(all, catalog, learn, LOG);
		// Six paused trails: started once, then taken out of the log.
		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			engine.startTrail("p" + i, obs(1_000), recorder);
		}
		for (int i = 0; i < QuestEngine.MAX_TRACKED; i++) {
			engine.setTracked("p" + i, false);
		}
		engine.startTrail("t", obs(1_000), recorder);
		assertEquals(1, engine.trackedCount());

		finish(engine, "minecraft:t", 2_000);

		assertEquals(1, recorder.choices.size(), "two follow-ups are offered, not started for the player");
		assertEquals(2, recorder.choices.get(0).size());
		assertNull(engine.activeTrail());
		assertEquals(2, engine.pendingNextChoices().size());
	}

	/** t is finished while q stays in the log, so another trail is active when the popup opens. */
	private QuestEngine popupWithOtherTrailActive() {
		QuestEngine engine = new QuestEngine(List.of(trail("t", "minecraft:t", 1, 5),
				trail("q", "minecraft:q", 3, 6),
				trail("c1", "minecraft:c1", 1, 100, List.of("t")),
				trail("c2", "minecraft:c2", 1, 101, List.of("t"))), catalog, learn, LOG);
		engine.startTrail("q", obs(1_000), recorder);
		engine.startTrail("t", obs(1_000), recorder);
		finish(engine, "minecraft:t", 2_000);
		return engine;
	}

	@Test
	void popupStaysOpenWhileAnotherTrailIsActive() {
		QuestEngine engine = popupWithOtherTrailActive();

		assertEquals(1, recorder.choices.size());
		assertEquals("q", engine.activeTrail().id(), "the focus moved on to the other quest");
		assertEquals(2, engine.pendingNextChoices().size());
		for (int i = 0; i < 20; i++) {
			engine.tick(obs(3_000 + i * 50L), recorder);
			assertEquals(2, engine.pendingNextChoices().size(), "ticks must not wipe the popup");
		}
	}

	@Test
	void popupClosesWhenThePlayerPicksOrMovesOn() {
		QuestEngine picked = popupWithOtherTrailActive();
		assertTrue(picked.startTrail("c2", obs(4_000), recorder));
		assertTrue(picked.pendingNextChoices().isEmpty());

		setUp();
		QuestEngine moved = popupWithOtherTrailActive();
		assertTrue(moved.setTracked("c1", true));
		assertTrue(moved.pendingNextChoices().isEmpty());
	}

	@Test
	void popupRunsOutAfterTheOfferTime() {
		QuestEngine engine = popupWithOtherTrailActive();
		engine.tick(obs(2_050 + NextQuestPolicy.OFFER_MS + 1_000), recorder);
		assertEquals(2, engine.pendingNextChoices().size(), "the UI still gets its own timeout first");

		engine.tick(obs(2_050 + NextQuestPolicy.OFFER_MS + 10_000), recorder);

		assertTrue(engine.pendingNextChoices().isEmpty());
	}

	private static final class Recorder implements QuestEngine.Feedback {
		final List<List<Questline>> choices = new ArrayList<>();

		@Override
		public void banner(String ko, String de, boolean success) {
		}

		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void chooseNext(List<Questline> options) {
			choices.add(options);
		}
	}
}
