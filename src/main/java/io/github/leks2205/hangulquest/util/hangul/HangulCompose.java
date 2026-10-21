package io.github.leks2205.hangulquest.util.hangul;

/**
 * Hangul syllable arithmetic: the jamo tables, composing a syllable from its parts, taking it apart
 * again, and the compound vowels and final clusters. Pure and allocation-light; no Minecraft types.
 * Ported from a 두벌식 typing trainer.
 */
public final class HangulCompose {
	/** The 19 initial consonants, in Unicode order (compatibility jamo). */
	public static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
	/** The 21 medial vowels, in Unicode order. */
	public static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
	/** The 27 final consonants in Unicode order, behind a space for "no final". */
	public static final String JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

	public static final char FIRST_SYLLABLE = '가';
	public static final char LAST_SYLLABLE = '힣';
	public static final int SYLLABLES = 11172;

	private static final String VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅛㅜㅠㅡㅣ";
	/** Compound vowel followed by the two keys that make it. */
	private static final String[] COMPOUND_VOWELS = {
			"ㅘㅗㅏ", "ㅙㅗㅐ", "ㅚㅗㅣ", "ㅝㅜㅓ", "ㅞㅜㅔ", "ㅟㅜㅣ", "ㅢㅡㅣ"};
	/** Compound final followed by the two keys that make it. */
	private static final String[] COMPOUND_FINALS = {
			"ㄳㄱㅅ", "ㄵㄴㅈ", "ㄶㄴㅎ", "ㄺㄹㄱ", "ㄻㄹㅁ", "ㄼㄹㅂ", "ㄽㄹㅅ", "ㄾㄹㅌ", "ㄿㄹㅍ", "ㅀㄹㅎ", "ㅄㅂㅅ"};

	/** A syllable taken apart; {@code jong} is 0 when there is no final. */
	public record Syllable(char cho, char jung, char jong) {
	}

	private HangulCompose() {
	}

	public static boolean isSyllable(int codePoint) {
		return codePoint >= FIRST_SYLLABLE && codePoint <= LAST_SYLLABLE;
	}

	/** True for a syllable block or a compatibility jamo (the ones a typed answer is made of). */
	public static boolean isHangul(int codePoint) {
		return isSyllable(codePoint) || (codePoint >= 0x3131 && codePoint <= 0x318E);
	}

	/** A consonant that can open a syllable (all 19 initials). */
	public static boolean isInitial(char c) {
		return CHO.indexOf(c) >= 0;
	}

	/** A single-key vowel (not a compound). */
	public static boolean isVowel(char c) {
		return VOWELS.indexOf(c) >= 0;
	}

	/** True for any of the 14 key vowels or 19 key consonants, i.e. the jamo a keystroke can produce. */
	public static boolean isKeyJamo(char c) {
		return isVowel(c) || isInitial(c);
	}

	/** A consonant that can close a syllable (ㄸ, ㅃ and ㅉ cannot). */
	public static boolean canBeFinal(char c) {
		return c != ' ' && JONG.indexOf(c) > 0 && splitFinal(c) == null;
	}

	/** Index into {@link #CHO}, or -1. */
	public static int choIndex(char c) {
		return CHO.indexOf(c);
	}

	/** Index into {@link #JUNG}, or -1. */
	public static int jungIndex(char c) {
		return JUNG.indexOf(c);
	}

	/** Index into the final table (0 means no final), or -1 for a char that cannot be a final. */
	public static int jongIndex(char c) {
		return c == 0 || c == ' ' ? 0 : JONG.indexOf(c);
	}

	/** The block for these parts; {@code jong} may be 0 or a space for none. */
	public static char compose(char cho, char jung, char jong) {
		int c = choIndex(cho);
		int v = jungIndex(jung);
		int f = jongIndex(jong);
		if (c < 0 || v < 0 || f < 0) {
			throw new IllegalArgumentException("not a syllable: " + cho + jung + jong);
		}
		return compose(c, v, f);
	}

	public static char compose(int cho, int jung, int jong) {
		return (char) (FIRST_SYLLABLE + (cho * 21 + jung) * 28 + jong);
	}

	/** The parts of a syllable block, or null for any other char. */
	public static Syllable decompose(char c) {
		if (!isSyllable(c)) {
			return null;
		}
		int index = c - FIRST_SYLLABLE;
		int jong = index % 28;
		return new Syllable(CHO.charAt(index / 588), JUNG.charAt(index % 588 / 28),
				jong == 0 ? 0 : JONG.charAt(jong));
	}

	/** The compound vowel two key vowels make, or 0. */
	public static char joinVowel(char first, char second) {
		return join(COMPOUND_VOWELS, first, second);
	}

	/** The compound final two consonants make, or 0. */
	public static char joinFinal(char first, char second) {
		return join(COMPOUND_FINALS, first, second);
	}

	/** The two keys of a compound vowel ("ㅘ" gives "ㅗㅏ"), or null when it is not one. */
	public static String splitVowel(char c) {
		return split(COMPOUND_VOWELS, c);
	}

	/** The two keys of a compound final ("ㄳ" gives "ㄱㅅ"), or null when it is not one. */
	public static String splitFinal(char c) {
		return split(COMPOUND_FINALS, c);
	}

	private static char join(String[] table, char first, char second) {
		for (String entry : table) {
			if (entry.charAt(1) == first && entry.charAt(2) == second) {
				return entry.charAt(0);
			}
		}
		return 0;
	}

	private static String split(String[] table, char c) {
		for (String entry : table) {
			if (entry.charAt(0) == c) {
				return entry.substring(1);
			}
		}
		return null;
	}

	/**
	 * The text as the keys that type it: every syllable becomes initial, vowel keys and final keys,
	 * compound vowels and finals split in two ("왜요" gives ㅇㅗㅐㅇㅛ, "값" gives ㄱㅏㅂㅅ). Anything
	 * that is not Hangul stays as it is. Grading compares these sequences.
	 */
	public static String toJamo(CharSequence text) {
		StringBuilder out = new StringBuilder(text.length() * 3);
		text.codePoints().forEach(cp -> appendJamo(out, cp));
		return out.toString();
	}

	static void appendJamo(StringBuilder out, int codePoint) {
		if (codePoint > 0xFFFF) {
			out.appendCodePoint(codePoint);
			return;
		}
		char c = (char) codePoint;
		Syllable syllable = decompose(c);
		if (syllable != null) {
			out.append(syllable.cho());
			appendSplit(out, syllable.jung());
			if (syllable.jong() != 0) {
				appendSplit(out, syllable.jong());
			}
		} else {
			appendSplit(out, c);
		}
	}

	private static void appendSplit(StringBuilder out, char c) {
		String parts = splitVowel(c);
		if (parts == null) {
			parts = splitFinal(c);
		}
		if (parts == null) {
			out.append(c);
		} else {
			out.append(parts);
		}
	}
}
