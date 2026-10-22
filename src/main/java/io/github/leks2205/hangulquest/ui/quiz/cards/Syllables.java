package io.github.leks2205.hangulquest.ui.quiz.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Hangul syllable arithmetic for the tile card: splitting a syllable into its jamo, measuring how many
 * jamo two words differ by, and finding syllables that look or sound close to a given one.
 * Free of any game types.
 */
public final class Syllables {
	private static final int FIRST = 0xAC00;
	private static final int LAST = 0xD7A3;
	private static final int VOWELS = 21;
	private static final int TAILS = 28;

	/** Lead jamo (index order of the Unicode syllable block) that are easy to mix up, as index groups. */
	private static final int[][] LEAD_GROUPS = {
			{0, 15, 1},   // ㄱ ㅋ ㄲ
			{3, 16, 4},   // ㄷ ㅌ ㄸ
			{7, 17, 8},   // ㅂ ㅍ ㅃ
			{12, 14, 13}, // ㅈ ㅊ ㅉ
			{9, 10},      // ㅅ ㅆ
			{2, 6, 11},   // ㄴ ㅁ ㅇ
			{5, 2},       // ㄹ ㄴ
			{18, 11},     // ㅎ ㅇ
	};
	/**
	 * Vowel groups: ㅏ ㅓ, ㅗ ㅜ ㅡ, ㅑ ㅕ, ㅛ ㅠ, ㅣ ㅢ, ㅘ ㅝ, ㅏ ㅑ, ㅓ ㅕ. ㅐ and ㅔ are left out: they sound
	 * the same today, so no one can tell them apart by ear.
	 */
	private static final int[][] VOWEL_GROUPS = {
			{0, 4}, {8, 13, 18}, {2, 6}, {12, 17}, {20, 19}, {9, 14}, {0, 2}, {4, 6},
	};
	/** Tail alternatives: none, and the sounds that are heard at the end of a syllable. */
	private static final int[] COMMON_TAILS = {0, 4, 8, 16, 21};

	private Syllables() {
	}

	public static boolean isSyllable(char c) {
		return c >= FIRST && c <= LAST;
	}

	/** True when the text is one or more Hangul syllables and nothing else (no space, no Latin). */
	public static boolean onlySyllables(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (!isSyllable(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/** The jamo of a text as small integers: lead, vowel and, when there is one, tail of every syllable. */
	static List<Integer> jamo(String text) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (!isSyllable(c)) {
				out.add(-c); // anything else stays itself
				continue;
			}
			int index = c - FIRST;
			out.add(index / (VOWELS * TAILS));
			out.add(1000 + (index / TAILS) % VOWELS);
			int tail = index % TAILS;
			if (tail > 0) {
				out.add(2000 + tail);
			}
		}
		return out;
	}

	/** How many jamo must change, be added or be removed to turn one text into the other. */
	public static int jamoDistance(String a, String b) {
		List<Integer> x = jamo(a);
		List<Integer> y = jamo(b);
		int[] previous = new int[y.size() + 1];
		int[] current = new int[y.size() + 1];
		for (int j = 0; j <= y.size(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= x.size(); i++) {
			current[0] = i;
			for (int j = 1; j <= y.size(); j++) {
				int cost = x.get(i - 1).equals(y.get(j - 1)) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[y.size()];
	}

	/** Syllables one jamo away from this one in a way learners confuse: a similar lead, vowel or tail. */
	static Set<Character> lookAlikes(char syllable) {
		Set<Character> out = new LinkedHashSet<>();
		int index = syllable - FIRST;
		int lead = index / (VOWELS * TAILS);
		int vowel = (index / TAILS) % VOWELS;
		int tail = index % TAILS;
		for (int other : partners(LEAD_GROUPS, lead)) {
			out.add(compose(other, vowel, tail));
		}
		for (int other : partners(VOWEL_GROUPS, vowel)) {
			out.add(compose(lead, other, tail));
		}
		for (int other : COMMON_TAILS) {
			if (other != tail) {
				out.add(compose(lead, vowel, other));
			}
		}
		out.remove(syllable);
		return out;
	}

	private static List<Integer> partners(int[][] groups, int value) {
		List<Integer> out = new ArrayList<>();
		for (int[] group : groups) {
			for (int member : group) {
				if (member == value) {
					for (int other : group) {
						if (other != value && !out.contains(other)) {
							out.add(other);
						}
					}
				}
			}
		}
		return out;
	}

	private static char compose(int lead, int vowel, int tail) {
		return (char) (FIRST + (lead * VOWELS + vowel) * TAILS + tail);
	}

	/**
	 * Distractor syllables for a word: look-alikes of its syllables, taken in turn so every syllable gets
	 * some, none equal to a syllable of the word and none twice.
	 */
	public static List<String> distractors(String word, int count, Random random) {
		Set<Character> own = new LinkedHashSet<>();
		for (int i = 0; i < word.length(); i++) {
			own.add(word.charAt(i));
		}
		List<List<Character>> perSyllable = new ArrayList<>();
		for (char c : own) {
			List<Character> options = new ArrayList<>(lookAlikes(c));
			options.removeAll(own);
			Collections.shuffle(options, random);
			perSyllable.add(options);
		}
		Set<Character> picked = new LinkedHashSet<>();
		for (int round = 0; picked.size() < count; round++) {
			boolean any = false;
			for (List<Character> options : perSyllable) {
				if (round < options.size()) {
					any = true;
					if (picked.size() < count) {
						picked.add(options.get(round));
					}
				}
			}
			if (!any) {
				break;
			}
		}
		List<String> out = new ArrayList<>();
		for (char c : picked) {
			out.add(String.valueOf(c));
		}
		return out;
	}
}
