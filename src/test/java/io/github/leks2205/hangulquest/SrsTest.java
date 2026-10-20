package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Srs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SrsTest {
	@Test
	void intervalsGrow() {
		assertEquals(10 * Srs.MINUTE, Srs.intervalMs(0));
		assertEquals(5 * Srs.MINUTE, Srs.intervalMs(1));
		assertEquals(3 * Srs.DAY, Srs.intervalMs(6));
	}

	@Test
	void correctAdvancesAndWrongRetreats() {
		assertEquals(1, Srs.onCorrect(0));
		assertEquals(6, Srs.onCorrect(6));
		assertEquals(0, Srs.onWrong(0));
		assertEquals(2, Srs.onWrong(3));
	}

	@Test
	void practiceNeverDemotes() {
		assertEquals(4, Srs.onPractice(3, true));
		assertEquals(3, Srs.onPractice(3, false));
		assertEquals(6, Srs.onPractice(6, true));
	}
}
