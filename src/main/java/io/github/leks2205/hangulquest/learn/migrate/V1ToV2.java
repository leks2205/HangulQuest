package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonObject;

/**
 * v2 added quests, vocabulary info and the tracked list. All of them are optional on read, and a missing
 * {@code quests.tracked} deliberately keeps its old meaning ("every saved trail is tracked"), so nothing
 * has to be filled in.
 */
final class V1ToV2 implements ProgressMigration {
	@Override
	public int from() {
		return 1;
	}

	@Override
	public int to() {
		return 2;
	}

	@Override
	public void apply(JsonObject root) {
		root.addProperty("version", 2);
	}
}
