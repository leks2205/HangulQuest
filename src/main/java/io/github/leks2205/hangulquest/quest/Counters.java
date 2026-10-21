package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the counter for a counted noun from {@code data/counters.json}: the registry id decides (families
 * such as flowers, books, animals), then the Korean word the sentence uses (종이, 꽃). Anything else takes
 * 개. A broken or missing table falls back to 개, so quest text never fails because of it.
 */
public final class Counters {
	public static final String RESOURCE = "/assets/hangulquest/data/counters.json";
	public static final String DEFAULT = "개";

	/** Counters for a whole building or a set of armour: wrong for the single block or piece a goal counts. */
	private static final Set<String> NOT_FOR_WORDS = Set.of("채", "벌");

	private record Rule(String family, String counter) {
	}

	private final Map<String, List<String>> families;
	private final List<Rule> rules;
	private final Map<String, String> words;
	private final Map<String, String> grammar;

	private Counters(Map<String, List<String>> families, List<Rule> rules, Map<String, String> words,
					 Map<String, String> grammar) {
		this.families = families;
		this.rules = rules;
		this.words = words;
		this.grammar = grammar;
	}

	private static volatile Counters shared;

	public static Counters shared() {
		Counters table = shared;
		if (table == null) {
			table = load();
			shared = table;
		}
		return table;
	}

	public static Counters load() {
		try (InputStream stream = Counters.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				return empty();
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (Exception broken) {
			return empty();
		}
	}

	public static Counters empty() {
		return new Counters(Map.of(), List.of(), Map.of(), Map.of());
	}

	public static Counters parse(JsonObject root) {
		Map<String, List<String>> families = new HashMap<>();
		if (root.has("families")) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("families").entrySet()) {
				List<String> patterns = new ArrayList<>();
				entry.getValue().getAsJsonArray().forEach(pattern -> patterns.add(pattern.getAsString()));
				families.put(entry.getKey(), List.copyOf(patterns));
			}
		}
		List<Rule> rules = new ArrayList<>();
		if (root.has("rules")) {
			for (JsonElement element : root.getAsJsonArray("rules")) {
				JsonObject object = element.getAsJsonObject();
				rules.add(new Rule(object.has("family") ? object.get("family").getAsString() : null,
						object.get("counter").isJsonNull() ? null : object.get("counter").getAsString()));
			}
		}
		Map<String, String> words = new HashMap<>();
		if (root.has("wordCounters")) {
			root.getAsJsonObject("wordCounters").entrySet()
					.forEach(entry -> words.put(entry.getKey(), entry.getValue().getAsString()));
		}
		Map<String, String> grammar = new HashMap<>();
		if (root.has("counters")) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("counters").entrySet()) {
				JsonObject counter = entry.getValue().getAsJsonObject();
				if (counter.has("counter") && counter.has("grammar")) {
					grammar.put(counter.get("counter").getAsString(), counter.get("grammar").getAsString());
				}
			}
		}
		return new Counters(families, List.copyOf(rules), words, grammar);
	}

	/**
	 * The counter of a noun. {@code id} is the registry id of what is counted (may be null), {@code noun}
	 * the Korean word the sentence says. A rule that holds back a counter (illagers) gives 개 here, since
	 * the goal needs its count in the sentence.
	 */
	public String counter(String id, String noun) {
		String byId = id == null ? null : byId(id);
		if (byId != null && !DEFAULT.equals(byId)) {
			return byId;
		}
		String byWord = noun == null ? null : words.get(noun);
		if (byWord != null && !NOT_FOR_WORDS.contains(byWord)) {
			return byWord;
		}
		return byId == null ? DEFAULT : byId;
	}

	private String byId(String id) {
		String path = id.startsWith("#") ? id : id.substring(id.indexOf(':') + 1);
		for (Rule rule : rules) {
			if (rule.family() == null || inFamily(rule.family(), path)) {
				return rule.counter() == null ? DEFAULT : rule.counter();
			}
		}
		return DEFAULT;
	}

	private boolean inFamily(String family, String path) {
		for (String pattern : families.getOrDefault(family, List.of())) {
			if (VerbTable.matches(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	/** The grammar point of a counter (L1.counter_gae, L2.counter_more), or null when the table has none. */
	public String grammar(String counter) {
		return grammar.get(counter);
	}
}
