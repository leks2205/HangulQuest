package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.story.MarkFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The card queue: one card at a time, each note shown once, never while a screen is open. */
class CultureCardsTest {
	private static final CultureSet SET = CultureSet.parse("""
			{"notes": [
			  {"id": "ondol", "titleKo": "온돌", "ko": "x", "icon": "minecraft:furnace", "anchors": ["minecraft:furnace"]},
			  {"id": "yutnori", "titleKo": "윷놀이", "ko": "x", "icon": "minecraft:stick", "anchors": ["minecraft:stick"]}
			 ]}""");
	private static final LocalDate DAY = LocalDate.of(2026, 10, 22);
	private static final long CARD = 16_000L;

	@TempDir
	Path tempDir;

	private CultureCards cards;

	@BeforeEach
	void setUp() {
		cards = new CultureCards(MarkFile.open(tempDir, "culture-seen.json", LoggerFactory.getLogger("culture-test")));
	}

	private void offer(String... items) {
		cards.offer(CultureTrigger.due(SET, Set.of(items), DAY, cards.shown()));
	}

	@Test
	void theCardAppearsForADueNoteAndFadesAfterItsTime() {
		offer("minecraft:furnace");
		CultureCards.Card card = cards.tick(1000, true, CARD);
		assertNotNull(card);
		assertEquals("ondol", card.note().id());
		assertEquals(1000 + CARD, card.until());
		assertSame(card, cards.tick(1000 + CARD - 1, true, CARD));
		assertNull(cards.tick(1000 + CARD, true, CARD));
	}

	@Test
	void aNoteIsShownOnceEvenWhenItsItemKeepsBeingHeld() {
		offer("minecraft:furnace");
		cards.tick(0, true, CARD);
		cards.tick(CARD, true, CARD);
		for (int second = 1; second < 20; second++) {
			offer("minecraft:furnace");
			assertNull(cards.tick(CARD + second * 1000L, true, CARD), "second " + second);
		}
	}

	@Test
	void theShownMarkSurvivesARestart() {
		offer("minecraft:furnace");
		cards.tick(0, true, CARD);

		CultureCards restarted = new CultureCards(
				MarkFile.open(tempDir, "culture-seen.json", LoggerFactory.getLogger("culture-test")));
		restarted.offer(CultureTrigger.due(SET, Set.of("minecraft:furnace"), DAY, restarted.shown()));
		assertNull(restarted.tick(0, true, CARD));
	}

	@Test
	void noCardAppearsWhileAScreenIsOpenButTheNoteWaits() {
		offer("minecraft:furnace");
		assertNull(cards.tick(0, false, CARD));
		assertEquals(1, cards.waiting());
		assertEquals("ondol", cards.tick(5000, true, CARD).note().id());
	}

	@Test
	void twoNotesComeOneAfterTheOther() {
		offer("minecraft:furnace", "minecraft:stick");
		assertEquals(2, cards.waiting());
		String first = cards.tick(0, true, CARD).note().id();
		assertEquals(first, cards.tick(1000, true, CARD).note().id(), "the first card stays until it fades");
		String second = cards.tick(CARD, true, CARD).note().id();
		assertTrue(Set.of("ondol", "yutnori").containsAll(List.of(first, second)));
		assertTrue(!first.equals(second));
		assertNull(cards.tick(2 * CARD, true, CARD));
	}

	@Test
	void offeringTheSameNoteAgainWhileItWaitsDoesNotQueueItTwice() {
		offer("minecraft:furnace");
		offer("minecraft:furnace");
		offer("minecraft:furnace");
		assertEquals(1, cards.waiting());
	}

	@Test
	void dismissingTheCardClearsItWithoutShowingTheNoteAgain() {
		offer("minecraft:furnace");
		cards.tick(0, true, CARD);
		cards.dismiss();
		assertNull(cards.current());
		offer("minecraft:furnace");
		assertNull(cards.tick(1000, true, CARD));
	}
}
