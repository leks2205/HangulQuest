package io.github.leks2205.hangulquest.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Study days computed from the persisted day keys (real-world dates). Nothing here is stored: the run
 * and the freezes are derived from the days that have activity, so there is nothing to lose.
 *
 * <p>The goal is {@link #WEEKLY_GOAL} study days out of the last {@link #WEEK_DAYS}. A run of days
 * survives a gap when a freeze covers every missed day; the player gets {@link #FREEZES_PER_MONTH}
 * a calendar month and they are used automatically, oldest gap first. A gap that freezes cannot cover
 * just starts a new run; the earlier run stays in {@code best}.
 *
 * @param current       length of the run that is still open (frozen days bridge it but are not counted)
 * @param best          longest run ever
 * @param lastDay       the latest day with activity
 * @param weekDays      study days among the last seven, today included
 * @param freezesUsed   freezes that covered a missed day in this calendar month
 * @param freezesLeft   freezes still available this month
 */
public record Streak(int current, int best, String lastDay, int weekDays, int freezesUsed, int freezesLeft) {
	/** Local hour at which a new study day starts, so a session from 23:00 to 01:00 is one day. */
	public static final int DAY_STARTS_AT_HOUR = 4;
	public static final int WEEKLY_GOAL = 4;
	public static final int WEEK_DAYS = 7;
	public static final int FREEZES_PER_MONTH = 2;

	/** The study day a moment belongs to. */
	public static LocalDate studyDay(LocalDateTime moment) {
		return moment.minusHours(DAY_STARTS_AT_HOUR).toLocalDate();
	}

	/** The persisted key of the study day, {@code yyyy-MM-dd}. */
	public static String dayKey(LocalDateTime moment) {
		return studyDay(moment).toString();
	}

	/** True when the last seven days hold enough study days. */
	public boolean weeklyGoalMet() {
		return weekDays >= WEEKLY_GOAL;
	}

	public static Streak compute(Set<String> dayKeys, LocalDate today) {
		List<LocalDate> dates = dayKeys.stream().map(Streak::parse).filter(date -> date != null).distinct().sorted().toList();
		Map<YearMonth, Integer> frozen = new HashMap<>();
		int best = 0;
		int run = 0;
		LocalDate previous = null;
		for (LocalDate date : dates) {
			if (previous == null) {
				run = 1;
			} else if (previous.plusDays(1).equals(date)) {
				run++;
			} else if (bridge(frozen, previous.plusDays(1), date.minusDays(1))) {
				run++;
			} else {
				run = 1;
			}
			best = Math.max(best, run);
			previous = date;
		}

		int current = 0;
		if (previous != null) {
			// Today is never "missed" yet; only the days between the last activity and today are.
			LocalDate through = today.minusDays(1);
			boolean open = !previous.isBefore(through)
					|| bridge(frozen, previous.plusDays(1), through);
			current = open ? run : 0;
		}

		int weekDays = 0;
		for (LocalDate date : dates) {
			if (!date.isAfter(today) && date.isAfter(today.minusDays(WEEK_DAYS))) {
				weekDays++;
			}
		}
		int used = frozen.getOrDefault(YearMonth.from(today), 0);
		return new Streak(current, best, dayKeys.stream().max(String::compareTo).orElse(null), weekDays, used,
				Math.max(0, FREEZES_PER_MONTH - used));
	}

	/**
	 * Covers the missed days {@code from} to {@code to} (inclusive) with freezes if every month they fall
	 * in still has enough; uses them only when all of them fit.
	 */
	private static boolean bridge(Map<YearMonth, Integer> frozen, LocalDate from, LocalDate to) {
		Map<YearMonth, Integer> needed = new HashMap<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			int count = needed.merge(YearMonth.from(day), 1, Integer::sum);
			if (frozen.getOrDefault(YearMonth.from(day), 0) + count > FREEZES_PER_MONTH) {
				return false;
			}
		}
		needed.forEach((month, count) -> frozen.merge(month, count, Integer::sum));
		return true;
	}

	private static LocalDate parse(String key) {
		try {
			return LocalDate.parse(key);
		} catch (RuntimeException broken) {
			return null;
		}
	}
}
