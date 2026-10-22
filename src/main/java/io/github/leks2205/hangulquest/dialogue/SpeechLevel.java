package io.github.leks2205.hangulquest.dialogue;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The speech levels dialogue uses. {@link #HAEYO} is the default, {@link #BANMAL} is for children and
 * friends, {@link #HAPSYO} for elders, strangers and formal places. {@link #PLAIN} (-ㄴ/는다) is only
 * ever recognised, never written in a dialogue.
 */
public enum SpeechLevel {
	HAEYO("haeyo"),
	BANMAL("banmal"),
	HAPSYO("hapsyo"),
	PLAIN("plain");

	private static final Pattern HAPSYO_END = Pattern.compile("(?:습니다|ㅂ니다|입니다|니다|습니까|니까|십시오)$");
	private static final Pattern HAEYO_END = Pattern.compile("요$");
	private static final Pattern PLAIN_END = Pattern.compile("[^니]다$|[는ㄴ]다$");
	private static final Pattern BANMAL_END = Pattern.compile("(?:해|어|아|야|지|네|래|니|냐|자|라)$");
	private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s*");

	private final String id;

	SpeechLevel(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** The level named in dialogue data; null for an unknown name and for {@code plain}, which is not allowed there. */
	public static SpeechLevel fromId(String id) {
		if (id == null) {
			return null;
		}
		String wanted = id.trim().toLowerCase(Locale.ROOT);
		for (SpeechLevel level : values()) {
			if (level != PLAIN && level.id.equals(wanted)) {
				return level;
			}
		}
		return null;
	}

	/**
	 * The level a sentence ending shows, or null when the ending says nothing (a greeting, a bare noun).
	 * Same rule as {@code _ending_class} in tools/lint_korean.py, plus the formal command -십시오.
	 */
	public static SpeechLevel ofSentence(String sentence) {
		String s = trimEnd(sentence.strip());
		if (s.isEmpty()) {
			return null;
		}
		if (HAPSYO_END.matcher(s).find()) {
			return HAPSYO;
		}
		if (HAEYO_END.matcher(s).find()) {
			return HAEYO;
		}
		if (PLAIN_END.matcher(s).find()) {
			return PLAIN;
		}
		if (s.length() > 3 && BANMAL_END.matcher(s).find()) {
			return BANMAL;
		}
		return null;
	}

	/** The levels of all finished sentences of a line (the linter only counts sentences that end in . ! ?). */
	public static Set<SpeechLevel> ofLine(String line) {
		Set<SpeechLevel> levels = EnumSet.noneOf(SpeechLevel.class);
		for (String sentence : SENTENCE_BREAK.split(line)) {
			if (sentence.endsWith(".") || sentence.endsWith("!") || sentence.endsWith("?")) {
				SpeechLevel level = ofSentence(sentence);
				if (level != null) {
					levels.add(level);
				}
			}
		}
		return levels;
	}

	private static String trimEnd(String text) {
		int end = text.length();
		while (end > 0 && ".!?…~ ".indexOf(text.charAt(end - 1)) >= 0) {
			end--;
		}
		return text.substring(0, end);
	}
}
