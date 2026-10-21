package io.github.leks2205.hangulquest.learn;

import java.util.ArrayList;
import java.util.List;

/**
 * What a word has been through, kept per word in progress.json: how often it was met, in how many
 * different contexts, when, and on which study days it was recalled unaided. The recycling rule and
 * {@link Mastery} read this; nothing here moves the review schedule.
 */
public final class ExposureLedger {
	/** The newest timestamps kept per word. */
	public static final int TIMES = 20;
	static final int CONTEXTS = 40;
	static final int RECALL_DAYS = 12;

	/** Where an encounter happened: the goal type, the verb, the word's role in the step and the trail. */
	public record Context(String goalType, String verb, String role, String trail) {
		public static final Context NONE = new Context("", "", "", "");

		public Context {
			goalType = goalType == null ? "" : goalType;
			verb = verb == null ? "" : verb;
			role = role == null ? "" : role;
			trail = trail == null ? "" : trail;
		}

		public boolean isNone() {
			return goalType.isEmpty() && verb.isEmpty() && role.isEmpty() && trail.isEmpty();
		}

		/** Stable text form, {@code goalType|verb|role|trail}. */
		public String key() {
			return goalType + "|" + verb + "|" + role + "|" + trail;
		}

		public static Context parse(String key) {
			String[] parts = key == null ? new String[0] : key.split("\\|", -1);
			return new Context(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3));
		}

		private static String part(String[] parts, int index) {
			return index < parts.length ? parts[index] : "";
		}
	}

	/** The saved part, hung on {@link WordProgress#ledger}. */
	public static final class Entry {
		public int encounters;
		/** Graded recall attempts (right or wrong). */
		public int retrievals;
		/** Distinct {@link Context#key()} values, oldest first. */
		public List<String> contexts = new ArrayList<>();
		/** The last {@link #TIMES} encounters and retrievals, oldest first. */
		public List<Long> times = new ArrayList<>();
		/** Study days ({@code yyyy-MM-dd}) with a right unaided recall, ascending, newest kept. */
		public List<String> recallDays = new ArrayList<>();
	}

	private ExposureLedger() {
	}

	/** The ledger of a word, created empty on first use. */
	public static Entry of(WordProgress progress) {
		if (progress.ledger == null) {
			progress.ledger = new Entry();
		}
		Entry entry = progress.ledger;
		entry.contexts = entry.contexts == null ? new ArrayList<>() : entry.contexts;
		entry.times = entry.times == null ? new ArrayList<>() : entry.times;
		entry.recallDays = entry.recallDays == null ? new ArrayList<>() : entry.recallDays;
		return entry;
	}

	/** An encounter without recall. */
	public static void encounter(WordProgress progress, Context context, long now) {
		Entry entry = of(progress);
		entry.encounters++;
		addContext(entry, context);
		addTime(entry, now);
	}

	/** A graded recall attempt; a right one on a new study day is remembered for {@link Mastery}. */
	public static void retrieval(WordProgress progress, Context context, long now, boolean rightRecall, String studyDay) {
		Entry entry = of(progress);
		entry.encounters++;
		entry.retrievals++;
		addContext(entry, context);
		addTime(entry, now);
		if (rightRecall && studyDay != null && !entry.recallDays.contains(studyDay)) {
			entry.recallDays.add(studyDay);
			entry.recallDays.sort(String::compareTo);
			while (entry.recallDays.size() > RECALL_DAYS) {
				entry.recallDays.remove(0);
			}
		}
	}

	public static int encounters(WordProgress progress) {
		return progress.ledger == null ? 0 : progress.ledger.encounters;
	}

	public static int retrievals(WordProgress progress) {
		return progress.ledger == null ? 0 : progress.ledger.retrievals;
	}

	public static int distinctContexts(WordProgress progress) {
		return progress.ledger == null || progress.ledger.contexts == null ? 0 : progress.ledger.contexts.size();
	}

	public static int recallDays(WordProgress progress) {
		return progress.ledger == null || progress.ledger.recallDays == null ? 0 : progress.ledger.recallDays.size();
	}

	/** Encounters among the kept timestamps that are not older than {@code days}. */
	public static int recentEncounters(WordProgress progress, long now, int days) {
		if (progress.ledger == null || progress.ledger.times == null) {
			return 0;
		}
		long from = now - days * 86_400_000L;
		int count = 0;
		for (long time : progress.ledger.times) {
			if (time >= from) {
				count++;
			}
		}
		return count;
	}

	private static void addContext(Entry entry, Context context) {
		if (context == null || context.isNone() || entry.contexts.contains(context.key())) {
			return;
		}
		entry.contexts.add(context.key());
		while (entry.contexts.size() > CONTEXTS) {
			entry.contexts.remove(0);
		}
	}

	private static void addTime(Entry entry, long now) {
		entry.times.add(now);
		while (entry.times.size() > TIMES) {
			entry.times.remove(0);
		}
	}
}
