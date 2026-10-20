package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Official Korean/German names from the client language assets (downloaded by the
 * launcher), with the bundled names.json as fallback.
 */
public final class LangTables {
	private final Map<String, String> korean;
	private final Map<String, String> german;
	private final Map<String, String> english;

	private LangTables(Map<String, String> korean, Map<String, String> german, Map<String, String> english) {
		this.korean = korean;
		this.german = german;
		this.english = english;
	}

	public String en(String key) {
		return english.get(key);
	}

	public String ko(String key) {
		return korean.get(key);
	}

	public String de(String key) {
		return german.get(key);
	}

	public boolean has(String key) {
		return korean.containsKey(key) && german.containsKey(key);
	}

	public int size() {
		return korean.size();
	}

	public static LangTables load(Minecraft mc) {
		Map<String, String> ko = new HashMap<>();
		Map<String, String> de = new HashMap<>();
		Map<String, String> en = new HashMap<>();
		try {
			read(mc.getResourceManager(), "ko_kr.json", ko);
			read(mc.getResourceManager(), "de_de.json", de);
			read(mc.getResourceManager(), "en_us.json", en);
		} catch (RuntimeException broken) {
			ko.clear();
			de.clear();
			en.clear();
		}
		if (ko.isEmpty() || de.isEmpty()) {
			loadFallback(ko, de);
		}
		if (en.isEmpty()) {
			en.putAll(fallbackEnglish(de));
		}
		return new LangTables(ko, de, en);
	}

	private static void read(ResourceManager manager, String file, Map<String, String> target) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", "lang/" + file);
		Optional<Resource> resource = manager.getResource(id);
		if (resource.isEmpty()) {
			return;
		}
		try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
			JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
			for (String key : object.keySet()) {
				target.put(key, object.get(key).getAsString());
			}
		} catch (Exception failed) {
			target.clear();
		}
	}

	private static void loadFallback(Map<String, String> ko, Map<String, String> de) {
		try (var stream = LangTables.class.getResourceAsStream("/assets/hangulquest/data/names.json")) {
			if (stream == null) {
				return;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
				for (String key : object.keySet()) {
					var pair = object.getAsJsonArray(key);
					if (pair.size() >= 2) {
						ko.put(key, pair.get(0).getAsString());
						de.put(key, pair.get(1).getAsString());
					}
				}
			}
		} catch (Exception ignored) {
			// curated vocabulary still works
		}
	}

	/** English names are only used for the interface; the German set decides which entries exist. */
	private static Map<String, String> fallbackEnglish(Map<String, String> de) {
		Map<String, String> en = new HashMap<>();
		for (String key : de.keySet()) {
			en.put(key, prettify(key));
		}
		return en;
	}

	private static String prettify(String key) {
		String path = key.substring(key.indexOf('.') + 1);
		path = path.substring(path.indexOf('.') + 1);
		StringBuilder out = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	/** Test helper. */
	public static LangTables of(Map<String, String> ko, Map<String, String> de) {
		return new LangTables(new HashMap<>(ko), new HashMap<>(de), fallbackEnglish(de));
	}

	public List<String> keys() {
		return new ArrayList<>(korean.keySet());
	}
}
