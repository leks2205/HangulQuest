package io.github.leks2205.hangulquest.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The short meaning of each hanja the lexicon tags mention ({@code hanja-meanings.json}). The file maps a
 * character to a key suffix; the English and German texts are the lang keys
 * {@code hangulquest.ui.hanja.<suffix>}.
 */
public final class HanjaMeanings {
	public static final String RESOURCE = "/assets/hangulquest/data/hanja-meanings.json";
	public static final String KEY_PREFIX = "hangulquest.ui.hanja.";
	private static final HanjaMeanings EMPTY = new HanjaMeanings(Map.of());

	private static volatile HanjaMeanings shared;

	private final Map<String, String> suffixes;

	private HanjaMeanings(Map<String, String> suffixes) {
		this.suffixes = suffixes;
	}

	public static HanjaMeanings shared() {
		HanjaMeanings loaded = shared;
		if (loaded == null) {
			loaded = load();
			shared = loaded;
		}
		return loaded;
	}

	public static HanjaMeanings of(Map<String, String> suffixes) {
		return new HanjaMeanings(Map.copyOf(suffixes));
	}

	/** From the classpath; an unreadable or missing file gives no meanings rather than a crash. */
	public static HanjaMeanings load() {
		try (InputStream stream = HanjaMeanings.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? EMPTY : parse(stream);
		} catch (IOException | RuntimeException broken) {
			return EMPTY;
		}
	}

	static HanjaMeanings parse(InputStream stream) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject all = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("meanings");
			for (String hanja : all.keySet()) {
				out.put(hanja, all.get(hanja).getAsString());
			}
		}
		return new HanjaMeanings(out);
	}

	/** The lang key suffix of a character, or null when it has no meaning yet. */
	public String suffix(String hanja) {
		return suffixes.get(hanja);
	}

	/** The meaning in the interface language; empty when the character is not covered. */
	public String meaning(String hanja) {
		String suffix = suffixes.get(hanja);
		return suffix == null ? "" : I18n.t(KEY_PREFIX + suffix);
	}

	public java.util.Set<String> hanja() {
		return suffixes.keySet();
	}
}
