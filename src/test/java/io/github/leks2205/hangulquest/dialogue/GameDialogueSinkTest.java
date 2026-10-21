package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a conversation writes into the learning data: grammar in the log only, words as encounters and practice. */
class GameDialogueSinkTest {
	private static final String APPLE = "concept:apple";
	private static final long NOW = 1_800_000_000_000L;

	@TempDir
	Path tempDir;

	private LearningService learn;
	private final List<String> startedTrails = new ArrayList<>();
	private GameDialogueSink sink;

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LoggerFactory.getLogger("dialogue-sink-test"));
		Concept concept = new Concept();
		concept.id = "apple";
		concept.ko = "사과";
		concept.de = "Apfel";
		concept.category = "FOOD";
		learn = new LearningService(store.data(), store, Catalog.ofConcepts(List.of(concept)));
		sink = new GameDialogueSink(learn, learn.catalog(), id -> startedTrails.add(id), () -> NOW, "child");
	}

	private List<ReviewLog.Event> events() {
		learn.reviewLog().flush();
		return ReviewLog.readAll(tempDir);
	}

	@Test
	void aGrammarPointMetIsLoggedAsAnExposureWithoutAGrade() {
		sink.exposeGrammar("L6.banmal");
		ReviewLog.Event event = events().get(0);
		assertEquals("L6.banmal", event.item());
		assertEquals(ReviewKind.GRAMMAR, event.kind());
		assertEquals(ReviewMode.EXPOSURE, event.mode());
		assertNull(event.grade());
		assertEquals("dialogue/child", event.ctx());
		assertEquals(NOW, event.t());
	}

	@Test
	void aSpeechLevelChoiceIsLoggedAsAGuessAndNeverSchedules() {
		sink.practiceGrammar("L6.banmal", 2, true);
		sink.practiceGrammar("L6.banmal", 2, false);
		sink.practiceGrammar("L5.hapsyo", 3, true);
		sink.practiceGrammar("L5.hapsyo", 4, true);
		List<ReviewLog.Event> events = events();
		assertEquals(4, events.size());
		assertEquals(List.of(ReviewMode.MC2, ReviewMode.MC2, ReviewMode.MC3, ReviewMode.MC3),
				events.stream().map(ReviewLog.Event::mode).toList(), "never counted as more than a three-way guess");
		assertTrue(events.stream().allMatch(event -> !event.mode().schedules() && !event.mode().recall()));
		assertEquals(List.of(3, 1, 3, 3), events.stream().map(ReviewLog.Event::grade).toList());
		assertTrue(learn.data().words.isEmpty(), "grammar practice creates no word and moves no card");
		assertTrue(learn.data().days.isEmpty());
	}

	@Test
	void aWordSeenInALineIsAnEncounterInTheDialogueContext() {
		sink.exposeWord(APPLE);
		WordProgress progress = learn.data().words.get(APPLE);
		assertNotNull(progress);
		assertEquals(1, progress.seen);
		assertEquals(0, progress.correct);
		assertEquals(1, progress.ledger.encounters);
		assertEquals(0, progress.ledger.retrievals);
		assertEquals(List.of("DIALOGUE||dialogue|child"), progress.ledger.contexts);
		assertTrue(progress.fsrs.isNew(), "an encounter does not review the card");
		assertEquals(ReviewMode.EXPOSURE, events().get(0).mode());
	}

	@Test
	void aWordThatIsNotInTheCatalogIsIgnored() {
		sink.exposeWord("concept:nope");
		assertTrue(learn.data().words.isEmpty());
		assertTrue(events().isEmpty());
	}

	@Test
	void aTypedCopyOfAWordIsPracticeThatLeavesTheScheduleAlone() {
		sink.exposeWord(APPLE);
		CardState before = learn.data().words.get(APPLE).fsrs.copy();
		sink.practiceWord(APPLE, true);
		sink.practiceWord(APPLE, false);
		WordProgress progress = learn.data().words.get(APPLE);
		assertEquals(1, progress.correct);
		assertEquals(1, progress.wrong);
		assertEquals(before, progress.fsrs);
		assertFalse(progress.known);
		List<ReviewLog.Event> events = events();
		assertEquals(ReviewMode.MC2, events.get(1).mode());
		assertEquals(ReviewKind.WORD, events.get(1).kind());
	}

	@Test
	void startingATrailIsHandedOnAndTheAnswerPassedBack() {
		assertTrue(sink.startTrail("essenspfad"));
		assertEquals(List.of("essenspfad"), startedTrails);
		GameDialogueSink refusing = new GameDialogueSink(learn, learn.catalog(), id -> false, () -> NOW, "child");
		assertFalse(refusing.startTrail("essenspfad"));
	}
}
