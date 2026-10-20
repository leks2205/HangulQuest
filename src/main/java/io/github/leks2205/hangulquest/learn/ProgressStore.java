package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.util.JsonIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads/saves progress.json with atomic writes, debouncing and corruption handling. */
public final class ProgressStore {
	private final Path file;
	private final Logger log;
	private ProgressData data;
	private boolean dirty;
	private long lastFlush;

	public ProgressStore(Path dir, Logger log) {
		this.file = dir.resolve("progress.json");
		this.log = log;
		this.data = load();
	}

	public ProgressData data() {
		return data;
	}

	public Path file() {
		return file;
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
		try {
			JsonIO.write(file, data);
			dirty = false;
			lastFlush = System.currentTimeMillis();
		} catch (IOException failed) {
			log.warn("could not save progress to {}: {}", file, failed.toString());
		}
	}

	private ProgressData load() {
		if (!Files.exists(file)) {
			return new ProgressData();
		}
		ProgressData loaded = null;
		try {
			loaded = JsonIO.read(file, ProgressData.class);
		} catch (IOException | RuntimeException broken) {
			log.warn("progress.json is unreadable ({}), keeping it aside", broken.toString());
			quarantine();
		}
		if (loaded == null) {
			return new ProgressData();
		}
		migrate(loaded);
		loaded.quests = loaded.quests == null ? new ProgressData.QuestData() : loaded.quests;
		loaded.vocab = loaded.vocab == null ? new ProgressData.VocabData() : loaded.vocab;
		loaded.totals = loaded.totals == null ? new ProgressData.Totals() : loaded.totals;
		loaded.meta = loaded.meta == null ? new ProgressData.Meta() : loaded.meta;
		return loaded;
	}

	private void migrate(ProgressData loaded) {
		if (loaded.version >= 2) {
			return;
		}
		try {
			Path backup = file.resolveSibling("progress.v" + loaded.version + "-backup-"
					+ LocalStamp.now() + ".json");
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			log.info("migrated progress v{} -> v2 (backup {})", loaded.version, backup.getFileName());
		} catch (IOException ignored) {
			// still migrate in memory
		}
		loaded.version = 2;
		dirty = true;
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

	private static final class LocalStamp {
		static String now() {
			return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		}
	}
}
