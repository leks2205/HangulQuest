package io.github.leks2205.hangulquest.content;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One daemon thread for the mod's own background work (content reloads), so it never blocks exit. */
public final class Background {
	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "hangulquest-content");
		thread.setDaemon(true);
		return thread;
	});

	private Background() {
	}

	public static Executor executor() {
		return POOL;
	}
}
