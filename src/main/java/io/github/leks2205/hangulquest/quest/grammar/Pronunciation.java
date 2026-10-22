package io.github.leks2205.hangulquest.quest.grammar;

import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.util.hangul.HangulCompose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The standard pronunciation (표준 발음법) of a word or a short phrase: {@code 국물} gives {@code [궁물]}, and
 * the rules that did it. Rule based and conservative: where the standard allows variants the first form is
 * returned, and what only the dictionary knows (compound words, ㄴ insertion, a few stems) comes from
 * {@link PronunciationExceptions}.
 *
 * <p>Inside a word all rules apply. Between two words (a space) only the ones that every speaker makes
 * apply: the final sound, carrying a final over to a vowel, nasalisation of ㄱ ㄷ ㅂ before ㄴ ㅁ, and the
 * ㅎ contact. Tensification and ㄹ rules stay inside the word.
 */
public final class Pronunciation {
	/** The rule tags, in the order they are listed. {@link #id()} is what the data files and lang keys use. */
	public enum Rule {
		LIAISON("liaison", "연음"),
		TENSIFICATION("tensification", "경음화"),
		NASALISATION("nasalisation", "비음화"),
		LATERALISATION("lateralisation", "유음화"),
		H("h", "ㅎ 받침"),
		PALATALISATION("palatalisation", "구개음화"),
		NEUTRALISATION("neutralisation", "끝소리 규칙"),
		DOUBLE_FINAL("double_final", "겹받침"),
		N_INSERTION("n_insertion", "ㄴ 첨가"),
		UI("ui", "ㅢ 발음");

		private final String id;
		private final String ko;

		Rule(String id, String ko) {
			this.id = id;
			this.ko = ko;
		}

		public String id() {
			return id;
		}

		/** The grammar term, as a learner meets it in textbooks. */
		public String ko() {
			return ko;
		}

		/** Lang key of the short name in the interface language. */
		public String key() {
			return "hangulquest.ui.pron.rule." + id;
		}

		public static Rule byId(String id) {
			for (Rule rule : values()) {
				if (rule.id.equals(id)) {
					return rule;
				}
			}
			return null;
		}
	}

	/**
	 * The spelling, its standard pronunciation (without brackets) and the rules that applied, in {@link Rule}
	 * order. {@code lexical} says that a word came from {@link PronunciationExceptions} and not from the rules.
	 */
	public record Result(String ko, String pron, Set<Rule> rules, boolean lexical) {
		public Result {
			rules = Collections.unmodifiableSet(rules.isEmpty() ? EnumSet.noneOf(Rule.class) : EnumSet.copyOf(rules));
		}

		/** True when the word is not said as it is written. */
		public boolean differs() {
			return !pron.equals(ko);
		}

		/** {@code [궁물]}, or null when the word is said as written (nothing to show then). */
		public String bracket() {
			return differs() ? "[" + pron + "]" : null;
		}

		/** The rule ids, comma separated, in {@link Rule} order: "liaison,neutralisation". */
		public String ruleIds() {
			StringBuilder out = new StringBuilder();
			for (Rule rule : rules) {
				out.append(out.length() == 0 ? "" : ",").append(rule.id());
			}
			return out.toString();
		}
	}

	private static final char[] TENSE_FROM = {'ㄱ', 'ㄷ', 'ㅂ', 'ㅅ', 'ㅈ'};
	private static final char[] TENSE_TO = {'ㄲ', 'ㄸ', 'ㅃ', 'ㅆ', 'ㅉ'};
	/** The dictionary ending of a verb. 고/지/게 would catch nouns such as 신고 and 감지, so only 다 counts. */
	private static final String STEM_ENDINGS = "다";

	private Pronunciation() {
	}

	public static Result of(String ko) {
		return of(ko, false, PronunciationExceptions.shared());
	}

	/**
	 * {@code sino}: the word is Sino-Korean, so ㄹ before ㄷ ㅅ ㅈ is tensed (발달 gives 발딸). For a phrase it
	 * holds for every word; the words listed as {@code sinoWords} in the exceptions are Sino-Korean anyway.
	 */
	public static Result of(String ko, boolean sino) {
		return of(ko, sino, PronunciationExceptions.shared());
	}

	public static Result of(String ko, boolean sino, PronunciationExceptions exceptions) {
		String text = ko == null ? "" : ko.strip();
		Set<Rule> rules = EnumSet.noneOf(Rule.class);
		boolean lexical = false;
		List<List<Syl>> eojeols = new ArrayList<>();
		for (String word : text.split(" +")) {
			if (word.isEmpty()) {
				continue;
			}
			PronunciationExceptions.Override override = exceptions.override(word);
			if (override != null) {
				eojeols.add(parse(override.pron()));
				rules.addAll(override.rules());
				lexical = true;
			} else {
				List<Syl> syllables = parse(word);
				// the Sino-Korean rule goes by the origin of this word, not of the phrase around it
				boolean wordSino = sino || exceptions.isSino(word);
				lexical |= inside(syllables, word, wordSino, exceptions, rules);
				eojeols.add(syllables);
			}
		}
		for (int i = 0; i + 1 < eojeols.size(); i++) {
			List<Syl> left = eojeols.get(i);
			List<Syl> right = eojeols.get(i + 1);
			Syl a = left.get(left.size() - 1);
			Syl b = right.get(0);
			if (a.isLiteral() || b.isLiteral()) {
				finalise(a, rules);
			} else {
				boundary(a, b, Mode.SPACE, false, false, false, rules);
			}
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < eojeols.size(); i++) {
			List<Syl> eojeol = eojeols.get(i);
			if (i == eojeols.size() - 1 && !eojeol.isEmpty()) {
				finalise(eojeol.get(eojeol.size() - 1), rules);
			}
			if (i > 0) {
				out.append(' ');
			}
			for (Syl syllable : eojeol) {
				out.append(syllable.text());
			}
		}
		return new Result(text, out.toString(), rules, lexical);
	}

	/** A curated word: the Sino-Korean flag comes from its lexicon tags (none for other words). */
	public static Result of(String ko, String conceptId, LexiconTags tags) {
		LexiconTags.Tag tag = tags == null ? null : tags.tag(conceptId);
		return of(ko, tag != null && "sino".equals(tag.origin()));
	}

	/** The bracket form such as {@code [궁물]}, or null when it equals the spelling. */
	public static String bracket(String ko, boolean sino) {
		return of(ko, sino).bracket();
	}

	// ------------------------------------------------------------------ one word

	private enum Mode {
		/** Inside a word. */
		WITHIN,
		/** The second part of a compound that starts with a vowel and counts as a word of its own. */
		LINKED,
		/** Between two words. */
		SPACE
	}

	/** A syllable taken apart, or a literal character that is not Hangul. */
	private static final class Syl {
		char cho;
		char jung;
		char jong;
		final char literal;
		/** What a bar in the exceptions data says about this syllable, or null. */
		PronunciationExceptions.Join join;

		Syl(char cho, char jung, char jong) {
			this.cho = cho;
			this.jung = jung;
			this.jong = jong;
			this.literal = 0;
		}

		Syl(char literal) {
			this.literal = literal;
		}

		boolean isLiteral() {
			return literal != 0;
		}

		String text() {
			return isLiteral() ? String.valueOf(literal) : String.valueOf(HangulCompose.compose(cho, jung, jong));
		}
	}

	private static List<Syl> parse(String word) {
		List<Syl> out = new ArrayList<>();
		for (int i = 0; i < word.length(); i++) {
			HangulCompose.Syllable s = HangulCompose.decompose(word.charAt(i));
			out.add(s == null ? new Syl(word.charAt(i)) : new Syl(s.cho(), s.jung(), s.jong()));
		}
		return out;
	}

	/** Applies the rules inside one word; returns true when the exceptions data took part. */
	private static boolean inside(List<Syl> syllables, String word, boolean sino, PronunciationExceptions exceptions,
								  Set<Rule> rules) {
		for (Syl s : syllables) {
			// ㅢ after a consonant is said ㅣ (희망 gives 히망)
			if (!s.isLiteral() && s.jung == 'ㅢ' && s.cho != 'ㅇ') {
				s.jung = 'ㅣ';
				rules.add(Rule.UI);
			}
		}
		PronunciationExceptions.Marked marked = exceptions.join(word);
		if (marked != null && marked.at() < syllables.size()) {
			syllables.get(marked.at()).join = marked.join();
		}
		int n = syllables.size();
		// a verb stem in ㄴ or ㅁ plus its ending: 신다 gives 신따
		boolean stemEnding = n >= 2 && STEM_ENDINGS.indexOf(word.charAt(n - 1)) >= 0
				&& exceptions.isStem(word.substring(0, n - 1));
		// 읽고 gives 일꼬: a two-syllable stem in ㄺ before ㄱ
		boolean readsRieul = n == 2 && !syllables.get(0).isLiteral() && syllables.get(0).jong == 'ㄺ'
				&& (word.charAt(1) == '고' || word.charAt(1) == '게');
		for (int i = 0; i + 1 < n; i++) {
			Syl a = syllables.get(i);
			Syl b = syllables.get(i + 1);
			if (a.isLiteral() || b.isLiteral()) {
				finalise(a, rules);
				continue;
			}
			Mode mode = b.join == PronunciationExceptions.Join.LINKED ? Mode.LINKED : Mode.WITHIN;
			if (b.join == PronunciationExceptions.Join.N_INSERTION && b.cho == 'ㅇ' && a.jong != 0) {
				b.cho = 'ㄴ';
				rules.add(Rule.N_INSERTION);
			} else if (b.join == PronunciationExceptions.Join.TENSE) {
				char tense = tense(b.cho);
				if (tense != b.cho) {
					b.cho = tense;
					rules.add(Rule.TENSIFICATION);
				}
			}
			boundary(a, b, mode, sino, stemEnding && i == n - 2, readsRieul, rules);
		}
		return marked != null || stemEnding;
	}

	// ------------------------------------------------------------------ one boundary

	/**
	 * What happens where syllable {@code a} meets {@code b}. The final of {@code a} and the initial of
	 * {@code b} are changed in place.
	 */
	private static void boundary(Syl a, Syl b, Mode mode, boolean sino, boolean stemEnding, boolean readsRieul,
								 Set<Rule> rules) {
		char j = a.jong;
		if (j == 0) {
			return;
		}
		char c = b.cho;
		boolean within = mode != Mode.SPACE;
		if (c == 'ㅇ') {
			if (j == 'ㅇ') {
				return;
			}
			// Between words only ㅏ ㅓ ㅗ ㅜ ㅟ carry a final over, and only the sounds that change (밭 아래
			// [바다래]); a ㄴ ㄹ ㅁ stays where it is
			if (mode == Mode.SPACE && ("ㅏㅓㅗㅜㅟ".indexOf(b.jung) < 0 || !obstruentFinal(a))) {
				finalise(a, rules);
				return;
			}
			link(a, b, mode, rules);
			return;
		}

		// ㅎ in the final: 놓고 [노코], 좋소 [조쏘], 놓는 [논는]
		if (within && (j == 'ㅎ' || j == 'ㄶ' || j == 'ㅀ')) {
			char rest = j == 'ㅎ' ? 0 : j == 'ㄶ' ? 'ㄴ' : 'ㄹ';
			char aspirated = c == 'ㄱ' ? 'ㅋ' : c == 'ㄷ' ? 'ㅌ' : c == 'ㅈ' ? 'ㅊ' : 0;
			if (aspirated != 0) {
				b.cho = aspirated;
				a.jong = rest;
				rules.add(Rule.H);
				return;
			}
			if (c == 'ㅅ') {
				b.cho = 'ㅆ';
				a.jong = rest;
				rules.add(Rule.H);
				return;
			}
			if (j == 'ㅎ' && (c == 'ㄴ' || c == 'ㅁ')) {
				a.jong = 'ㄴ';
				rules.add(Rule.H);
				return;
			}
		}

		// ㅎ in the initial: 축하 [추카], 먹히다 [머키다], 굳히다 [구치다], 옷 한 벌 [오탄벌]
		if (c == 'ㅎ') {
			char[] asp = aspirate(j, mode == Mode.SPACE);
			if (asp != null) {
				a.jong = asp[0];
				b.cho = asp[1];
				if (within && b.jung == 'ㅣ' && (j == 'ㄷ' || j == 'ㅌ')) {
					b.cho = 'ㅊ';
					rules.add(Rule.PALATALISATION);
				}
				rules.add(Rule.H);
				finalise(a, rules);
				return;
			}
		}

		// the final, with the double finals reduced
		boolean tenseAfter = false;
		if (isDouble(j)) {
			if (readsRieul && j == 'ㄺ' && c == 'ㄱ') {
				a.jong = 'ㄹ';
				b.cho = 'ㄲ';
				rules.add(Rule.DOUBLE_FINAL);
				rules.add(Rule.TENSIFICATION);
				return;
			}
			// verb stems tense the ending after ㄵ ㄻ ㄼ ㄾ (앉다 [안따], 넓게 [널께]) but not -기-
			tenseAfter = within && (j == 'ㄵ' || j == 'ㄻ' || j == 'ㄼ' || j == 'ㄾ')
					&& !(b.cho == 'ㄱ' && b.jung == 'ㅣ' && b.jong == 0);
			j = reduce(a);
			rules.add(Rule.DOUBLE_FINAL);
		}
		char neutral = neutral(j);
		if (neutral != j) {
			rules.add(Rule.NEUTRALISATION);
			j = neutral;
		}

		if (within) {
			boolean obstruent = j == 'ㄱ' || j == 'ㄷ' || j == 'ㅂ';
			boolean stem = stemEnding && (j == 'ㄴ' || j == 'ㅁ');
			boolean tense = obstruent && tense(c) != c
					|| tenseAfter && c != 'ㅂ' && tense(c) != c
					|| stem && c != 'ㅂ' && tense(c) != c
					|| sino && j == 'ㄹ' && (c == 'ㄷ' || c == 'ㅅ' || c == 'ㅈ');
			if (tense) {
				b.cho = tense(c);
				c = b.cho;
				rules.add(Rule.TENSIFICATION);
			}
		}

		boolean obstruent = j == 'ㄱ' || j == 'ㄷ' || j == 'ㅂ';
		if (obstruent && (c == 'ㄴ' || c == 'ㅁ' || (within && c == 'ㄹ'))) {
			j = nasal(j);
			if (c == 'ㄹ') {
				b.cho = 'ㄴ';
			}
			rules.add(Rule.NASALISATION);
		} else if (within && c == 'ㄹ' && (j == 'ㅁ' || j == 'ㅇ')) {
			b.cho = 'ㄴ';
			rules.add(Rule.NASALISATION);
		} else if (within && c == 'ㄹ' && j == 'ㄴ') {
			j = 'ㄹ';
			rules.add(Rule.LATERALISATION);
		} else if (within && c == 'ㄴ' && j == 'ㄹ') {
			b.cho = 'ㄹ';
			rules.add(Rule.LATERALISATION);
		}
		a.jong = j;
	}

	/** A final meets a vowel: it moves to the next syllable (연음), with the double finals and ㅎ special. */
	private static void link(Syl a, Syl b, Mode mode, Set<Rule> rules) {
		char j = a.jong;
		if (j == 'ㅎ') {
			a.jong = 0; // 좋아 [조아]
			rules.add(Rule.H);
			return;
		}
		if (j == 'ㄶ' || j == 'ㅀ') {
			b.cho = j == 'ㄶ' ? 'ㄴ' : 'ㄹ'; // 많아 [마나], 싫어 [시러]
			a.jong = 0;
			rules.add(Rule.H);
			rules.add(Rule.LIAISON);
			return;
		}
		if (mode == Mode.WITHIN && b.jung == 'ㅣ' && b.jong == 0 && (j == 'ㄷ' || j == 'ㅌ' || j == 'ㄾ')) {
			b.cho = j == 'ㄷ' ? 'ㅈ' : 'ㅊ'; // 굳이 [구지], 같이 [가치]
			a.jong = j == 'ㄾ' ? 'ㄹ' : 0;
			rules.add(Rule.PALATALISATION);
			return;
		}
		if (mode != Mode.WITHIN) {
			// the second part of a compound or the next word: the final is neutralised first (옷 안 [오단])
			char first = isDouble(j) ? reduce(a) : j;
			char neutral = neutral(first);
			if (isDouble(j)) {
				rules.add(Rule.DOUBLE_FINAL);
			}
			if (neutral != first) {
				rules.add(Rule.NEUTRALISATION);
			}
			b.cho = neutral;
			a.jong = 0;
			rules.add(Rule.LIAISON);
			return;
		}
		char[] parts = splitDouble(j);
		if (parts != null) {
			a.jong = parts[0];
			b.cho = parts[1];
			rules.add(Rule.DOUBLE_FINAL);
		} else {
			b.cho = j;
			a.jong = 0;
		}
		rules.add(Rule.LIAISON);
	}

	/** The final sound at the end of a word or before something that is not Hangul. */
	private static void finalise(Syl a, Set<Rule> rules) {
		if (a.isLiteral() || a.jong == 0) {
			return;
		}
		char j = a.jong;
		if (isDouble(j)) {
			j = reduce(a);
			rules.add(Rule.DOUBLE_FINAL);
		}
		char neutral = neutral(j);
		if (neutral != j) {
			rules.add(Rule.NEUTRALISATION);
		}
		a.jong = neutral;
	}

	// ------------------------------------------------------------------ sounds

	/** True when the final is said as ㄱ, ㄷ or ㅂ (ㅎ is silent before a vowel and counts as none). */
	private static boolean obstruentFinal(Syl a) {
		char j = a.jong == 'ㅎ' ? 0 : isDouble(a.jong) ? reduce(a) : a.jong;
		char n = neutral(j);
		return n == 'ㄱ' || n == 'ㄷ' || n == 'ㅂ';
	}

	private static boolean isDouble(char j) {
		return HangulCompose.splitFinal(j) != null;
	}

	/** Which consonant of a double final is said before a consonant: ㄺ gives ㄱ, ㄼ gives ㄹ (밟 gives ㅂ). */
	private static char reduce(Syl a) {
		char j = a.jong;
		if (j == 'ㄼ' && a.cho == 'ㅂ' && a.jung == 'ㅏ') {
			return 'ㅂ'; // 밟다 [밥따], the one ㄼ stem that keeps the second consonant
		}
		return switch (j) {
			case 'ㄳ', 'ㄺ' -> 'ㄱ';
			case 'ㄵ', 'ㄶ' -> 'ㄴ';
			case 'ㄻ' -> 'ㅁ';
			case 'ㄼ', 'ㄽ', 'ㄾ', 'ㅀ' -> 'ㄹ';
			case 'ㄿ', 'ㅄ' -> 'ㅂ';
			default -> j;
		};
	}

	/** The two sounds of a double final when a vowel follows: the first stays, the second moves (ㅅ is tensed). */
	private static char[] splitDouble(char j) {
		String parts = HangulCompose.splitFinal(j);
		if (parts == null) {
			return null;
		}
		char moved = parts.charAt(1);
		return new char[] {parts.charAt(0), moved == 'ㅅ' ? 'ㅆ' : moved};
	}

	/** The seven sounds a final can have: ㅋ ㄲ give ㄱ, ㅅ ㅆ ㅈ ㅊ ㅌ ㅎ give ㄷ, ㅍ gives ㅂ. */
	private static char neutral(char j) {
		return switch (j) {
			case 'ㄲ', 'ㅋ' -> 'ㄱ';
			case 'ㅅ', 'ㅆ', 'ㅈ', 'ㅊ', 'ㅌ', 'ㅎ' -> 'ㄷ';
			case 'ㅍ' -> 'ㅂ';
			default -> j;
		};
	}

	private static char nasal(char j) {
		return j == 'ㄱ' ? 'ㅇ' : j == 'ㄷ' ? 'ㄴ' : 'ㅁ';
	}

	private static char tense(char cho) {
		for (int i = 0; i < TENSE_FROM.length; i++) {
			if (TENSE_FROM[i] == cho) {
				return TENSE_TO[i];
			}
		}
		return cho;
	}

	/** The rest of the final and the new initial when ㅎ follows {@code j}; null when nothing is aspirated. */
	private static char[] aspirate(char j, boolean space) {
		return switch (j) {
			case 'ㄱ', 'ㄲ', 'ㅋ', 'ㄳ' -> new char[] {0, 'ㅋ'};
			case 'ㄺ' -> new char[] {'ㄹ', 'ㅋ'};
			case 'ㄷ', 'ㅅ', 'ㅆ', 'ㅌ', 'ㅊ' -> new char[] {0, 'ㅌ'};
			case 'ㅈ' -> new char[] {0, space ? 'ㅌ' : 'ㅊ'};
			case 'ㄵ' -> new char[] {'ㄴ', 'ㅊ'};
			case 'ㅂ', 'ㅍ', 'ㅄ' -> new char[] {0, 'ㅍ'};
			case 'ㄼ', 'ㄿ' -> new char[] {'ㄹ', 'ㅍ'};
			case 'ㄾ' -> new char[] {'ㄹ', 'ㅌ'};
			default -> null;
		};
	}
}
