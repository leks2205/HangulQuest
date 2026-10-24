package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * v5 adds the villager requests: {@code requests.open} (the accepted ones) and {@code requests.finished} (how often
 * each was handed in). A file that already has a {@code requests} block only gets the parts it lacks.
 */
final class V4ToV5 implements ProgressMigration {
	@Override
	public int from() {
		return 4;
	}

	@Override
	public int to() {
		return 5;
	}

	@Override
	public void apply(JsonObject root) {
		JsonElement existing = root.get("requests");
		JsonObject requests = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
		if (!requests.has("open") || !requests.get("open").isJsonArray()) {
			requests.add("open", new JsonArray());
		}
		if (!requests.has("finished") || !requests.get("finished").isJsonObject()) {
			requests.add("finished", new JsonObject());
		}
		root.add("requests", requests);
		ProgressMigrations.addHistory(root, 4, 5);
		root.addProperty("version", 5);
	}
}
