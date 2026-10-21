package io.github.leks2205.hangulquest.util;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Language of the interface and of the translations that accompany the Korean text.
 * The Korean instructions are always shown; this only picks the gloss language.
 *
 * <p>The setting can be {@code AUTO}: then the language follows Minecraft's selected language
 * ({@code de_*} gives German, everything else English) and is resolved on every {@link #current()} call,
 * so changing the game language takes effect without touching this mod's config.
 */
public enum Lang {
	EN,
	DE;

	/** Config value that follows Minecraft's language. */
	public static final String AUTO = "AUTO";

	private static volatile boolean auto;
	private static volatile Lang fixed = EN;
	private static volatile Supplier<String> localeSource = Lang::minecraftLocale;

	public static Lang current() {
		return auto ? fromLocale(localeSource.get()) : fixed;
	}

	/** Applies a config value: {@code AUTO}, {@code DE} or anything else for English. */
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

	public static Lang parse(String name) {
		if (name == null) {
			return EN;
		}
		if (isAuto(name)) {
			return fromLocale(localeSource.get());
		}
		return "DE".equalsIgnoreCase(name.trim()) ? DE : EN;
	}

	/** Config value to store for a user-supplied one: {@code AUTO}, {@code EN} or {@code DE}. */
	public static String normalise(String name) {
		return isAuto(name) ? AUTO : parse(name).code();
	}

	/** {@code de_de}, {@code de_at}, {@code de} give German; everything else English. */
	public static Lang fromLocale(String locale) {
		if (locale == null) {
			return EN;
		}
		String code = locale.trim().toLowerCase(Locale.ROOT);
		return code.equals("de") || code.startsWith("de_") || code.startsWith("de-") ? DE : EN;
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

	public String code() {
		return name();
	}

	/** Label of the other language, for the switch button. */
	public String label() {
		return this == DE ? "Deutsch" : "English";
	}
}
