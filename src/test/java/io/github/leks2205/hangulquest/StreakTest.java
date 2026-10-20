package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Streak;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakTest {
	@Test
	void countsConsecutiveDaysEndingToday() {
		LocalDate today = LocalDate.of(2026, 10, 18);
		Set<String> days = Set.of("2026-10-16", "2026-10-17", "2026-10-18", "2026-10-10");
		Streak streak = Streak.compute(days, today);
		assertEquals(3, streak.current());
		assertEquals(3, streak.best());
		assertEquals("2026-10-18", streak.lastDay());
	}

	@Test
	void yesterdayStillCountsButOlderDoesNot() {
		LocalDate today = LocalDate.of(2026, 10, 18);
		assertEquals(2, Streak.compute(Set.of("2026-10-16", "2026-10-17"), today).current());
		assertEquals(0, Streak.compute(Set.of("2026-10-15", "2026-10-16"), today).current());
	}

	@Test
	void emptyDaysIsZero() {
		assertEquals(0, Streak.compute(Set.of(), LocalDate.now()).current());
	}
}
