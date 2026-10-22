package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Official names from the client language assets (downloaded by the launcher): Korean, English, the
 * player's interface language and German when they are there. Korean decides which entries exist;
 * every other language only adds a gloss. The bundled names.json (Korean and German) is the
 * fallback when the game has no Korean file.
 */
public final class LangTables {
	private static final String KOREAN = "ko_kr";
	private static final String ENGLISH = "en_us";
	private static final String GERMAN = "de_de";

	/** Language code to its vanilla name table. */
	private final Map<String, Map<String, String>> tables;

	private LangTables(Map<String, Map<String, String>> tables) {
		this.tables = tables;
	}

	public String en(String key) {
		return name(ENGLISH, key);
	}

	public String ko(String key) {
		return name(KOREAN, key);
	}

	/** German name, or null when the German file is not there. */
	public String de(String key) {
		return name(GERMAN, key);
	}

	/** The name in the language with this code (lower-case, {@code es_es}), or null. */
	public String name(String code, String key) {
		Map<String, String> table = tables.get(code);
		return table == null ? null : table.get(key);
	}

	/**
	 * Names in the loaded languages other than Korean, English and German, keyed by language code.
	 * That is the player's interface language, when it is one of those.
	 */
	public Map<String, String> otherNames(String key) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> table : tables.entrySet()) {
			String code = table.getKey();
			String value = table.getValue().get(key);
			if (value != null && !value.isBlank() && !code.equals(KOREAN) && !code.equals(ENGLISH)
					&& !code.equals(GERMAN)) {
				out.put(code, value);
			}
		}
		return out;
	}

	/** True when there is a Korean name; that is all a dynamic entry needs. */
	public boolean has(String key) {
		return tables.getOrDefault(KOREAN, Map.of()).containsKey(key);
	}

	public int size() {
		return tables.getOrDefault(KOREAN, Map.of()).size();
	}

	public static LangTables load(Minecraft mc) {
		return load(mc.getResourceManager(), Lang.current());
	}

	/** Used by the off-thread content loader; reads the interface language once. */
	public static LangTables load(ResourceManager manager) {
		return load(manager, Lang.current());
	}

	/** Loads Korean, English, German and the language of the interface, whichever exist. */
	static LangTables load(ResourceManager manager, Lang interfaceLanguage) {
		Map<String, Map<String, String>> tables = new HashMap<>();
		List<String> codes = new ArrayList<>(List.of(KOREAN, ENGLISH, GERMAN));
		if (!codes.contains(interfaceLanguage.code())) {
			codes.add(interfaceLanguage.code());
		}
		for (String code : codes) {
			Map<String, String> table = new HashMap<>();
			read(manager, code + ".json", table);
			if (!table.isEmpty()) {
				tables.put(code, table);
			}
		}
		if (!tables.containsKey(KOREAN)) {
			Map<String, String> ko = new HashMap<>();
			Map<String, String> de = new HashMap<>();
			loadFallback(ko, de);
			if (!ko.isEmpty()) {
				tables.put(KOREAN, ko);
				tables.putIfAbsent(GERMAN, de);
			}
		}
		if (!tables.containsKey(ENGLISH)) {
			tables.put(ENGLISH, fallbackEnglish(tables.getOrDefault(KOREAN, Map.of())));
		}
		return new LangTables(tables);
	}

	private static void read(ResourceManager manager, String file, Map<String, String> target) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", "lang/" + file);
		Optional<Resource> resource;
		try {
			resource = manager.getResource(id);
		} catch (RuntimeException broken) {
			return;
		}
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

	/** English names are only used for the interface; the Korean set decides which entries exist. */
	private static Map<String, String> fallbackEnglish(Map<String, String> korean) {
		Map<String, String> en = new HashMap<>();
		for (String key : korean.keySet()) {
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

	/** Test helper: Korean and German only. */
	public static LangTables of(Map<String, String> ko, Map<String, String> de) {
		Map<String, Map<String, String>> tables = new HashMap<>();
		tables.put(KOREAN, new HashMap<>(ko));
		if (de != null && !de.isEmpty()) {
			tables.put(GERMAN, new HashMap<>(de));
		}
		tables.put(ENGLISH, fallbackEnglish(ko));
		return new LangTables(tables);
	}

	/** Test helper: any set of language tables, keyed by lower-case language code. */
	public static LangTables ofTables(Map<String, Map<String, String>> byLanguage) {
		Map<String, Map<String, String>> tables = new HashMap<>();
		byLanguage.forEach((code, table) -> tables.put(code, new HashMap<>(table)));
		tables.computeIfAbsent(ENGLISH, code -> fallbackEnglish(tables.getOrDefault(KOREAN, Map.of())));
		return new LangTables(tables);
	}

	public List<String> keys() {
		return new ArrayList<>(tables.getOrDefault(KOREAN, Map.of()).keySet());
	}
}
