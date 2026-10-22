package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.audio.AudioService;
import io.github.leks2205.hangulquest.audio.SoundBackend;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a card speaks its word, and what the feedback shows under it. */
class CardAudioTest {
	private static final class FakeBackend implements SoundBackend {
		final Set<String> events;
		final List<String> played = new ArrayList<>();

		FakeBackend(String... events) {
			this.events = Set.of(events);
		}

		@Override
		public boolean has(String event) {
			return events.contains(event);
		}

		@Override
		public boolean hasAny(String prefix) {
			return events.stream().anyMatch(e -> e.startsWith(prefix));
		}

		@Override
		public void play(String event, float volume) {
			played.add(event);
		}
	}

	private Word stone;
	private Word soup;

	private static Concept concept(String id, String ko) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = id;
		concept.en = id;
		concept.category = "BLOCKS";
		concept.items = List.of("minecraft:" + id);
		return concept;
	}

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		Catalog catalog = Catalog.ofConcepts(List.of(concept("stone", "돌"), concept("soup", "국물"),
				concept("dirt", "흙"), concept("sand", "모래"), concept("clay", "점토")));
		stone = catalog.byId("concept:stone").orElseThrow();
		soup = catalog.byId("concept:soup").orElseThrow();
	}

	@Test
	void theWordIsSpokenOnceWhenTheAnswerFirstShows() {
		FakeBackend backend = new FakeBackend("word.stone");
		CardAudio audio = new CardAudio(new AudioService(backend, () -> true), "stone");
		assertTrue(audio.listenable());
		assertFalse(audio.update(Card.Phase.ASKING));
		assertFalse(audio.update(Card.Phase.RETRY), "a wrong first try must not give the answer away");
		assertTrue(audio.update(Card.Phase.FEEDBACK));
		assertFalse(audio.update(Card.Phase.FEEDBACK), "once only");
		assertFalse(audio.update(Card.Phase.DONE));
		assertEquals(List.of("word.stone"), backend.played);
	}

	@Test
	void aSelfGradeCardSpeaksAtTheReveal() {
		FakeBackend backend = new FakeBackend("word.stone");
		RecallSelfGradeCard card = new RecallSelfGradeCard(CardInfo.of(stone));
		CardAudio audio = new CardAudio(new AudioService(backend, () -> true), "stone");
		audio.update(card.phase());
		assertTrue(backend.played.isEmpty());
		card.confirm();
		audio.update(card.phase());
		assertEquals(List.of("word.stone"), backend.played);
		card.choose(2);
		audio.update(card.phase());
		assertEquals(1, backend.played.size(), "grading does not speak again");
	}

	@Test
	void aRecognitionCardSpeaksAfterTheRetryNotBefore() {
		FakeBackend backend = new FakeBackend("word.stone");
		CardAudio audio = new CardAudio(new AudioService(backend, () -> true), "stone");
		RecognitionMc4Card card = new RecognitionMc4Card(CardInfo.of(stone), List.of("a", "b", "c", "d"), 1);
		card.choose(0);
		audio.update(card.phase());
		assertEquals(Card.Phase.RETRY, card.phase());
		assertTrue(backend.played.isEmpty());
		card.choose(1);
		audio.update(card.phase());
		assertEquals(List.of("word.stone"), backend.played);
	}

	@Test
	void replayPlaysAgainOnRequest() {
		FakeBackend backend = new FakeBackend("word.stone");
		CardAudio audio = new CardAudio(new AudioService(backend, () -> true), "stone");
		audio.update(Card.Phase.FEEDBACK);
		assertTrue(audio.replay());
		assertTrue(audio.replay());
		assertEquals(3, backend.played.size());
	}

	@Test
	void withoutThePackTheSettingOrAnAudioServiceTheCardIsSilent() {
		FakeBackend none = new FakeBackend();
		CardAudio noPack = new CardAudio(new AudioService(none, () -> true), "stone");
		assertFalse(noPack.listenable());
		assertFalse(noPack.update(Card.Phase.FEEDBACK));
		assertFalse(noPack.replay());

		FakeBackend backend = new FakeBackend("word.stone");
		CardAudio off = new CardAudio(new AudioService(backend, () -> false), "stone");
		assertFalse(off.listenable());
		assertFalse(off.update(Card.Phase.FEEDBACK));
		assertFalse(off.replay());

		CardAudio nobody = new CardAudio(null, "stone");
		assertFalse(nobody.listenable());
		assertFalse(nobody.update(Card.Phase.REVEALED));

		CardAudio noConcept = new CardAudio(new AudioService(backend, () -> true), null);
		assertFalse(noConcept.listenable());
		assertFalse(noConcept.replay());
		assertTrue(none.played.isEmpty() && backend.played.isEmpty());
	}

	@Test
	void onlyTheAnswerPhasesSpeak() {
		assertFalse(CardAudio.showsAnswer(Card.Phase.ASKING));
		assertFalse(CardAudio.showsAnswer(Card.Phase.RETRY));
		assertTrue(CardAudio.showsAnswer(Card.Phase.REVEALED));
		assertTrue(CardAudio.showsAnswer(Card.Phase.FEEDBACK));
		assertTrue(CardAudio.showsAnswer(Card.Phase.DONE));
	}

	// ------------------------------------------------------------------ the bracket on the feedback

	@Test
	void theFeedbackShowsABracketOnlyWhenTheWordIsNotSaidAsWritten() {
		FeedbackCard plain = FeedbackCard.of(CardInfo.of(stone), true, null);
		assertNull(plain.pron(), "돌 is said as it is written");
		FeedbackCard changed = FeedbackCard.of(CardInfo.of(soup), true, null);
		assertEquals("[궁물]", changed.pron());
		assertNotNull(changed.ko());
	}

	@Test
	void everyCardKindPutsTheBracketOnItsFeedback() {
		RecallSelfGradeCard card = new RecallSelfGradeCard(CardInfo.of(soup));
		card.confirm();
		assertEquals("[궁물]", card.view().feedback().pron());
		RecognitionMc4Card recognition = new RecognitionMc4Card(CardInfo.of(soup), List.of("a", "b", "c", "d"), 0);
		recognition.choose(0);
		assertEquals("[궁물]", recognition.view().feedback().pron());
	}
}
