package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-concept tags of {@code lexicon-tags.json} (semantic set, curriculum band and grade, origin,
 * hanja). Only what the learning engine needs is kept; the file stays the one source of truth.
 */
public final class LexiconTags {
	public static final String RESOURCE = "/assets/hangulquest/data/lexicon-tags.json";
	private static final LexiconTags EMPTY = new LexiconTags(Map.of());

	/** One concept's tags; a missing field is null, {@code curriculumGrade} 0 when untagged. */
	public record Tag(String semSet, String curriculumBand, int curriculumGrade, String origin, List<String> hanja,
					  boolean mcOnly, int freqRank) {
		public Tag {
			hanja = hanja == null ? List.of() : List.copyOf(hanja);
		}
	}

	private static volatile LexiconTags shared;

	private final Map<String, Tag> tags;

	private LexiconTags(Map<String, Tag> tags) {
		this.tags = tags;
	}

	/** Tags keyed by concept id; the instance read from the jar, loaded once. */
	public static LexiconTags shared() {
		LexiconTags loaded = shared;
		if (loaded == null) {
			loaded = load();
			shared = loaded;
		}
		return loaded;
	}

	public static LexiconTags of(Map<String, Tag> tags) {
		return new LexiconTags(Map.copyOf(tags));
	}

	public static LexiconTags empty() {
		return EMPTY;
	}

	/** From the classpath; an unreadable or missing file gives no tags rather than a crash. */
	public static LexiconTags load() {
		try (InputStream stream = LexiconTags.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? EMPTY : parse(stream);
		} catch (IOException | RuntimeException broken) {
			return EMPTY;
		}
	}

	static LexiconTags parse(InputStream stream) throws IOException {
		Map<String, Tag> tags = new LinkedHashMap<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject all = root.getAsJsonObject("tags");
			for (String id : all.keySet()) {
				JsonObject tag = all.getAsJsonObject(id);
				tags.put(id, new Tag(string(tag, "semSet"), string(tag, "curriculumBand"), integer(tag, "curriculumGrade"),
						string(tag, "origin"), strings(tag.get("hanja")), tag.has("mcOnly") && tag.get("mcOnly").getAsBoolean(),
						integer(tag, "freqRank")));
			}
		}
		return new LexiconTags(tags);
	}

	private static String string(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	private static int integer(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value == null || value.isJsonNull() ? 0 : value.getAsInt();
	}

	private static List<String> strings(JsonElement value) {
		List<String> out = new ArrayList<>();
		if (value != null && value.isJsonArray()) {
			for (JsonElement element : (JsonArray) value) {
				out.add(element.getAsString());
			}
		}
		return out;
	}

	/** The tags of a concept id (the part after {@code concept:}), or null. */
	public Tag tag(String conceptId) {
		return conceptId == null ? null : tags.get(conceptId);
	}

	/** The tags of a word; null for words that are not curated concepts. */
	public Tag tag(Word word) {
		return word == null ? null : tag(word.conceptId);
	}

	/**
	 * The semantic set of a word. Words without a tag (vanilla items, imported words) get a set of their
	 * own, so they never count as "the same set" as anything else.
	 */
	public String semSet(Word word) {
		Tag tag = tag(word);
		return tag == null || tag.semSet() == null ? "~" + word.id : tag.semSet();
	}

	public int size() {
		return tags.size();
	}
}
