package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.learn.Streak;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	void yesterdayStillCountsAndAFarOlderRunDoesNot() {
		LocalDate today = LocalDate.of(2026, 10, 18);
		assertEquals(2, Streak.compute(Set.of("2026-10-16", "2026-10-17"), today).current());
		assertEquals(0, Streak.compute(Set.of("2026-10-10", "2026-10-11"), today).current(),
				"six missed days are more than the freezes cover");
	}

	@Test
	void emptyDaysIsZero() {
		assertEquals(0, Streak.compute(Set.of(), LocalDate.now()).current());
	}

	// ------------------------------------------------------------------ weekly goal and freezes

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	private static Set<String> days(String... keys) {
		return new HashSet<>(List.of(keys));
	}

	@Test
	void theWeeklyGoalIsFourStudyDaysInTheLastSeven() {
		// The window is today and the six days before it: the 15th to the 21st.
		Streak met = Streak.compute(days("2026-10-15", "2026-10-17", "2026-10-19", "2026-10-21"), TODAY);
		assertEquals(4, met.weekDays());
		assertTrue(met.weeklyGoalMet());

		Streak notYet = Streak.compute(days("2026-10-14", "2026-10-17", "2026-10-19", "2026-10-21"), TODAY);
		assertEquals(3, notYet.weekDays(), "the 14th is eight days ago");
		assertFalse(notYet.weeklyGoalMet());

		assertFalse(Streak.compute(days(), TODAY).weeklyGoalMet());
	}

	@Test
	void aMissedDayIsCoveredByAFreezeAutomatically() {
		Streak streak = Streak.compute(days("2026-10-17", "2026-10-18", "2026-10-20", "2026-10-21"), TODAY);

		assertEquals(4, streak.current(), "the 19th was frozen, the run goes on (frozen days are not counted)");
		assertEquals(1, streak.freezesUsed());
		assertEquals(1, streak.freezesLeft());
	}

	@Test
	void twoFreezesPerMonthAndNotThree() {
		// Gaps on the 3rd, 5th and 7th: the third one has no freeze left in October.
		Streak streak = Streak.compute(days("2026-10-02", "2026-10-04", "2026-10-06", "2026-10-08", "2026-10-09"),
				LocalDate.of(2026, 10, 9));

		assertEquals(2, streak.current(), "the run restarted at the 8th");
		assertEquals(3, streak.best(), "the 2nd, 4th and 6th, bridged by two freezes");
		assertEquals(2, streak.freezesUsed());
		assertEquals(0, streak.freezesLeft());
	}

	@Test
	void freezesRefillWithTheNextMonth() {
		Streak streak = Streak.compute(days("2026-09-27", "2026-09-29", "2026-10-01", "2026-10-03", "2026-10-05"),
				LocalDate.of(2026, 10, 5));

		assertEquals(5, streak.current());
		assertEquals(2, streak.freezesUsed(), "this month's two; September's two were its own");
		assertEquals(0, streak.freezesLeft());
	}

	@Test
	void aGapTheFreezesCannotCoverStartsANewRunWithoutLosingTheBest() {
		Streak streak = Streak.compute(days("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-10", "2026-10-11"),
				LocalDate.of(2026, 10, 11));

		assertEquals(2, streak.current());
		assertEquals(3, streak.best(), "the earlier run is kept as the best");
		assertEquals(0, streak.freezesUsed(), "a freeze that cannot save the run is not spent");
		assertEquals(2, streak.freezesLeft());
	}

	@Test
	void todayIsNeverMissedYet() {
		Streak streak = Streak.compute(days("2026-10-19", "2026-10-20"), TODAY);

		assertEquals(2, streak.current());
		assertEquals(0, streak.freezesUsed());
	}

	@Test
	void missingTodayAndYesterdayUsesAFreezeForYesterday() {
		Streak streak = Streak.compute(days("2026-10-18", "2026-10-19"), TODAY);

		assertEquals(2, streak.current(), "the 20th is covered");
		assertEquals(1, streak.freezesUsed());
	}

	@Test
	void noStreakDataCanBeLost() {
		// Even a long absence leaves the best run in place and nothing negative anywhere.
		Streak streak = Streak.compute(days("2026-01-01", "2026-01-02", "2026-01-03"), TODAY);
		assertEquals(0, streak.current());
		assertEquals(3, streak.best());
		assertEquals(2, streak.freezesLeft());
		assertEquals(0, streak.weekDays());
	}
}
