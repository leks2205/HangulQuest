package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads curated concepts from the mod jar. */
public final class ConceptLoader {
	public static final String RESOURCE = "/assets/hangulquest/data/concepts.json";

	private ConceptLoader() {
	}

	public static List<Concept> load() {
		List<Concept> concepts = new ArrayList<>();
		try (InputStream stream = ConceptLoader.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				return concepts;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
				for (JsonElement element : array) {
					Concept concept = JsonIOHolder.GSON.fromJson(element, Concept.class);
					if (concept != null && concept.isValid()) {
						if (concept.aliases == null) {
							concept.aliases = new ArrayList<>();
						}
						concepts.add(concept);
					}
				}
			}
		} catch (IOException | RuntimeException broken) {
			throw new IllegalStateException("concepts.json is unreadable", broken);
		}
		return concepts;
	}

	private static final class JsonIOHolder {
		static final com.google.gson.Gson GSON = io.github.leks2205.hangulquest.util.JsonIO.GSON;
	}
}
