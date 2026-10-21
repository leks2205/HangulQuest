package io.github.leks2205.hangulquest.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.JsonFields;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reads story JSON. Problems are collected, never thrown: one bad file must not stop the others. */
public final class StoryParser {
	/** Ids end up in language keys and registry line ids, where a dot or a space would break them. */
	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

	/** The story, or null when the file could not be read at all; {@code problems} may be set either way. */
	public record Parsed(Story story, List<String> problems) {
	}

	private StoryParser() {
	}

	/** {@code fileId} is the file name without {@code .json}, {@code folder} the chapter folder it sits in. */
	public static Parsed parse(String folder, String fileId, byte[] bytes) {
		String where = folder + "/" + fileId;
		JsonObject root;
		try {
			root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException broken) {
			return new Parsed(null, List.of(where + ": not valid JSON (" + broken.getMessage() + ")"));
		}
		try {
			return read(folder, fileId, root);
		} catch (RuntimeException malformed) {
			return new Parsed(null, List.of(where + ": malformed (" + malformed + ")"));
		}
	}

	private static Parsed read(String folder, String fileId, JsonObject root) {
		String where = folder + "/" + fileId;
		List<String> problems = new ArrayList<>();
		String id = JsonFields.string(root, "id", "");
		if (!id.equals(fileId)) {
			problems.add(where + ": id \"" + id + "\" must be the file name");
		}
		if (!NAME.matcher(fileId).matches()) {
			problems.add(where + ": the id may only use a-z, 0-9 and _");
		}
		String chapter = JsonFields.string(root, "chapter", "");
		if (!chapter.equals(folder)) {
			problems.add(where + ": chapter \"" + chapter + "\" must be the folder name");
		}
		int level = JsonFields.integer(root, "level", 0);
		if (level < 1 || level > 6) {
			problems.add(where + ": level " + level + " is not between 1 and 6");
		}
		String titleKo = JsonFields.string(root, "titleKo", "");
		if (titleKo.isBlank()) {
			problems.add(where + ": no Korean title");
		}
		List<String> paragraphs = new ArrayList<>();
		if (root.has("paragraphs") && root.get("paragraphs").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("paragraphs")) {
				String text = element.getAsString().strip();
				if (text.isEmpty()) {
					problems.add(where + ": an empty paragraph");
				} else {
					paragraphs.add(text);
				}
			}
		}
		if (paragraphs.isEmpty()) {
			problems.add(where + ": no paragraphs");
		}
		JsonObject unlockJson = root.has("unlock") && root.get("unlock").isJsonObject() ? root.getAsJsonObject("unlock") : null;
		Story.Unlock unlock = new Story.Unlock(JsonFields.strings(unlockJson, "trails"),
				Math.max(0, JsonFields.integer(unlockJson, "minWords", 0)));
		Story story = new Story(id, chapter, level, titleKo, unlock, paragraphs, strings(root, "words"),
				strings(root, "grammar"));
		return new Parsed(story, problems);
	}

	private static List<String> strings(JsonObject root, String key) {
		JsonArray array = root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
		List<String> out = new ArrayList<>();
		array.forEach(element -> out.add(element.getAsString()));
		return out;
	}
}
