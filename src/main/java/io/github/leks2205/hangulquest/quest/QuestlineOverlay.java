package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The parsed content of {@code config/hangulquest/questlines/}. A refresh only reads and parses
 * files whose modification time or size changed since the last one, so editing one trail does not
 * re-parse the folder. Files that are byte-identical to a shipped file (old exported copies) are
 * recognised by digest and never parsed.
 */
public final class QuestlineOverlay {
	private record Entry(long mtime, long size, QuestFile file) {
	}

	private final Set<String> shippedDigests;
	private final Logger log;
	private final Map<String, Entry> cache = new HashMap<>();
	private int parsed;

	public QuestlineOverlay(Set<String> shippedDigests, Logger log) {
		this.shippedDigests = shippedDigests;
		this.log = log;
	}

	/** Brings the cache up to date with the folder; true when a file was added, changed or removed. */
	public synchronized boolean refresh(Path directory) {
		Map<String, BasicFileAttributes> present = new TreeMap<>();
		Map<String, Path> paths = new HashMap<>();
		if (directory != null && Files.isDirectory(directory)) {
			try (Stream<Path> stream = Files.find(directory, 1, (path, attrs) -> attrs.isRegularFile()
					&& QuestlineStore.isTrailFile(path.getFileName().toString()))) {
				stream.forEach(path -> {
					try {
						String name = path.getFileName().toString();
						present.put(name, Files.readAttributes(path, BasicFileAttributes.class));
						paths.put(name, path);
					} catch (IOException vanished) {
						// deleted between listing and stat: the next refresh sees it gone
					}
				});
			} catch (IOException | UncheckedIOException failed) {
				log.warn("could not read questline folder {}: {}", directory, failed.toString());
				return false;
			}
		}
		boolean changed = cache.keySet().removeIf(name -> !present.containsKey(name));
		for (Map.Entry<String, BasicFileAttributes> found : present.entrySet()) {
			String name = found.getKey();
			long mtime = found.getValue().lastModifiedTime().toMillis();
			long size = found.getValue().size();
			Entry known = cache.get(name);
			if (known != null && known.mtime == mtime && known.size == size) {
				continue;
			}
			try {
				byte[] bytes = Files.readAllBytes(paths.get(name));
				String digest = QuestFile.digest(bytes);
				QuestFile file;
				if (shippedDigests.contains(digest)) {
					file = QuestFile.identical(name, digest);
				} else {
					file = QuestFile.read(name, bytes);
					parsed++;
				}
				cache.put(name, new Entry(mtime, size, file));
				changed = true;
			} catch (IOException unreadable) {
				log.warn("questline {} could not be read: {}", name, unreadable.toString());
			}
		}
		return changed;
	}

	/** The cached files, sorted by name. */
	public synchronized List<QuestFile> files() {
		List<QuestFile> files = new ArrayList<>();
		cache.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> files.add(entry.getValue().file));
		return files;
	}

	/** Forgets everything, so the next refresh re-reads the whole folder. */
	public synchronized void invalidate() {
		cache.clear();
	}

	/** How many files were parsed so far (identical copies are not counted); for tests and logs. */
	public synchronized int parsedCount() {
		return parsed;
	}
}
