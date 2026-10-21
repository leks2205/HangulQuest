package io.github.leks2205.hangulquest.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds dialogue files in the resource manager, so the mod's own files and a resource pack are read
 * the same way. Files whose name starts with an underscore (the word list) are not dialogues.
 */
public final class DialogueResources {
	/** Folders searched in every namespace. */
	public static final List<String> FOLDERS = List.of("dialogue", "hangulquest/dialogue");
	private static final String OWN = "hangulquest";
	private static final Identifier LEXICON = Identifier.fromNamespaceAndPath(OWN, "dialogue/_lexicon.json");
	private static final Identifier GRAMMAR = Identifier.fromNamespaceAndPath(OWN, "data/grammar.json");

	private DialogueResources() {
	}

	/** Dialogue file id (the name without .json) to bytes. The mod's own namespace wins over a pack's. */
	public static Map<String, byte[]> read(ResourceManager manager, Logger log) {
		Map<String, byte[]> files = new TreeMap<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String folder : FOLDERS) {
			Map<Identifier, Resource> listed = manager.listResources(folder, id -> isDialogueFile(id.getPath()));
			List<Map.Entry<Identifier, Resource>> ordered = listed.entrySet().stream()
					.sorted(Comparator.comparing((Map.Entry<Identifier, Resource> entry) ->
							!entry.getKey().getNamespace().equals(OWN)).thenComparing(entry -> entry.getKey().toString()))
					.toList();
			for (Map.Entry<Identifier, Resource> entry : ordered) {
				String name = entry.getKey().getPath();
				String id = name.substring(name.lastIndexOf('/') + 1, name.length() - ".json".length());
				if (!seen.add(id)) {
					continue;
				}
				try (InputStream stream = entry.getValue().open()) {
					files.put(id, stream.readAllBytes());
				} catch (Exception unreadable) {
					log.warn("dialogue resource {} could not be read: {}", entry.getKey(), unreadable.toString());
				}
			}
		}
		return files;
	}

	public static boolean isDialogueFile(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.endsWith(".json") && !name.startsWith("_");
	}

	/** The word list for coverage and hover glosses; empty when it is missing or broken. */
	public static DialogueLexicon lexicon(ResourceManager manager, Logger log) {
		try {
			Optional<Resource> resource = manager.getResource(LEXICON);
			if (resource.isPresent()) {
				try (InputStream stream = resource.get().open()) {
					return DialogueLexicon.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		} catch (Exception broken) {
			log.warn("dialogue word list not loaded: {}", broken.toString());
		}
		return DialogueLexicon.EMPTY;
	}

	/** The construction ids of grammar.json, to check the grammar points a dialogue names. */
	public static Set<String> grammarIds(ResourceManager manager, Logger log) {
		Set<String> ids = new LinkedHashSet<>();
		try {
			Optional<Resource> resource = manager.getResource(GRAMMAR);
			if (resource.isPresent()) {
				try (InputStream stream = resource.get().open()) {
					JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
							.getAsJsonObject();
					for (JsonElement construction : root.getAsJsonArray("constructions")) {
						ids.add(construction.getAsJsonObject().get("id").getAsString());
					}
				}
			}
		} catch (Exception broken) {
			log.warn("grammar ids not loaded: {}", broken.toString());
		}
		return ids;
	}
}
