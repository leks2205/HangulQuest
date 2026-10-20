package io.github.leks2205.hangulquest.util;

/**
 * Language of the interface and of the translations that accompany the Korean text.
 * The Korean instructions are always shown; this only picks the gloss language.
 */
public enum Lang {
	EN,
	DE;

	private static volatile Lang current = EN;

	public static Lang current() {
		return current;
	}

	public static void set(String name) {
		current = parse(name);
	}

	public static Lang parse(String name) {
		if (name == null) {
			return EN;
		}
		return "DE".equalsIgnoreCase(name.trim()) ? DE : EN;
	}

	public String code() {
		return name();
	}

	/** Label of the other language, for the switch button. */
	public String label() {
		return this == DE ? "Deutsch" : "English";
	}
}
