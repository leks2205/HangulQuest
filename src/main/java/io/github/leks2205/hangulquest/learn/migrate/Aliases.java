package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * Renamed ids: old id to new id for trails, steps ({@code trailId/stepId}) and words, from
 * {@code data/aliases.json}. Applied to the raw progress tree on every load, so a rename in content
 * never costs a player their progress. Chains ({@code a -> b -> c}) resolve to the end.
 */
public final class Aliases {
	public static final String RESOURCE = "/assets/hangulquest/data/aliases.json";
	public static final Aliases NONE = new Aliases(Map.of(), Map.of(), Map.of());
	private static final int MAX_HOPS = 32;
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-aliases");
	private static Aliases bundled;

	private final Map<String, String> trails;
	private final Map<String, String> steps;
	private final Map<String, String> words;

	private Aliases(Map<String, String> trails, Map<String, String> steps, Map<String, String> words) {
		this.trails = trails;
		this.steps = steps;
		this.words = words;
	}

	/** The aliases shipped in the jar; none when the file cannot be read. */
	public static synchronized Aliases bundled() {
		if (bundled == null) {
			try (InputStream stream = Aliases.class.getResourceAsStream(RESOURCE)) {
				bundled = stream == null ? NONE : parse(JsonParser.parseReader(
						new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
			} catch (IOException | RuntimeException broken) {
				LOG.warn("aliases.json is unusable, renamed ids will not be followed: {}", broken.toString());
				bundled = NONE;
			}
		}
		return bundled;
	}

	/** @throws IllegalArgumentException for a cycle, a self alias or a step target without a trail */
	public static Aliases parse(JsonObject json) {
		Aliases aliases = new Aliases(table(json, "trails"), table(json, "steps"), table(json, "words"));
		check(aliases.trails, "trails");
		check(aliases.steps, "steps");
		check(aliases.words, "words");
		for (Map.Entry<String, String> step : aliases.steps.entrySet()) {
			if (step.getKey().indexOf('/') <= 0 || step.getValue().indexOf('/') <= 0) {
				throw new IllegalArgumentException("step alias needs trailId/stepId on both sides: " + step);
			}
		}
		return aliases;
	}

	private static Map<String, String> table(JsonObject json, String name) {
		Map<String, String> table = new LinkedHashMap<>();
		JsonElement section = json.get(name);
		if (section != null && section.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject().entrySet()) {
				table.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		return table;
	}

	private static void check(Map<String, String> table, String name) {
		for (String start : table.keySet()) {
			Set<String> seen = new HashSet<>();
			String id = start;
			while (id != null) {
				if (!seen.add(id)) {
					throw new IllegalArgumentException("alias cycle in " + name + " at " + id);
				}
				id = table.get(id);
			}
		}
	}

	public boolean isEmpty() {
		return trails.isEmpty() && steps.isEmpty() && words.isEmpty();
	}

	public String word(String id) {
		return follow(words, id);
	}

	public String trail(String id) {
		return follow(trails, id);
	}

	/** The final {@code {trailId, stepId}} of a saved position; steps and trails may each have been renamed. */
	public String[] step(String trailId, String stepId) {
		String trail = trailId;
		String step = stepId;
		for (int hop = 0; hop < MAX_HOPS; hop++) {
			boolean moved = false;
			String target = step == null ? null : steps.get(trail + "/" + step);
			if (target != null) {
				int cut = target.indexOf('/');
				trail = target.substring(0, cut);
				step = target.substring(cut + 1);
				moved = true;
			}
			String renamed = trails.get(trail);
			if (renamed != null) {
				trail = renamed;
				moved = true;
			}
			if (!moved) {
				break;
			}
		}
		return new String[] {trail, step};
	}

	private static String follow(Map<String, String> table, String id) {
		String current = id;
		for (int hop = 0; hop < MAX_HOPS && current != null; hop++) {
			String next = table.get(current);
			if (next == null) {
				break;
			}
			current = next;
		}
		return current;
	}

	/**
	 * Rewrites every old id in a raw progress tree to its current one: word keys, the active quest and
	 * its sidequest word, saved quests, completed trails, the tracked list, history and the adaptive word
	 * list. When a rename lands on an id that already has an entry, the entry with more progress wins.
	 *
	 * @return true when anything changed
	 */
	public boolean apply(JsonObject root) {
		if (isEmpty()) {
			return false;
		}
		boolean changed = false;
		if (root.has("words") && root.get("words").isJsonObject()) {
			changed |= rekey(root.getAsJsonObject("words"), this::word,
					(a, b) -> number(b, "seen") > number(a, "seen") ? b : a);
		}
		if (root.has("quests") && root.get("quests").isJsonObject()) {
			changed |= applyQuests(root.getAsJsonObject("quests"));
		}
		return changed;
	}

	private boolean applyQuests(JsonObject quests) {
		boolean changed = false;
		if (quests.has("active") && quests.get("active").isJsonObject()) {
			changed |= position(quests.getAsJsonObject("active"));
		}
		if (quests.has("saved") && quests.get("saved").isJsonObject()) {
			JsonObject saved = quests.getAsJsonObject("saved");
			for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					changed |= position(entry.getValue().getAsJsonObject());
				}
			}
			changed |= rekey(saved, this::trail, (a, b) -> number(b, "lastSeenAt") > number(a, "lastSeenAt") ? b : a);
		}
		if (quests.has("completed") && quests.get("completed").isJsonObject()) {
			changed |= rekey(quests.getAsJsonObject("completed"), this::trail,
					(a, b) -> a.getAsLong() <= b.getAsLong() ? a : b);
		}
		if (quests.has("tracked") && quests.get("tracked").isJsonArray()) {
			changed |= rewriteList(quests.getAsJsonArray("tracked"), this::trail);
		}
		if (quests.has("history") && quests.get("history").isJsonArray()) {
			for (JsonElement entry : quests.getAsJsonArray("history")) {
				if (entry.isJsonObject()) {
					changed |= rename(entry.getAsJsonObject(), "trailId", this::trail);
				}
			}
		}
		if (quests.has("adaptive") && quests.get("adaptive").isJsonObject()) {
			JsonObject adaptive = quests.getAsJsonObject("adaptive");
			if (adaptive.has("wordIds") && adaptive.get("wordIds").isJsonArray()) {
				changed |= rewriteList(adaptive.getAsJsonArray("wordIds"), this::word);
			}
		}
		return changed;
	}

	/** An active or saved quest: its trail and step, and the word of a running sidequest. */
	private boolean position(JsonObject quest) {
		boolean changed = false;
		String trail = text(quest, "trailId");
		if (trail != null) {
			String[] now = step(trail, text(quest, "stepId"));
			if (!now[0].equals(trail)) {
				quest.addProperty("trailId", now[0]);
				changed = true;
			}
			if (now[1] != null && !now[1].equals(text(quest, "stepId"))) {
				quest.addProperty("stepId", now[1]);
				changed = true;
			}
		}
		if (quest.has("sidequest") && quest.get("sidequest").isJsonObject()) {
			changed |= rename(quest.getAsJsonObject("sidequest"), "wordId", this::word);
		}
		return changed;
	}

	private static boolean rename(JsonObject object, String field, UnaryOperator<String> alias) {
		String value = text(object, field);
		if (value == null) {
			return false;
		}
		String now = alias.apply(value);
		if (now.equals(value)) {
			return false;
		}
		object.addProperty(field, now);
		return true;
	}

	private static boolean rewriteList(JsonArray list, UnaryOperator<String> alias) {
		Set<String> items = new LinkedHashSet<>();
		boolean changed = false;
		for (JsonElement item : list) {
			if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
				String before = item.getAsString();
				String now = alias.apply(before);
				changed |= !now.equals(before);
				if (!items.add(now)) {
					changed = true;
				}
			}
		}
		if (changed) {
			ArrayList<JsonElement> rebuilt = new ArrayList<>();
			items.forEach(item -> rebuilt.add(new JsonPrimitive(item)));
			while (list.size() > 0) {
				list.remove(0);
			}
			rebuilt.forEach(list::add);
		}
		return changed;
	}

	/** Renames the keys of {@code map}, in order; two keys that meet are merged with {@code pick}. */
	private static boolean rekey(JsonObject map, UnaryOperator<String> alias, BinaryOperator<JsonElement> pick) {
		Map<String, JsonElement> rebuilt = new LinkedHashMap<>();
		boolean changed = false;
		for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
			String key = alias.apply(entry.getKey());
			changed |= !key.equals(entry.getKey());
			JsonElement already = rebuilt.get(key);
			rebuilt.put(key, already == null ? entry.getValue() : pick.apply(already, entry.getValue()));
		}
		if (changed) {
			new ArrayList<>(map.keySet()).forEach(map::remove);
			rebuilt.forEach(map::add);
		}
		return changed;
	}

	private static String text(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : null;
	}

	private static long number(JsonElement entry, String field) {
		if (!entry.isJsonObject()) {
			return 0;
		}
		JsonElement value = entry.getAsJsonObject().get(field);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
				? value.getAsLong() : 0;
	}
}
