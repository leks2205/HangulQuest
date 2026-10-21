package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Srs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrsTest {
	@Test
	void intervalsGrow() {
		assertEquals(10 * Srs.MINUTE, Srs.intervalMs(0));
		assertEquals(Srs.HOUR, Srs.intervalMs(1));
		assertEquals(3 * Srs.DAY, Srs.intervalMs(4));
		assertEquals(80 * Srs.DAY, Srs.intervalMs(Srs.MAX_LEVEL));
	}

	@Test
	void intervalsStrictlyIncreaseWithTheLevel() {
		for (int level = 1; level <= Srs.MAX_LEVEL; level++) {
			assertTrue(Srs.intervalMs(level) > Srs.intervalMs(level - 1),
					"level " + level + " must wait longer than level " + (level - 1));
		}
		// Out-of-range levels clamp instead of throwing or going backwards.
		assertEquals(Srs.intervalMs(0), Srs.intervalMs(-3));
		assertEquals(Srs.intervalMs(Srs.MAX_LEVEL), Srs.intervalMs(Srs.MAX_LEVEL + 5));
	}

	@Test
	void levelsSavedByOlderVersionsStayValid() {
		// Older versions stored 0..6; none of them may be clamped down by the longer ladder.
		for (int saved = 0; saved <= 6; saved++) {
			assertEquals(saved + 1, Srs.onCorrect(saved), "a saved level " + saved + " must still advance");
			assertEquals(saved, Srs.onWrong(saved + 1));
		}
	}

	@Test
	void correctAdvancesAndWrongRetreats() {
		assertEquals(1, Srs.onCorrect(0));
		assertEquals(7, Srs.onCorrect(6));
		assertEquals(Srs.MAX_LEVEL, Srs.onCorrect(Srs.MAX_LEVEL));
		assertEquals(0, Srs.onWrong(0));
		assertEquals(2, Srs.onWrong(3));
	}
}
