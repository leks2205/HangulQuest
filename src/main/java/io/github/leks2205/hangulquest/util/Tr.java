package io.github.leks2205.hangulquest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Old entry point for interface text, where the German text was the key. Kept for one release so code
 * written against it still works: a German literal is mapped to its key through {@code de_de.json}
 * and a warning is logged once per text. New code calls {@link I18n#t(String, Object...)}.
 *
 * @deprecated use {@link I18n} with a {@code hangulquest.ui.*} key
 */
@Deprecated(forRemoval = true)
public final class Tr {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	private static volatile Map<String, String> keyByGerman;

	private Tr() {
	}

	/** Interface text for a German literal; the literal itself when it has no key. */
	public static String t(String de) {
		String key = keys().get(de);
		if (WARNED.add(de)) {
			LOG.warn("Tr.t(\"{}\") is deprecated, use I18n.t(\"{}\")", de, key == null ? "<new key>" : key);
		}
		return key == null ? de : I18n.t(key);
	}

	/** True when a German literal has a key. */
	public static boolean hasKey(String de) {
		return keys().containsKey(de);
	}

	/** Same as {@link I18n#pick(String, String)}. */
	public static String pick(String en, String de) {
		return I18n.pick(en, de);
	}

	private static Map<String, String> keys() {
		Map<String, String> known = keyByGerman;
		if (known == null) {
			known = new HashMap<>();
			for (Map.Entry<String, String> entry : I18n.bundle("de_de").entrySet()) {
				known.putIfAbsent(entry.getValue(), entry.getKey());
			}
			keyByGerman = known;
		}
		return known;
	}
}
