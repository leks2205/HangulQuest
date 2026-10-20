package de.zirks.hangulquest.learn;

import java.time.LocalDate;
import java.util.Set;

/** Daily streak computed from the persisted day keys (real-world dates). */
public record Streak(int current, int best, String lastDay) {
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
