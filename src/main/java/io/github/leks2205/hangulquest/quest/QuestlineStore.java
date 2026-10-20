package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Questlines live as one JSON file per trail in {@code config/hangulquest/questlines/}.
 * The shipped trails are exported there on first start; files with the same id override
 * the shipped version, new ids are added. The folder is re-read while the game runs.
 */
public final class QuestlineStore {
	public static final String README = """
			# Questlinien

			Jede Datei ist eine Questlinie (JSON). Der Mod liest diesen Ordner alle paar Sekunden neu:
			neue oder geänderte Dateien erscheinen ohne Neustart im Journal.

			- Dateiname egal, entscheidend ist das Feld "id".
			- Gleiche "id" wie eine mitgelieferte Linie: die Datei wird ignoriert, solange sie nicht
			  ausdrücklich "override": true enthält. So erreichen Inhalts-Fixes immer das Spiel.
			- "override": true + gleiche "id" = mitgelieferte Linie wird überschrieben.
			- Neue "id" = neue Linie; Voraussetzungen über "requires.completed".
			- "index.json" und Dateien, die mit "_" beginnen, werden ignoriert.
			- Vorlage: _template.json; Schema: docs/questline.schema.json im Projekt.

			Kaputte Dateien werden geloggt und ignoriert, die mitgelieferte Version bleibt aktiv.
			""";

	private QuestlineStore() {
	}

	/** Writes missing shipped trails/template/README into the folder (never overwrites edits). */
	public static void exportShipped(Path directory, Logger log) {
		try {
			Files.createDirectories(directory);
			Path readme = directory.resolve("README.md");
			if (!Files.exists(readme)) {
				Files.writeString(readme, README, StandardCharsets.UTF_8);
			}
			List<String> files = new ArrayList<>(QuestLoader.index());
			files.add("_template.json");
			for (String name : files) {
				Path target = directory.resolve(name);
				if (Files.exists(target)) {
					continue;
				}
				try (InputStream stream = QuestlineStore.class
						.getResourceAsStream(QuestLoader.BASE + name)) {
					if (stream != null) {
						Files.copy(stream, target);
					}
				}
			}
		} catch (Exception failed) {
			log.warn("could not export shipped questlines to {}: {}", directory, failed.toString());
		}
	}

	/** Shipped trails overridden by the JSON files in the folder. */
	public static List<Questline> loadAll(Path directory, Logger log) {
		Map<String, Questline> byId = new LinkedHashMap<>();
		for (Questline trail : QuestLoader.loadAll()) {
			byId.put(trail.id(), trail);
		}
		if (directory == null || !Files.isDirectory(directory)) {
			return new ArrayList<>(byId.values());
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				if (name.equals("index.json") || name.startsWith("_")) {
					continue;
				}
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
					Questline trail = QuestLoader.parse(root);
					if (trail == null) {
						log.warn("questline {} has no id, ignored", name);
						continue;
					}
					Questline shipped = byId.get(trail.id());
					boolean optIn = root.has("override")
							&& root.get("override").getAsBoolean();
					if (shipped != null && !optIn) {
						log.info("questline {} is an exported copy of shipped content; ignored "
								+ "(add \"override\": true to edit it)", name);
						continue;
					}
					// Opted-in overrides keep their edited steps, but always merge the shipped
					// parent links and structural level so the tree cannot collapse.
					if (shipped != null) {
						java.util.LinkedHashSet<String> parents = new java.util.LinkedHashSet<>(
								shipped.requires().completed());
						parents.addAll(trail.requires().completed());
						Questline.Requires requires = new Questline.Requires(
								shipped.requires().minWordsKnown(),
								shipped.requires().dimension(),
								new ArrayList<>(parents));
						trail = new Questline(trail.id(), trail.titleKo(), trail.titleDe(),
								root.has("topic") ? trail.topic() : shipped.topic(),
								shipped.tier(), trail.concepts(), requires,
								trail.steps(), trail.finale(), trail.finaleKo(), trail.finaleDe(),
								trail.badge(), trail.order(), shipped.koreanLevel(), false);
					}
					byId.put(trail.id(), trail);
				} catch (Exception broken) {
					log.warn("questline {} is unreadable, keeping shipped version: {}", name,
							broken.toString());
				}
			}
		} catch (IOException failed) {
			log.warn("could not read questline folder {}: {}", directory, failed.toString());
		}
		return new ArrayList<>(byId.values());
	}

	/** Number of trail JSON files currently in the folder (README/template excluded where sensible). */
	public static int fileCount(Path directory) {
		int count = 0;
		if (directory == null || !Files.isDirectory(directory)) {
			return 0;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				if (!name.equals("index.json") && !name.startsWith("_")) {
					count++;
				}
			}
		} catch (IOException ignored) {
			return count;
		}
		return count;
	}

	/** Cheap fingerprint of the folder: names, mtimes and sizes. */
	public static long snapshot(Path directory) {
		long hash = 1125899906842597L;
		if (directory == null || !Files.isDirectory(directory)) {
			return hash;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
			for (Path file : stream) {
				hash = hash * 31 + file.getFileName().toString().hashCode();
				try {
					hash = hash * 31 + Files.getLastModifiedTime(file).toMillis();
					hash = hash * 31 + Files.size(file);
				} catch (IOException ignored) {
					hash = hash * 31 + 1;
				}
			}
		} catch (IOException ignored) {
			return hash;
		}
		return hash;
	}
}
