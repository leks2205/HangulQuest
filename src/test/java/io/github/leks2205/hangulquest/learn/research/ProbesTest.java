package io.github.leks2205.hangulquest.learn.research;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.SessionPlanner;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.leks2205.hangulquest.learn.research.ResearchFixture.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbesTest {
	private static final long DAY = Srs.DAY;

	@TempDir
	Path dir;

	private List<ReviewLog.Event> probeEvents(ResearchFixture fixture, String id) {
		fixture.learn.reviewLog().flush();
		return ReviewLog.readAll(fixture.dir).stream()
				.filter(event -> event.mode() == ReviewMode.PROBE && event.item().equals(id)).toList();
	}

	// ------------------------------------------------------------------ which words

	@Test
	void theChoiceIsADeterministicFivePercent() {
		int held = 0;
		int total = 20_000;
		for (int i = 0; i < total; i++) {
			String id = "concept:word" + i;
			boolean first = Probes.eligible("salt-a", id);
			assertEquals(first, Probes.eligible("salt-a", id), "same salt and id, same answer");
			if (first) {
				held++;
			}
		}
		assertTrue(held > total * 0.045 && held < total * 0.055, "about 5% expected, got " + held);
	}

	@Test
	void anotherInstallHoldsOutOtherWords() {
		int differ = 0;
		for (int i = 0; i < 2000; i++) {
			String id = "concept:word" + i;
			if (Probes.eligible("salt-a", id) != Probes.eligible("salt-b", id)) {
				differ++;
			}
		}
		assertTrue(differ > 50, "two salts should pick different words, only " + differ + " differ");
	}

	@Test
	void aboutFivePercentOfWordsThatReachThreeDaysAreReserved() {
		ResearchFixture fixture = new ResearchFixture(dir, 400);
		fixture.learn.enableResearch(Experiments.none());
		Set<String> expected = new HashSet<>();
		for (String id : fixture.ids) {
			fixture.reachThreeDays(id);
			if (Probes.eligible(ResearchFixture.SALT, id)) {
				expected.add(id);
			}
		}
		Set<String> reserved = new HashSet<>(fixture.learn.probes().reservedIds());
		assertEquals(expected, reserved, "exactly the eligible words are reserved");
		assertTrue(reserved.size() >= 8 && reserved.size() <= 36, "5% of 400 is 20, got " + reserved.size());
	}

	@Test
	void aWordBelowThreeDaysOfStabilityIsNotReserved() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		fixture.learn.answer(id, true, T0);
		fixture.learn.answer(id, true, T0 + 11 * Srs.MINUTE);
		assertTrue(fixture.learn.data().words.get(id).fsrs.stability < Probes.STABILITY_DAYS);
		assertTrue(fixture.learn.probes().reservedIds().isEmpty());
	}

	@Test
	void withoutTheResearchSwitchNothingIsEverHeld() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		String id = fixture.eligible();
		fixture.reachThreeDays(id);
		assertNull(fixture.learn.probes());
		assertFalse(fixture.learn.heldOut(id, T0 + 10 * DAY));
		assertTrue(fixture.learn.dueProbe(T0 + 10 * DAY).isEmpty());
	}

	// ------------------------------------------------------------------ no reviews, no planning

	@Test
	void aReservedWordIsNeverPlannedOrListedAsDue() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String held = fixture.eligible();
		String normal = fixture.notEligible();
		fixture.reachThreeDays(held);
		fixture.reachThreeDays(normal);
		long late = T0 + 3 * DAY + 40 * DAY; // both are long overdue, and the held word is past its probe windows
		assertTrue(fixture.learn.data().words.get(held).dueAt < late);

		List<String> planned = fixture.learn.dueForPlay(late, 50).stream().map(word -> word.id).toList();
		assertTrue(planned.contains(normal), "the control word is planned");
		assertTrue(fixture.learn.heldOut(held, T0 + 3 * DAY + 16 * DAY));
		long during = T0 + 3 * DAY + 16 * DAY; // inside the hold: after the first probe window, before the second
		assertFalse(fixture.learn.dueForPlay(during, 50).stream().anyMatch(word -> word.id.equals(held)));
		assertFalse(fixture.learn.dueWords(during, 50).stream().anyMatch(word -> word.id.equals(held)));
		assertTrue(fixture.learn.dueWords(during, 50).stream().anyMatch(word -> word.id.equals(normal)));
		assertEquals(fixture.learn.dueWords(during, 50).size(), fixture.learn.dueCount(during));
	}

	@Test
	void theSessionPlannerSkipsWhatItIsToldIsHeldOut() {
		Concept concept = new Concept();
		concept.id = "a";
		concept.ko = "돌";
		concept.de = "a";
		concept.items = List.of("minecraft:stone");
		Word word = concept.toWord();
		Concept other = new Concept();
		other.id = "b";
		other.ko = "흙";
		other.de = "b";
		other.items = List.of("minecraft:dirt");
		List<SessionPlanner.Due> due = List.of(
				new SessionPlanner.Due(word, "set-a", false, 0.5, 1, 1, false),
				new SessionPlanner.Due(other.toWord(), "set-b", false, 0.6, 2, 1, false));
		assertEquals(2, SessionPlanner.rank(due, 5, Set.of(), 10).size());
		List<Word> kept = SessionPlanner.rank(due, 5, Set.of(), 10, id -> id.equals(word.id));
		assertEquals(List.of(other.toWord().id), kept.stream().map(w -> w.id).toList());
	}

	@Test
	void aQuestStepDoesNotReviewAHeldOutWord() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String held = fixture.eligible();
		String normal = fixture.notEligible();
		fixture.reachThreeDays(held);
		fixture.reachThreeDays(normal);
		long due = T0 + 3 * DAY + 20 * DAY;
		CardState heldBefore = fixture.learn.data().words.get(held).fsrs.copy();
		CardState normalBefore = fixture.learn.data().words.get(normal).fsrs.copy();

		fixture.learn.questStep(fixture.catalog.byId(held).orElseThrow(), due, false, false,
				ExposureLedger.Context.NONE, "step");
		fixture.learn.questStep(fixture.catalog.byId(normal).orElseThrow(), due, false, false,
				ExposureLedger.Context.NONE, "step");

		assertEquals(heldBefore, fixture.learn.data().words.get(held).fsrs, "the held word's card did not move");
		assertNotEquals(normalBefore, fixture.learn.data().words.get(normal).fsrs, "the control word's card did");
		fixture.learn.reviewLog().flush();
		List<ReviewMode> modes = ReviewLog.readAll(dir).stream().filter(e -> e.t() == due && e.item().equals(held))
				.map(ReviewLog.Event::mode).toList();
		assertEquals(List.of(ReviewMode.EXPOSURE), modes, "only an encounter is logged for the held word");
	}

	// ------------------------------------------------------------------ the probes

	@Test
	void probesAreAskedAtSevenAndThirtyDaysThenTheWordGoesBackToNormal() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		long reserved = fixture.reachThreeDays(id);
		CardState card = fixture.learn.data().words.get(id).fsrs.copy();
		int reviews = fixture.learn.totalReviews();

		assertTrue(fixture.learn.dueProbe(reserved + 6 * DAY).isEmpty(), "not before 7 days");
		Probes.Due first = fixture.learn.dueProbe(reserved + 7 * DAY).orElseThrow();
		assertEquals(new Probes.Due(id, 7), first);

		fixture.learn.answerMc(id, 4, true, 900, reserved + 7 * DAY);
		List<ReviewLog.Event> events = probeEvents(fixture, id);
		assertEquals(1, events.size());
		assertEquals("probe:7", events.get(0).ctx());
		assertEquals(ReviewKind.WORD, events.get(0).kind());
		assertEquals(3, events.get(0).grade());
		assertEquals(7, events.get(0).elapsedDays(), "days since the last review");
		assertEquals(card, fixture.learn.data().words.get(id).fsrs, "a probe never moves the card");
		assertEquals(reviews, fixture.learn.totalReviews(), "and is not a scheduled review");
		assertTrue(fixture.learn.dueProbe(reserved + 8 * DAY).isEmpty(), "asked once");
		assertTrue(fixture.learn.heldOut(id, reserved + 8 * DAY));

		assertTrue(fixture.learn.dueProbe(reserved + 29 * DAY).isEmpty());
		assertEquals(new Probes.Due(id, 30), fixture.learn.dueProbe(reserved + 30 * DAY).orElseThrow());
		fixture.learn.answerMc(id, 4, false, 900, reserved + 30 * DAY);
		events = probeEvents(fixture, id);
		assertEquals(2, events.size());
		assertEquals("probe:30", events.get(1).ctx());
		assertEquals(1, events.get(1).grade(), "a wrong probe is logged as Again");
		assertEquals(card, fixture.learn.data().words.get(id).fsrs);

		long after = reserved + 31 * DAY;
		assertFalse(fixture.learn.heldOut(id, after), "back to normal scheduling");
		assertTrue(fixture.learn.dueProbe(after).isEmpty());
		assertTrue(fixture.learn.dueForPlay(after, 50).stream().anyMatch(word -> word.id.equals(id)));
		fixture.learn.answerMc(id, 4, true, 900, after);
		assertNotEquals(card, fixture.learn.data().words.get(id).fsrs, "an ordinary answer schedules again");
		assertEquals(2, probeEvents(fixture, id).size());
	}

	@Test
	void aProbeThatIsTooLateIsSkippedAndAnOldHoldIsReleased() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		long reserved = fixture.reachThreeDays(id);

		assertTrue(fixture.learn.dueProbe(reserved + 15 * DAY).isEmpty(), "the 7-day slot ran out after a week");
		assertTrue(fixture.learn.heldOut(id, reserved + 15 * DAY));
		assertEquals(new Probes.Due(id, 30), fixture.learn.dueProbe(reserved + 35 * DAY).orElseThrow());
		assertTrue(fixture.learn.dueProbe(reserved + 38 * DAY).isEmpty(), "and the 30-day slot too");
		assertFalse(fixture.learn.heldOut(id, reserved + 38 * DAY), "an unanswered hold ends");
	}

	@Test
	void anAnswerWithFewerThanFourOptionsIsNeverTakenForAProbe() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		long reserved = fixture.reachThreeDays(id);
		fixture.learn.answerMc(id, 3, true, 900, reserved + 7 * DAY);
		assertTrue(probeEvents(fixture, id).isEmpty());
		assertEquals(new Probes.Due(id, 7), fixture.learn.dueProbe(reserved + 7 * DAY).orElseThrow(), "still waiting");
	}

	@Test
	void theStateSurvivesARestart() throws Exception {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		long reserved = fixture.reachThreeDays(id);
		fixture.learn.answerMc(id, 4, true, 900, reserved + 7 * DAY);
		assertTrue(Files.isRegularFile(dir.resolve(Probes.FILE)), "reservations are in probes.json");

		ResearchFixture again = fixture.restart();
		again.learn.enableResearch(Experiments.none());
		assertEquals(List.of(id), again.learn.probes().reservedIds());
		assertTrue(again.learn.dueProbe(reserved + 8 * DAY).isEmpty(), "the 7-day probe is known to be done");
		assertTrue(again.learn.heldOut(id, reserved + 8 * DAY));
		assertEquals(new Probes.Due(id, 30), again.learn.dueProbe(reserved + 30 * DAY).orElseThrow());
	}

	@Test
	void aWordIsNeverReservedTwice() {
		ResearchFixture fixture = new ResearchFixture(dir, 300);
		fixture.learn.enableResearch(Experiments.none());
		String id = fixture.eligible();
		long reserved = fixture.reachThreeDays(id);
		long released = reserved + 40 * DAY;
		fixture.learn.answer(id, true, released);
		fixture.learn.answer(id, true, released + 20 * DAY);
		assertEquals(List.of(id), new ArrayList<>(fixture.learn.probes().reservedIds()));
		assertEquals(reserved, fixture.learn.probes().reservedAt().get(id));
	}
}
