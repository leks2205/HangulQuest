package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What each kind of answer does to the schedule, the counts and the log. */
class LearningGradingTest {
	private static final String STONE = "concept:stone";
	private static final long T0 = 1_800_000_000_000L;

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir, LoggerFactory.getLogger("grading-test"));
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		catalog = Catalog.ofConcepts(List.of(concept));
		learn = new LearningService(store.data(), store, catalog);
	}

	private WordProgress stone() {
		return learn.data().words.get(STONE);
	}

	private List<ReviewLog.Event> events() {
		learn.reviewLog().flush();
		return ReviewLog.readAll(tempDir);
	}

	/** Gives the stone an established card so changes are easy to see. */
	private CardState reviewedStone() {
		learn.answer(STONE, true, T0);
		learn.answer(STONE, true, T0 + 11 * Srs.MINUTE);
		return stone().fsrs.copy();
	}

	@Test
	void fourOptionsGoThroughTheGraderAndMoveTheCard() {
		CardState before = reviewedStone();
		long now = before.due;
		learn.answerMc(STONE, 4, true, 800, now);
		assertEquals(Srs.fsrs().review(before, Fsrs.GOOD, now), stone().fsrs);

		CardState afterGood = stone().fsrs.copy();
		learn.answerMc(STONE, 4, true, 90_000, afterGood.due);
		assertEquals(Srs.fsrs().review(afterGood, Fsrs.HARD, afterGood.due), stone().fsrs, "slow is Hard");

		CardState afterHard = stone().fsrs.copy();
		learn.answerMc(STONE, 4, false, 800, afterHard.due);
		assertEquals(Srs.fsrs().review(afterHard, Fsrs.AGAIN, afterHard.due), stone().fsrs, "wrong is Again");
		assertEquals(CardState.State.RELEARNING, stone().fsrs.state);
		assertEquals(stone().fsrs.due, stone().dueAt, "the old due field follows the card");
	}

	@Test
	void twoAndThreeOptionsChangeNoScheduleButAreCountedAndLogged() {
		CardState before = reviewedStone();
		long dueAt = stone().dueAt;
		int level = stone().level;
		int reviews = learn.totalReviews();
		int seen = stone().seen;

		learn.answerMc(STONE, 3, true, 500, T0 + 20 * Srs.MINUTE);
		learn.answerMc(STONE, 3, false, 500, T0 + 21 * Srs.MINUTE);
		learn.answerMc(STONE, 2, true, 500, T0 + 22 * Srs.MINUTE);
		learn.answerMc(STONE, 2, false, 500, T0 + 23 * Srs.MINUTE);

		assertEquals(before, stone().fsrs);
		assertEquals(dueAt, stone().dueAt);
		assertEquals(level, stone().level);
		assertEquals(reviews, learn.totalReviews());
		assertEquals(seen + 4, stone().seen);
		List<ReviewLog.Event> events = events();
		List<ReviewMode> modes = events.subList(events.size() - 4, events.size()).stream().map(ReviewLog.Event::mode).toList();
		assertEquals(List.of(ReviewMode.MC3, ReviewMode.MC3, ReviewMode.MC2, ReviewMode.MC2), modes);
	}

	@Test
	void theSidequestPracticeIsLoggedAsAGuessAndNeverSchedules() {
		CardState before = reviewedStone();
		learn.practice(STONE, 3, true, T0 + 30 * Srs.MINUTE);
		learn.practice(STONE, 2, false, T0 + 31 * Srs.MINUTE);
		learn.practice(STONE, 4, true, T0 + 32 * Srs.MINUTE); // even four options: a sidequest is practice
		assertEquals(before, stone().fsrs);
		List<ReviewLog.Event> events = events();
		assertEquals(ReviewMode.MC3, events.get(events.size() - 3).mode());
		assertEquals(ReviewMode.MC2, events.get(events.size() - 2).mode());
		assertEquals(ReviewMode.MC3, events.get(events.size() - 1).mode());
	}

	@Test
	void anExposureNeverChangesTheScheduleAndLogsNoGrade() {
		CardState before = reviewedStone();
		long dueAt = stone().dueAt;
		Word word = catalog.byId(STONE).orElseThrow();
		for (int i = 0; i < 5; i++) {
			learn.expose(word, T0 + 40 * Srs.MINUTE + i);
		}
		assertEquals(before, stone().fsrs);
		assertEquals(dueAt, stone().dueAt);
		ReviewLog.Event last = events().getLast();
		assertEquals(ReviewMode.EXPOSURE, last.mode());
		assertNull(last.grade());
	}

	@Test
	void aSelfGradedCardIsScheduledAsChosen() {
		CardState before = reviewedStone();
		long now = before.due;
		learn.answerSelfGraded(STONE, Fsrs.EASY, "self-card", now);
		assertEquals(Srs.fsrs().review(before, Fsrs.EASY, now), stone().fsrs);
		ReviewLog.Event last = events().getLast();
		assertEquals(ReviewMode.SELF, last.mode());
		assertEquals(Fsrs.EASY, last.grade());
		assertEquals("self-card", last.ctx());
	}

	@Test
	void aTypedAnswerOneJamoOffIsHard() {
		CardState before = reviewedStone();
		long now = before.due;
		learn.answerGraded(STONE, Grader.typed(1), 3000, "typed-card", now);
		assertEquals(Srs.fsrs().review(before, Fsrs.HARD, now), stone().fsrs);
	}

	@Test
	void aProbeIsLoggedWithoutMovingTheSchedule() {
		CardState before = reviewedStone();
		learn.answerGraded(STONE, Grader.probe(false), 3000, "probe", before.due);
		assertEquals(before, stone().fsrs);
		assertEquals(ReviewMode.PROBE, events().getLast().mode());
	}

	@Test
	void everyLineCarriesTheFieldsOfTheRoadmap() {
		reviewedStone();
		ReviewLog.Event first = events().getFirst();
		assertEquals(STONE, first.item());
		assertEquals(ReviewKind.WORD, first.kind());
		assertEquals(ReviewMode.MC4, first.mode());
		assertEquals(Fsrs.GOOD, first.grade());
		assertEquals(T0, first.t());
		assertEquals(-1, first.elapsedDays(), "no earlier review");
		assertEquals(1, first.v());
		assertNull(first.arm());
		assertTrue(events().get(1).elapsedDays() >= 0);
	}
}
