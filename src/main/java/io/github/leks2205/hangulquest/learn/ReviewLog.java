package io.github.leks2205.hangulquest.learn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.stream.Stream;

/**
 * An append-only record of every review and encounter, one JSON object per line, in the profile
 * folder next to progress.json. It stays on this computer and is never sent anywhere. It is the second
 * safety net for progress: {@link #replay} rebuilds the FSRS card states from it.
 *
 * <p>Lines are queued and written in batches by a background thread, each batch forced to disk. When
 * the first event of a new month arrives, the file is compressed into
 * {@code reviews-YYYY-MM.ndjson.gz} (named after the month it holds) and a new file is started.
 */
public final class ReviewLog implements AutoCloseable {
	public static final String FILE = "reviews.ndjson";
	public static final int VERSION = 1;
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-reviewlog");
	private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
	private static final Set<ReviewLog> OPEN = Collections.synchronizedSet(
			Collections.newSetFromMap(new WeakHashMap<>()));
	private static boolean hookInstalled;

	/**
	 * One line of the log. {@code grade} is null for exposures; {@code elapsedDays} is the days since the
	 * previous review of the item (-1 for the first); {@code ctx} says where it happened (trail/step/role
	 * or the card source); {@code arm} is reserved for experiments and null for now.
	 */
	public record Event(long t, String item, ReviewKind kind, ReviewMode mode, Integer grade, long latencyMs,
						int elapsedDays, String ctx, String arm, int v) {
		public Event(long t, String item, ReviewKind kind, ReviewMode mode, Integer grade, long latencyMs,
					 int elapsedDays, String ctx) {
			this(t, item, kind, mode, grade, latencyMs, elapsedDays, ctx, null, VERSION);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("t", t);
			json.addProperty("item", item);
			json.addProperty("kind", kind.name());
			json.addProperty("mode", mode.name());
			json.add("grade", grade == null ? JsonNull.INSTANCE : GSON.toJsonTree(grade));
			json.addProperty("latencyMs", latencyMs);
			json.addProperty("elapsedDays", elapsedDays);
			json.add("ctx", ctx == null ? JsonNull.INSTANCE : GSON.toJsonTree(ctx));
			json.add("arm", arm == null ? JsonNull.INSTANCE : GSON.toJsonTree(arm));
			json.addProperty("v", v);
			return json;
		}

		static Event fromJson(JsonObject json) {
			JsonElement grade = json.get("grade");
			JsonElement ctx = json.get("ctx");
			JsonElement arm = json.get("arm");
			return new Event(json.get("t").getAsLong(), json.get("item").getAsString(),
					ReviewKind.valueOf(json.get("kind").getAsString()), ReviewMode.valueOf(json.get("mode").getAsString()),
					grade == null || grade.isJsonNull() ? null : grade.getAsInt(),
					json.has("latencyMs") ? json.get("latencyMs").getAsLong() : -1,
					json.has("elapsedDays") ? json.get("elapsedDays").getAsInt() : -1,
					ctx == null || ctx.isJsonNull() ? null : ctx.getAsString(),
					arm == null || arm.isJsonNull() ? null : arm.getAsString(),
					json.has("v") ? json.get("v").getAsInt() : VERSION);
		}
	}

	private final Path dir;
	private final Clock clock;
	private final boolean enabled;
	private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();
	private final ThreadPoolExecutor writer;
	/** Month of the lines in the current file; touched only by the writer thread. */
	private YearMonth fileMonth;
	private boolean monthKnown;

	private ReviewLog(Path dir, Clock clock, boolean enabled) {
		this.dir = dir;
		this.clock = clock;
		this.enabled = enabled;
		this.writer = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
			Thread thread = new Thread(task, "hangulquest-reviewlog");
			thread.setDaemon(true);
			return thread;
		});
		this.writer.allowCoreThreadTimeOut(true);
	}

	/** A log in the given profile folder. Nothing is created on disk until the first event. */
	public static ReviewLog open(Path profileDir, Clock clock) {
		ReviewLog log = new ReviewLog(profileDir, clock, true);
		OPEN.add(log);
		installHook();
		return log;
	}

	/** A log that drops everything (for tests and read-only sessions). */
	public static ReviewLog disabled() {
		return new ReviewLog(Path.of("."), Clock.systemDefaultZone(), false);
	}

	public Path dir() {
		return dir;
	}

	public Path file() {
		return dir.resolve(FILE);
	}

	/** Queues an event; returns at once. */
	public void append(Event event) {
		if (!enabled || event == null) {
			return;
		}
		queue.add(event);
		writer.execute(this::drain);
	}

	/** Waits until everything queued so far is on disk. */
	public void flush() {
		if (!enabled) {
			return;
		}
		try {
			writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} catch (Exception failed) {
			LOG.warn("could not flush the review log: {}", failed.toString());
		}
	}

	@Override
	public void close() {
		flush();
		OPEN.remove(this);
		writer.shutdown();
	}

	private static synchronized void installHook() {
		if (hookInstalled) {
			return;
		}
		hookInstalled = true;
		Runtime.getRuntime().addShutdownHook(new Thread(ReviewLog::flushAll, "hangulquest-reviewlog-flush"));
	}

	/** Waits for every open log to write what it has queued (shutdown, and test teardown). */
	public static void flushAll() {
		List<ReviewLog> logs;
		synchronized (OPEN) {
			logs = new ArrayList<>(OPEN);
		}
		logs.forEach(ReviewLog::flush);
	}

	// ----------------------------------------------------------------- writer thread

	private void drain() {
		List<Event> batch = new ArrayList<>();
		for (Event event; (event = queue.poll()) != null; ) {
			batch.add(event);
		}
		if (batch.isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(dir);
			if (!monthKnown) {
				fileMonth = monthOfFile();
				monthKnown = true;
			}
			StringBuilder text = new StringBuilder();
			for (Event event : batch) {
				YearMonth month = monthOf(event.t());
				if (fileMonth != null && month.isAfter(fileMonth)) {
					write(text);
					text.setLength(0);
					rotate(fileMonth);
					fileMonth = null;
				}
				if (fileMonth == null) {
					fileMonth = month;
				}
				text.append(GSON.toJson(event.toJson())).append('\n');
			}
			write(text);
		} catch (IOException | RuntimeException failed) {
			LOG.warn("could not write the review log in {}: {}", dir, failed.toString());
		}
	}

	private void write(CharSequence text) throws IOException {
		if (text.length() == 0) {
			return;
		}
		byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
		try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.APPEND)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
			channel.force(true);
		}
	}

	/** Compresses the current file into the archive of its month and removes it. */
	private void rotate(YearMonth month) throws IOException {
		Path current = file();
		if (!Files.isRegularFile(current)) {
			return;
		}
		Path archive = dir.resolve(archiveName(month));
		Path partial = dir.resolve(archiveName(month) + ".tmp");
		try (OutputStream out = Files.newOutputStream(partial)) {
			if (Files.isRegularFile(archive)) {
				Files.copy(archive, out); // gzip members can simply follow each other
			}
			GZIPOutputStream zip = new GZIPOutputStream(out);
			Files.copy(current, zip);
			zip.finish();
		}
		try (FileChannel channel = FileChannel.open(partial, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
		Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		Files.delete(current);
	}

	private YearMonth monthOfFile() {
		Path current = file();
		if (!Files.isRegularFile(current)) {
			return null;
		}
		try (BufferedReader reader = Files.newBufferedReader(current, StandardCharsets.UTF_8)) {
			for (String line; (line = reader.readLine()) != null; ) {
				Event event = parse(line);
				if (event != null) {
					return monthOf(event.t());
				}
			}
		} catch (IOException failed) {
			LOG.warn("could not read {}: {}", current, failed.toString());
		}
		return null;
	}

	private YearMonth monthOf(long t) {
		return YearMonth.from(Instant.ofEpochMilli(t).atZone(clock.getZone()));
	}

	static String archiveName(YearMonth month) {
		return "reviews-" + month + ".ndjson.gz";
	}

	// ----------------------------------------------------------------- reading and replay

	/** Every readable event of a profile folder, oldest archive first; damaged lines are skipped. */
	public static List<Event> readAll(Path profileDir) {
		List<Event> events = new ArrayList<>();
		List<Path> archives = new ArrayList<>();
		if (Files.isDirectory(profileDir)) {
			try (Stream<Path> listing = Files.list(profileDir)) {
				listing.filter(path -> {
					String name = path.getFileName().toString();
					return name.startsWith("reviews-") && name.endsWith(".ndjson.gz");
				}).sorted().forEach(archives::add);
			} catch (IOException failed) {
				LOG.warn("could not list {}: {}", profileDir, failed.toString());
			}
		}
		for (Path archive : archives) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					new GZIPInputStream(Files.newInputStream(archive)), StandardCharsets.UTF_8))) {
				readLines(reader, events);
			} catch (IOException failed) {
				LOG.warn("could not read {}: {}", archive, failed.toString());
			}
		}
		Path current = profileDir.resolve(FILE);
		if (Files.isRegularFile(current)) {
			try (BufferedReader reader = Files.newBufferedReader(current, StandardCharsets.UTF_8)) {
				readLines(reader, events);
			} catch (IOException failed) {
				LOG.warn("could not read {}: {}", current, failed.toString());
			}
		}
		return events;
	}

	private static void readLines(BufferedReader reader, List<Event> into) throws IOException {
		for (String line; (line = reader.readLine()) != null; ) {
			Event event = parse(line);
			if (event != null) {
				into.add(event);
			}
		}
	}

	private static Event parse(String line) {
		if (line.isBlank()) {
			return null;
		}
		try {
			return Event.fromJson(JsonParser.parseString(line).getAsJsonObject());
		} catch (RuntimeException damaged) {
			return null; // a half-written last line after a crash
		}
	}

	/**
	 * Rebuilds the FSRS card of every word from the log: each word event whose mode schedules and that has
	 * a grade is replayed, in log order, through {@code fsrs}. Exposures and guesses among two or three
	 * options change nothing, exactly as they did when they happened. {@code start} may hold cards that
	 * were not made by reviews (seeded from an old level); it is not changed.
	 */
	public static Map<String, CardState> replay(Path profileDir, Fsrs fsrs, Map<String, CardState> start) {
		Map<String, CardState> cards = new LinkedHashMap<>();
		if (start != null) {
			start.forEach((id, card) -> cards.put(id, card.copy()));
		}
		for (Event event : readAll(profileDir)) {
			if (event.kind() != ReviewKind.WORD || event.grade() == null || !event.mode().schedules()
					|| event.grade() < Fsrs.AGAIN || event.grade() > Fsrs.EASY) {
				continue;
			}
			cards.put(event.item(), fsrs.review(cards.getOrDefault(event.item(), new CardState()), event.grade(), event.t()));
		}
		return cards;
	}

	/**
	 * Puts replayed cards into the progress document: existing words get the card (and the level and due
	 * time that follow from it), words missing from progress.json are created from it.
	 */
	public static void restore(ProgressData data, Map<String, CardState> cards) {
		cards.forEach((id, card) -> {
			WordProgress progress = data.words.get(id);
			if (progress == null) {
				progress = new WordProgress();
				progress.introducedAt = card.lastReview;
				progress.lastSeenAt = card.lastReview;
				data.words.put(id, progress);
			}
			progress.fsrs = card.copy();
			progress.level = Srs.levelFor(card.stability);
			if (!progress.known) {
				progress.dueAt = card.due;
			}
		});
	}
}
