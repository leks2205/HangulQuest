package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.LongSupplier;

/**
 * v4 adds the FSRS card to every word ({@code words.<id>.fsrs}). The card starts from the old Leitner
 * level: stability 0.1, 0.2, 0.5, 1, 2, 4, 8, 16 and 35 days for levels 0 to 8, or 30 days for a word marked
 * known; difficulty 5; due {@code max(old dueAt, now)}. {@code level} stays as it was, for the journal and
 * for downgrades. A word that already has an {@code fsrs} block only gets the fields it lacks. The table is
 * copied here on purpose: a migration must not change when the live scheduler is tuned.
 */
final class V3ToV4 implements ProgressMigration {
	private static final double[] LEVEL_STABILITY = {0.1, 0.2, 0.5, 1, 2, 4, 8, 16, 35};
	private static final double KNOWN_STABILITY = 30;
	private static final double START_DIFFICULTY = 5;

	private final LongSupplier clock;

	V3ToV4() {
		this(System::currentTimeMillis);
	}

	V3ToV4(LongSupplier clock) {
		this.clock = clock;
	}

	@Override
	public int from() {
		return 3;
	}

	@Override
	public int to() {
		return 4;
	}

	@Override
	public void apply(JsonObject root) {
		long now = clock.getAsLong();
		JsonElement words = root.get("words");
		if (words != null && words.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : words.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonObject()) {
					seed(entry.getValue().getAsJsonObject(), now);
				}
			}
		}
		ProgressMigrations.addHistory(root, 3, 4);
		root.addProperty("version", 4);
	}

	private static void seed(JsonObject word, long now) {
		long dueAt = number(word, "dueAt", 0);
		JsonElement existing = word.get("fsrs");
		if (existing != null && existing.isJsonObject()) {
			JsonObject card = existing.getAsJsonObject();
			if (!card.has("state")) {
				card.addProperty("state", card.has("stability") && card.get("stability").getAsDouble() > 0 ? "REVIEW" : "NEW");
			}
			if (!card.has("difficulty")) {
				card.addProperty("difficulty", START_DIFFICULTY);
			}
			if (!card.has("due")) {
				card.addProperty("due", Math.max(dueAt, now));
			}
			return;
		}
		boolean known = word.has("known") && word.get("known").isJsonPrimitive() && word.get("known").getAsBoolean();
		int level = (int) Math.max(0, Math.min(LEVEL_STABILITY.length - 1, number(word, "level", 0)));
		long lastSeen = number(word, "lastSeenAt", 0);
		long wrong = number(word, "wrong", 0);

		JsonObject card = new JsonObject();
		card.addProperty("stability", known ? KNOWN_STABILITY : LEVEL_STABILITY[level]);
		card.addProperty("difficulty", START_DIFFICULTY);
		card.addProperty("state", "REVIEW");
		card.addProperty("step", 0);
		card.addProperty("reps", number(word, "correct", 0) + wrong);
		card.addProperty("lapses", wrong);
		card.addProperty("lastReview", lastSeen > 0 && lastSeen <= now ? lastSeen : now);
		card.addProperty("due", Math.max(dueAt, now));
		word.add("fsrs", card);
	}

	private static long number(JsonObject object, String name, long fallback) {
		JsonElement value = object.get(name);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return value.getAsLong();
	}
}
