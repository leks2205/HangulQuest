package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * External places to learn to read Hangul, shown on the welcome screen. The mod never fetches these:
 * a click opens the link through Minecraft's confirmation screen and the system browser.
 */
public final class HangulResources {
	public static final String PATH = "/assets/hangulquest/data/hangul_resources.json";

	/** {@code lang} is "en" or "de"; {@code kind} is a short word such as "lessons", "comic" or "article". */
	public record Entry(String title, String url, String lang, boolean free, String kind) {
		public boolean secure() {
			return url != null && url.startsWith("https://");
		}
	}

	private HangulResources() {
	}

	public static List<Entry> load() {
		try (InputStream stream = HangulResources.class.getResourceAsStream(PATH)) {
			if (stream == null) {
				return List.of();
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return parse(reader);
			}
		} catch (IOException | RuntimeException broken) {
			return List.of();
		}
	}

	/** Entries without a title or an https URL are dropped, so a bad row can never open a plain-http link. */
	public static List<Entry> parse(Reader reader) {
		List<Entry> entries = new ArrayList<>();
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		JsonArray array = root.has("resources") ? root.getAsJsonArray("resources") : new JsonArray();
		for (JsonElement element : array) {
			JsonObject object = element.getAsJsonObject();
			Entry entry = new Entry(text(object, "title"), text(object, "url"),
					text(object, "lang").toLowerCase(Locale.ROOT),
					object.has("free") && object.get("free").getAsBoolean(), text(object, "kind"));
			if (!entry.title().isBlank() && entry.secure()) {
				entries.add(entry);
			}
		}
		return entries;
	}

	/** Resources in the player's language first, the rest after them in file order. */
	public static List<Entry> orderedFor(List<Entry> entries, Lang lang) {
		String preferred = lang == Lang.DE ? "de" : "en";
		List<Entry> ordered = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.lang().equals(preferred)) {
				ordered.add(entry);
			}
		}
		for (Entry entry : entries) {
			if (!entry.lang().equals(preferred)) {
				ordered.add(entry);
			}
		}
		return ordered;
	}

	private static String text(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
	}
}
