package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.quest.condition.ConditionType;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the JSON of the goals and conditions of a trail against the fields their types list (the same list the
 * published schema is generated from). The parsers take a missing field as its default and ignore an unknown
 * one, so {@code {"type":"WEATHER","phase":"CLEAR"}} used to load as clear weather; this finds it, and says in
 * which trail and step.
 */
public final class StrictFields {
	private StrictFields() {
	}

	/** The problems of a trail file: an unknown field, a missing required one; empty when the goals and conditions are right. */
	public static List<String> problems(JsonObject root) {
		List<String> problems = new ArrayList<>();
		String trail = root.has("id") && root.get("id").isJsonPrimitive() ? root.get("id").getAsString() : "?";
		if (root.has("steps") && root.get("steps").isJsonArray()) {
			for (JsonElement step : root.getAsJsonArray("steps")) {
				if (step.isJsonObject()) {
					step(trail, step.getAsJsonObject(), problems);
				}
			}
		}
		if (root.has("finale") && root.get("finale").isJsonObject()) {
			JsonObject finale = root.getAsJsonObject("finale");
			step(trail, finale.has("step") && finale.get("step").isJsonObject() ? finale.getAsJsonObject("step") : finale,
					problems);
		}
		return problems;
	}

	private static void step(String trail, JsonObject step, List<String> problems) {
		String where = trail + "/" + (step.has("id") && step.get("id").isJsonPrimitive() ? step.get("id").getAsString() : "?");
		// the schema says minItems 1: the first taught word supplies the noun of the sentence
		if (!step.has("teach") || !step.get("teach").isJsonArray() || step.getAsJsonArray("teach").isEmpty()) {
			problems.add(where + ": teaches no word (teach needs at least one concept)");
		}
		if (step.has("goal") && step.get("goal").isJsonObject()) {
			goal(where + " goal", step.getAsJsonObject("goal"), problems);
		}
		if (step.has("conditions") && step.get("conditions").isJsonArray()) {
			for (JsonElement condition : step.getAsJsonArray("conditions")) {
				if (condition.isJsonObject()) {
					condition(where + " condition", condition.getAsJsonObject(), problems);
				}
			}
		}
	}

	private static void goal(String where, JsonObject json, List<String> problems) {
		GoalType<?> type = GoalTypes.byId(typeOf(json));
		if (type != null) {
			fields(where + " " + type.id(), json, type.fields(), problems);
		}
	}

	private static void condition(String where, JsonObject json, List<String> problems) {
		ConditionType<?> type = ConditionTypes.byId(typeOf(json));
		if (type != null) {
			fields(where + " " + type.id(), json, type.fields(), problems);
		}
	}

	private static String typeOf(JsonObject json) {
		return json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : null;
	}

	private static void fields(String where, JsonObject json, List<Field> fields, List<String> problems) {
		for (String key : json.keySet()) {
			if (!key.equals("type") && fields.stream().noneMatch(field -> field.name().equals(key))) {
				problems.add(where + ": unknown field '" + key + "' (known: "
						+ fields.stream().map(Field::name).toList() + ")");
			}
		}
		for (Field field : fields) {
			if (field.required() && !json.has(field.name())) {
				problems.add(where + ": missing field '" + field.name() + "'");
			} else if (json.has(field.name())) {
				nested(where + "." + field.name(), json.get(field.name()), field.kind(), problems);
			}
		}
	}

	private static void nested(String where, JsonElement value, Field.Kind kind, List<String> problems) {
		switch (kind) {
			case GOAL -> {
				if (value.isJsonObject()) {
					goal(where, value.getAsJsonObject(), problems);
				}
			}
			case CONDITION -> {
				if (value.isJsonObject()) {
					condition(where, value.getAsJsonObject(), problems);
				}
			}
			case GOAL_OR_CONDITION -> {
				if (value.isJsonObject()) {
					JsonObject json = value.getAsJsonObject();
					if (ConditionTypes.byId(typeOf(json)) != null) {
						condition(where, json, problems);
					} else {
						goal(where, json, problems);
					}
				}
			}
			case GOAL_LIST -> {
				if (value.isJsonArray()) {
					JsonArray parts = value.getAsJsonArray();
					for (int i = 0; i < parts.size(); i++) {
						if (parts.get(i).isJsonObject()) {
							goal(where + "[" + i + "]", parts.get(i).getAsJsonObject(), problems);
						}
					}
				}
			}
			default -> {
			}
		}
	}
}
