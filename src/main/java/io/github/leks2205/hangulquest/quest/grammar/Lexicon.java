package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator.Irregular;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The irregular-stem lexicon (irregulars.json): which dictionary forms are irregular, which are
 * adjectives, which ㅂ verbs take 와. Spelling alone cannot decide these (입다 is regular, 줍다 is not),
 * so they are listed; everything else falls back to the spelling rules in {@link Conjugator}.
 */
final class Lexicon {
	private static final String RESOURCE = "irregulars.json";
	static final Lexicon SHARED = load();

	private final Map<String, Irregular> exact;
	private final Map<Irregular, List<String>> suffixes;
	private final Set<String> wa;
	private final Set<String> adjectives;
	private final Map<String, String> honorific;

	Lexicon(Map<String, Irregular> exact, Map<Irregular, List<String>> suffixes, Set<String> wa,
			Set<String> adjectives, Map<String, String> honorific) {
		this.exact = exact;
		this.suffixes = suffixes;
		this.wa = wa;
		this.adjectives = adjectives;
		this.honorific = honorific;
	}

	private static Lexicon load() {
		try (InputStream stream = Lexicon.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException(RESOURCE + " is missing");
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	static Lexicon parse(JsonObject root) {
		Map<String, Irregular> exact = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("exact").entrySet()) {
			Irregular irregular = Irregular.byId(entry.getKey());
			for (JsonElement word : entry.getValue().getAsJsonArray()) {
				Irregular before = exact.put(word.getAsString(), irregular);
				if (before != null && before != irregular) {
					throw new IllegalStateException(word.getAsString() + " is listed as " + before + " and " + irregular);
				}
			}
		}
		Map<Irregular, List<String>> suffixes = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("suffix").entrySet()) {
			suffixes.put(Irregular.byId(entry.getKey()), strings(entry.getValue().getAsJsonArray()));
		}
		Map<String, String> honorific = new HashMap<>();
		if (root.has("honorific")) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("honorific").entrySet()) {
				honorific.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		return new Lexicon(exact, suffixes, new HashSet<>(strings(root.getAsJsonArray("wa"))),
				new HashSet<>(strings(root.getAsJsonArray("adjectives"))), Map.copyOf(honorific));
	}

	private static List<String> strings(JsonArray array) {
		List<String> out = new ArrayList<>();
		for (JsonElement element : array) {
			out.add(element.getAsString());
		}
		return out;
	}

	/** The listed class of this word, or null when the spelling rules decide. */
	Irregular listed(String dictionary) {
		Irregular direct = exact.get(dictionary);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<Irregular, List<String>> entry : suffixes.entrySet()) {
			for (String suffix : entry.getValue()) {
				if (dictionary.endsWith(suffix)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	/** The -(으)세요 form of a verb that has a word of its own for it (먹다 is 드세요), or null. */
	String honorific(String dictionary) {
		return honorific.get(dictionary);
	}

	boolean takesWa(String dictionary) {
		return wa.contains(dictionary);
	}

	boolean isAdjective(String dictionary) {
		return adjectives.contains(dictionary) || dictionary.endsWith("롭다") || dictionary.endsWith("스럽다")
				|| dictionary.endsWith("답다");
	}

	Set<String> exactWords() {
		return exact.keySet();
	}
}
