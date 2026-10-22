package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Re-reads the overlay folder off the main thread and hands the result back on it. Requests that
 * arrive while a pass runs are folded into one more pass. The two executors keep it testable:
 * the game passes a background pool and {@code Minecraft::execute}.
 *
 * @param <R> what the caller builds from the merged trails (for the game: the validated result)
 */
public final class OverlayReloader<R> {
	private final Path directory;
	private final QuestlineOverlay overlay;
	private final List<Questline> shipped;
	private final Function<List<Questline>, R> finish;
	private final Executor background;
	private final Executor main;
	private final Consumer<R> sink;
	private final Logger log;

	private boolean running;
	private boolean again;
	private volatile boolean closed;
	private volatile boolean forced;

	/**
	 * @param finish runs on the background thread after merging (validation); its result goes to {@code sink}
	 * @param sink   runs on the main thread, only when something changed
	 */
	public OverlayReloader(Path directory, QuestlineOverlay overlay, List<Questline> shipped,
						   Function<List<Questline>, R> finish, Executor background, Executor main,
						   Consumer<R> sink, Logger log) {
		this.directory = directory;
		this.overlay = overlay;
		this.shipped = shipped;
		this.finish = finish;
		this.background = background;
		this.main = main;
		this.sink = sink;
		this.log = log;
	}

	/** Asks for a pass over the folder; returns at once. Safe from any thread. */
	public synchronized void request() {
		if (closed) {
			return;
		}
		if (running) {
			again = true;
			return;
		}
		running = true;
		background.execute(this::run);
	}

	/** Like {@link #request()}, but re-reads every file, not only the changed ones. */
	public void requestFull() {
		overlay.invalidate();
		forced = true;
		request();
	}

	/** Drops pending and future results (the content was replaced by a newer load). */
	public void close() {
		closed = true;
	}

	private void run() {
		while (true) {
			try {
				boolean changed = !closed && (overlay.refresh(directory) | forced);
				forced = false;
				if (changed) {
					R result = finish.apply(QuestlineStore.merge(shipped, overlay.files(), log));
					main.execute(() -> {
						if (!closed) {
							sink.accept(result);
						}
					});
				}
			} catch (RuntimeException failed) {
				log.warn("questline reload failed, keeping the current trails: {}", failed.toString());
			}
			synchronized (this) {
				if (again) {
					again = false;
					continue;
				}
				running = false;
				return;
			}
		}
	}
}
