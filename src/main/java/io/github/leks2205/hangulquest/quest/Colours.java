package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colours as adjectives (level 5, -(으)ㄴ + noun): the registry says "빨간색 침대", the upper level says "빨간 침대".
 * Only the colours that are an adjective have the form (빨갛다, 파랗다, 노랗다, 하얗다, 검다); 보라색 and 주황색 stay nouns.
 * A step that teaches the colour word, or a name that holds it (하얀색 색유리), keeps the noun, since the clue must say
 * what the step teaches.
 */
final class Colours {
	/** The level of -(으)ㄴ before a noun. */
	static final int LEVEL = 5;

	/** The colour noun and the adjective it comes from. */
	private static final Map<String, String> ADJECTIVES = new LinkedHashMap<>();

	static {
		ADJECTIVES.put("빨간색", "빨갛다");
		ADJECTIVES.put("파란색", "파랗다");
		ADJECTIVES.put("노란색", "노랗다");
		ADJECTIVES.put("하얀색", "하얗다");
		ADJECTIVES.put("검은색", "검다");
	}

	/** A colour noun at the start of a noun phrase, with a noun after it. */
	private static final Pattern COLOUR = Pattern.compile("(?<![가-힣])(빨간색|파란색|노란색|하얀색|검은색) (?=[가-힣])");

	private Colours() {
	}

	/** The sentence with its colour nouns spoken as adjectives from level 5; the colour the step teaches stays. */
	static String modifiers(String sentence, Word taught, int level) {
		if (level < LEVEL || sentence == null) {
			return sentence;
		}
		Matcher match = COLOUR.matcher(sentence);
		StringBuilder out = new StringBuilder();
		while (match.find()) {
			String noun = match.group(1);
			boolean kept = taught != null && taught.ko != null && (noun.equals(taught.ko) || taught.ko.contains(noun + " "));
			match.appendReplacement(out, Matcher.quoteReplacement(kept ? match.group() : adjective(noun) + " "));
		}
		match.appendTail(out);
		return out.toString();
	}

	/** Whether {@link #modifiers} changes the sentence, so the grammar point of the adjective belongs to it. */
	static boolean applies(String sentence, Word taught, int level) {
		return !modifiers(sentence, taught, level).equals(sentence);
	}

	private static String adjective(String noun) {
		return Conjugator.of(ADJECTIVES.get(noun)).eun();
	}
}
