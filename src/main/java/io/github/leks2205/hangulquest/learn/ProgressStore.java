package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.migrate.Aliases;
import io.github.leks2205.hangulquest.learn.migrate.ProgressMigrations;
import io.github.leks2205.hangulquest.util.JsonIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads and saves progress.json. Learner progress must survive every update, so:
 * writes are atomic and forced to disk, fields this build does not know are written back unchanged,
 * a file from a newer version is never overwritten, corrupt files are moved aside instead of replaced,
 * and a daily and a weekly backup are kept in {@code backups/}. Older schemas are brought up to date on
 * the raw JSON ({@link ProgressMigrations}) before they are bound, after a copy of the old file.
 */
public final class ProgressStore {
	static final int DAILY_BACKUPS = 7;
	static final int WEEKLY_BACKUPS = 4;

	private final Path file;
	private final Logger log;
	private final ProgressMigrations migrations = ProgressMigrations.standard();
	private final Aliases aliases;
	private ProgressData data;
	/** The tree last read from or written to disk; source of the fields this build does not know. */
	private JsonObject onDisk;
	private boolean readOnly;
	private boolean dirty;
	private long lastFlush;

	public ProgressStore(Path dir, Logger log) {
		this(dir, log, Aliases.bundled());
	}

	public ProgressStore(Path dir, Logger log, Aliases aliases) {
		this.file = dir.resolve("progress.json");
		this.log = log;
		this.aliases = aliases;
		this.data = load();
	}

	public ProgressData data() {
		return data;
	}

	public Path file() {
		return file;
	}

	/** True when progress.json was written by a newer version; nothing is saved then. */
	public boolean readOnly() {
		return readOnly;
	}

	public void markDirty() {
		dirty = true;
	}

	public void tick(long now) {
		if (dirty && now - lastFlush > 2000) {
			flush();
		}
	}

	public void flush() {
		if (!dirty) {
			return;
		}
		dirty = false;
		if (readOnly) {
			return;
		}
		try {
			JsonObject tree = JsonIO.GSON.toJsonTree(data).getAsJsonObject();
			if (onDisk != null) {
				keepUnknown(onDisk, tree, ProgressData.class);
			}
			JsonIO.writeCompact(file, tree);
			onDisk = tree;
			lastFlush = System.currentTimeMillis();
		} catch (IOException failed) {
			dirty = true;
			log.warn("could not save progress to {}: {}", file, failed.toString());
		}
	}

	private ProgressData load() {
		if (!Files.exists(file)) {
			return fresh();
		}
		JsonObject tree;
		int version;
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (text.startsWith("\uFEFF")) {
				text = text.substring(1);
			}
			tree = JsonParser.parseString(text).getAsJsonObject();
			version = ProgressMigrations.versionOf(tree);
		} catch (IOException | RuntimeException broken) {
			log.warn("progress.json is unreadable ({}), keeping it aside", broken.toString());
			quarantine();
			return fresh();
		}
		rotateBackups();
		if (version > ProgressData.CURRENT_VERSION) {
			readOnly = true;
			log.warn("progress.json has schema v{}, this build knows v{}: progress is read-only until you update the mod",
					version, ProgressData.CURRENT_VERSION);
			try {
				ProgressData newer = normalized(bind(tree));
				onDisk = tree;
				return newer;
			} catch (RuntimeException broken) {
				log.warn("could not read progress v{}, leaving the file alone: {}", version, broken.toString());
				return new ProgressData();
			}
		}
		if (version < ProgressData.CURRENT_VERSION) {
			backupBeforeMigration(version);
		}
		try {
			boolean migrated = migrations.run(tree);
			boolean renamed = aliases.apply(tree);
			dirty |= migrated || renamed;
		} catch (RuntimeException failed) {
			// the file on disk stays as it is: better an empty read-only session than a lost save
			readOnly = true;
			log.warn("could not migrate progress v{}, it stays read-only and unchanged: {}", version, failed.toString());
			return new ProgressData();
		}
		ProgressData loaded;
		try {
			loaded = bind(tree);
		} catch (RuntimeException broken) {
			log.warn("progress.json is unreadable ({}), keeping it aside", broken.toString());
			quarantine();
			return fresh();
		}
		onDisk = tree;
		if (version < ProgressData.CURRENT_VERSION) {
			log.info("migrated progress v{} -> v{}", version, ProgressData.CURRENT_VERSION);
		}
		return normalized(loaded);
	}

	private static ProgressData bind(JsonObject tree) {
		ProgressData bound = JsonIO.GSON.fromJson(tree, ProgressData.class);
		if (bound == null) {
			throw new IllegalStateException("empty progress document");
		}
		return bound;
	}

	/** A new install: empty progress with its salt and schema history set. */
	private ProgressData fresh() {
		onDisk = null;
		dirty = true;
		return normalized(new ProgressData());
	}

	/** Fills what a bound document may lack: empty collections, the install salt, the schema history. */
	private ProgressData normalized(ProgressData loaded) {
		loaded.words = loaded.words == null ? new java.util.LinkedHashMap<>() : loaded.words;
		loaded.days = loaded.days == null ? new java.util.LinkedHashMap<>() : loaded.days;
		loaded.achievements = loaded.achievements == null ? new java.util.LinkedHashMap<>() : loaded.achievements;
		loaded.quests = loaded.quests == null ? new ProgressData.QuestData() : loaded.quests;
		loaded.quests.completed = loaded.quests.completed == null ? new java.util.LinkedHashMap<>() : loaded.quests.completed;
		loaded.quests.saved = loaded.quests.saved == null ? new java.util.LinkedHashMap<>() : loaded.quests.saved;
		loaded.quests.badges = loaded.quests.badges == null ? new ArrayList<>() : loaded.quests.badges;
		loaded.quests.history = loaded.quests.history == null ? new ArrayList<>() : loaded.quests.history;
		loaded.vocab = loaded.vocab == null ? new ProgressData.VocabData() : loaded.vocab;
		loaded.requests = loaded.requests == null ? new ProgressData.RequestData() : loaded.requests;
		loaded.requests.open = loaded.requests.open == null ? new ArrayList<>() : loaded.requests.open;
		loaded.requests.open.removeIf(open -> open == null || open.id == null || open.villager == null);
		loaded.requests.finished = loaded.requests.finished == null ? new java.util.LinkedHashMap<>()
				: loaded.requests.finished;
		loaded.hvpt = loaded.hvpt == null ? new ProgressData.HvptData() : loaded.hvpt;
		loaded.hvpt.sessions = loaded.hvpt.sessions == null ? new java.util.LinkedHashMap<>() : loaded.hvpt.sessions;
		loaded.hvpt.contrasts = loaded.hvpt.contrasts == null ? new java.util.LinkedHashMap<>() : loaded.hvpt.contrasts;
		loaded.totals = loaded.totals == null ? new ProgressData.Totals() : loaded.totals;
		loaded.meta = loaded.meta == null ? new ProgressData.Meta() : loaded.meta;
		if (!readOnly && loaded.version == ProgressData.CURRENT_VERSION) {
			if (loaded.meta.installSalt == null || loaded.meta.installSalt.isBlank()) {
				loaded.meta.installSalt = ProgressMigrations.newSalt();
				dirty = true;
			}
			if (loaded.meta.schemaHistory == null) {
				loaded.meta.schemaHistory = new ArrayList<>();
			}
			if (!loaded.meta.schemaHistory.contains(ProgressData.CURRENT_VERSION)) {
				loaded.meta.schemaHistory.add(ProgressData.CURRENT_VERSION);
				dirty = true;
			}
		}
		return loaded;
	}

	private void backupBeforeMigration(int version) {
		try {
			Path backup = file.resolveSibling("progress.v" + version + "-backup-" + LocalStamp.now() + ".json");
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			log.info("backed up progress v{} as {}", version, backup.getFileName());
		} catch (IOException failed) {
			log.warn("could not back up progress before migrating: {}", failed.toString());
		}
	}

	private void quarantine() {
		try {
			Path broken = file.resolveSibling("progress.json.broken-" + LocalStamp.now());
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			log.warn("moved corrupt progress to {}", broken.getFileName());
		} catch (IOException failed) {
			log.warn("could not quarantine corrupt progress: {}", failed.toString());
		}
	}

	public void backup(String suffix) {
		if (!Files.exists(file)) {
			return;
		}
		try {
			Files.copy(file, file.resolveSibling("progress.json.backup-" + suffix + "-" + LocalStamp.now()),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException failed) {
			log.warn("backup failed: {}", failed.toString());
		}
	}

	/** Copies the file as loaded into backups/ once per day and once per ISO week, and prunes old copies. */
	private void rotateBackups() {
		Path dir = file.resolveSibling("backups");
		LocalDate today = LocalDate.now();
		String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W"
				+ String.format("%02d", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
		try {
			Files.createDirectories(dir);
			copyOnce(dir.resolve("progress-day-" + today + ".json"));
			copyOnce(dir.resolve("progress-week-" + week + ".json"));
			prune(dir, "progress-day-", DAILY_BACKUPS);
			prune(dir, "progress-week-", WEEKLY_BACKUPS);
		} catch (IOException failed) {
			log.warn("could not rotate progress backups: {}", failed.toString());
		}
	}

	private void copyOnce(Path target) throws IOException {
		if (!Files.exists(target)) {
			Files.copy(file, target);
		}
	}

	private static void prune(Path dir, String prefix, int keep) throws IOException {
		List<Path> copies;
		try (Stream<Path> listing = Files.list(dir)) {
			copies = listing.filter(path -> path.getFileName().toString().startsWith(prefix))
					.sorted()
					.toList();
		}
		for (int i = 0; i < copies.size() - keep; i++) {
			Files.deleteIfExists(copies.get(i));
		}
	}

	/**
	 * Copies every field of {@code old} that {@code type} does not declare into {@code fresh}, recursing
	 * into known objects and into the entries of maps that still exist. Declared fields are never copied
	 * back, so a value that was cleared on purpose (a finished quest, a removed word) stays cleared.
	 */
	static void keepUnknown(JsonObject old, JsonObject fresh, Type type) {
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == Map.class) {
			Type valueType = parameterized.getActualTypeArguments()[1];
			for (Map.Entry<String, JsonElement> entry : old.entrySet()) {
				JsonElement now = fresh.get(entry.getKey());
				if (now != null && now.isJsonObject() && entry.getValue().isJsonObject()) {
					keepUnknown(entry.getValue().getAsJsonObject(), now.getAsJsonObject(), valueType);
				}
			}
			return;
		}
		if (!(type instanceof Class<?> cls) || !cls.getName().startsWith("io.github.leks2205.hangulquest.")) {
			return;
		}
		Set<String> declared = new HashSet<>();
		for (Field field : cls.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
				continue;
			}
			declared.add(field.getName());
			JsonElement before = old.get(field.getName());
			JsonElement now = fresh.get(field.getName());
			if (before != null && now != null && before.isJsonObject() && now.isJsonObject()) {
				keepUnknown(before.getAsJsonObject(), now.getAsJsonObject(), field.getGenericType());
			}
		}
		for (Map.Entry<String, JsonElement> entry : old.entrySet()) {
			if (!declared.contains(entry.getKey()) && !fresh.has(entry.getKey())) {
				fresh.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
	}

	private static final class LocalStamp {
		static String now() {
			return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		}
	}
}
