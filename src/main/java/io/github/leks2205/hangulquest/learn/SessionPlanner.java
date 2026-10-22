package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.data.Word;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides which due words are worth the player's time right now. Pure functions of their inputs, so the
 * rules can be tested without a game: {@link LearningService#dueForPlay} feeds it the live state.
 *
 * <ul>
 *   <li>Reviews come before new words, most forgotten first.</li>
 *   <li>New words per study day: the configured 5, 10 or 15, nudged by the 85% rule (Wilson et al. 2019).
 *       "New" means graded for the first time; a quest can still show a word without it counting.</li>
 *   <li>At most one new word per semantic set per session, because similar words interfere while learning.</li>
 * </ul>
 */
public final class SessionPlanner {
	/** Graded reviews the success rate is measured over. */
	public static final int WINDOW = 20;
	/** Fewer graded reviews than this say nothing about the rate yet. */
	public static final int MIN_SAMPLES = 10;
	/** Below this success rate new words are cut in half. */
	public static final double LOW = 0.80;
	/** Above this rate more new words are allowed. */
	public static final double HIGH = 0.92;
	/** Below this rate no new words at all: it is a review day. */
	public static final double STRUGGLING = 0.60;

	private SessionPlanner() {
	}

	/** One word that is due, with what the ranking needs to know. */
	public record Due(Word word, String semSet, boolean fresh, double retrievability, long dueAt,
					  int curriculumGrade, boolean mcOnly) {
	}

	/** The share of right answers among the newest {@link #WINDOW} outcomes; 1 when there are none. */
	public static double successRate(Collection<Boolean> recent) {
		List<Boolean> window = tail(recent);
		if (window.isEmpty()) {
			return 1;
		}
		int right = 0;
		for (boolean correct : window) {
			if (correct) {
				right++;
			}
		}
		return right / (double) window.size();
	}

	/**
	 * The 85% controller: the daily budget after looking at how the last reviews went. Under 80% it is
	 * halved (nothing new under 60%), over 92% it grows by half; in between, or with too few reviews, it stays.
	 *
	 * @param recent outcomes of graded reviews, oldest first
	 */
	public static int adjustedBudget(int base, Collection<Boolean> recent) {
		List<Boolean> window = tail(recent);
		if (window.size() < MIN_SAMPLES) {
			return base;
		}
		double rate = successRate(window);
		if (rate < STRUGGLING) {
			return 0;
		}
		if (rate < LOW) {
			return base / 2;
		}
		if (rate > HIGH) {
			return (int) Math.ceil(base * 1.5);
		}
		return base;
	}

	/** New words still allowed today. */
	public static int newWordsLeft(int configured, Collection<Boolean> recent, int introducedToday) {
		return Math.max(0, adjustedBudget(Config.nearestNewWords(configured), recent) - introducedToday);
	}

	/**
	 * The words to practise, best first: reviews by how much has been forgotten, then the new words the
	 * budget and the semantic-set rule let through (easier curriculum grades first, Minecraft-only words last).
	 *
	 * @param newLeft      new words still allowed today
	 * @param usedSets     semantic sets that already got a new word this session
	 * @param limit        how many words to return at most
	 */
	public static List<Word> rank(List<Due> due, int newLeft, Set<String> usedSets, int limit) {
		return rank(due, newLeft, usedSets, limit, id -> false);
	}

	/** Same, skipping the words {@code heldOut} says are held-out probes: they are never planned. */
	public static List<Word> rank(List<Due> due, int newLeft, Set<String> usedSets, int limit,
								  Predicate<String> heldOut) {
		List<Due> reviews = new ArrayList<>();
		List<Due> fresh = new ArrayList<>();
		for (Due entry : due) {
			if (heldOut.test(entry.word().id)) {
				continue;
			}
			(entry.fresh() ? fresh : reviews).add(entry);
		}
		reviews.sort(Comparator.comparingDouble(Due::retrievability).thenComparingLong(Due::dueAt)
				.thenComparing(entry -> entry.word().id));
		fresh.sort(Comparator.comparing(Due::mcOnly)
				.thenComparingInt(entry -> entry.curriculumGrade() <= 0 ? 99 : entry.curriculumGrade())
				.thenComparingLong(Due::dueAt).thenComparing(entry -> entry.word().id));

		List<Word> out = new ArrayList<>();
		for (Due entry : reviews) {
			if (out.size() >= limit) {
				return out;
			}
			out.add(entry.word());
		}
		Set<String> taken = new HashSet<>(usedSets);
		int picked = 0;
		for (Due entry : fresh) {
			if (out.size() >= limit || picked >= newLeft) {
				break;
			}
			if (taken.add(entry.semSet())) {
				out.add(entry.word());
				picked++;
			}
		}
		return out;
	}

	private static List<Boolean> tail(Collection<Boolean> recent) {
		List<Boolean> all = new ArrayList<>(recent);
		return all.size() <= WINDOW ? all : all.subList(all.size() - WINDOW, all.size());
	}
}
