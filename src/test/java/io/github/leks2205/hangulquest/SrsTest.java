package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Srs} is the facade over FSRS: display levels and the cards that old levels start from. */
class SrsTest {
	private static final long NOW = 1_800_000_000_000L;

	@Test
	void seedStabilityGrowsWithTheLevel() {
		assertEquals(Srs.MAX_LEVEL + 1, Srs.LEVEL_STABILITY.length);
		for (int level = 1; level <= Srs.MAX_LEVEL; level++) {
			assertTrue(Srs.LEVEL_STABILITY[level] > Srs.LEVEL_STABILITY[level - 1], "level " + level);
		}
		assertEquals(0.1, Srs.LEVEL_STABILITY[0], 0);
		assertEquals(35, Srs.LEVEL_STABILITY[Srs.MAX_LEVEL], 0);
	}

	@Test
	void aSeededCardComesBackAsTheSameLevel() {
		for (int level = 0; level <= Srs.MAX_LEVEL; level++) {
			CardState card = Srs.seed(level, NOW, Srs.fsrs());
			assertEquals(level, Srs.levelFor(card.stability), "level " + level);
			assertEquals(CardState.State.REVIEW, card.state);
			assertEquals(5, card.difficulty, 0);
			assertTrue(card.due > NOW);
		}
		// out-of-range levels clamp instead of throwing or going backwards
		assertEquals(0.1, Srs.seed(-3, NOW, Srs.fsrs()).stability, 0);
		assertEquals(35, Srs.seed(Srs.MAX_LEVEL + 5, NOW, Srs.fsrs()).stability, 0);
	}

	@Test
	void aKnownWordStartsAtThirtyDays() {
		CardState card = Srs.seedKnown(NOW, Srs.fsrs());
		assertEquals(30, card.stability, 0);
		assertTrue(card.due - NOW > 21 * Srs.DAY);
	}

	@Test
	void theDisplayLevelFollowsStability() {
		assertEquals(0, Srs.levelFor(0));
		assertEquals(0, Srs.levelFor(0.15));
		assertEquals(3, Srs.levelFor(1.5));
		assertEquals(7, Srs.levelFor(30));
		assertEquals(Srs.MAX_LEVEL, Srs.levelFor(4000));
	}
}
