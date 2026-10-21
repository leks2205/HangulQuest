package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
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

/**
 * Picks the Korean verb that fits an object, from {@code data/verbs.json}: families of registry ids
 * (logs, flowers, carpets, ...) and ordered per-goal rules. A broken or missing table falls back to
 * the caller's default verb, so a bad resource pack can never take quest text down.
 */
public final class VerbTable {
	public static final String RESOURCE = "/assets/hangulquest/data/verbs.json";

	private record Rule(String family, int minCount, int maxCount, Verb verb) {
	}

	private final Map<String, List<String>> families;
	private final Map<String, List<Rule>> rules;

	private VerbTable(Map<String, List<String>> families, Map<String, List<Rule>> rules) {
		this.families = families;
		this.rules = rules;
	}

	private static volatile VerbTable shared;

	public static VerbTable shared() {
		VerbTable table = shared;
		if (table == null) {
			table = load();
			shared = table;
		}
		return table;
	}

	public static VerbTable load() {
		try (InputStream stream = VerbTable.class.getResourceAsStream(RESOURCE)) {
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

	public static VerbTable empty() {
		return new VerbTable(Map.of(), Map.of());
	}

	public static VerbTable parse(JsonObject root) {
		Map<String, List<String>> families = new HashMap<>();
		JsonObject familyObject = root.getAsJsonObject("families");
		if (familyObject != null) {
			for (Map.Entry<String, JsonElement> entry : familyObject.entrySet()) {
				List<String> patterns = new ArrayList<>();
				for (JsonElement pattern : entry.getValue().getAsJsonArray()) {
					patterns.add(pattern.getAsString());
				}
				families.put(entry.getKey(), List.copyOf(patterns));
			}
		}
		Map<String, List<Rule>> rules = new HashMap<>();
		JsonObject ruleObject = root.getAsJsonObject("rules");
		if (ruleObject != null) {
			for (Map.Entry<String, JsonElement> entry : ruleObject.entrySet()) {
				List<Rule> list = new ArrayList<>();
				JsonArray array = entry.getValue().getAsJsonArray();
				for (JsonElement element : array) {
					JsonObject object = element.getAsJsonObject();
					Verb verb = Verb.byId(object.get("verb").getAsString(), null);
					if (verb == null) {
						throw new IllegalArgumentException("unknown verb in verbs.json: "
								+ object.get("verb").getAsString());
					}
					list.add(new Rule(
							object.has("family") ? object.get("family").getAsString() : null,
							object.has("minCount") ? object.get("minCount").getAsInt() : Integer.MIN_VALUE,
							object.has("maxCount") ? object.get("maxCount").getAsInt() : Integer.MAX_VALUE,
							verb));
				}
				rules.put(entry.getKey(), List.copyOf(list));
			}
		}
		return new VerbTable(families, rules);
	}

	/** The verb for {@code goalType} (JSON name such as BREAK_BLOCK) on {@code id}, or {@code fallback}. */
	public Verb choose(String goalType, String id, int count, Verb fallback) {
		List<Rule> list = rules.get(goalType);
		if (list == null) {
			return fallback;
		}
		for (Rule rule : list) {
			if (count < rule.minCount() || count > rule.maxCount()) {
				continue;
			}
			if (rule.family() == null || inFamily(rule.family(), id)) {
				return rule.verb();
			}
		}
		return fallback;
	}

	public boolean hasFamily(String family) {
		return families.containsKey(family);
	}

	public boolean inFamily(String family, String id) {
		List<String> patterns = families.get(family);
		if (patterns == null || id == null) {
			return false;
		}
		String path = id.startsWith("#") ? id : id.substring(id.indexOf(':') + 1);
		for (String pattern : patterns) {
			if (matches(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	/** Exact, {@code *suffix}, {@code prefix*} or {@code *part*}. */
	static boolean matches(String pattern, String path) {
		boolean starts = pattern.startsWith("*");
		boolean ends = pattern.endsWith("*");
		if (starts && ends && pattern.length() > 2) {
			return path.contains(pattern.substring(1, pattern.length() - 1));
		}
		if (starts) {
			return path.endsWith(pattern.substring(1));
		}
		if (ends) {
			return path.startsWith(pattern.substring(0, pattern.length() - 1));
		}
		return path.equals(pattern);
	}

	public java.util.Set<String> families() {
		return families.keySet();
	}

	public List<String> patterns(String family) {
		return families.getOrDefault(family, List.of());
	}
}
