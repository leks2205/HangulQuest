package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage.Hit;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage.State;
import io.github.leks2205.hangulquest.util.TextClean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuts a paragraph into eojeol and looks each one up: a conjugated verb or adjective by its surface form
 * (with a breakdown into stem, ending and construction), a noun or function word as a stem followed by a
 * particle. An eojeol that matches nothing is {@link State#UNRESOLVED}: it counts as unknown and has no
 * gloss, so a story that ships with one is a mistake the tests catch. The cutting follows the dialogue
 * coverage ({@link DialogueCoverage}); the verb forms come from {@link StoryVocabulary}.
 */
public final class StoryText {
	/** Particles, copulas and endings that may follow a noun or function word. Closed on purpose: a new one is added when a story needs it. */
	public static final Set<String> TAILS = Set.of(
			"", "이", "가", "은", "는", "을", "를", "도", "만", "의", "에", "에서", "에게", "에게는", "와", "과",
			"로", "으로", "예요", "이에요", "야", "이야", "에게도", "에도", "에는", "에서는", "에서도", "하고", "와는", "과는", "로는",
			"으로는", "부터", "까지", "처럼", "보다", "요", "이요", "입니다", "어요", "아요", "해요", "해", "세요", "였어요", "이었어요", "에게서");

	/** The construction a particle teaches; 에 and (으)로 each teach two, so they are not named. */
	private static final Map<String, String> PARTICLE_GRAMMAR = Map.ofEntries(
			Map.entry("이", "L1.subj_i"), Map.entry("가", "L1.subj_i"),
			Map.entry("을", "L1.obj_eul"), Map.entry("를", "L1.obj_eul"),
			Map.entry("은", "L2.eun_neun_topic"), Map.entry("는", "L2.eun_neun_topic"),
			Map.entry("에서", "L2.eseo_activity"), Map.entry("에게", "L2.ege"), Map.entry("에게서", "L2.ege"),
			Map.entry("와", "L2.hago_wa_gwa"), Map.entry("과", "L2.hago_wa_gwa"), Map.entry("하고", "L2.hago_wa_gwa"),
			Map.entry("예요", "L1.copula_ieyo"), Map.entry("이에요", "L1.copula_ieyo"),
			Map.entry("였어요", "L3.past"), Map.entry("이었어요", "L3.past"));

	/**
	 * How a form is built: the word it comes from ({@code base}, a dictionary form for verbs), its {@code stem},
	 * the ending or particle and the grammar.json construction that teaches it (empty when none is named).
	 */
	public record Breakdown(String base, String stem, String ending, String grammar) {
	}

	/** One eojeol. {@code word} is false for punctuation alone, which counts for nothing. */
	public record Token(String text, boolean word, State state, String id, String gloss, Breakdown breakdown) {
		public boolean known() {
			return state == State.KNOWN;
		}
	}

	private StoryText() {
	}

	/** The longest name of several words that is looked for (엔더 드래곤 is two, 약간 녹슨 깎인 구리 is four). */
	private static final int LONGEST_NAME = 4;

	public static List<Token> analyze(String paragraph, StoryVocabulary vocabulary) {
		List<Token> tokens = new ArrayList<>();
		String[] eojeols = paragraph.strip().split("\\s+");
		for (int i = 0; i < eojeols.length; i++) {
			if (eojeols[i].isEmpty()) {
				continue;
			}
			int span = 0;
			for (int n = Math.min(LONGEST_NAME, eojeols.length - i); n >= 2 && span == 0; n--) {
				String joined = String.join(" ", java.util.Arrays.copyOfRange(eojeols, i, i + n));
				Token name = name(joined, vocabulary);
				if (name != null) {
					tokens.add(name);
					span = n;
				}
			}
			if (span > 0) {
				i += span - 1;
			} else {
				tokens.add(token(eojeols[i], vocabulary));
			}
		}
		boundNouns(tokens);
		return tokens;
	}

	/** What a bound noun makes of the adnominal in front of it: -(으)ㄹ 수, -(으)ㄴ 후에 and -(으)ㄹ 거예요 are L3 constructions, -(으)ㄹ 때 has none. */
	private static final Map<String, String> BOUND_NOUNS = Map.of("수", "L3.su_itda", "후에", "L3.eun_hue", "때", "", "거", "L3.future_geoyeyo");

	/** Before 수, 후에, 거 or 때 the -(으)ㄴ / -(으)ㄹ form is part of that phrase, not the level-5 adnominal. */
	private static void boundNouns(List<Token> tokens) {
		for (int i = 0; i + 1 < tokens.size(); i++) {
			Token verb = tokens.get(i);
			if (verb.breakdown() == null || !(verb.breakdown().grammar().equals("L5.adn_past_eun")
					|| verb.breakdown().grammar().equals("L5.adn_future_eul"))) {
				continue;
			}
			String next = strip(tokens.get(i + 1).text());
			for (Map.Entry<String, String> bound : BOUND_NOUNS.entrySet()) {
				String key = bound.getKey();
				if (next.equals(key) || (next.startsWith(key) && TAILS.contains(next.substring(key.length())))) {
					Breakdown old = verb.breakdown();
					tokens.set(i, new Token(verb.text(), verb.word(), verb.state(), verb.id(), verb.gloss(),
							new Breakdown(old.base(), old.stem(), old.ending(), bound.getValue())));
					break;
				}
			}
		}
	}

	/** Several eojeol read as one name with a particle after it (엔더 드래곤을), or null. */
	private static Token name(String joined, StoryVocabulary vocabulary) {
		String core = strip(joined);
		for (int end = core.length(); end >= 1; end--) {
			String tail = core.substring(end);
			if (!TAILS.contains(tail)) {
				continue;
			}
			String stem = core.substring(0, end);
			if (stem.indexOf(' ') < 0) {
				break;
			}
			Hit hit = vocabulary.findPhrase(stem);
			if (hit != null) {
				return new Token(joined, true, hit.known() ? State.KNOWN : State.UNKNOWN, hit.id(), hit.gloss(),
						nounBreakdown(stem, tail, vocabulary));
			}
		}
		return null;
	}

	private static Token token(String eojeol, StoryVocabulary vocabulary) {
		String core = strip(eojeol);
		if (core.isEmpty() || !TextClean.hasHangul(core)) {
			return new Token(eojeol, false, State.KNOWN, null, null, null);
		}
		StoryVocabulary.VerbMatch verb = vocabulary.verb(core);
		if (verb != null) {
			Hit hit = verb.hit();
			return new Token(eojeol, true, hit.known() ? State.KNOWN : State.UNKNOWN, hit.id(), hit.gloss(),
					new Breakdown(verb.dictionary(), verb.stem(), verb.ending().label(), verb.grammar()));
		}
		for (int end = core.length(); end >= 1; end--) {
			String tail = core.substring(end);
			if (!TAILS.contains(tail)) {
				continue;
			}
			String stem = core.substring(0, end);
			Hit hit = vocabulary.find(stem, tail);
			if (hit != null) {
				return new Token(eojeol, true, hit.known() ? State.KNOWN : State.UNKNOWN, hit.id(), hit.gloss(),
						nounBreakdown(stem, tail, vocabulary));
			}
		}
		return new Token(eojeol, true, State.UNRESOLVED, null, null, null);
	}

	private static Breakdown nounBreakdown(String stem, String tail, StoryVocabulary vocabulary) {
		String own = vocabulary.grammarOf(stem, tail);
		if (tail.isEmpty()) {
			return own == null ? null : new Breakdown(stem, stem, "", own);
		}
		return new Breakdown(stem, stem, tail, own != null ? own : PARTICLE_GRAMMAR.getOrDefault(tail, ""));
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
}
