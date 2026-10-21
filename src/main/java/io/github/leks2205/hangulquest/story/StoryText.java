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
			"으로는", "부터", "까지", "처럼", "보다", "요", "이요", "입니다", "어요", "아요", "해요", "해", "세요");

	/** The construction a particle teaches; 에 and (으)로 each teach two, so they are not named. */
	private static final Map<String, String> PARTICLE_GRAMMAR = Map.ofEntries(
			Map.entry("이", "L1.subj_i"), Map.entry("가", "L1.subj_i"),
			Map.entry("을", "L1.obj_eul"), Map.entry("를", "L1.obj_eul"),
			Map.entry("은", "L2.eun_neun_topic"), Map.entry("는", "L2.eun_neun_topic"),
			Map.entry("에서", "L2.eseo_activity"), Map.entry("에게", "L2.ege"),
			Map.entry("와", "L2.hago_wa_gwa"), Map.entry("과", "L2.hago_wa_gwa"), Map.entry("하고", "L2.hago_wa_gwa"),
			Map.entry("예요", "L1.copula_ieyo"), Map.entry("이에요", "L1.copula_ieyo"));

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

	public static List<Token> analyze(String paragraph, StoryVocabulary vocabulary) {
		List<Token> tokens = new ArrayList<>();
		for (String eojeol : paragraph.strip().split("\\s+")) {
			if (!eojeol.isEmpty()) {
				tokens.add(token(eojeol, vocabulary));
			}
		}
		return tokens;
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
