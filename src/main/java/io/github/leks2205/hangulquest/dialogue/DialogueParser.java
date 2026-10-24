package io.github.leks2205.hangulquest.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads dialogue JSON. Problems are collected, never thrown: one bad file must not stop the others. */
public final class DialogueParser {
	/** The dialogue, or null when the file could not be read at all; {@code problems} may be set either way. */
	public record Parsed(Dialogue dialogue, List<String> problems) {
	}

	private DialogueParser() {
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
			// A field of the wrong type (a string where a list belongs) lands here.
			return new Parsed(null, List.of(fileId + ": malformed (" + malformed + ")"));
		}
	}

	private static Parsed read(String fileId, JsonObject root) {
		List<String> problems = new ArrayList<>();
		String id = JsonFields.string(root, "id", "");
		if (!id.equals(fileId)) {
			problems.add(fileId + ": id \"" + id + "\" must be the file name");
		}
		JsonObject triggerJson = root.has("trigger") && root.get("trigger").isJsonObject()
				? root.getAsJsonObject("trigger") : null;
		if (triggerJson == null || JsonFields.string(triggerJson, "entity", "").isBlank()) {
			problems.add(fileId + ": trigger.entity is missing");
		}
		Dialogue.Trigger trigger = triggerJson == null ? new Dialogue.Trigger("", null, null, List.of())
				: trigger(fileId, triggerJson, problems);
		SpeechLevel villagerLevel = villagerLevel(trigger);

		Map<String, Dialogue.Node> nodes = new LinkedHashMap<>();
		if (!root.has("nodes") || !root.get("nodes").isJsonObject()) {
			problems.add(fileId + ": nodes are missing");
		} else {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("nodes").entrySet()) {
				Dialogue.Node node = node(fileId, entry.getKey(), entry.getValue(), villagerLevel, problems);
				if (node != null) {
					nodes.put(node.id(), node);
				}
			}
		}
		Dialogue dialogue = new Dialogue(id, JsonFields.integer(root, "level", 1), trigger,
				JsonFields.string(root, "start", ""), nodes);
		return new Parsed(dialogue, problems);
	}

	private static Dialogue.Trigger trigger(String fileId, JsonObject json, List<String> problems) {
		List<Personality> personalities = new ArrayList<>();
		for (String name : JsonFields.strings(json, "personalities")) {
			Personality personality = Personality.fromId(name);
			if (personality == null) {
				problems.add(fileId + ": unknown personality \"" + name + "\"");
			} else {
				personalities.add(personality);
			}
		}
		List<Region> regions = new ArrayList<>();
		for (String name : JsonFields.strings(json, "regions")) {
			Region region = Region.fromId(name);
			if (region == null) {
				problems.add(fileId + ": unknown region \"" + name + "\"");
			} else {
				regions.add(region);
			}
		}
		ConversationKind kind = ConversationKind.SMALLTALK;
		if (json.has("kind")) {
			kind = ConversationKind.fromId(JsonFields.string(json, "kind", ""));
			if (kind == null) {
				problems.add(fileId + ": unknown kind \"" + JsonFields.string(json, "kind", "") + "\"");
				kind = ConversationKind.SMALLTALK;
			}
		}
		return new Dialogue.Trigger(JsonFields.string(json, "entity", ""), JsonFields.string(json, "profession", null),
				json.has("baby") ? json.get("baby").getAsBoolean() : null, JsonFields.strings(json, "professions"),
				personalities, regions, kind, JsonFields.integer(json, "friendship", 0));
	}

	/**
	 * The level a villager line of this dialogue has unless the node says otherwise: the register of its
	 * personalities when they share one (합쇼체 for SNOOTY), else null (the speaker's own level).
	 */
	static SpeechLevel villagerLevel(Dialogue.Trigger trigger) {
		SpeechLevel shared = null;
		for (Personality personality : trigger.personalities()) {
			if (shared != null && shared != personality.register()) {
				return null;
			}
			shared = personality.register();
		}
		return shared;
	}

	private static Dialogue.Node node(String file, String id, JsonElement element, SpeechLevel villagerLevel,
									  List<String> problems) {
		String where = file + "." + id;
		if (!element.isJsonObject()) {
			problems.add(where + ": a node must be an object");
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		Speaker speaker = Speaker.fromId(JsonFields.string(json, "speaker", ""));
		if (speaker == null) {
			problems.add(where + ": unknown speaker \"" + JsonFields.string(json, "speaker", "") + "\"");
			return null;
		}
		SpeechLevel level = speaker == Speaker.VILLAGER && villagerLevel != null ? villagerLevel : speaker.level();
		if (json.has("speechLevel")) {
			level = SpeechLevel.fromId(JsonFields.string(json, "speechLevel", ""));
			if (level == null) {
				problems.add(where + ": unknown speechLevel \"" + JsonFields.string(json, "speechLevel", "") + "\"");
				level = speaker.level();
			}
		}
		List<Dialogue.Choice> choices = new ArrayList<>();
		if (json.has("choices") && json.get("choices").isJsonArray()) {
			for (JsonElement choice : json.getAsJsonArray("choices")) {
				if (choice.isJsonObject()) {
					choices.add(choice(where, choice.getAsJsonObject(), problems));
				} else {
					problems.add(where + ": a choice must be an object");
				}
			}
		}
		Dialogue.Typed typed = null;
		if (json.has("typed") && json.get("typed").isJsonObject()) {
			JsonObject typedJson = json.getAsJsonObject("typed");
			typed = new Dialogue.Typed(JsonFields.strings(typedJson, "expected"),
					JsonFields.string(typedJson, "word", null),
					typedJson.has("free") && typedJson.get("free").getAsBoolean());
		}
		return new Dialogue.Node(id, speaker, level, JsonFields.string(json, "ko", ""),
				JsonFields.string(json, "next", null), choices, typed, effects(where, json, problems),
				json.has("end") && json.get("end").getAsBoolean(), JsonFields.string(json, "grammar", null),
				JsonFields.string(json, "ko_banmal", null), json.has("selfIntro") && json.get("selfIntro").getAsBoolean());
	}

	private static Dialogue.Choice choice(String where, JsonObject json, List<String> problems) {
		SpeechLevel level = SpeechLevel.HAEYO;
		if (json.has("speechLevel")) {
			SpeechLevel named = SpeechLevel.fromId(JsonFields.string(json, "speechLevel", ""));
			if (named == null) {
				problems.add(where + ": unknown choice speechLevel \"" + JsonFields.string(json, "speechLevel", "") + "\"");
			} else {
				level = named;
			}
		}
		return new Dialogue.Choice(JsonFields.string(json, "ko", ""), level, JsonFields.string(json, "next", null),
				json.has("correct") ? json.get("correct").getAsBoolean() : null, effects(where, json, problems));
	}

	private static List<Dialogue.Effect> effects(String where, JsonObject json, List<String> problems) {
		List<Dialogue.Effect> effects = new ArrayList<>();
		if (!json.has("effects") || !json.get("effects").isJsonArray()) {
			return effects;
		}
		for (JsonElement element : json.getAsJsonArray("effects")) {
			if (!element.isJsonObject()) {
				problems.add(where + ": an effect must be an object");
				continue;
			}
			JsonObject effect = element.getAsJsonObject();
			String type = JsonFields.string(effect, "type", "");
			switch (type) {
				case "startTrail" -> effects.add(new Dialogue.StartTrail(JsonFields.string(effect, "trail", "")));
				case "exposeWords" -> {
					JsonArray words = effect.has("words") && effect.get("words").isJsonArray()
							? effect.getAsJsonArray("words") : new JsonArray();
					List<String> ids = new ArrayList<>();
					words.forEach(word -> ids.add(word.getAsString()));
					effects.add(new Dialogue.ExposeWords(ids));
				}
				case "exposeGrammar" -> effects.add(new Dialogue.ExposeGrammar(JsonFields.string(effect, "point", "")));
				case "acceptBanmal" -> effects.add(new Dialogue.AcceptBanmal());
				default -> problems.add(where + ": unknown effect type \"" + type + "\"");
			}
		}
		return effects;
	}
}
