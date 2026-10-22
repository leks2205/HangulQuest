package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The lexical part of {@link Pronunciation}: what no rule can know from the spelling alone. It lives in
 * {@code pronunciation-exceptions.json} and stays small.
 *
 * <ul>
 * <li>{@code overrides}: the whole word, spelled out ({@code "pron"} and the rule ids that explain it);</li>
 * <li>{@code nInsertion}: compounds with a bar before the second part, which gets an inserted ㄴ
 *     ({@code 솜|이불} gives 솜니불);</li>
 * <li>{@code linked}: a bar before a vowel-initial second part of a compound; the first part's final is
 *     neutralised, then carried over ({@code 맛|없다} gives 마덥따);</li>
 * <li>{@code tenseJoin}: a bar before a second part that is tensed ({@code 길|가} gives 길까);</li>
 * <li>{@code stems}: verb stems ending in ㄴ or ㅁ, whose ending is tensed (신다 gives 신따);</li>
 * <li>{@code sinoWords}: Sino-Korean words inside a phrase whose own origin the phrase tag does not show
 *     (철사 in 철사 덫 갈고리), so the ㄹ rule tenses ㅅ ㄷ ㅈ in them (철사 gives 철싸).</li>
 * <li>{@code crossWord}: how the sound rules treat two words of a phrase, see {@link CrossWord}.</li>
 * </ul>
 */
public final class PronunciationExceptions {
	public static final String RESOURCE = "/assets/hangulquest/data/pronunciation-exceptions.json";
	private static final PronunciationExceptions EMPTY = new PronunciationExceptions(Map.of(), Map.of(), Set.of(),
			Set.of(), CrossWord.MIXED);

	/**
	 * What happens where one word of a phrase meets the next: carrying a final over to a vowel (liaison),
	 * ㅎ and its aspiration, and the nasalisation of ㄱ ㄷ ㅂ before ㄴ ㅁ. Whether names of items count as one
	 * unit for these is a style choice that the native review decides, so the data file has the switch.
	 */
	public enum CrossWord {
		/**
		 * As the standard describes it for two words: nasalisation and the ㅎ initial are applied, a final is
		 * carried over only before ㅏ ㅓ ㅗ ㅜ ㅟ (밭 아래 [바다래]) and a ㅎ final is not touched. The default.
		 */
		MIXED,
		/** All three as inside a word: every final is carried over, a ㅎ final aspirates (좋은 집 [조은 집]). */
		ALL,
		/** None of them: a word keeps its own sounds and only its final is neutralised (밭 아래 [받 아래]). */
		NONE;

		static CrossWord byId(String id) {
			for (CrossWord mode : values()) {
				if (mode.name().equalsIgnoreCase(id)) {
					return mode;
				}
			}
			throw new IllegalArgumentException("crossWord must be mixed, all or none, not " + id);
		}
	}

	/** A whole word spelled out; the rules are ids of {@link Pronunciation.Rule}. */
	public record Override(String pron, List<Pronunciation.Rule> rules) {
	}

	/** Marks a compound boundary: the part before the bar and what happens to the part after it. */
	enum Join {
		N_INSERTION, LINKED, TENSE
	}

	private static volatile PronunciationExceptions shared;

	private final Map<String, Override> overrides;
	/** Word without bars, mapped to the bar position and the kind of join. */
	private final Map<String, Marked> joins;
	private final Set<String> stems;
	private final Set<String> sinoWords;
	private final CrossWord crossWord;

	record Marked(int at, Join join) {
	}

	private PronunciationExceptions(Map<String, Override> overrides, Map<String, Marked> joins, Set<String> stems,
									Set<String> sinoWords, CrossWord crossWord) {
		this.overrides = overrides;
		this.joins = joins;
		this.stems = stems;
		this.sinoWords = sinoWords;
		this.crossWord = crossWord;
	}

	public static PronunciationExceptions shared() {
		PronunciationExceptions loaded = shared;
		if (loaded == null) {
			loaded = load();
			shared = loaded;
		}
		return loaded;
	}

	public static PronunciationExceptions empty() {
		return EMPTY;
	}

	/** From the classpath; a missing or broken file gives no exceptions rather than a crash. */
	public static PronunciationExceptions load() {
		try (InputStream stream = PronunciationExceptions.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? EMPTY : parse(stream);
		} catch (IOException | RuntimeException broken) {
			return EMPTY;
		}
	}

	static PronunciationExceptions parse(InputStream stream) throws IOException {
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			Map<String, Override> overrides = new LinkedHashMap<>();
			if (root.has("overrides")) {
				JsonObject all = root.getAsJsonObject("overrides");
				for (String word : all.keySet()) {
					JsonObject entry = all.getAsJsonObject(word);
					List<Pronunciation.Rule> rules = new ArrayList<>();
					for (JsonElement id : entry.getAsJsonArray("rules")) {
						Pronunciation.Rule rule = Pronunciation.Rule.byId(id.getAsString());
						if (rule == null) {
							throw new IllegalArgumentException("unknown rule " + id + " for " + word);
						}
						rules.add(rule);
					}
					overrides.put(word, new Override(entry.get("pron").getAsString(), List.copyOf(rules)));
				}
			}
			Map<String, Marked> joins = new LinkedHashMap<>();
			marked(root.getAsJsonArray("nInsertion"), Join.N_INSERTION, joins);
			marked(root.getAsJsonArray("linked"), Join.LINKED, joins);
			marked(root.getAsJsonArray("tenseJoin"), Join.TENSE, joins);
			Set<String> stems = new java.util.LinkedHashSet<>();
			if (root.has("stems")) {
				for (JsonElement stem : root.getAsJsonArray("stems")) {
					stems.add(stem.getAsString());
				}
			}
			Set<String> sinoWords = new java.util.LinkedHashSet<>();
			if (root.has("sinoWords")) {
				for (JsonElement word : root.getAsJsonArray("sinoWords")) {
					sinoWords.add(word.getAsString());
				}
			}
			CrossWord crossWord = root.has("crossWord") ? CrossWord.byId(root.get("crossWord").getAsString())
					: CrossWord.MIXED;
			return new PronunciationExceptions(Map.copyOf(overrides), Map.copyOf(joins), Set.copyOf(stems),
					Set.copyOf(sinoWords), crossWord);
		}
	}

	private static void marked(JsonArray list, Join join, Map<String, Marked> into) {
		if (list == null) {
			return;
		}
		for (JsonElement element : list) {
			String text = element.getAsString();
			int bar = text.indexOf('|');
			if (bar <= 0 || bar == text.length() - 1) {
				throw new IllegalArgumentException("a join needs a bar inside the word: " + text);
			}
			into.put(text.replace("|", ""), new Marked(bar, join));
		}
	}

	public CrossWord crossWord() {
		return crossWord;
	}

	/** The same exceptions with another {@code crossWord}, to compare the modes on the real data. */
	public PronunciationExceptions withCrossWord(CrossWord mode) {
		return new PronunciationExceptions(overrides, joins, stems, sinoWords, mode);
	}

	/** The spelled-out pronunciation of an eojeol, or null. */
	public Override override(String eojeol) {
		return overrides.get(eojeol);
	}

	Marked join(String eojeol) {
		return joins.get(eojeol);
	}

	boolean isStem(String stem) {
		return stems.contains(stem);
	}

	/** True for a word listed as Sino-Korean, whatever the tag of the phrase around it says. */
	boolean isSino(String eojeol) {
		return sinoWords.contains(eojeol);
	}

	/**
	 * Same, knowing whether the word stands in a longer phrase. An entry written {@code 갈색*} counts only inside
	 * a phrase (갈색 양탄자), so the word on its own stays as the tags of its concept say.
	 */
	boolean isSino(String eojeol, boolean inPhrase) {
		return sinoWords.contains(eojeol) || inPhrase && sinoWords.contains(eojeol + "*");
	}

	public int size() {
		return overrides.size() + joins.size() + stems.size() + sinoWords.size();
	}
}
