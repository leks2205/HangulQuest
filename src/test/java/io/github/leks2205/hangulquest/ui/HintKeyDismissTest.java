package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing a window with the hint key is not asking for a hint. The key goes through the real hint path of
 * the quest engine here: with a window open the engine is never called, so the step is not marked as
 * helped, no sidequest starts, and the later grade, the review log and the stats are those of an unaided
 * step. With no window open the same press is a hint, which turns Good into Hard.
 */
class HintKeyDismissTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("hint-key-dismiss-test");
	private static final String STONE = "concept:stone";
	private static final long FIRST = 1_000L;
	private static final long NOW = 700_000_000L;

	@TempDir
	Path tempDir;

	private LearningService learn;
	private QuestEngine engine;
	private final HudWindows windows = new HudWindows();
	private int hintsAsked;
	private int windowsOpened;

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

	private static final QuestEngine.Feedback QUIET = new QuestEngine.Feedback() {
		@Override
		public void banner(String ko, String de, boolean success) {
		}


		@Override
		public void sound(boolean success) {
		}
	};

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LOG);
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		learn = new LearningService(store.data(), store, catalog);
		engine = new QuestEngine(List.of(trail()), catalog, learn, LOG);
		// the stone is due, so finishing the step is a graded recall
		learn.answer(STONE, true, FIRST);
		learn.answer(STONE, true, FIRST + 61_000L);
		Observation start = new Observation();
		start.now = NOW - 1000;
		start.dimension = "minecraft:overworld";
		engine.startTrail("t", start, QUIET);
	}

	/** What the hint key does when no window is open: the call that ends in QuestEngine.hint. */
	private void askForHint() {
		hintsAsked++;
		Observation obs = new Observation();
		obs.now = NOW;
		engine.hint(obs, QUIET);
	}

	private void openAWindow() {
		windows.show(HudWindows.Window.of(HudWindows.Kind.NOTICE, "banner:" + windowsOpened++, null));
		windows.tick(NOW, true, false);
	}

	private ReviewLog.Event finishTheStep() {
		Observation breaking = new Observation();
		breaking.dimension = "minecraft:overworld";
		breaking.now = NOW + 100;
		breaking.brokenBlocks.add("minecraft:stone");
		engine.tick(breaking, QUIET);
		learn.reviewLog().flush();
		List<ReviewLog.Event> events = ReviewLog.readAll(tempDir);
		return events.get(events.size() - 1);
	}

	@Test
	void aPressThatClosesAWindowNeverReachesTheHintService() {
		openAWindow();
		int reviewsBefore = learn.totalReviews();

		boolean closed = windows.pressHintKey(true, this::askForHint);

		assertTrue(closed);
		assertEquals(0, hintsAsked, "the key was used up by the window");
		assertFalse(engine.hintUsed(), "the step is not marked as helped");
		assertNull(engine.sideQuest(), "no sidequest started");
		assertEquals(reviewsBefore, learn.totalReviews());
	}

	@Test
	void theStepAfterAClosedWindowIsGradedAsUnaided() {
		openAWindow();
		windows.pressHintKey(true, this::askForHint);

		ReviewLog.Event event = finishTheStep();

		assertEquals(ReviewMode.QUEST_UNAIDED, event.mode());
		assertEquals(Fsrs.GOOD, event.grade());
	}

	@Test
	void theSamePressWithNoWindowOpenIsAHintAndTurnsGoodIntoHard() {
		boolean closed = windows.pressHintKey(true, this::askForHint);

		assertFalse(closed);
		assertEquals(1, hintsAsked);
		assertTrue(engine.hintUsed(), "this is the control: a real hint marks the step");
		// the hint started a sidequest; the step carries on once it is answered
		engine.answerSidequest(engine.sideQuest().correctIndex(), QUIET);
		engine.answerSidequest(engine.sideQuest().correctIndex(), QUIET);

		ReviewLog.Event event = finishTheStep();
		assertEquals(ReviewMode.QUEST_HINTED, event.mode());
		assertEquals(Fsrs.HARD, event.grade());
	}

	@Test
	void closingTheHintWindowItselfIsNotASecondHint() {
		windows.pressHintKey(true, this::askForHint); // no window: a hint
		windows.show(HudWindows.Window.of(HudWindows.Kind.HINT, "hint:x", null));
		windows.tick(NOW, true, false);
		assertEquals(1, hintsAsked);

		windows.pressHintKey(true, this::askForHint); // the hint panel is open: this closes it

		assertEquals(1, hintsAsked, "still the one hint");
		assertFalse(windows.isOpen());
	}

	@Test
	void everyPressOfAQueueOfWindowsIsUsedUpBeforeTheNextOneAsksForAHint() {
		openAWindow();
		openAWindow();
		openAWindow();

		for (int press = 0; press < 3; press++) {
			assertTrue(windows.pressHintKey(true, this::askForHint));
			windows.tick(NOW + 50 * (press + 1), true, false);
		}
		assertEquals(0, hintsAsked, "three windows, three presses, no hint");
		assertFalse(engine.hintUsed());

		assertFalse(windows.pressHintKey(true, this::askForHint));
		assertEquals(1, hintsAsked, "the fourth press finds nothing open: now it is a hint");
		assertTrue(engine.hintUsed());
	}

	@Test
	void aWindowThatCannotBeSeenDoesNotSwallowTheKey() {
		openAWindow();

		boolean closed = windows.pressHintKey(false, this::askForHint);

		assertFalse(closed);
		assertEquals(1, hintsAsked);
	}
}
