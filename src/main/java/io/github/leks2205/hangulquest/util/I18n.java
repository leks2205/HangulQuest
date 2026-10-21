package io.github.leks2205.hangulquest.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface text from the mod's own language files ({@code assets/hangulquest/lang/<code>.json}).
 *
 * <p>The interface language can differ from the game language, so this does not go through
 * {@code Component.translatable}: the file for {@link Lang#current()} is read directly and a key it
 * lacks falls back to {@code en_us}, never to another language. Arguments are {@code %s}.
 * Vanilla key mappings stay with {@code Component.translatable}.
 */
public final class I18n {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	private static final String FALLBACK = "en_us";
	private static final String LANG_DIR = "/assets/hangulquest/lang/";
	private static final String INDEX = "/assets/hangulquest/data/languages.json";
	private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

	private static final Map<String, Map<String, String>> BUNDLES = new ConcurrentHashMap<>();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	private static volatile List<Language> index;

	private I18n() {
	}

	/** A language the mod ships interface text for. */
	public record Language(String code, String name) {
	}

	/** Text for a key in the current interface language; format {@code %s} with the arguments, if any. */
	public static String t(String key, Object... args) {
		return t(Lang.current(), key, args);
	}

	public static String t(Lang lang, String key, Object... args) {
		String template = bundle(lang.code()).get(key);
		if (template == null) {
			template = bundle(FALLBACK).get(key);
		}
		if (template == null) {
			if (WARNED.add(key)) {
				LOG.warn("missing interface text: {}", key);
			}
			return key;
		}
		return args == null || args.length == 0 ? template : format(template, args);
	}

	/** True when the English file has this key (it is the one every other language falls back to). */
	public static boolean has(String key) {
		return bundle(FALLBACK).containsKey(key);
	}

	/**
	 * Picks between two texts that content already carries (quest titles and the like): German only
	 * for the German interface, English for everything else. Goes away when that content moves to keys.
	 */
	public static String pick(String en, String de) {
		if (Lang.current() == Lang.DE) {
			return de == null || de.isBlank() ? en : de;
		}
		return en == null || en.isBlank() ? de : en;
	}

	private static String format(String template, Object[] args) {
		try {
			return String.format(java.util.Locale.ROOT, template, args);
		} catch (IllegalFormatException broken) {
			// A bad translation must not take the screen down.
			return template;
		}
	}

	/** The key/value pairs of one language file; empty when the mod ships none for the code. */
	public static Map<String, String> bundle(String code) {
		return BUNDLES.computeIfAbsent(code, I18n::load);
	}

	/** True when the mod ships a file for exactly this code. */
	public static boolean hasBundle(String code) {
		return !bundle(code).isEmpty();
	}

	private static Map<String, String> load(String code) {
		try (InputStream stream = I18n.class.getResourceAsStream(LANG_DIR + code + ".json")) {
			if (stream == null) {
				return Map.of();
			}
			return Collections.unmodifiableMap(readStrings(stream));
		} catch (Exception unreadable) {
			LOG.warn("could not read language file {}: {}", code, unreadable.toString());
			return Map.of();
		}
	}

	private static Map<String, String> readStrings(InputStream stream) throws java.io.IOException {
		Map<String, String> out = new java.util.LinkedHashMap<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
			for (String name : object.keySet()) {
				JsonElement value = object.get(name);
				if (value.isJsonPrimitive()) {
					out.put(name, value.getAsString());
				}
			}
		}
		return out;
	}

	/**
	 * The shipped language that serves a game language code: the code itself when the mod has a file
	 * for it, else a listed language with the same language part ({@code de_at} gives {@code de_de}),
	 * else {@code null}.
	 */
	static String shippedLanguage(String code) {
		if (hasBundle(code)) {
			return code;
		}
		String language = code.contains("_") ? code.substring(0, code.indexOf('_')) : code;
		for (Language listed : languages()) {
			if (listed.code().startsWith(language + "_") || listed.code().equals(language)) {
				return listed.code();
			}
		}
		String doubled = language + "_" + language;
		return hasBundle(doubled) ? doubled : null;
	}

	/** The languages offered in the settings, from {@code data/languages.json}. */
	public static List<Language> languages() {
		List<Language> known = index;
		if (known == null) {
			known = readIndex();
			index = known;
		}
		return known;
	}

	private static List<Language> readIndex() {
		List<Language> out = new ArrayList<>();
		try (InputStream stream = I18n.class.getResourceAsStream(INDEX)) {
			if (stream != null) {
				for (Map.Entry<String, String> entry : readStrings(stream).entrySet()) {
					out.add(new Language(entry.getKey(), entry.getValue()));
				}
			}
		} catch (Exception unreadable) {
			LOG.warn("could not read the language index: {}", unreadable.toString());
		}
		if (out.isEmpty()) {
			out.add(new Language("en_us", "English"));
		}
		return List.copyOf(out);
	}

	/** Name of a language in itself; the code when it is not listed. */
	public static String languageName(String code) {
		for (Language listed : languages()) {
			if (listed.code().equals(code)) {
				return listed.name();
			}
		}
		return code;
	}

	/** Number of arguments a template takes: the highest {@code %s} / {@code %2$s} index. */
	public static int argCount(String template) {
		int sequential = 0;
		int highest = 0;
		Matcher matcher = ARG.matcher(template.replace("%%", ""));
		while (matcher.find()) {
			if (matcher.group(1) == null) {
				sequential++;
				highest = Math.max(highest, sequential);
			} else {
				highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
			}
		}
		return highest;
	}
}
