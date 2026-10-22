package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loads curated concepts from the mod jar, or from a resource pack that replaces concepts.json. */
public final class ConceptLoader {
	public static final String RESOURCE = "/assets/hangulquest/data/concepts.json";
	public static final String RESOURCE_PATH = "data/concepts.json";

	private ConceptLoader() {
	}

	/** From the classpath (tools and tests). */
	public static List<Concept> load() {
		try (InputStream stream = ConceptLoader.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? new ArrayList<>() : parse(stream);
		} catch (IOException | RuntimeException broken) {
			throw new IllegalStateException("concepts.json is unreadable", broken);
		}
	}

	/** From the resource manager, so a resource pack can replace the file. */
	public static List<Concept> load(ResourceManager manager) {
		try {
			Optional<Resource> resource = manager.getResource(
					Identifier.fromNamespaceAndPath("hangulquest", RESOURCE_PATH));
			if (resource.isEmpty()) {
				return load();
			}
			try (InputStream stream = resource.get().open()) {
				return parse(stream);
			}
		} catch (IOException | RuntimeException broken) {
			throw new IllegalStateException("concepts.json is unreadable", broken);
		}
	}

	private static List<Concept> parse(InputStream stream) throws IOException {
		List<Concept> concepts = new ArrayList<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			for (JsonElement element : array) {
				Concept concept = JsonIOHolder.GSON.fromJson(element, Concept.class);
				if (concept != null) {
					concept.fillText();
				}
				if (concept != null && concept.isValid()) {
					if (concept.aliases == null) {
						concept.aliases = new ArrayList<>();
					}
					concepts.add(concept);
				}
			}
		}
		return concepts;
	}

	private static final class JsonIOHolder {
		static final com.google.gson.Gson GSON = io.github.leks2205.hangulquest.util.JsonIO.GSON;
	}
}
