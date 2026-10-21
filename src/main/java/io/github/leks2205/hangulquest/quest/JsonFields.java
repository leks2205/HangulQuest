package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Lenient readers for questline JSON: a missing or broken value falls back instead of throwing. */
public final class JsonFields {
	private JsonFields() {
	}

	public static String string(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsString() : fallback;
	}

	public static int integer(JsonObject object, String key, int fallback) {
		try {
			return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
		} catch (RuntimeException broken) {
			return fallback;
		}
	}

	public static double number(JsonObject object, String key, double fallback) {
		try {
			return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
		} catch (RuntimeException broken) {
			return fallback;
		}
	}

	public static List<String> strings(JsonObject object, String key) {
		List<String> values = new ArrayList<>();
		if (object != null && object.has(key)) {
			JsonArray array = object.getAsJsonArray(key);
			for (JsonElement element : array) {
				values.add(element.getAsString());
			}
		}
		return values;
	}
}
