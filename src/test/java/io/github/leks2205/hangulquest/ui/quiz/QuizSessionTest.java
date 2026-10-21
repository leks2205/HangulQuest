package io.github.leks2205.hangulquest.ui.quiz;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quiz state machine: nothing is recorded unless the player opened the quiz and answered. */
class QuizSessionTest {
	private static final long T0 = 50_000L;

	private final List<String> recorded = new ArrayList<>();
	private final QuizSession.Recorder recorder = (wordId, correct) -> recorded.add(wordId + ":" + correct);

	private static QuizSession.Quiz quiz() {
		return new QuizSession.Quiz("concept:stone", List.of("Stein", "Erde", "Sand"), 1);
	}

	@Test
	void anAutomaticQuizOnlyShowsAPrompt() {
		QuizSession session = new QuizSession();

		assertTrue(session.offer(quiz(), T0));

		assertEquals(QuizSession.State.PROMPT, session.state());
		assertTrue(session.promptVisible(T0));
		assertEquals(QuizSession.PROMPT_MS, session.promptRemainingMs(T0));
		assertEquals(Optional.empty(), session.answer(1, recorder), "a prompt cannot be answered");
		assertTrue(recorded.isEmpty());
	}

	@Test
	void aTimedOutPromptDisappearsAndRecordsNothing() {
		QuizSession session = new QuizSession();
		session.offer(quiz(), T0);

		assertFalse(session.tick(T0 + QuizSession.PROMPT_MS - 1), "still waiting just before the timeout");
		assertTrue(session.promptVisible(T0 + QuizSession.PROMPT_MS - 1));
		assertTrue(session.tick(T0 + QuizSession.PROMPT_MS));

		assertEquals(QuizSession.State.IDLE, session.state());
		assertFalse(session.promptVisible(T0 + QuizSession.PROMPT_MS));
		assertNull(session.quiz());
		assertFalse(session.open(T0 + QuizSession.PROMPT_MS), "an expired prompt cannot be opened");
		assertEquals(Optional.empty(), session.answer(1, recorder));
		assertTrue(recorded.isEmpty(), "a prompt that timed out records no review, wrong or right");
	}

	@Test
	void anExpiredButNotYetTickedPromptCannotBeOpened() {
		QuizSession session = new QuizSession();
		session.offer(quiz(), T0);

		assertFalse(session.open(T0 + QuizSession.PROMPT_MS + 5));
		assertEquals(Optional.empty(), session.answer(1, recorder));
		assertTrue(recorded.isEmpty());
	}

	@Test
	void openingThePromptMakesItAnswerableAndAnsweringRecordsOnce() {
		QuizSession session = new QuizSession();
		session.offer(quiz(), T0);

		assertTrue(session.open(T0 + 5_000));
		assertEquals(QuizSession.State.ACTIVE, session.state());
		assertFalse(session.tick(T0 + 10 * QuizSession.PROMPT_MS), "an open quiz does not time out");

		Optional<QuizSession.Result> result = session.answer(1, recorder);

		assertEquals(Optional.of(new QuizSession.Result("concept:stone", true)), result);
		assertEquals(List.of("concept:stone:true"), recorded);
		assertEquals(QuizSession.State.IDLE, session.state());
		assertEquals(Optional.empty(), session.answer(1, recorder), "a quiz can be answered only once");
		assertEquals(1, recorded.size());
	}

	@Test
	void aWrongAnswerIsRecordedAsWrong() {
		QuizSession session = new QuizSession();
		session.begin(quiz());

		assertEquals(Optional.of(new QuizSession.Result("concept:stone", false)), session.answer(0, recorder));
		assertEquals(List.of("concept:stone:false"), recorded);
	}

	@Test
	void dismissingRecordsNothing() {
		QuizSession session = new QuizSession();
		session.begin(quiz());

		session.dismiss();

		assertEquals(QuizSession.State.IDLE, session.state());
		assertEquals(Optional.empty(), session.answer(1, recorder));
		assertTrue(recorded.isEmpty());
	}

	@Test
	void dismissingAPromptRecordsNothingEither() {
		QuizSession session = new QuizSession();
		session.offer(quiz(), T0);

		session.dismiss();

		assertFalse(session.promptVisible(T0));
		assertTrue(recorded.isEmpty());
	}

	@Test
	void outOfRangeIndexesAreIgnoredAndKeepTheQuizOpen() {
		QuizSession session = new QuizSession();
		session.begin(quiz());

		assertEquals(Optional.empty(), session.answer(3, recorder));
		assertEquals(Optional.empty(), session.answer(-1, recorder));

		assertEquals(QuizSession.State.ACTIVE, session.state());
		assertTrue(recorded.isEmpty());
	}

	@Test
	void aSecondOfferIsIgnoredWhileOneIsPending() {
		QuizSession session = new QuizSession();
		session.offer(quiz(), T0);

		assertFalse(session.offer(new QuizSession.Quiz("concept:torch", List.of("a", "b"), 0), T0 + 1));
		assertEquals("concept:stone", session.quiz().wordId());
	}
}
