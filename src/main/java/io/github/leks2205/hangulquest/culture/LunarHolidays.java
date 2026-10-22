package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.util.Lang;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A holiday that follows the lunar calendar, from a small table of its dates in the Gregorian calendar
 * (culture.json): no network, no calendar library. The table is short on purpose; past its last year the
 * holiday simply has no date and its note is not shown by date.
 */
public record LunarHolidays(String id, int before, int after, List<LocalDate> dates) {
	public LunarHolidays {
		dates = List.copyOf(dates);
	}

	/** The date of the holiday that today is close to: from {@code before} days ahead to {@code after} days past. */
	public Optional<LocalDate> activeOn(LocalDate today) {
		return dates.stream()
				.filter(date -> !today.isBefore(date.minusDays(before)) && !today.isAfter(date.plusDays(after)))
				.findFirst();
	}

	/** A date as the interface language writes it ("February 7, 2027", "7. Februar 2027"). */
	public static String display(LocalDate date, Lang lang) {
		Locale locale = Locale.forLanguageTag(lang.code().replace('_', '-'));
		return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date);
	}

	/** The first date that is not over yet (still within its {@code after} days), or empty when the table has run out. */
	public Optional<LocalDate> nextFrom(LocalDate today) {
		return dates.stream().filter(date -> !today.isAfter(date.plusDays(after))).findFirst();
	}
}
