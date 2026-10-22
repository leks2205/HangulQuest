package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * One JSON property of a goal or condition. The registry types list their fields; the published
 * schema is generated from that list.
 */
public record Field(String name, Kind kind, boolean required, Integer min, Integer max,
					List<String> values, String description) {
	public enum Kind {
		/** A namespaced id such as minecraft:stone, or a tag. */
		RESOURCE_ID,
		INTEGER,
		ENUM,
		// Action variety: lists, flags and nested goals or conditions.
		RESOURCE_LIST,
		BOOLEAN,
		GOAL,
		GOAL_LIST,
		CONDITION,
		/** A goal or a condition, told apart by its {@code type}. */
		GOAL_OR_CONDITION,
		/** A namespaced id or tag like {@link #RESOURCE_ID}, or {@code @stepId}: the block placed in that earlier step. */
		ANCHOR
	}

	public static Field resource(String name) {
		return new Field(name, Kind.RESOURCE_ID, true, null, null, List.of(), null);
	}

	public static Field integer(String name, int min, int max, boolean required) {
		return new Field(name, Kind.INTEGER, required, min, max, List.of(), null);
	}

	/** At least {@code min}, no upper limit. */
	public static Field count(String name) {
		return new Field(name, Kind.INTEGER, true, 1, null, List.of(), null);
	}

	public static Field choice(String name, boolean required, String description, String... values) {
		return new Field(name, Kind.ENUM, required, null, null, List.of(values), description);
	}

	/** A block, family or tag, or {@code @stepId} for the block placed in an earlier step of the trail. */
	public static Field anchor(String name, boolean required, String description) {
		return new Field(name, Kind.ANCHOR, required, null, null, List.of(), description);
	}

	/** Same, with a description for the schema. */
	public static Field optionalResource(String name, String description) {
		return new Field(name, Kind.RESOURCE_ID, false, null, null, List.of(), description);
	}

	public static Field optionalResource(String name) {
		return new Field(name, Kind.RESOURCE_ID, false, null, null, List.of(), null);
	}

	/** An optional list of namespaced ids. */
	public static Field resourceList(String name, String description) {
		return new Field(name, Kind.RESOURCE_LIST, false, null, null, List.of(), description);
	}

	public static Field flag(String name, String description) {
		return new Field(name, Kind.BOOLEAN, false, null, null, List.of(), description);
	}

	public static Field goal(String name, boolean required, String description) {
		return new Field(name, Kind.GOAL, required, null, null, List.of(), description);
	}

	/** A list of goals with {@code min}..{@code max} entries. */
	public static Field goalList(String name, int min, int max, String description) {
		return new Field(name, Kind.GOAL_LIST, true, min, max, List.of(), description);
	}

	public static Field condition(String name, boolean required, String description) {
		return new Field(name, Kind.CONDITION, required, null, null, List.of(), description);
	}

	public static Field goalOrCondition(String name, String description) {
		return new Field(name, Kind.GOAL_OR_CONDITION, true, null, null, List.of(), description);
	}

	/** The JSON schema of this property. */
	public JsonObject schema() {
		JsonObject schema = new JsonObject();
		switch (kind) {
			case RESOURCE_ID -> schema.addProperty("$ref", "#/definitions/resourceId");
			case ANCHOR -> {
				schema.addProperty("type", "string");
				schema.addProperty("pattern", "^(#?[a-z0-9_.-]+:[a-z0-9_./-]+|@[A-Za-z0-9_-]+)$");
			}
			case INTEGER -> {
				schema.addProperty("type", "integer");
				schema.addProperty("minimum", min);
				if (max != null) {
					schema.addProperty("maximum", max);
				}
			}
			case ENUM -> {
				JsonArray array = new JsonArray();
				values.forEach(array::add);
				schema.add("enum", array);
			}
			case RESOURCE_LIST -> {
				schema.addProperty("type", "array");
				JsonObject items = new JsonObject();
				items.addProperty("$ref", "#/definitions/resourceId");
				schema.add("items", items);
			}
			case BOOLEAN -> schema.addProperty("type", "boolean");
			case GOAL -> schema.addProperty("$ref", "#/definitions/goal");
			case CONDITION -> schema.addProperty("$ref", "#/definitions/condition");
			case GOAL_LIST -> {
				schema.addProperty("type", "array");
				JsonObject items = new JsonObject();
				items.addProperty("$ref", "#/definitions/goal");
				schema.add("items", items);
				schema.addProperty("minItems", min);
				schema.addProperty("maxItems", max);
			}
			case GOAL_OR_CONDITION -> {
				JsonArray either = new JsonArray();
				for (String ref : List.of("goal", "condition")) {
					JsonObject option = new JsonObject();
					option.addProperty("$ref", "#/definitions/" + ref);
					either.add(option);
				}
				schema.add("oneOf", either);
			}
		}
		if (description != null) {
			schema.addProperty("description", description);
		}
		return schema;
	}
}
