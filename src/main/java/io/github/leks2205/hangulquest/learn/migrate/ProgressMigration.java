package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonObject;

/**
 * One step of the progress.json schema chain, working on the raw tree before it is bound to
 * {@code ProgressData}. It must be idempotent: running it on a tree it already changed does nothing more.
 * Never remove or rename a field here; progress that older versions wrote has to survive.
 */
public interface ProgressMigration {
	/** The schema version this step reads. */
	int from();

	/** The schema version this step writes. */
	int to();

	/** Changes {@code root} in place and sets its {@code version} to {@link #to()}. */
	void apply(JsonObject root);
}
