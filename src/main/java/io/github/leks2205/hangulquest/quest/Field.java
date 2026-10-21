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
		ENUM
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

	/** The JSON schema of this property. */
	public JsonObject schema() {
		JsonObject schema = new JsonObject();
		switch (kind) {
			case RESOURCE_ID -> schema.addProperty("$ref", "#/definitions/resourceId");
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
		}
		if (description != null) {
			schema.addProperty("description", description);
		}
		return schema;
	}
}
