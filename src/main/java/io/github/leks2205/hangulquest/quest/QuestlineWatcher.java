package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;

/**
 * Watches the questline folder on a daemon thread and calls back once the changes have settled:
 * an editor that writes a file in several steps, or a sync tool that touches many files, causes
 * one callback after the folder was quiet for the debounce time. The callback runs on the
 * watcher thread, so it must only hand work on.
 */
public final class QuestlineWatcher implements AutoCloseable {
	public static final long DEBOUNCE_MS = 500;

	private final WatchService service;
	private final Thread thread;
	private final long debounceMs;
	private final Runnable onQuiet;
	private final Logger log;
	private volatile boolean closed;

	private QuestlineWatcher(WatchService service, long debounceMs, Runnable onQuiet, Logger log) {
		this.service = service;
		this.debounceMs = debounceMs;
		this.onQuiet = onQuiet;
		this.log = log;
		this.thread = new Thread(this::run, "hangulquest-questline-watch");
		this.thread.setDaemon(true);
	}

	/** Starts watching; null when the folder cannot be watched (the manual reload still works). */
	public static QuestlineWatcher start(Path directory, long debounceMs, Runnable onQuiet, Logger log) {
		try {
			Files.createDirectories(directory);
			WatchService service = directory.getFileSystem().newWatchService();
			directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
			QuestlineWatcher watcher = new QuestlineWatcher(service, debounceMs, onQuiet, log);
			watcher.thread.start();
			return watcher;
		} catch (IOException | RuntimeException failed) {
			log.warn("cannot watch {} for questline changes: {}", directory, failed.toString());
			return null;
		}
	}

	private void run() {
		boolean dirty = false;
		long lastEvent = 0;
		try {
			while (!closed) {
				long wait = dirty ? Math.max(1, lastEvent + debounceMs - now()) : 1000;
				WatchKey key = service.poll(wait, TimeUnit.MILLISECONDS);
				if (key != null) {
					for (WatchEvent<?> event : key.pollEvents()) {
						if (event.kind() == StandardWatchEventKinds.OVERFLOW || relevant(event.context())) {
							dirty = true;
							lastEvent = now();
						}
					}
					if (!key.reset()) {
						return; // folder deleted
					}
				}
				if (dirty && now() - lastEvent >= debounceMs) {
					dirty = false;
					try {
						onQuiet.run();
					} catch (RuntimeException failed) {
						log.warn("questline reload callback failed: {}", failed.toString());
					}
				}
			}
		} catch (InterruptedException | ClosedWatchServiceException stopped) {
			// closed
		}
	}

	private static boolean relevant(Object context) {
		return context instanceof Path path && QuestlineStore.isTrailFile(path.toString());
	}

	private static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	@Override
	public void close() {
		closed = true;
		try {
			service.close();
		} catch (IOException ignored) {
			// already gone
		}
		thread.interrupt();
	}
}
