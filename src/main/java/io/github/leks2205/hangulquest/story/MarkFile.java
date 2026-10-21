package io.github.leks2205.hangulquest.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.JsonIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small key to text map kept in a file of the profile folder: which stories were read on which day, which
 * culture notes were shown. It lives next to the progress file but is not part of it, so the progress layout
 * stays as it is; losing the file only means a note shows once more.
 */
public final class MarkFile {
	/** The oldest marks are forgotten beyond this many. */
	static final int MAX_ENTRIES = 200;

	private final Path file;
	private final Logger log;
	private final Map<String, String> marks = new LinkedHashMap<>();

	private MarkFile(Path file, Logger log) {
		this.file = file;
		this.log = log;
	}

	/** Reads {@code profileDir/name}; a missing or broken file starts empty. */
	public static MarkFile open(Path profileDir, String name, Logger log) {
		MarkFile marks = new MarkFile(profileDir.resolve(name), log);
		marks.read();
		return marks;
	}

	public String get(String key) {
		return marks.get(key);
	}

	public boolean is(String key, String value) {
		return value.equals(marks.get(key));
	}

	/** Sets a mark and saves; a mark with the value it already has costs no write. */
	public void set(String key, String value) {
		if (value.equals(marks.put(key, value))) {
			return;
		}
		while (marks.size() > MAX_ENTRIES) {
			String oldest = marks.keySet().iterator().next();
			marks.remove(oldest);
		}
		save();
	}

	public int size() {
		return marks.size();
	}

	private void read() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject stored = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("marks");
			for (Map.Entry<String, JsonElement> entry : stored.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					marks.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		} catch (IOException | RuntimeException broken) {
			log.warn("could not read {}: {}", file, broken.toString());
			marks.clear();
		}
	}

	private void save() {
		JsonObject stored = new JsonObject();
		marks.forEach(stored::addProperty);
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("marks", stored);
		try {
			JsonIO.writeString(file, JsonIO.GSON.toJson(root));
		} catch (IOException failed) {
			log.warn("could not save {}: {}", file, failed.toString());
		}
	}
}
