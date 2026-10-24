package io.github.leks2205.hangulquest.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads request JSON. Problems are collected, never thrown: one bad file must not stop the others. */
public final class RequestParser {
	/** The request, or null when the file could not be read at all; {@code problems} may be set either way. */
	public record Parsed(Request request, List<String> problems) {
	}

	private RequestParser() {
	}

	/** {@code fileId} is the file name without {@code .json}; the {@code id} field must match it. */
	public static Parsed parse(String fileId, byte[] bytes) {
		JsonObject root;
		try {
			root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException broken) {
			return new Parsed(null, List.of(fileId + ": not valid JSON (" + broken.getMessage() + ")"));
		}
		try {
			return read(fileId, root);
		} catch (RuntimeException malformed) {
			return new Parsed(null, List.of(fileId + ": malformed (" + malformed + ")"));
		}
	}

	private static Parsed read(String fileId, JsonObject root) {
		List<String> problems = new ArrayList<>();
		String id = JsonFields.string(root, "id", "");
		if (!id.equals(fileId)) {
			problems.add(fileId + ": id \"" + id + "\" must be the file name");
		}
		Request.Kind kind = Request.Kind.fromId(JsonFields.string(root, "kind", ""));
		if (kind == null) {
			problems.add(fileId + ": kind must be SHOW, GIVE or DO");
		}
		List<Region> regions = new ArrayList<>();
		for (String name : JsonFields.strings(root, "regions")) {
			Region region = Region.fromId(name);
			if (region == null) {
				problems.add(fileId + ": unknown region \"" + name + "\"");
			} else {
				regions.add(region);
			}
		}
		List<Personality> personalities = new ArrayList<>();
		for (String name : JsonFields.strings(root, "personalities")) {
			Personality personality = Personality.fromId(name);
			if (personality == null) {
				problems.add(fileId + ": unknown personality \"" + name + "\"");
			} else {
				personalities.add(personality);
			}
		}
		Goal goal = null;
		if (root.has("goal")) {
			if (!root.get("goal").isJsonObject()) {
				problems.add(fileId + ": goal must be an object");
			} else {
				goal = GoalTypes.parse(root.getAsJsonObject("goal"));
				if (goal == null) {
					problems.add(fileId + ": unknown goal type \"" + JsonFields.string(root.getAsJsonObject("goal"), "type", "")
							+ "\"");
				}
			}
		}
		String showItem = null;
		int showCount = 0;
		if (root.has("show")) {
			JsonObject show = root.getAsJsonObject("show");
			showItem = JsonFields.string(show, "item", null);
			showCount = JsonFields.integer(show, "count", 1);
		}
		Request.Line yes = null;
		Request.Line no = null;
		if (root.has("answers") && root.get("answers").isJsonObject()) {
			JsonObject answers = root.getAsJsonObject("answers");
			yes = line(fileId + ".answers.yes", answers.get("yes"), problems);
			no = line(fileId + ".answers.no", answers.get("no"), problems);
		}
		Map<Personality, Map<Request.Part, Request.Line>> lines = new EnumMap<>(Personality.class);
		if (root.has("lines") && root.get("lines").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("lines").entrySet()) {
				Personality personality = Personality.fromId(entry.getKey());
				if (personality == null) {
					problems.add(fileId + ".lines: unknown personality \"" + entry.getKey() + "\"");
					continue;
				}
				if (!entry.getValue().isJsonObject()) {
					problems.add(fileId + ".lines." + entry.getKey() + ": must be an object");
					continue;
				}
				Map<Request.Part, Request.Line> parts = new EnumMap<>(Request.Part.class);
				for (Map.Entry<String, JsonElement> part : entry.getValue().getAsJsonObject().entrySet()) {
					Request.Part which = part(part.getKey());
					if (which == null) {
						problems.add(fileId + ".lines." + entry.getKey() + ": unknown line \"" + part.getKey()
								+ "\" (offer, accept, decline, reminder, thanks)");
						continue;
					}
					Request.Line line = line(fileId + ".lines." + entry.getKey() + "." + part.getKey(), part.getValue(),
							problems);
					if (line != null) {
						parts.put(which, line);
					}
				}
				lines.put(personality, parts);
			}
		}
		Request request = new Request(id, JsonFields.integer(root, "level", 1), kind, regions, personalities,
				JsonFields.strings(root, "professions"), goal, showItem, showCount, JsonFields.strings(root, "words"),
				yes, no, lines);
		return new Parsed(request, problems);
	}

	private static Request.Part part(String name) {
		for (Request.Part part : Request.Part.values()) {
			if (part.id().equals(name)) {
				return part;
			}
		}
		return null;
	}

	/** A line is a string, or an object with {@code ko} and an optional {@code ko_banmal}. */
	private static Request.Line line(String where, JsonElement element, List<String> problems) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonPrimitive()) {
			return new Request.Line(element.getAsString(), null);
		}
		if (element.isJsonObject()) {
			JsonObject json = element.getAsJsonObject();
			return new Request.Line(JsonFields.string(json, "ko", ""), JsonFields.string(json, "ko_banmal", null));
		}
		problems.add(where + ": a line is a string or {\"ko\", \"ko_banmal\"}");
		return null;
	}
}
