package io.github.leks2205.hangulquest.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.Questline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The shipped dialogue files as the tests see them: read from the source tree, like the other content tests. */
public final class DialogueFixtures {
	public static final Path DIR = Path.of("src/main/resources/assets/hangulquest/dialogue");
	private static final Path LANG_DIR = Path.of("src/main/resources/assets/hangulquest/lang");
	private static final Path GRAMMAR = Path.of("src/main/resources/assets/hangulquest/data/grammar.json");

	private DialogueFixtures() {
	}

	/** Dialogue file id to bytes; the word list is not a dialogue. */
	public static Map<String, byte[]> files() {
		Map<String, byte[]> files = new TreeMap<>();
		try (Stream<Path> listing = Files.list(DIR)) {
			for (Path file : listing.toList()) {
				String name = file.getFileName().toString();
				if (DialogueResources.isDialogueFile(name)) {
					files.put(name.substring(0, name.length() - ".json".length()), Files.readAllBytes(file));
				}
			}
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
		return files;
	}

	/** Every shipped dialogue, parsed; fails the test on a parse problem. */
	public static List<Dialogue> shipped() {
		List<Dialogue> dialogues = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : files().entrySet()) {
			DialogueParser.Parsed parsed = DialogueParser.parse(file.getKey(), file.getValue());
			if (!parsed.problems().isEmpty() || parsed.dialogue() == null) {
				throw new AssertionError(file.getKey() + ": " + parsed.problems());
			}
			dialogues.add(parsed.dialogue());
		}
		return dialogues;
	}

	/** The nodes of a dialogue in the order of their ids, so dumps are stable. */
	public static Map<String, Dialogue.Node> sorted(Dialogue dialogue) {
		return new TreeMap<>(dialogue.nodes());
	}

	public static DialogueLexicon lexicon() {
		try {
			return DialogueLexicon.parse(Files.readString(DIR.resolve("_lexicon.json"), StandardCharsets.UTF_8));
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	public static Set<String> grammarIds() {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(GRAMMAR, StandardCharsets.UTF_8)).getAsJsonObject();
			Set<String> ids = new LinkedHashSet<>();
			for (JsonElement construction : root.getAsJsonArray("constructions")) {
				ids.add(construction.getAsJsonObject().get("id").getAsString());
			}
			return ids;
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	public static Set<String> trailIds() {
		return QuestLoader.loadAll().stream().map(Questline::id).collect(Collectors.toSet());
	}

	/** A language file as flat key to text. */
	public static Map<String, String> lang(String code) {
		try {
			JsonObject object = JsonParser.parseString(
					Files.readString(LANG_DIR.resolve(code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> out = new LinkedHashMap<>();
			object.entrySet().forEach(entry -> out.put(entry.getKey(), entry.getValue().getAsString()));
			return out;
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	/** The checks the game makes when it loads, against the real trails, grammar ids, catalog and English file. */
	public static DialogueValidator.Env env(Catalog catalog) {
		Map<String, String> english = lang("en_us");
		return new DialogueValidator.Env(trailIds(), grammarIds(), id -> catalog.byId(id).isPresent(),
				english::containsKey);
	}
}
