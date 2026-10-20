package de.zirks.hangulquest.data;

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

	private LangTables(Map<String, String> korean, Map<String, String> german) {
		this.korean = korean;
		this.german = german;
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
		try {
			read(mc.getResourceManager(), "ko_kr.json", ko);
			read(mc.getResourceManager(), "de_de.json", de);
		} catch (RuntimeException broken) {
			ko.clear();
			de.clear();
		}
		if (ko.isEmpty() || de.isEmpty()) {
			loadFallback(ko, de);
		}
		return new LangTables(ko, de);
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

	/** Test helper. */
	public static LangTables of(Map<String, String> ko, Map<String, String> de) {
		return new LangTables(new HashMap<>(ko), new HashMap<>(de));
	}

	public List<String> keys() {
		return new ArrayList<>(korean.keySet());
	}
}
