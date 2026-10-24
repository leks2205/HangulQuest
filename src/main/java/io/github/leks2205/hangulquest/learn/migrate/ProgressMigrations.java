package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;

/** The ordered chain of {@link ProgressMigration}s, run on the raw tree before it is bound. */
public final class ProgressMigrations {
	private static final ProgressMigrations STANDARD = new ProgressMigrations(
			List.of(new V1ToV2(), new V2ToV3(), new V3ToV4(), new V4ToV5()));

	private final List<ProgressMigration> chain;

	/** The chain in use; its last step ends at {@code ProgressData.CURRENT_VERSION}. */
	public static ProgressMigrations standard() {
		return STANDARD;
	}

	public static ProgressMigrations of(List<ProgressMigration> chain) {
		return new ProgressMigrations(chain);
	}

	private ProgressMigrations(List<ProgressMigration> chain) {
		for (int i = 0; i < chain.size(); i++) {
			ProgressMigration step = chain.get(i);
			if (step.to() <= step.from() || (i > 0 && step.from() != chain.get(i - 1).to())) {
				throw new IllegalArgumentException("migration chain is not contiguous at " + step.from() + "->" + step.to());
			}
		}
		this.chain = List.copyOf(chain);
	}

	/** The version the chain ends at. */
	public int latest() {
		return chain.isEmpty() ? 0 : chain.get(chain.size() - 1).to();
	}

	/** The schema version of a raw tree; a missing or nonsensical one counts as v1. */
	public static int versionOf(JsonObject root) {
		JsonElement version = root.get("version");
		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
			return 1;
		}
		return Math.max(1, version.getAsInt());
	}

	/**
	 * Brings {@code root} up to {@link #latest()}. A tree that is already there, or newer, is not touched.
	 *
	 * @return true when the tree was changed
	 * @throws IllegalStateException when a step is missing from the chain
	 */
	public boolean run(JsonObject root) {
		int version = versionOf(root);
		if (version >= latest()) {
			return false;
		}
		List<Integer> path = new ArrayList<>();
		path.add(version);
		while (version < latest()) {
			ProgressMigration step = stepFrom(version);
			step.apply(root);
			root.addProperty("version", step.to());
			version = step.to();
			path.add(version);
		}
		if (root.has("meta") && root.get("meta").isJsonObject()
				&& root.getAsJsonObject("meta").has("schemaHistory")) {
			addHistory(root, path.stream().mapToInt(Integer::intValue).toArray());
		}
		return true;
	}

	private ProgressMigration stepFrom(int version) {
		for (ProgressMigration step : chain) {
			if (step.from() == version) {
				return step;
			}
		}
		throw new IllegalStateException("no migration from progress schema v" + version);
	}

	/** Gets {@code root[name]}, creating an empty object when it is missing. */
	static JsonObject object(JsonObject root, String name) {
		JsonElement existing = root.get(name);
		if (existing == null || existing.isJsonNull()) {
			JsonObject created = new JsonObject();
			root.add(name, created);
			return created;
		}
		return existing.getAsJsonObject();
	}

	/** Adds versions to {@code meta.schemaHistory}, keeping it sorted and free of duplicates. */
	static void addHistory(JsonObject root, int... versions) {
		JsonObject meta = object(root, "meta");
		TreeSet<Integer> all = new TreeSet<>();
		JsonElement existing = meta.get("schemaHistory");
		if (existing != null && existing.isJsonArray()) {
			for (JsonElement item : existing.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
					all.add(item.getAsInt());
				}
			}
		}
		for (int version : versions) {
			all.add(version);
		}
		JsonArray history = new JsonArray();
		all.forEach(history::add);
		meta.add("schemaHistory", history);
	}

	/** Random hex, made once per install and kept local. */
	public static String newSalt() {
		byte[] bytes = new byte[16];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}
}
