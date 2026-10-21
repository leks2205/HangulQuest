package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A holiday from a table of dates: when it is near, when it is over and what comes after the table. */
class LunarHolidaysTest {
	private static final LunarHolidays SEOLLAL = new LunarHolidays("seollal", 7, 1,
			List.of(LocalDate.of(2027, 2, 7), LocalDate.of(2028, 1, 27)));

	@Test
	void theWindowRunsFromAWeekAheadToTheLastDayOfTheHoliday() {
		assertTrue(SEOLLAL.activeOn(LocalDate.of(2027, 1, 30)).isEmpty(), "eight days ahead is too early");
		assertEquals(Optional.of(LocalDate.of(2027, 2, 7)), SEOLLAL.activeOn(LocalDate.of(2027, 1, 31)));
		assertEquals(Optional.of(LocalDate.of(2027, 2, 7)), SEOLLAL.activeOn(LocalDate.of(2027, 2, 7)));
		assertEquals(Optional.of(LocalDate.of(2027, 2, 7)), SEOLLAL.activeOn(LocalDate.of(2027, 2, 8)));
		assertTrue(SEOLLAL.activeOn(LocalDate.of(2027, 2, 9)).isEmpty(), "two days after is over");
	}

	@Test
	void theNextDateIsTheFirstOneThatIsNotOverYet() {
		assertEquals(Optional.of(LocalDate.of(2027, 2, 7)), SEOLLAL.nextFrom(LocalDate.of(2026, 10, 22)));
		assertEquals(Optional.of(LocalDate.of(2027, 2, 7)), SEOLLAL.nextFrom(LocalDate.of(2027, 2, 8)));
		assertEquals(Optional.of(LocalDate.of(2028, 1, 27)), SEOLLAL.nextFrom(LocalDate.of(2027, 2, 9)));
	}

	@Test
	void afterTheLastYearOfTheTableThereIsNoDate() {
		assertTrue(SEOLLAL.nextFrom(LocalDate.of(2028, 1, 29)).isEmpty());
		assertTrue(SEOLLAL.activeOn(LocalDate.of(2031, 2, 1)).isEmpty());
	}

	@Test
	void aDateIsWrittenInTheInterfaceLanguage() {
		LocalDate date = LocalDate.of(2027, 2, 7);
		assertEquals("February 7, 2027", LunarHolidays.display(date, Lang.EN));
		assertEquals("7. Februar 2027", LunarHolidays.display(date, Lang.DE));
	}
}
