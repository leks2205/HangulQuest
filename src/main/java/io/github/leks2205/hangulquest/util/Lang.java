package io.github.leks2205.hangulquest.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Language of the interface and of the glosses that accompany the Korean text: a lower-case
 * language code such as {@code en_us} or {@code de_de}. The Korean instructions are always shown;
 * this only picks the gloss language.
 *
 * <p>Instances are interned, so {@code ==} works and {@link #EN} / {@link #DE} stay usable as
 * constants. The setting can be {@code AUTO}: then the language follows Minecraft's selected language
 * and is resolved on every {@link #current()} call, so changing the game language takes effect
 * without touching this mod's config. A game language the mod has no interface text for (say
 * {@code fr_fr}) still resolves to its own code, so glosses can come from that language's file;
 * the interface text falls back to English.
 */
public final class Lang {
	private static final AtomicInteger IDS = new AtomicInteger();

	public static final Lang EN = new Lang("en_us");
	public static final Lang DE = new Lang("de_de");

	/** Config value that follows Minecraft's language. */
	public static final String AUTO = "AUTO";

	private static final Map<String, Lang> INTERNED = new ConcurrentHashMap<>(
			Map.of(EN.code, EN, DE.code, DE));
	/** Raw game language code to the language it resolves to. */
	private static final Map<String, Lang> RESOLVED = new ConcurrentHashMap<>();

	private static volatile boolean auto;
	private static volatile Lang fixed = EN;
	private static volatile Supplier<String> localeSource = Lang::minecraftLocale;

	private final String code;
	private final int id;

	private Lang(String code) {
		this.code = code;
		this.id = IDS.getAndIncrement();
	}

	public static Lang current() {
		return auto ? fromLocale(localeSource.get()) : fixed;
	}

	/** Applies a config value: {@code AUTO}, or a language code the mod ships text for (else English). */
	public static void set(String name) {
		if (isAuto(name)) {
			auto = true;
		} else {
			auto = false;
			fixed = parse(name);
		}
	}

	public static boolean isAuto(String name) {
		return name != null && AUTO.equalsIgnoreCase(name.trim());
	}

	/** The shipped language for a config value; English for {@code null} and unknown languages. */
	public static Lang parse(String name) {
		if (name == null || name.isBlank()) {
			return EN;
		}
		if (isAuto(name)) {
			return fromLocale(localeSource.get());
		}
		String shipped = I18n.shippedLanguage(canonical(name));
		return shipped == null ? EN : of(shipped);
	}

	/** Config value to store for a user-supplied one: {@code AUTO} or the code of a shipped language. */
	public static String normalise(String name) {
		return isAuto(name) ? AUTO : parse(name).code();
	}

	/**
	 * The language for a game language code. {@code de_at} gives {@code de_de}; a language without
	 * interface text keeps its own code.
	 */
	public static Lang fromLocale(String locale) {
		String raw = canonical(locale);
		Lang known = RESOLVED.get(raw);
		if (known != null) {
			return known;
		}
		String shipped = I18n.shippedLanguage(raw);
		Lang resolved = of(shipped != null ? shipped : raw);
		RESOLVED.put(raw, resolved);
		return resolved;
	}

	/** The interned language for a code. */
	public static Lang of(String code) {
		return INTERNED.computeIfAbsent(canonical(code), Lang::new);
	}

	/** {@code de-AT} to {@code de_at}; blank to {@code en_us}. */
	static String canonical(String code) {
		if (code == null || code.isBlank()) {
			return "en_us";
		}
		return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
	}

	/** Replaces where AUTO reads the game language from (tests only). */
	public static void setLocaleSource(Supplier<String> source) {
		localeSource = source == null ? Lang::minecraftLocale : source;
	}

	private static String minecraftLocale() {
		try {
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			if (client != null && client.options != null && client.options.languageCode != null) {
				return client.options.languageCode;
			}
		} catch (Throwable unavailable) {
			// Not running inside the game (unit tests) or options are not loaded yet.
		}
		return "en_us";
	}

	/** Lower-case code, for example {@code es_es}. */
	public String code() {
		return code;
	}

	/** The language part of the code: {@code es} for {@code es_es}. */
	public String language() {
		int cut = code.indexOf('_');
		return cut < 0 ? code : code.substring(0, cut);
	}

	/** Small number that differs between languages, for cache stamps. */
	public int id() {
		return id;
	}

	/** Name of the language in that language, from the language index; the code when it has none. */
	public String label() {
		return I18n.languageName(code);
	}

	@Override
	public String toString() {
		return code;
	}
}
