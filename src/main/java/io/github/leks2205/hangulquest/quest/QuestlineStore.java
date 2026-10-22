package io.github.leks2205.hangulquest.quest;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Questline rules: which files count, how shipped content and the local overlay folder
 * {@code config/hangulquest/questlines/} combine. The shipped trails come from the mod's resources
 * (and any resource pack); the folder only holds a template, a README and the player's own files.
 */
public final class QuestlineStore {
	public static final String README = """
			# Questlines

			Every file in this folder is one questline (JSON). The mod watches the folder: new or changed
			files appear in the journal after a moment, without a restart.

			- The file name does not matter; the "id" field does.
			- The shipped questlines live inside the mod (and in resource packs under
			  assets/<namespace>/hangulquest/questlines/), not in this folder.
			- A file with the "id" of a shipped questline is ignored unless it says "override": true,
			  so content fixes always reach the game. With "override": true it replaces the shipped one.
			- A new "id" adds a new questline; prerequisites go in "requires.completed".
			- English and German text goes inline in the file: "titleEn"/"titleDe", "clue"/"hint" with
			  "en"/"de", and "textEn"/"textDe" in "finale". The shipped questlines keep this text in the
			  mod's language files instead; an inline field always wins over those.
			- Files starting with "_" are ignored. Template: _template.json.
			- Broken files are logged and ignored; the shipped version stays active.

			Deutsch: Jede Datei hier ist eine Questlinie. Neue oder geänderte Dateien erscheinen ohne
			Neustart im Journal. Gleiche "id" wie eine mitgelieferte Linie braucht "override": true.
			""";

	/** Shipped trails (mod resources plus resource packs) and the digests of their files. */
	public record Shipped(List<Questline> trails, Set<String> digests) {
	}

	private QuestlineStore() {
	}

	/** True for the files that hold a trail: not the index, not the {@code _}-prefixed templates. */
	public static boolean isTrailFile(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.endsWith(".json") && !name.equals("index.json") && !name.startsWith("_");
	}

	/** Writes the template and README into the overlay folder (never overwrites an existing file). */
	public static void exportShipped(Path directory, Logger log) {
		try {
			Files.createDirectories(directory);
			Path readme = directory.resolve("README.md");
			if (!Files.exists(readme)) {
				Files.writeString(readme, README, StandardCharsets.UTF_8);
			}
			Path target = directory.resolve("_template.json");
			if (!Files.exists(target)) {
				try (InputStream stream = QuestlineStore.class
						.getResourceAsStream(QuestLoader.BASE + "_template.json")) {
					if (stream != null) {
						Files.copy(stream, target);
					}
				}
			}
		} catch (Exception failed) {
			log.warn("could not write the questline template to {}: {}", directory, failed.toString());
		}
	}

	/**
	 * Parses shipped questline files (name to bytes). Files with the same trail id replace each
	 * other, the later one wins; broken files are logged and skipped.
	 */
	public static Shipped readShipped(Map<String, byte[]> files, Logger log) {
		Map<String, Questline> byId = new LinkedHashMap<>();
		Set<String> digests = new HashSet<>();
		for (Map.Entry<String, byte[]> entry : files.entrySet()) {
			if (!isTrailFile(entry.getKey())) {
				continue;
			}
			QuestFile file = QuestFile.read(entry.getKey(), entry.getValue());
			digests.add(file.digest());
			if (file.trail() == null) {
				log.warn("questline {} is unreadable: {}", entry.getKey(), file.error());
				continue;
			}
			byId.put(file.trail().id(), file.trail());
		}
		return new Shipped(new ArrayList<>(byId.values()), digests);
	}

	/**
	 * Shipped trails combined with the overlay files. A file whose id is new adds a trail; a file
	 * with a shipped id is ignored unless it says {@code "override": true}. Copies that are
	 * identical to a shipped file and broken files are skipped.
	 */
	public static List<Questline> merge(List<Questline> shippedTrails, List<QuestFile> overlay, Logger log) {
		Map<String, Questline> byId = new LinkedHashMap<>();
		for (Questline trail : shippedTrails) {
			byId.put(trail.id(), trail);
		}
		Map<String, Questline> shippedById = new LinkedHashMap<>(byId);
		for (QuestFile file : overlay) {
			if (file.identical()) {
				continue;
			}
			if (file.error() != null) {
				if ("no id".equals(file.error())) {
					log.warn("questline {} has no id, ignored", file.name());
				} else {
					log.warn("questline {} is unreadable, keeping shipped version: {}", file.name(),
							file.error());
				}
				continue;
			}
			Questline trail = file.trail();
			Questline shipped = shippedById.get(trail.id());
			if (shipped != null && !file.override()) {
				log.info("questline {} is a copy of shipped content; ignored "
						+ "(add \"override\": true to edit it)", file.name());
				continue;
			}
			// Opted-in overrides keep their edited steps, but always merge the shipped
			// parent links and structural level so the tree cannot collapse.
			if (shipped != null) {
				LinkedHashSet<String> parents = new LinkedHashSet<>(shipped.requires().completed());
				parents.addAll(trail.requires().completed());
				Questline.Requires requires = new Questline.Requires(
						shipped.requires().minWordsKnown(),
						shipped.requires().dimension(),
						new ArrayList<>(parents));
				trail = trail.withRequires(requires)
						.withTopic(file.hasTopic() ? trail.topic() : shipped.topic())
						.withLevels(shipped.tier(), shipped.koreanLevel())
						.withAdaptive(false);
			}
			byId.put(trail.id(), trail);
		}
		return new ArrayList<>(byId.values());
	}

	/** Shipped trails from the classpath overridden by the folder, read synchronously (tools and tests). */
	public static List<Questline> loadAll(Path directory, Logger log) {
		Shipped shipped = readShipped(QuestLoader.classpathFiles(), log);
		QuestlineOverlay overlay = new QuestlineOverlay(shipped.digests(), log);
		overlay.refresh(directory);
		return merge(shipped.trails(), overlay.files(), log);
	}

	/** Number of trail JSON files currently in the folder. */
	public static int fileCount(Path directory) {
		int count = 0;
		if (directory == null || !Files.isDirectory(directory)) {
			return 0;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				if (isTrailFile(file.getFileName().toString())) {
					count++;
				}
			}
		} catch (IOException ignored) {
			return count;
		}
		return count;
	}
}
