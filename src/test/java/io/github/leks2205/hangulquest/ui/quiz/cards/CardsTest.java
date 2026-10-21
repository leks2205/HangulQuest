package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.ui.quiz.QuizSession;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardsTest {
	private static final long T0 = 1_000_000L;

	@TempDir
	Path dir;

	private Catalog catalog;
	private LearningService learn;
	private Word stone;
	private Word torch;

	private static Concept concept(String id, String ko, String en, String sentence) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = en;
		concept.en = en;
		concept.category = "BLOCKS";
		concept.items = List.of("minecraft:" + id);
		concept.sentenceKo = sentence;
		concept.sentenceEn = "A sentence about " + en + ".";
		concept.sentenceDe = "Ein Satz über " + en + ".";
		return concept;
	}

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("cards-test"));
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "stone", "돌이 있어요."),
				concept("torch", "횃불", "torch", null), concept("dirt", "흙", "dirt", null),
				concept("sand", "모래", "sand", null), concept("clay", "점토", "clay", null)));
		learn = new LearningService(store.data(), store, catalog);
		stone = catalog.byId("concept:stone").orElseThrow();
		torch = catalog.byId("concept:torch").orElseThrow();
	}

	private static WordProgress progress(double stability, boolean fresh) {
		WordProgress progress = new WordProgress();
		progress.fsrs = new CardState();
		progress.fsrs.state = fresh ? CardState.State.NEW : CardState.State.REVIEW;
		progress.fsrs.stability = stability;
		return progress;
	}

	// ------------------------------------------------------------------ unlock rule

	@Test
	void productiveCardsUnlockAtThreeDaysOfStability() {
		assertFalse(CardFactory.productiveUnlocked(null));
		assertFalse(CardFactory.productiveUnlocked(progress(30, true)), "never reviewed");
		assertFalse(CardFactory.productiveUnlocked(progress(2.99, false)));
		assertTrue(CardFactory.productiveUnlocked(progress(3.0, false)));
		assertTrue(CardFactory.productiveUnlocked(progress(40, false)));
		WordProgress known = progress(40, false);
		known.known = true;
		assertFalse(CardFactory.productiveUnlocked(known), "known words are not asked");
	}

	@Test
	void aLockedWordIsAlwaysRecognitionAnUnlockedOneSometimesProductive() {
		Random random = new Random(1);
		for (int i = 0; i < 300; i++) {
			assertEquals(CardFactory.Kind.MC4, CardFactory.choose(false, true, random));
		}
		boolean tiles = false;
		boolean self = false;
		boolean mc = false;
		for (int i = 0; i < 300; i++) {
			CardFactory.Kind kind = CardFactory.choose(true, true, random);
			tiles |= kind == CardFactory.Kind.TILES;
			self |= kind == CardFactory.Kind.SELF_GRADE;
			mc |= kind == CardFactory.Kind.MC4;
		}
		assertTrue(tiles && self && mc);
		for (int i = 0; i < 300; i++) {
			assertFalse(CardFactory.choose(true, false, random) == CardFactory.Kind.TILES,
					"a word tiles cannot spell never gets a tile card");
		}
	}

	@Test
	void theFactoryGivesALockedWordARecognitionCard() {
		Random locked = new Random(3);
		for (int i = 0; i < 50; i++) {
			Card card = CardFactory.create(stone, progress(1, false), catalog, LexiconTags.empty(), locked);
			assertTrue(card instanceof RecognitionMc4Card);
		}
		boolean productive = false;
		Random random = new Random(2);
		for (int i = 0; i < 50; i++) {
			productive |= !(CardFactory.create(stone, progress(5, false), catalog, LexiconTags.empty(),
					random) instanceof RecognitionMc4Card);
		}
		assertTrue(productive, "a stable word is sometimes asked productively");
	}

	// ------------------------------------------------------------------ recognition card

	private RecognitionMc4Card mc() {
		return RecognitionMc4Card.create(CardInfo.of(catalog, stone), catalog.all(),
				LexiconTags.empty(), new Random(5));
	}

	@Test
	void aRightFirstPickIsRecordedAndShowsTheFeedbackWithTheExampleSentence() {
		RecognitionMc4Card card = mc();

		Optional<Card.Answer> answer = card.choose(card.correctIndex());

		assertTrue(answer.isPresent());
		assertTrue(answer.get().correct());
		assertEquals(ReviewMode.MC4, answer.get().mode());
		assertEquals(Card.Phase.FEEDBACK, card.phase());
		FeedbackCard feedback = card.view().feedback();
		assertNotNull(feedback);
		assertEquals("돌", feedback.ko());
		assertEquals("stone", feedback.gloss());
		assertEquals("minecraft:stone", feedback.icon());
		assertEquals("돌이 있어요.", feedback.sentenceKo());
		assertEquals("A sentence about stone.", feedback.sentenceGloss());
		assertEquals("minecraft:stone", card.view().icon());
	}

	@Test
	void theIconStaysHiddenWhileTheQuestionIsOpen() {
		assertNull(mc().view().icon(), "the picture would give the answer away");
	}

	@Test
	void aWrongFirstPickIsRecordedOnceThenPromptsAndAllowsOneRetry() {
		RecognitionMc4Card card = mc();
		int wrong = card.correctIndex() == 0 ? 1 : 0;

		Optional<Card.Answer> first = card.choose(wrong);

		assertTrue(first.isPresent());
		assertFalse(first.get().correct());
		assertEquals(Card.Phase.RETRY, card.phase());
		assertFalse(card.view().message().isEmpty(), "the retry prompt is shown");
		assertNull(card.view().feedback(), "the correct form comes only after the retry");
		assertNull(card.view().icon());
		assertEquals(CardView.State.DISABLED, card.view().items().get(wrong).state());

		assertTrue(card.choose(wrong).isEmpty(), "the wrong option cannot be picked again");
		assertEquals(Card.Phase.RETRY, card.phase());
		Optional<Card.Answer> second = card.choose(card.correctIndex());
		assertTrue(second.isEmpty(), "the retry is practice and is never recorded");
		assertEquals(Card.Phase.FEEDBACK, card.phase());
		assertNotNull(card.view().feedback());
		assertTrue(card.view().message().isEmpty(), "the prompt is shown once");
		assertTrue(card.answered());
	}

	@Test
	void aSecondWrongPickEndsInTheCorrectForm() {
		RecognitionMc4Card card = mc();
		List<Integer> wrongs = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (i != card.correctIndex()) {
				wrongs.add(i);
			}
		}
		card.choose(wrongs.get(0));
		assertTrue(card.choose(wrongs.get(1)).isEmpty());

		assertEquals(Card.Phase.FEEDBACK, card.phase());
		assertEquals("돌", card.view().feedback().ko());
		assertEquals(CardView.State.CORRECT, card.view().items().get(card.correctIndex()).state());
		assertTrue(card.confirm().isEmpty());
		assertTrue(card.finished());
	}

	// ------------------------------------------------------------------ self grade

	@Test
	void theSelfGradeCardRevealsThenGradesWithKeysOneToFour() {
		RecallSelfGradeCard card = new RecallSelfGradeCard(CardInfo.of(catalog, stone));

		assertEquals("stone", card.view().main());
		assertEquals("minecraft:stone", card.view().icon());
		assertTrue(card.choose(2).isEmpty(), "grading before the reveal does nothing");

		card.confirm();
		assertEquals(Card.Phase.REVEALED, card.phase());
		assertEquals(4, card.view().items().size());
		assertEquals("돌", card.view().feedback().ko());

		Optional<Card.Answer> answer = card.choose(0);
		assertTrue(answer.isPresent());
		assertEquals(ReviewMode.SELF, answer.get().mode());
		assertEquals(1, answer.get().grade());
		assertFalse(answer.get().correct());
		assertTrue(card.finished());
	}

	@Test
	void everySelfGradeMapsToItsNumber() {
		for (int index = 0; index < 4; index++) {
			RecallSelfGradeCard card = new RecallSelfGradeCard(CardInfo.of(catalog, stone));
			card.confirm();
			assertEquals(index + 1, card.choose(index).orElseThrow().grade());
		}
	}

	// ------------------------------------------------------------------ tiles

	private SyllableTileCard tiles() {
		return SyllableTileCard.create(CardInfo.of(catalog, torch), new Random(11));
	}

	private static void place(SyllableTileCard card, String text) {
		for (char c : text.toCharArray()) {
			int index = card.tiles().indexOf(String.valueOf(c));
			assertTrue(index >= 0);
			card.choose(index);
		}
	}

	@Test
	void anExactAssemblyIsGoodAndShowsTheFeedback() {
		SyllableTileCard card = tiles();
		place(card, "횃불");
		assertEquals("횃불", card.assembled());

		Card.Answer answer = card.confirm().orElseThrow();

		assertEquals(ReviewMode.TILE, answer.mode());
		assertEquals(0, answer.jamoOff());
		assertTrue(answer.correct());
		assertEquals(Card.Phase.FEEDBACK, card.phase());
	}

	@Test
	void checkingNeedsEveryTilePlaced() {
		SyllableTileCard card = tiles();
		place(card, "횃");
		assertTrue(card.confirm().isEmpty());
		assertEquals(Card.Phase.ASKING, card.phase());
		assertFalse(card.answered());
	}

	@Test
	void undoAndClickingAPlacedTileTakeTilesBack() {
		SyllableTileCard card = tiles();
		place(card, "횃불");
		card.undo();
		assertEquals("횃", card.assembled());
		card.choose(card.tiles().indexOf("횃"));
		assertEquals("", card.assembled());
	}

	@Test
	void aWrongAssemblyIsGradedByJamoThenRetriedOnceWithoutAnotherRecord() {
		SyllableTileCard card = tiles();
		// Replace the second syllable with one of the look-alike tiles.
		String wrongTile = card.tiles().stream().filter(t -> !"횃불".contains(t)).findFirst().orElseThrow();
		card.choose(card.tiles().indexOf("횃"));
		card.choose(card.tiles().indexOf(wrongTile));

		Card.Answer answer = card.confirm().orElseThrow();

		assertTrue(answer.jamoOff() >= 1);
		assertEquals(Card.Phase.RETRY, card.phase());
		assertEquals("", card.assembled(), "the tiles are cleared for the retry");
		assertFalse(card.view().message().isEmpty());

		place(card, "횃불");
		assertTrue(card.confirm().isEmpty(), "the retry is not recorded");
		assertEquals(Card.Phase.FEEDBACK, card.phase());
		assertEquals("횃불", card.view().feedback().ko());
	}

	// ------------------------------------------------------------------ recording

	@Test
	void theSessionRecordsTheFirstAttemptOnlyAndOnlyWhenOpen() {
		RecognitionMc4Card card = mc();
		List<String> recorded = new ArrayList<>();
		QuizSession session = new QuizSession();
		Card.Answer answer = Card.Answer.multipleChoice(4, false);

		assertTrue(session.answer(answer, T0, (id, given, latency) -> recorded.add(id)).isEmpty(), "nothing is open");

		session.begin(QuizSession.Quiz.of(stone.id, card));
		assertTrue(session.answer(answer, T0, (id, given, latency) -> recorded.add(id)).isPresent());
		assertTrue(session.answer(answer, T0, (id, given, latency) -> recorded.add(id)).isEmpty(),
				"a second record for the same quiz is refused");
		assertEquals(List.of(stone.id), recorded);
	}

	@Test
	void everyKindOfAnswerReachesTheLearningServiceWithItsGrade() {
		CardRecording.record(learn, stone.id, Card.Answer.multipleChoice(4, true), 1000, T0);
		assertEquals(ReviewMode.MC4, lastMode());
		CardRecording.record(learn, stone.id, Card.Answer.selfGraded(4), -1, T0 + 1);
		assertEquals(ReviewMode.SELF, lastMode());
		CardRecording.record(learn, torch.id, Card.Answer.tiles(1), 3000, T0 + 2);
		assertEquals(ReviewMode.TILE, lastMode());
		assertEquals(3, learn.totalReviews());
		assertEquals(2, learn.data().words.get(stone.id).correct);
		assertEquals(1, learn.data().words.get(torch.id).correct, "one jamo off still counts as a recall");
	}

	@Test
	void aTwoOptionAnswerIsLoggedButNeverMovesTheSchedule() {
		CardRecording.record(learn, stone.id, Card.Answer.multipleChoice(2, true), 1000, T0);
		assertEquals(0, learn.totalReviews());
	}

	private ReviewMode lastMode() {
		learn.reviewLog().flush();
		List<io.github.leks2205.hangulquest.learn.ReviewLog.Event> events =
				io.github.leks2205.hangulquest.learn.ReviewLog.readAll(dir);
		return events.get(events.size() - 1).mode();
	}
}
