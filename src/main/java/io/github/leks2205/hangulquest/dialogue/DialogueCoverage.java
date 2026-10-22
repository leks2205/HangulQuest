package io.github.leks2205.hangulquest.dialogue;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.TextClean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lexical coverage of a dialogue line: which of its words the player does not know yet. The rule is
 * at most one such word per line; those are glossed on hover. A line is split into eojeol; each one
 * is a stem from the word lists followed by a particle or ending ({@link #TAILS}). An eojeol that
 * matches nothing is {@link State#UNRESOLVED}: it counts as unknown and has no gloss, so content that
 * ships with one is a mistake the tests catch.
 */
public final class DialogueCoverage {
	/** The most unknown words a line may have. */
	public static final int MAX_UNKNOWN = 1;

	public enum State {
		KNOWN, UNKNOWN, UNRESOLVED
	}

	/** A word of a vocabulary: its id, its gloss in the interface language, and whether the player knows it. */
	public record Hit(String id, String gloss, boolean known) {
	}

	public interface Vocabulary {
		/** The word with exactly this stem, or null. */
		Hit find(String stem);
	}

	/** One eojeol of a line. {@code id} and {@code gloss} are null when nothing matched. */
	public record Token(String text, State state, String id, String gloss) {
	}

	/** Particles, copulas and endings that may follow a stem. Closed on purpose: a new one is added when a line needs it. */
	static final Set<String> TAILS = Set.of(
			"", "이", "가", "은", "는", "을", "를", "도", "만", "의", "에", "에서", "에게", "에게는", "와", "과",
			"로", "으로", "예요", "이에요", "야", "이야", "입니다", "요", "어요", "아요", "해요", "해", "세요",
			"으세요", "습니까", "시겠습니까", "십시오", "겠습니다", "습니다", "합니다", "니다", "자");

	private DialogueCoverage() {
	}

	public static List<Token> analyze(String line, Vocabulary vocabulary) {
		List<Token> tokens = new ArrayList<>();
		for (String eojeol : line.strip().split("\\s+")) {
			if (!eojeol.isEmpty()) {
				tokens.add(token(eojeol, vocabulary));
			}
		}
		return tokens;
	}

	private static Token token(String eojeol, Vocabulary vocabulary) {
		String core = strip(eojeol);
		if (core.isEmpty() || !TextClean.hasHangul(core)) {
			return new Token(eojeol, State.KNOWN, null, null);
		}
		for (int end = core.length(); end >= 1; end--) {
			if (!TAILS.contains(core.substring(end))) {
				continue;
			}
			Hit hit = vocabulary.find(core.substring(0, end));
			if (hit != null) {
				return new Token(eojeol, hit.known() ? State.KNOWN : State.UNKNOWN, hit.id(), hit.gloss());
			}
		}
		return new Token(eojeol, State.UNRESOLVED, null, null);
	}

	/** The eojeol without punctuation or quotes around it. */
	static String strip(String eojeol) {
		int start = 0;
		int end = eojeol.length();
		while (start < end && !isHangul(eojeol.charAt(start))) {
			start++;
		}
		while (end > start && !isHangul(eojeol.charAt(end - 1))) {
			end--;
		}
		return eojeol.substring(start, end);
	}

	private static boolean isHangul(char c) {
		return (c >= '가' && c <= '힣') || (c >= 'ㄱ' && c <= 'ㅣ');
	}

	/** Words the player does not know yet: each unknown word once, and every eojeol nothing matched. */
	public static int unknownWords(List<Token> tokens) {
		Set<String> seen = new HashSet<>();
		int count = 0;
		for (Token token : tokens) {
			if (token.state() == State.UNRESOLVED) {
				count++;
			} else if (token.state() == State.UNKNOWN && seen.add(token.id())) {
				count++;
			}
		}
		return count;
	}

	public static boolean meetsRule(List<Token> tokens) {
		return unknownWords(tokens) <= MAX_UNKNOWN;
	}

	/**
	 * The vocabulary dialogue lines are read against: the word list first, then the catalog. {@code knows}
	 * says whether the player has met a catalog word by its id.
	 */
	public static Vocabulary vocabulary(Catalog catalog, DialogueLexicon lexicon, Predicate<String> knows, Lang lang) {
		Map<String, Hit> forms = new HashMap<>();
		for (DialogueLexicon.Entry entry : lexicon.entries()) {
			Hit hit = new Hit("dialogue:" + entry.id(), entry.gloss(lang), entry.known());
			for (String form : entry.forms()) {
				forms.putIfAbsent(form, hit);
			}
		}
		Map<String, Word> words = new HashMap<>();
		for (Word word : catalog.all()) {
			if (word.phrase || word.ko == null || word.ko.contains(" ")) {
				continue;
			}
			words.putIfAbsent(word.ko, word);
			if (word.category == Category.ACTIONS && word.ko.endsWith("다")) {
				String stem = word.ko.substring(0, word.ko.length() - 1);
				words.putIfAbsent(stem, word);
				if (stem.endsWith("하")) {
					words.putIfAbsent(stem.substring(0, stem.length() - 1) + "해", word);
				}
			}
		}
		return stem -> {
			Hit own = forms.get(stem);
			if (own != null) {
				return own;
			}
			Word word = words.get(stem);
			return word == null ? null : new Hit(word.id, word.gloss(lang), knows.test(word.id));
		};
	}
}
