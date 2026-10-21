package io.github.leks2205.hangulquest.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Off-thread parse, main-thread apply, and the folder watcher's debounce. */
class OverlayReloaderTest {
	private static final Logger LOG = LoggerFactory.getLogger("overlay-reloader-test");

	@TempDir
	Path dir;

	/** Runs tasks only when the test drains it, like the game's main thread. */
	private static final class Queued implements Executor {
		final Queue<Runnable> tasks = new ArrayDeque<>();

		@Override
		public void execute(Runnable task) {
			tasks.add(task);
		}

		int drain() {
			int ran = 0;
			while (!tasks.isEmpty()) {
				tasks.poll().run();
				ran++;
			}
			return ran;
		}
	}

	private static String json(String id) {
		return """
				{"schema": 1, "id": "%s", "titleKo": "길", "titleDe": "Pfad", "tier": 1,
				 "steps": [{"id": "s1", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}]}
				""".formatted(id);
	}

	private void write(String name, String content) throws Exception {
		Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
	}

	private OverlayReloader<List<String>> reloader(Executor background, Executor main,
													java.util.function.Consumer<List<String>> sink,
													AtomicReference<String> parseThread) {
		return new OverlayReloader<>(dir, new QuestlineOverlay(Set.of(), LOG), List.of(),
				trails -> {
					parseThread.set(Thread.currentThread().getName());
					return trails.stream().map(Questline::id).toList();
				}, background, main, sink, LOG);
	}

	@Test
	void parsesOnTheBackgroundExecutorAndAppliesOnTheMainOne() throws Exception {
		Queued background = new Queued();
		Queued main = new Queued();
		AtomicReference<String> parseThread = new AtomicReference<>();
		AtomicReference<String> applyThread = new AtomicReference<>();
		List<List<String>> applied = new ArrayList<>();
		OverlayReloader<List<String>> reloader = reloader(background, main, result -> {
			applyThread.set(Thread.currentThread().getName());
			applied.add(result);
		}, parseThread);

		write("a.json", json("a"));
		reloader.request();
		assertEquals(1, background.tasks.size(), "exactly one background task queued");
		assertTrue(applied.isEmpty(), "request() itself does no work");

		ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "parse-worker"));
		try {
			worker.submit(background::drain).get(5, TimeUnit.SECONDS);
		} finally {
			worker.shutdown();
		}
		assertEquals("parse-worker", parseThread.get());
		assertTrue(applied.isEmpty(), "the result waits for the main thread");
		assertEquals(1, main.tasks.size());

		main.drain();
		assertEquals(List.of(List.of("a")), applied);
		assertEquals(Thread.currentThread().getName(), applyThread.get());
		assertNotEquals(parseThread.get(), applyThread.get());
	}

	@Test
	void requestsWhileQueuedAreFoldedAndAnUnchangedFolderAppliesNothing() throws Exception {
		Queued background = new Queued();
		Queued main = new Queued();
		AtomicInteger finishes = new AtomicInteger();
		List<List<String>> applied = new ArrayList<>();
		OverlayReloader<List<String>> reloader = new OverlayReloader<>(dir, new QuestlineOverlay(Set.of(), LOG),
				List.of(), trails -> {
			finishes.incrementAndGet();
			return trails.stream().map(Questline::id).toList();
		}, background, main, applied::add, LOG);

		write("a.json", json("a"));
		reloader.request();
		reloader.request();
		reloader.request();
		assertEquals(1, background.tasks.size(), "one pass, not three");
		background.drain();
		main.drain();
		assertEquals(1, finishes.get(), "the repeated passes found no change");
		assertEquals(1, applied.size());

		reloader.request();
		background.drain();
		assertEquals(0, main.drain(), "nothing changed, nothing applied");
	}

	@Test
	void fullReloadAppliesEvenWithoutChanges() throws Exception {
		Queued background = new Queued();
		Queued main = new Queued();
		List<List<String>> applied = new ArrayList<>();
		OverlayReloader<List<String>> reloader = reloader(background, main, applied::add, new AtomicReference<>());
		write("a.json", json("a"));
		reloader.request();
		background.drain();
		main.drain();
		reloader.requestFull();
		background.drain();
		main.drain();
		assertEquals(2, applied.size());
	}

	@Test
	void closedReloaderDropsItsResult() throws Exception {
		Queued background = new Queued();
		Queued main = new Queued();
		List<List<String>> applied = new ArrayList<>();
		OverlayReloader<List<String>> reloader = reloader(background, main, applied::add, new AtomicReference<>());
		write("a.json", json("a"));
		reloader.request();
		background.drain();
		reloader.close();
		main.drain();
		assertTrue(applied.isEmpty());
	}

	@Test
	void watcherFiresOnceAfterABurstOfChanges() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch first = new CountDownLatch(1);
		try (QuestlineWatcher watcher = QuestlineWatcher.start(dir, 300, () -> {
			calls.incrementAndGet();
			first.countDown();
		}, LOG)) {
			assertTrue(watcher != null);
			for (int i = 0; i < 5; i++) {
				write("burst" + i + ".json", json("burst" + i));
				Thread.sleep(40);
			}
			assertTrue(first.await(5, TimeUnit.SECONDS), "the watcher never reported");
			Thread.sleep(700);
			assertEquals(1, calls.get(), "a burst is one callback");

			write("later.json", json("later"));
			long deadline = System.currentTimeMillis() + 5000;
			while (calls.get() < 2 && System.currentTimeMillis() < deadline) {
				Thread.sleep(25);
			}
			assertEquals(2, calls.get());
		}
	}

	@Test
	void watcherIgnoresFilesThatAreNotTrails() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		try (QuestlineWatcher watcher = QuestlineWatcher.start(dir, 100, calls::incrementAndGet, LOG)) {
			assertTrue(watcher != null);
			write("notes.txt", "x");
			write("_template.json", "{}");
			write("index.json", "[]");
			Thread.sleep(600);
			assertEquals(0, calls.get());
		}
	}
}
