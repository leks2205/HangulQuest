package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonObject;

/**
 * v3 adds {@code meta.schemaHistory}, {@code meta.installSalt} and the persisted sidequest and quest-step
 * counters. An existing salt is never replaced.
 */
final class V2ToV3 implements ProgressMigration {
	@Override
	public int from() {
		return 2;
	}

	@Override
	public int to() {
		return 3;
	}

	@Override
	public void apply(JsonObject root) {
		JsonObject meta = ProgressMigrations.object(root, "meta");
		if (!meta.has("installSalt") || !meta.get("installSalt").isJsonPrimitive()
				|| meta.get("installSalt").getAsString().isBlank()) {
			meta.addProperty("installSalt", ProgressMigrations.newSalt());
		}
		JsonObject totals = ProgressMigrations.object(root, "totals");
		for (String counter : new String[] {"sidequestsCompleted", "questStepsCompleted"}) {
			if (!totals.has(counter)) {
				totals.addProperty(counter, 0);
			}
		}
		ProgressMigrations.addHistory(root, 2, 3);
		root.addProperty("version", 3);
	}
}
