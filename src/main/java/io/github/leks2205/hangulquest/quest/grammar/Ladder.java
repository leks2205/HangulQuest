package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The grammar ladder as data (ladder.json): which instruction ending each Korean level uses, how many
 * steps open the next level and which emitted grammar ids stand for which construction. The endings are
 * generator names, so the surface forms always come from {@link Endings}.
 */
public final class Ladder {
	public static final String RESOURCE = "ladder.json";
	public static final int LEVELS = 6;
	private static final int DEFAULT_GATE = 3;

	/** One level: its ending generator, the construction it exercises and the optional forced-conclusion ending. */
	public record Rung(int level, String ending, String grammar, String forcedEnding, String forcedGrammar,
					   boolean connectives) {
		/** The ending for a plain instruction, or for a stated forced conclusion where the level has one. */
		public String endingFor(boolean forced) {
			return forced && forcedEnding != null ? forcedEnding : ending;
		}

		public String grammarFor(boolean forced) {
			return forced && forcedGrammar != null ? forcedGrammar : grammar;
		}
	}

	private static volatile Ladder shared;

	private final int gateSteps;
	private final Rung[] rungs;
	private final Map<String, String> aliases;

	Ladder(int gateSteps, Rung[] rungs, Map<String, String> aliases) {
		this.gateSteps = gateSteps;
		this.rungs = rungs;
		this.aliases = aliases;
	}

	public static Ladder shared() {
		Ladder ladder = shared;
		if (ladder == null) {
			ladder = load();
			shared = ladder;
		}
		return ladder;
	}

	private static Ladder load() {
		try (InputStream stream = Ladder.class.getResourceAsStream("/assets/hangulquest/data/" + RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException(RESOURCE + " is missing");
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	public static Ladder parse(String json) {
		return parse(JsonParser.parseString(json).getAsJsonObject());
	}

	static Ladder parse(JsonObject root) {
		int gate = root.has("gateSteps") ? Math.max(1, root.get("gateSteps").getAsInt()) : DEFAULT_GATE;
		JsonObject levels = root.getAsJsonObject("levels");
		Rung[] rungs = new Rung[LEVELS + 1];
		for (int level = 1; level <= LEVELS; level++) {
			JsonObject entry = levels.getAsJsonObject(Integer.toString(level));
			String ending = entry.get("ending").getAsString();
			if (!Endings.has(ending)) {
				throw new IllegalStateException("level " + level + " names the unknown generator " + ending);
			}
			String forced = text(entry, "forcedEnding");
			if (forced != null && !Endings.has(forced)) {
				throw new IllegalStateException("level " + level + " names the unknown generator " + forced);
			}
			rungs[level] = new Rung(level, ending, entry.get("grammar").getAsString(), forced,
					text(entry, "forcedGrammar"), entry.has("connectives") && entry.get("connectives").getAsBoolean());
		}
		Map<String, String> aliases = new HashMap<>();
		if (root.has("aliases")) {
			for (Map.Entry<String, JsonElement> alias : root.getAsJsonObject("aliases").entrySet()) {
				aliases.put(alias.getKey(), alias.getValue().getAsString());
			}
		}
		return new Ladder(gate, rungs, Map.copyOf(aliases));
	}

	private static String text(JsonObject object, String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
	}

	/** The level's rung; levels outside 1..6 are clamped. */
	public Rung rung(int level) {
		return rungs[Math.max(1, Math.min(LEVELS, level))];
	}

	/** How many quest steps at a level open the next one. */
	public int gateSteps() {
		return gateSteps;
	}

	/** The construction id of grammar.json for an id a goal type emitted; the id itself when it has no alias. */
	public String resolve(String id) {
		return aliases.getOrDefault(id, id);
	}

	public Map<String, String> aliases() {
		return aliases;
	}
}
