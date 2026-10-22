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
public final class Colours {
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

	/** The level from which a sentence about a taught general word keeps the colour of a coloured block. */
	public static final int SAID_FROM = 3;

	/** The level the sentence under construction is read at; unset outside {@link #at}, where the colour stays. */
	private static final ThreadLocal<Integer> READ_AT = new ThreadLocal<>();

	private static final java.util.Set<String> DYES = java.util.Set.of("white", "orange", "magenta", "light_blue",
			"yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/** What the vanilla Korean names write with the colour in front (하얀색 침대, 회색 초, 분홍색 양털). */
	private static final java.util.Set<String> COLOURED = java.util.Set.of("banner", "bed", "candle", "candle_cake",
			"carpet", "concrete", "concrete_powder", "concrete_slab", "concrete_stairs", "glazed_terracotta",
			"shulker_box", "stained_glass", "stained_glass_pane", "terracotta", "wool", "wool_slab", "wool_stairs",
			"tulip", "orchid", "bundle", "harness", "dye");

	private Colours() {
	}

	/** The Korean colour noun of each dye, as the vanilla names write it. */
	private static final Map<String, String> NOUNS = Map.ofEntries(Map.entry("white", "하얀색"),
			Map.entry("orange", "주황색"), Map.entry("magenta", "자홍색"), Map.entry("light_blue", "하늘색"),
			Map.entry("yellow", "노란색"), Map.entry("lime", "연두색"), Map.entry("pink", "분홍색"),
			Map.entry("gray", "회색"), Map.entry("light_gray", "회백색"), Map.entry("cyan", "청록색"),
			Map.entry("purple", "보라색"), Map.entry("blue", "파란색"), Map.entry("brown", "갈색"),
			Map.entry("green", "초록색"), Map.entry("red", "빨간색"), Map.entry("black", "검은색"));

	/** The dye an id opens with (red_bed: red), or null when it is not a coloured block or item. */
	private static String dye(String id) {
		if (id == null) {
			return null;
		}
		String path = id.substring(id.indexOf(':') + 1);
		int cut = path.indexOf('_');
		while (cut > 0) {
			if (DYES.contains(path.substring(0, cut)) && COLOURED.contains(path.substring(cut + 1))) {
				return path.substring(0, cut);
			}
			cut = path.indexOf('_', cut + 1);
		}
		return null;
	}

	/** Whether the id is a block or item whose registry name opens with its colour (red_bed, lime_wool). */
	public static boolean isColoured(String id) {
		return dye(id) != null;
	}

	/** A dye: its colour is what it is, so every sentence names it, whatever the level. */
	public static boolean isDye(String id) {
		return id != null && id.endsWith("_dye") && isColoured(id);
	}

	/**
	 * The name with the colour in front when the name leaves it out: always for a dye (염료 is the red dye here),
	 * for the other coloured blocks and items wherever a sentence says the colour ({@link #kept}).
	 */
	public static String withColour(String id, String name) {
		String dye = dye(id);
		if (dye == null || name == null || name.isBlank() || !isDye(id) && !kept()) {
			return name;
		}
		String noun = NOUNS.get(dye);
		return name.contains(noun) ? name : noun + " " + name;
	}

	/** Whether the sentence names the colour of this coloured id, as the noun (빨간색) or the adjective (빨간). */
	public static boolean saysColourOf(String sentence, String id) {
		String dye = dye(id);
		if (dye == null || sentence == null) {
			return false;
		}
		String noun = NOUNS.get(dye);
		if (sentence.contains(noun)) {
			return true;
		}
		return ADJECTIVES.containsKey(noun) && sentence.contains(adjective(noun) + " ");
	}

	/** Runs the builder of a sentence read at {@code level}: a taught general word keeps its colour from level 3. */
	public static <T> T at(int level, java.util.function.Supplier<T> sentence) {
		Integer before = READ_AT.get();
		READ_AT.set(level);
		try {
			return sentence.get();
		} finally {
			if (before == null) {
				READ_AT.remove();
			} else {
				READ_AT.set(before);
			}
		}
	}

	/** Same, but a scope that is already open (a hint built inside its clue's level) stays in force. */
	public static <T> T ifUnset(int level, java.util.function.Supplier<T> sentence) {
		return READ_AT.get() != null ? sentence.get() : at(level, sentence);
	}

	/** Whether a taught general word keeps the colour of the block it stands for in the sentence under construction. */
	public static boolean kept() {
		Integer level = READ_AT.get();
		return level == null || level >= SAID_FROM;
	}

	private static final Pattern LEADING = Pattern.compile("^([가-힣]+색) (?=[가-힣])");

	/** The colour noun a registry name opens with ("파란색" in "파란색 침대", "회백색" in "회백색 양털"), or null. */
	public static String leadingColour(String registryName) {
		if (registryName == null) {
			return null;
		}
		Matcher match = LEADING.matcher(registryName);
		return match.find() ? match.group(1) : null;
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
