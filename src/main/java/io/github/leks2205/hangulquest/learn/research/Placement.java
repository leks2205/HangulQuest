package io.github.leks2205.hangulquest.learn.research;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.data.LexiconTags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The placement test: a Yes/No vocabulary test (Meara). The player sees one Korean word at a time and says
 * whether they know it. Most are real words from across the curriculum; some are pseudowords, and saying
 * "yes" to those is a false alarm, which is how guessing and boasting are told apart from knowing.
 *
 * <p>The score is the hit rate minus the false alarm rate. Words the player claims are seeded into the
 * scheduler with a stability of three days, so they are checked soon rather than trusted. The test is
 * optional and never a gate.
 */
public final class Placement {
	public static final int REAL_WORDS = 48;
	public static final int PSEUDOWORDS = 12;
	/** Real words per band: curriculum grades 1-2, 3-4, 5-6, then words the curriculum list does not grade. */
	static final int[] PER_BAND = {16, 14, 10, 8};
	/** A false alarm rate above this means the answers say little; no word is seeded. */
	public static final double MAX_FALSE_ALARM_RATE = 0.5;
	/** Stability in days given to a word the player claims to know. */
	public static final double SEED_STABILITY_DAYS = 3;

	/** One thing to judge: a real word (with its id) or a pseudoword (no word id). */
	public record Item(String ko, String wordId, boolean real) {
	}

	/** The outcome of a finished test. */
	public record Result(int realShown, int hits, int pseudoShown, int falseAlarms) {
		public double hitRate() {
			return realShown == 0 ? 0 : hits / (double) realShown;
		}

		public double falseAlarmRate() {
			return pseudoShown == 0 ? 0 : falseAlarms / (double) pseudoShown;
		}

		/** Hits minus false alarms, as rates, from 0 to 1 (Meara's correction for guessing). */
		public double score() {
			return Math.max(0, hitRate() - falseAlarmRate());
		}

		/** False alarms are rare enough that the claims can be believed at all. */
		public boolean reliable() {
			return falseAlarmRate() <= MAX_FALSE_ALARM_RATE;
		}
	}

	private final List<Item> items;
	private final List<Boolean> answers = new ArrayList<>();

	public Placement(List<Item> items) {
		this.items = List.copyOf(items);
	}

	/**
	 * Draws the test: {@link #REAL_WORDS} real words spread over the bands and {@link #PSEUDOWORDS} of the
	 * pseudowords, mixed. A band without enough words is topped up from the others.
	 */
	public static Placement build(Collection<Word> words, LexiconTags tags, List<String> pseudowords, Random random) {
		List<List<Word>> bands = new ArrayList<>();
		for (int i = 0; i < PER_BAND.length; i++) {
			bands.add(new ArrayList<>());
		}
		for (Word word : words) {
			if (!usable(word)) {
				continue;
			}
			LexiconTags.Tag tag = tags.tag(word);
			if (tag == null || tag.mcOnly()) {
				continue;
			}
			int grade = tag.curriculumGrade();
			int band = grade <= 0 ? 3 : grade <= 2 ? 0 : grade <= 4 ? 1 : 2;
			bands.get(band).add(word);
		}
		List<Word> picked = new ArrayList<>();
		List<Word> spare = new ArrayList<>();
		for (int i = 0; i < PER_BAND.length; i++) {
			List<Word> band = bands.get(i);
			band.sort((a, b) -> a.id.compareTo(b.id)); // the same random seed gives the same test
			Collections.shuffle(band, random);
			int take = Math.min(PER_BAND[i], band.size());
			picked.addAll(band.subList(0, take));
			spare.addAll(band.subList(take, band.size()));
		}
		spare.sort((a, b) -> a.id.compareTo(b.id));
		Collections.shuffle(spare, random);
		for (int i = 0; picked.size() < REAL_WORDS && i < spare.size(); i++) {
			picked.add(spare.get(i));
		}
		List<String> nonwords = new ArrayList<>(pseudowords);
		Collections.shuffle(nonwords, random);
		List<Item> items = new ArrayList<>();
		for (Word word : picked) {
			items.add(new Item(word.ko, word.id, true));
		}
		for (int i = 0; i < Math.min(PSEUDOWORDS, nonwords.size()); i++) {
			items.add(new Item(nonwords.get(i), null, false));
		}
		Collections.shuffle(items, random);
		return new Placement(items);
	}

	/** A single-token, short, ordinary word: no phrases, no spaces, no digits. */
	private static boolean usable(Word word) {
		return word != null && word.conceptId != null && !word.phrase && word.ko != null
				&& word.ko.length() >= 1 && word.ko.length() <= 5 && word.ko.chars().noneMatch(Character::isWhitespace);
	}

	public List<Item> items() {
		return items;
	}

	public int size() {
		return items.size();
	}

	public int answered() {
		return answers.size();
	}

	public boolean finished() {
		return answers.size() >= items.size();
	}

	/** The item to judge now, or null when the test is over. */
	public Item current() {
		return finished() ? null : items.get(answers.size());
	}

	/** Records the answer to the current item; ignored once the test is over. */
	public void answer(boolean knows) {
		if (!finished()) {
			answers.add(knows);
		}
	}

	public Result result() {
		int realShown = 0;
		int hits = 0;
		int pseudoShown = 0;
		int alarms = 0;
		for (int i = 0; i < answers.size(); i++) {
			Item item = items.get(i);
			if (item.real()) {
				realShown++;
				if (answers.get(i)) {
					hits++;
				}
			} else {
				pseudoShown++;
				if (answers.get(i)) {
					alarms++;
				}
			}
		}
		return new Result(realShown, hits, pseudoShown, alarms);
	}

	/** The real words the player said they know; empty when the false alarms make the answers unreliable. */
	public List<String> claimedWordIds() {
		if (!result().reliable()) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < answers.size(); i++) {
			Item item = items.get(i);
			if (item.real() && answers.get(i)) {
				ids.add(item.wordId());
			}
		}
		return ids;
	}
}
