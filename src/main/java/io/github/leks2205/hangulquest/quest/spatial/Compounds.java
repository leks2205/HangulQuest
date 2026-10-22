package io.github.leks2205.hangulquest.quest.spatial;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Material plus structure, spelled as the dictionary does: 돌담, 돌기둥 are one word each, 조약돌 담 and
 * 석재 벽돌 기둥 are two or more. The words that close up are data (compound-nouns.json), taken from
 * 표준국어대사전 one by one, because no rule tells 돌계단 (a headword) from 흙 계단 (not).
 */
public final class Compounds {
	public static final String RESOURCE = "/assets/hangulquest/data/compound-nouns.json";

	private static volatile Set<String> closed;

	private Compounds() {
	}

	private static Set<String> closed() {
		Set<String> set = closed;
		if (set == null) {
			set = load();
			closed = set;
		}
		return set;
	}

	private static Set<String> load() {
		Set<String> words = new HashSet<>();
		try (InputStream stream = Compounds.class.getResourceAsStream(RESOURCE)) {
			if (stream != null) {
				try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
					for (JsonElement word : root.getAsJsonArray("closed")) {
						words.add(word.getAsString());
					}
				}
			}
		} catch (IOException | RuntimeException broken) {
			words.clear(); // a missing table only means every phrase keeps its space
		}
		return Set.copyOf(words);
	}

	/** The words that are written as one. */
	public static Set<String> words() {
		return closed();
	}

	/** "돌담" when the dictionary has it as one word, else "material structure"; just the structure without a material. */
	public static String join(String material, String structure) {
		if (material == null || material.isBlank()) {
			return structure;
		}
		String solid = material + structure;
		return closed().contains(solid) ? solid : material + " " + structure;
	}
}
