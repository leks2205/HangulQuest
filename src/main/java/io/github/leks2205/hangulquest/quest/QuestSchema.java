package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.quest.condition.ConditionType;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;

import java.util.List;

/**
 * The goal and condition parts of docs/questline.schema.json, generated from the type registries.
 * The rest of the schema (steps, requires, texts) stays hand-written in that file; a test keeps both
 * in step and rewrites the file on request.
 */
public final class QuestSchema {
	private QuestSchema() {
	}

	/** Returns a copy of {@code schema} whose goal and condition alternatives come from the registries. */
	public static JsonObject generate(JsonObject schema) {
		JsonObject copy = schema.deepCopy();
		JsonObject definitions = copy.getAsJsonObject("definitions");
		JsonArray goals = new JsonArray();
		for (GoalType<?> type : GoalTypes.all()) {
			goals.add(alternative(type.id(), type.fields()));
		}
		definitions.getAsJsonObject("goal").add("oneOf", goals);
		JsonArray conditions = new JsonArray();
		for (ConditionType<?> type : ConditionTypes.all()) {
			conditions.add(alternative(type.id(), type.fields()));
		}
		definitions.getAsJsonObject("condition").add("oneOf", conditions);
		return copy;
	}

	private static JsonObject alternative(String id, List<Field> fields) {
		JsonObject type = new JsonObject();
		type.addProperty("const", id);
		JsonObject properties = new JsonObject();
		properties.add("type", type);
		JsonArray required = new JsonArray();
		required.add("type");
		for (Field field : fields) {
			properties.add(field.name(), field.schema());
			if (field.required()) {
				required.add(field.name());
			}
		}
		JsonObject alternative = new JsonObject();
		alternative.add("properties", properties);
		alternative.add("required", required);
		alternative.addProperty("additionalProperties", false);
		return alternative;
	}
}
