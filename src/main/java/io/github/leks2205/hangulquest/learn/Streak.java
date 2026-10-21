package io.github.leks2205.hangulquest.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** Daily streak computed from the persisted day keys (real-world dates). */
public record Streak(int current, int best, String lastDay) {
	/** Local hour at which a new study day starts, so a session from 23:00 to 01:00 is one day. */
	public static final int DAY_STARTS_AT_HOUR = 4;

	/** The study day a moment belongs to. */
	public static LocalDate studyDay(LocalDateTime moment) {
		return moment.minusHours(DAY_STARTS_AT_HOUR).toLocalDate();
	}

	/** The persisted key of the study day, {@code yyyy-MM-dd}. */
	public static String dayKey(LocalDateTime moment) {
		return studyDay(moment).toString();
	}

	public static Streak compute(Set<String> dayKeys, LocalDate today) {
		int best = 0;
		int run = 0;
		LocalDate previous = null;
		for (LocalDate date : dayKeys.stream().map(Streak::parse).sorted().toList()) {
			if (date == null) {
				continue;
			}
			if (previous != null && previous.plusDays(1).equals(date)) {
				run++;
			} else {
				run = 1;
			}
			best = Math.max(best, run);
			previous = date;
		}

		int current = 0;
		LocalDate cursor = today;
		if (!dayKeys.contains(cursor.toString()) && dayKeys.contains(today.minusDays(1).toString())) {
			cursor = today.minusDays(1);
		}
		while (dayKeys.contains(cursor.toString())) {
			current++;
			cursor = cursor.minusDays(1);
		}
		if (!dayKeys.contains(today.toString()) && !dayKeys.contains(today.minusDays(1).toString())) {
			current = 0;
		}
		return new Streak(current, best, dayKeys.stream().max(String::compareTo).orElse(null));
	}

	private static LocalDate parse(String key) {
		try {
			return LocalDate.parse(key);
		} catch (RuntimeException broken) {
			return null;
		}
	}
}
