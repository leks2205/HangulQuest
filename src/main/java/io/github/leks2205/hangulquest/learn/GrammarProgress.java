package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the player has met of the grammar ladder, derived from the GRAMMAR lines of the review log (no
 * separate file, so nothing to migrate). Per construction: the quest steps it appeared in, whether its
 * rule card was shown, and a Bayesian knowledge tracing estimate p(L) fed by form-dependent outcomes
 * (cloze answers, steps where the form decides the action).
 *
 * <p>Log lines: an exposure has no grade and the step in {@code ctx}; a shown rule card has {@code ctx}
 * {@link #CARD}; an outcome has a grade; {@code ladder.N} lines count one step written at ladder level N;
 * a {@link #RESET} line starts over.
 */
public final class GrammarProgress {
	public static final double P_L0 = 0.10;
	public static final double P_TRANSIT = 0.15;
	public static final double P_GUESS = 0.25;
	public static final double P_SLIP = 0.10;
	public static final double MASTERED = 0.95;

	public static final String CARD = "card";
	public static final String RESET = "reset";
	public static final String LADDER = "ladder.";

	private static final Pattern CONSTRUCTION = Pattern.compile("L[1-6]\\.\\S+");

	private static final class Entry {
		int steps;
		boolean card;
		int answers;
		int correct;
		double pL = P_L0;
	}

	private final Map<String, Entry> entries = new HashMap<>();
	private final int[] ladderSteps = new int[Ladder.LEVELS + 1];
	private int unlocked;
	private int version;

	/** True for a construction id of grammar.json ({@code L2.seyo}); false for words and bookkeeping items. */
	public static boolean isConstruction(String item) {
		return item != null && CONSTRUCTION.matcher(item).matches();
	}

	/** The BKT step: the evidence of one answer, then the chance of having learned it on this attempt. */
	public static double update(double pL, boolean correct) {
		double seen = correct
				? pL * (1 - P_SLIP) / (pL * (1 - P_SLIP) + (1 - pL) * P_GUESS)
				: pL * P_SLIP / (pL * P_SLIP + (1 - pL) * (1 - P_GUESS));
		return seen + (1 - seen) * P_TRANSIT;
	}

	/** Counts every change, so a screen can tell when to rebuild what it shows. */
	public int version() {
		return version;
	}

	/** The construction appeared in a quest step. */
	public void step(String id) {
		entry(id).steps++;
		version++;
	}

	/** A quest step was written at this ladder level. */
	public void ladderStep(int level) {
		if (level >= 1 && level <= Ladder.LEVELS) {
			ladderSteps[level]++;
			version++;
		}
	}

	/** The rule card of the construction was shown. */
	public void cardShown(String id) {
		entry(id).card = true;
		version++;
	}

	/** A form-dependent outcome: updates p(L). */
	public void outcome(String id, boolean correct) {
		Entry entry = entry(id);
		entry.answers++;
		if (correct) {
			entry.correct++;
		}
		entry.pL = update(entry.pL, correct);
		version++;
	}

	private Entry entry(String id) {
		return entries.computeIfAbsent(id, key -> new Entry());
	}

	/** Met: seen in a step, its card shown, or answered. */
	public boolean met(String id) {
		Entry entry = entries.get(id);
		return entry != null && (entry.steps > 0 || entry.card || entry.answers > 0);
	}

	/** Whether the rule card of the construction has been shown. */
	public boolean carded(String id) {
		Entry entry = entries.get(id);
		return entry != null && entry.card;
	}

	public int steps(String id) {
		Entry entry = entries.get(id);
		return entry == null ? 0 : entry.steps;
	}

	public int answers(String id) {
		Entry entry = entries.get(id);
		return entry == null ? 0 : entry.answers;
	}

	/** p(L) of a construction; the prior for one never answered. */
	public double pL(String id) {
		Entry entry = entries.get(id);
		return entry == null ? P_L0 : entry.pL;
	}

	public boolean mastered(String id) {
		return pL(id) >= MASTERED;
	}

	/** Every met construction id, sorted for a stable order. */
	public Set<String> met() {
		Set<String> ids = new LinkedHashSet<>();
		entries.keySet().stream().sorted().filter(this::met).forEach(ids::add);
		return ids;
	}

	/** Quest steps written at a ladder level so far. */
	public int stepsAtLevel(int level) {
		return level >= 1 && level <= Ladder.LEVELS ? ladderSteps[level] : 0;
	}

	/**
	 * The player's grammar level: the highest ladder level with at least {@code gateSteps} quest steps
	 * behind it, 0 for a new player. Text may use this level plus the next one.
	 */
	public int level(int gateSteps) {
		for (int level = Ladder.LEVELS; level >= 1; level--) {
			if (ladderSteps[level] >= gateSteps) {
				return Math.max(level, unlocked);
			}
		}
		return unlocked;
	}

	public int level() {
		return level(Ladder.shared().gateSteps());
	}

	/** For tools and tests that need every sentence the content can produce, whatever the player has met. */
	public void unlockAll() {
		unlocked = Ladder.LEVELS;
		version++;
	}

	/** Applies one line of the review log; other kinds are ignored. */
	public void apply(ReviewLog.Event event) {
		if (event.kind() != ReviewKind.GRAMMAR) {
			return;
		}
		String item = event.item();
		if (RESET.equals(item)) {
			entries.clear();
			Arrays.fill(ladderSteps, 0);
			version++;
		} else if (item.startsWith(LADDER)) {
			try {
				ladderStep(Integer.parseInt(item.substring(LADDER.length())));
			} catch (NumberFormatException damaged) {
				// a line from a newer version; nothing to count
			}
		} else if (!isConstruction(item)) {
			return;
		} else if (event.grade() != null) {
			outcome(item, event.grade() > Fsrs.AGAIN);
		} else if (CARD.equals(event.ctx())) {
			cardShown(item);
		} else {
			step(item);
		}
	}

	/** Replays a log into this progress, lines in time order. */
	public void load(List<ReviewLog.Event> events) {
		events.stream().sorted(Comparator.comparingLong(ReviewLog.Event::t)).forEach(this::apply);
	}

	/** The progress a log tells. */
	public static GrammarProgress fromEvents(List<ReviewLog.Event> events) {
		GrammarProgress progress = new GrammarProgress();
		progress.load(events);
		return progress;
	}
}
