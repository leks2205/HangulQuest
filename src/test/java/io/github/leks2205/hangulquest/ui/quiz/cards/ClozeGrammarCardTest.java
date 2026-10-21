package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClozeGrammarCardTest {
	private static final long T0 = 5_000_000L;
	private static final GrammarBook BOOK = GrammarBook.shared();

	@TempDir
	Path dir;

	private LearningService learn;

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("cloze-test"));
		learn = new LearningService(store.data(), store, Catalog.ofConcepts(List.of()));
	}

	@AfterEach
	void tearDown() {
		Lang.set("EN");
	}

	private List<ReviewLog.Event> grammarLog() {
		learn.reviewLog().flush();
		return ReviewLog.readAll(dir).stream().filter(event -> event.kind() == ReviewKind.GRAMMAR).toList();
	}

	// ------------------------------------------------------------------ building the question

	@Test
	void everyClozeInTheBookMakesAValidQuestion() {
		for (GrammarBook.Construction construction : BOOK.all()) {
			for (int seed = 0; seed < 12; seed++) {
				ClozeGrammarCard card = ClozeGrammarCard.create(construction, new Random(seed));
				assertNotNull(card, construction.id());
				List<String> options = card.options();
				String where = construction.id() + " seed " + seed + " " + options;
				assertTrue(options.size() >= 3 && options.size() <= ClozeGrammarCard.MAX_OPTIONS, where);
				assertEquals(options.size(), new HashSet<>(options).size(), "an option twice: " + where);
				assertTrue(card.correctIndex() >= 0 && card.correctIndex() < options.size(), where);
				// the right option is an accepted fill and no other option is
				long accepted = options.stream().filter(option -> construction.clozes().stream()
						.anyMatch(cloze -> cloze.sentence().equals(card.view().main()) && cloze.accepted().contains(option))).count();
				assertEquals(1, accepted, "exactly one right option: " + where);
				assertTrue(card.view().main().contains("___"), "the gap is shown: " + where);
				assertEquals(ReviewMode.multipleChoice(options.size()), card.mode(), where);
			}
		}
	}

	@Test
	void theOptionOrderIsRandomButTheAnswerIsAlwaysThere() {
		GrammarBook.Construction seyo = BOOK.get("L2.seyo");
		Set<Integer> positions = new HashSet<>();
		for (int seed = 0; seed < 40; seed++) {
			ClozeGrammarCard card = ClozeGrammarCard.create(seyo, new Random(seed));
			positions.add(card.correctIndex());
		}
		assertTrue(positions.size() > 1, "the answer should not always sit in the same place");
	}

	// ------------------------------------------------------------------ playing it

	@Test
	void aWrongFirstPickIsTheAttemptAndTheRetryIsPractice() {
		ClozeGrammarCard card = ClozeGrammarCard.create(BOOK.get("L1.obj_eul"), new Random(3));
		int wrong = card.correctIndex() == 0 ? 1 : 0;
		Optional<Card.Answer> first = card.choose(wrong);
		assertTrue(first.isPresent());
		assertFalse(first.get().correct());
		assertEquals(card.options().size(), first.get().options());
		assertEquals(Card.Phase.RETRY, card.phase());
		assertTrue(card.answered());

		assertTrue(card.choose(wrong).isEmpty(), "the wrong pick is disabled");
		assertTrue(card.choose(card.correctIndex()).isEmpty(), "the second try is not recorded");
		assertEquals(Card.Phase.FEEDBACK, card.phase());
		CardView view = card.view();
		assertNotNull(view.feedback());
		assertFalse(view.main().contains("___"), "the gap is filled once the question is over");
		assertEquals(view.main(), view.feedback().ko());
		card.confirm();
		assertTrue(card.finished());
	}

	// ------------------------------------------------------------------ grading as GRAMMAR

	@Test
	void aThreeOptionAnswerIsLoggedAsGrammarAndMovesNoSchedule() {
		ClozeGrammarCard card = ClozeGrammarCard.create(BOOK.get("L1.obj_eul"), new Random(5));
		assertEquals(3, card.options().size());
		Card.Answer answer = card.choose(card.correctIndex()).orElseThrow();

		CardRecording.record(learn, "L1.obj_eul", answer, 1_500, T0);
		List<ReviewLog.Event> events = grammarLog();
		assertEquals(1, events.size());
		ReviewLog.Event event = events.get(0);
		assertEquals("L1.obj_eul", event.item());
		assertEquals(ReviewKind.GRAMMAR, event.kind());
		assertEquals(ReviewMode.MC3, event.mode());
		assertEquals(Fsrs.GOOD, event.grade());
		assertEquals(1_500, event.latencyMs());
		assertEquals("card:cloze", event.ctx());

		assertTrue(learn.data().words.isEmpty(), "a construction is not a word");
		assertEquals(0, learn.totalReviews(), "three options never move a schedule");
		assertEquals(GrammarProgress.update(0.1, true), learn.grammar().pL("L1.obj_eul"), 1e-12);
		assertEquals(1, learn.grammar().answers("L1.obj_eul"));
	}

	@Test
	void aWrongAnswerIsAgainAndLowersWhatBktBelieved() {
		learn.grammar().outcome("L1.obj_eul", true);
		double before = learn.grammar().pL("L1.obj_eul");
		ClozeGrammarCard card = ClozeGrammarCard.create(BOOK.get("L1.obj_eul"), new Random(5));
		Card.Answer answer = card.choose(card.correctIndex() == 0 ? 1 : 0).orElseThrow();

		CardRecording.record(learn, "L1.obj_eul", answer, 900, T0);

		ReviewLog.Event event = grammarLog().get(0);
		assertEquals(Fsrs.AGAIN, event.grade());
		assertEquals(ReviewMode.MC3, event.mode());
		assertTrue(learn.grammar().pL("L1.obj_eul") < before);
	}

	@Test
	void fourOptionsAreGradedLikeAWordsMc4() {
		GrammarBook.Construction seyo = BOOK.get("L2.seyo");
		GrammarBook.Cloze cloze = seyo.clozes().get(0);
		ClozeGrammarCard card = new ClozeGrammarCard(seyo, cloze, List.of(cloze.answer(), "a", "b", "c"), 0);
		Card.Answer answer = card.choose(0).orElseThrow();
		assertEquals(ReviewMode.MC4, answer.mode());

		CardRecording.record(learn, "L2.seyo", answer, 30_000, T0);

		ReviewLog.Event event = grammarLog().get(0);
		assertEquals(ReviewMode.MC4, event.mode());
		assertEquals(Fsrs.HARD, event.grade(), "right but slow");
		assertEquals(ReviewKind.GRAMMAR, event.kind());
	}

	@Test
	void theHudRecordsTheFirstAttemptOnceThroughTheSession() {
		ClozeGrammarCard card = ClozeGrammarCard.create(BOOK.get("L1.eoyo"), new Random(9));
		QuizSession session = new QuizSession();
		session.begin(QuizSession.Quiz.of(card.construction().id(), card));
		int wrong = card.correctIndex() == 0 ? 1 : 0;
		Optional<QuizSession.Result> result = session.answer(card.choose(wrong).orElseThrow(), T0,
				(id, given, latency) -> CardRecording.record(learn, id, given, latency, T0));
		assertTrue(result.isPresent());
		assertFalse(result.get().correct());
		assertEquals("L1.eoyo", result.get().wordId());
		assertTrue(session.answer(card.choose(card.correctIndex()).orElse(null), T0, (id, given, latency) -> {
			throw new AssertionError("the retry must not be recorded");
		}).isEmpty());
		assertEquals(1, grammarLog().size());
	}

	// ------------------------------------------------------------------ which construction is asked

	@Test
	void noCardUntilAConstructionIsMet() {
		assertNull(CardFactory.grammar(new GrammarProgress(), BOOK, new Random(1)));
	}

	@Test
	void onlyMetConstructionsAreAsked() {
		GrammarProgress progress = new GrammarProgress();
		progress.step("L2.seyo");
		for (int seed = 0; seed < 20; seed++) {
			assertEquals("L2.seyo", CardFactory.grammar(progress, BOOK, new Random(seed)).construction().id());
		}
	}

	@Test
	void theLeastSecureConstructionComesFirstAndMasteredOnesRest() {
		GrammarProgress progress = new GrammarProgress();
		progress.step("L1.eoyo");
		progress.step("L1.obj_eul");
		progress.step("L2.seyo");
		for (int i = 0; i < 4; i++) {
			progress.outcome("L1.eoyo", true); // mastered
		}
		progress.outcome("L2.seyo", true);     // p(L) .39
		assertTrue(progress.mastered("L1.eoyo"));
		for (int seed = 0; seed < 20; seed++) {
			assertEquals("L1.obj_eul", CardFactory.grammar(progress, BOOK, new Random(seed)).construction().id(),
					"untouched p(L) .1 is lower than .39");
		}
		progress.outcome("L1.obj_eul", true);
		progress.outcome("L1.obj_eul", true);
		progress.outcome("L1.obj_eul", true);
		progress.outcome("L1.obj_eul", true);
		assertEquals("L2.seyo", CardFactory.grammar(progress, BOOK, new Random(1)).construction().id(),
				"with the others mastered only L2.seyo is left");
		for (int i = 0; i < 3; i++) {
			progress.outcome("L2.seyo", true);
		}
		assertNull(CardFactory.grammar(progress, BOOK, new Random(1)), "everything met is mastered");
	}

	@Test
	void aQuarterOfTheReviewsTryGrammarFirst() {
		Random random = new Random(11);
		int turns = 0;
		for (int i = 0; i < 10_000; i++) {
			if (CardFactory.grammarTurn(random)) {
				turns++;
			}
		}
		assertEquals(CardFactory.GRAMMAR_SHARE, turns / 10_000.0, 0.02);
	}
}
