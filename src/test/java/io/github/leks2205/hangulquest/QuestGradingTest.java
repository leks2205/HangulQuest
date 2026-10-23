package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest steps and the hint feed the grader: a due word done unaided is Good, after a hint Hard, and
 * everything else (first meeting, not yet due, creative mode) is only an encounter.
 */
class QuestGradingTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("quest-grading-test");
	private static final String STONE = "concept:stone";
	/** The stone card is reviewed at 1 s (first step, due in 1 min) and again at 61 s. */
	private static final long FIRST = 1_000L;
	private static final long NOW = 700_000_000L;

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;
	private QuestEngine engine;
	private final Recorder recorder = new Recorder();

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	private static Questline trail() {
		QuestStep step = new QuestStep("s1", List.of("stone"),
				new Goal.BreakBlock("minecraft:stone", 1, null), List.of(),
				"돌을 캐요.", "Baue Stein ab.", "돌을 캐요.", "Baue Stein ab.", null);
		QuestStep finale = new QuestStep("finale", List.of(),
				new Goal.PlaceBlock("minecraft:torch", 1), List.of(), null, null, null, null, null);
		return new Questline("t", "길", "Pfad", 1, List.of("stone"),
				Questline.Requires.none(), List.of(step), finale, "끝났어요.", "Fertig.", "t-badge", 10, 1, false);
	}

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LOG);
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		learn = new LearningService(store.data(), store, catalog);
		engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
	}

	private static Observation breaking(long now) {
		Observation obs = new Observation();
		obs.dimension = "minecraft:overworld";
		obs.now = now;
		obs.brokenBlocks.add("minecraft:stone");
		return obs;
	}

	private WordProgress stone() {
		return learn.data().words.get(STONE);
	}

	/** The stone has been through two good reviews and is due again at {@code NOW}. */
	private CardState dueStone() {
		learn.answer(STONE, true, FIRST);
		learn.answer(STONE, true, FIRST + 61_000L);
		CardState card = stone().fsrs.copy();
		assertTrue(card.due < NOW, "due before the quest step");
		return card;
	}

	private void startTrail() {
		Observation start = new Observation();
		start.now = NOW - 1000;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, recorder);
	}

	private ReviewLog.Event lastEvent() {
		learn.reviewLog().flush();
		List<ReviewLog.Event> events = ReviewLog.readAll(tempDir);
		return events.get(events.size() - 1);
	}

	@Test
	void aDueWordDoneUnaidedIsGood() {
		CardState before = dueStone();
		startTrail();
		engine.tick(breaking(NOW), recorder);

		assertEquals(Srs.fsrs().review(before, Fsrs.GOOD, NOW), stone().fsrs);
		ReviewLog.Event event = lastEvent();
		assertEquals(ReviewMode.QUEST_UNAIDED, event.mode());
		assertEquals(Fsrs.GOOD, event.grade());
		assertEquals("t/s1/primary", event.ctx());
		assertEquals(3, learn.totalReviews(), "two quiz answers and the quest recall");
	}

	@Test
	void aHintTurnsGoodIntoHard() {
		CardState before = dueStone();
		startTrail();
		Observation hintObs = new Observation();
		hintObs.now = NOW;
		assertTrue(engine.hint(hintObs, recorder).sidequestStarted());
		// the hint is shown and the sidequest answered; neither moves the schedule
		assertEquals(before, stone().fsrs);
		engine.answerSidequest(engine.sideQuest().correctIndex(), recorder);
		engine.answerSidequest(engine.sideQuest().correctIndex(), recorder);
		assertFalse(engine.sidequestActive());
		assertEquals(before, stone().fsrs, "sidequest answers are practice only");

		engine.tick(breaking(NOW + 100), recorder);

		assertEquals(Srs.fsrs().review(before, Fsrs.HARD, NOW + 100), stone().fsrs);
		ReviewLog.Event event = lastEvent();
		assertEquals(ReviewMode.QUEST_HINTED, event.mode());
		assertEquals(Fsrs.HARD, event.grade());
	}

	@Test
	void creativeModeNeverGrades() {
		CardState before = dueStone();
		startTrail();
		Observation obs = breaking(NOW);
		obs.creative = true;
		engine.tick(obs, recorder);

		assertEquals(1, recorder.steps, "the step itself still completes");
		assertEquals(before, stone().fsrs, "an assisted step is an encounter only");
		assertEquals(ReviewMode.EXPOSURE, lastEvent().mode());
		assertEquals(null, lastEvent().grade());
	}

	@Test
	void aWordThatIsNotDueIsOnlyExposed() {
		learn.answer(STONE, true, FIRST);
		learn.answer(STONE, true, FIRST + 61_000L);
		stone().fsrs.due = NOW + 86_400_000L; // not due for another day
		CardState before = stone().fsrs.copy();
		startTrail();
		engine.tick(breaking(NOW), recorder);

		assertEquals(before, stone().fsrs);
		assertEquals(ReviewMode.EXPOSURE, lastEvent().mode());
	}

	@Test
	void theFirstMeetingIsOnlyExposed() {
		startTrail();
		engine.tick(breaking(NOW), recorder);

		assertTrue(stone().fsrs.isNew(), "meeting a word in a quest does not start its schedule");
		assertEquals(0, learn.totalReviews());
		assertEquals(ReviewMode.EXPOSURE, lastEvent().mode());
	}

	@Test
	void aNewWordNeedsTimeToSettleBeforeAQuestCountsAsRecall() {
		Word word = catalog.byId(STONE).orElseThrow();
		learn.expose(word, NOW - 1000); // met a second ago
		startTrail();
		engine.tick(breaking(NOW), recorder);
		assertTrue(stone().fsrs.isNew(), "a second later is no recall");

		ProgressStore other = new ProgressStore(tempDir.resolve("settled"), LOG);
		LearningService settled = new LearningService(other.data(), other, catalog);
		settled.expose(word, NOW - 3_600_000L);
		QuestEngine second = new QuestEngine(List.of(trail()), catalog, settled, LOG);
		Observation start = new Observation();
		start.now = NOW - 1000;
		start.dimension = "minecraft:overworld";
		second.startTrail("t", start, recorder);
		second.tick(breaking(NOW), recorder);
		assertFalse(settled.data().words.get(STONE).fsrs.isNew(), "an hour later it is a recall");
	}

	@Test
	void everyEncounterRecordsItsContext() {
		learn.expose(catalog.byId(STONE).orElseThrow(), NOW - 7_200_000L);
		startTrail();
		engine.tick(breaking(NOW), recorder);

		List<String> contexts = ExposureLedger.of(stone()).contexts;
		assertEquals(List.of("BREAK_BLOCK|MINE|primary|t"), contexts);
		assertEquals(2, ExposureLedger.encounters(stone()));
	}

	private static final class Recorder implements QuestEngine.Feedback {
		int steps;

		@Override
		public void banner(String ko, String de, boolean success) {
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
		}

		@Override
		public void trailCompleted(Questline trail) {
		}

		@Override
		public void chooseNext(List<Questline> options) {
		}
	}
}
