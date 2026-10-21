package io.github.leks2205.hangulquest.ui.quiz.cards;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The typed card: who is asked, how the answer is graded, and that the session records it once. */
class TypedCardTest {
	private static final long T0 = 1_000_000L;

	@TempDir
	Path dir;

	private Catalog catalog;
	private LearningService learn;
	private Word torch;
	private Word stone;

	private static Concept concept(String id, String ko, String en) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = en;
		concept.en = en;
		concept.category = "BLOCKS";
		concept.items = List.of("minecraft:" + id);
		return concept;
	}

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("typed-card-test"));
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "stone"), concept("torch", "횃불", "torch"),
				concept("dirt", "흙", "dirt"), concept("sand", "모래", "sand")));
		learn = new LearningService(store.data(), store, catalog);
		torch = catalog.byId("concept:torch").orElseThrow();
		stone = catalog.byId("concept:stone").orElseThrow();
	}

	private static WordProgress progress(double stability) {
		WordProgress progress = new WordProgress();
		progress.fsrs = new CardState();
		progress.fsrs.state = CardState.State.REVIEW;
		progress.fsrs.stability = stability;
		return progress;
	}

	private static Word named(String ko) {
		return new Word("w", ko, "x", "x", "x", Category.MISC, 1, "w", null, null, null, null, Origin.CURATED,
				false, null);
	}

	private static KeyEvent enter() {
		return new KeyEvent(InputConstants.KEY_RETURN, 0, 0);
	}

	private static void type(TypedCard card, String text) {
		for (char c : text.toCharArray()) {
			card.charTyped(new CharacterEvent(c));
		}
	}

	private TypedCard cardFor(Word word) {
		return TypedCard.create(CardInfo.of(catalog, word));
	}

	// ------------------------------------------------------------------ who is asked

	@Test
	void onlyShortHangulWordsCanBeTyped() {
		assertTrue(TypedCard.eligible("돌"));
		assertTrue(TypedCard.eligible("화강암"));
		assertTrue(TypedCard.eligible("윤나는 화강암"), "two eojeol are fine");
		assertFalse(TypedCard.eligible("윤나는 화강암 반"), "three eojeol are too long");
		assertFalse(TypedCard.eligible("돌  돌"), "an empty eojeol counts");
		assertFalse(TypedCard.eligible("TNT"));
		assertFalse(TypedCard.eligible("돌2"));
		assertFalse(TypedCard.eligible(""));
		assertFalse(TypedCard.eligible(null));
		assertNull(TypedCard.create(CardInfo.of(named("철 문 열쇠"))));
	}

	@Test
	void typedCardsWaitForAWeekOfStabilityAndTheSetting() {
		Word word = named("횃불");
		assertFalse(CardFactory.typedAllowed(progress(6.9), word, true));
		assertTrue(CardFactory.typedAllowed(progress(7.0), word, true));
		assertTrue(CardFactory.typedAllowed(progress(40), word, true));
		assertFalse(CardFactory.typedAllowed(progress(40), word, false), "the player turned them off");
		assertFalse(CardFactory.typedAllowed(null, word, true));
		assertFalse(CardFactory.typedAllowed(progress(40), named("철 문 열쇠"), true), "too long to type");
		WordProgress known = progress(40);
		known.known = true;
		assertFalse(CardFactory.typedAllowed(known, word, true));
	}

	@Test
	void aboutAThirdOfProductiveTurnsAreTypedWhenAllowed() {
		Random random = new Random(4);
		int productive = 0;
		int typed = 0;
		for (int i = 0; i < 6000; i++) {
			CardFactory.Kind kind = CardFactory.choose(true, true, true, random);
			if (kind != CardFactory.Kind.MC4) {
				productive++;
				typed += kind == CardFactory.Kind.TYPED ? 1 : 0;
			}
		}
		assertTrue(productive > 2500 && productive < 3500, "half the turns are productive: " + productive);
		double share = typed / (double) productive;
		assertTrue(share > 0.28 && share < 0.39, "typed share of productive turns: " + share);
	}

	@Test
	void notAllowedMeansNeverTypedAndLockedMeansRecognition() {
		Random random = new Random(9);
		for (int i = 0; i < 1000; i++) {
			assertFalse(CardFactory.choose(true, true, false, random) == CardFactory.Kind.TYPED);
			assertEquals(CardFactory.Kind.MC4, CardFactory.choose(false, true, true, random));
		}
	}

	@Test
	void theOldChooseNeverGivesATypedCard() {
		Random random = new Random(2);
		for (int i = 0; i < 1000; i++) {
			assertFalse(CardFactory.choose(true, true, random) == CardFactory.Kind.TYPED);
		}
	}

	@Test
	void theFactoryOffersATypedCardOnlyToAStableWordWhileTheSettingIsOn() {
		boolean typed = false;
		Random random = new Random(6);
		for (int i = 0; i < 200; i++) {
			Card card = CardFactory.create(torch, progress(20), catalog, LexiconTags.empty(), random,
					CardFactory.UNLOCK_STABILITY_DAYS, true);
			typed |= card instanceof TypedCard;
		}
		assertTrue(typed, "a stable word is sometimes typed");
		for (int i = 0; i < 200; i++) {
			assertFalse(CardFactory.create(torch, progress(20), catalog, LexiconTags.empty(), random,
					CardFactory.UNLOCK_STABILITY_DAYS, false) instanceof TypedCard, "setting off");
			assertFalse(CardFactory.create(torch, progress(5), catalog, LexiconTags.empty(), random,
					CardFactory.UNLOCK_STABILITY_DAYS, true) instanceof TypedCard, "not stable for a week yet");
		}
	}

	// ------------------------------------------------------------------ the card

	@Test
	void anExactAnswerIsGoodAndTheNextEnterClosesTheCard() {
		TypedCard card = cardFor(torch);
		assertEquals(Card.Phase.ASKING, card.phase());
		assertEquals(ReviewMode.TYPED, card.mode());
		assertTrue(card.takeAnswer().isEmpty());

		type(card, "횃불");
		assertTrue(card.takeAnswer().isEmpty(), "typing alone submits nothing");
		assertTrue(card.keyPressed(enter()));
		Card.Answer answer = card.takeAnswer().orElseThrow();

		assertEquals(ReviewMode.TYPED, answer.mode());
		assertTrue(answer.correct());
		assertEquals(0, answer.jamoOff());
		assertEquals(Card.Phase.FEEDBACK, card.phase());
		assertTrue(card.answered());
		assertTrue(card.takeAnswer().isEmpty(), "the first attempt is handed out once");
		assertNotNull(card.view().feedback());
		assertEquals("횃불", card.view().feedback().ko());

		assertFalse(card.finished());
		card.keyPressed(enter());
		assertTrue(card.finished());
		assertTrue(card.takeAnswer().isEmpty(), "closing is not another attempt");
	}

	@Test
	void anEmptyFieldDoesNotSubmit() {
		TypedCard card = cardFor(torch);
		card.keyPressed(enter());
		assertFalse(card.answered());
		assertEquals(Card.Phase.ASKING, card.phase());
	}

	@Test
	void oneJamoOffIsHardAndStillRight() {
		TypedCard card = cardFor(torch);
		type(card, "횃부"); // 불 without its final ㄹ
		card.keyPressed(enter());

		Card.Answer answer = card.takeAnswer().orElseThrow();

		assertEquals(1, answer.jamoOff());
		assertTrue(answer.correct());
	}

	@Test
	void aSlipInAVeryShortWordIsAnotherWordNotATypo() {
		TypedCard card = cardFor(stone);
		type(card, "도"); // 돌 without ㄹ: one jamo, but 돌 has only three
		card.keyPressed(enter());

		Card.Answer answer = card.takeAnswer().orElseThrow();

		assertEquals(2, answer.jamoOff());
		assertFalse(answer.correct());
	}

	@Test
	void aWrongAnswerIsAgainAndHasNoRetry() {
		TypedCard card = cardFor(torch);
		type(card, "모래");
		card.keyPressed(enter());

		Card.Answer answer = card.takeAnswer().orElseThrow();

		assertTrue(answer.jamoOff() >= 2);
		assertFalse(answer.correct());
		assertEquals(Card.Phase.FEEDBACK, card.phase(), "no second try: the diff and the answer are shown");
		assertEquals("모래", card.view().feedback().yours());
	}

	@Test
	void numberKeysAndButtonsDoNothingOnATypedCard() {
		TypedCard card = cardFor(torch);
		assertTrue(card.choose(0).isEmpty());
		assertTrue(card.confirm().isEmpty());
		assertFalse(card.answered());
	}

	// ------------------------------------------------------------------ the session

	private ReviewLog.Event lastEvent() {
		learn.reviewLog().flush();
		List<ReviewLog.Event> events = ReviewLog.readAll(dir);
		return events.get(events.size() - 1);
	}

	@Test
	void theSessionRecordsATypedAnswerOnceAsATypedReview() {
		learn.answer(torch.id, true, T0);
		TypedCard card = cardFor(torch);
		QuizSession session = new QuizSession();
		session.begin(QuizSession.Quiz.of(torch.id, card));
		type(card, "횃불");
		card.keyPressed(enter());
		Card.Answer answer = card.takeAnswer().orElseThrow();
		int before = learn.totalReviews();
		CardState state = learn.data().words.get(torch.id).fsrs.copy();

		Optional<QuizSession.Result> result = session.answer(answer, T0 + 120_000L,
				(id, given, latency) -> CardRecording.record(learn, id, given, latency, T0 + 120_000L));

		assertTrue(result.orElseThrow().correct());
		assertEquals(before + 1, learn.totalReviews());
		ReviewLog.Event event = lastEvent();
		assertEquals(ReviewMode.TYPED, event.mode());
		assertEquals(Fsrs.GOOD, event.grade());
		assertEquals("card:typed", event.ctx());
		assertFalse(state.equals(learn.data().words.get(torch.id).fsrs), "a typed answer moves the schedule");
		assertTrue(session.answer(answer, T0 + 130_000L, (id, given, latency) -> {
			throw new AssertionError("a second record for the same quiz");
		}).isEmpty());
	}

	@Test
	void aOneOffAnswerIsRecordedAsHardAndAWrongOneAsAgain() {
		learn.answer(torch.id, true, T0);
		for (String[] attempt : new String[][] {{"횃부", "2"}, {"모래", "1"}}) {
			TypedCard card = cardFor(torch);
			QuizSession session = new QuizSession();
			session.begin(QuizSession.Quiz.of(torch.id, card));
			type(card, attempt[0]);
			card.keyPressed(enter());
			session.answer(card.takeAnswer().orElseThrow(), T0 + 200_000L,
					(id, given, latency) -> CardRecording.record(learn, id, given, latency, T0 + 200_000L));

			ReviewLog.Event event = lastEvent();
			assertEquals(ReviewMode.TYPED, event.mode());
			assertEquals(Integer.parseInt(attempt[1]), event.grade(), attempt[0]);
		}
	}

	@Test
	void aTypedAnswerCountsAsRecall() {
		assertTrue(ReviewMode.TYPED.recall());
		assertTrue(ReviewMode.TYPED.schedules());
	}
}
