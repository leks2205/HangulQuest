package io.github.leks2205.hangulquest.data;

import java.util.ArrayList;
import java.util.List;

/** Curated vocabulary entry as stored in concepts.json. */
public final class Concept {
	public String id;
	public String ko;
	public String de;
	public String en;
	public String category = "MISC";
	public int tier = 1;
	/** Items the word really names (instances of it); empty for animals, places, verbs. */
	public List<String> items = new ArrayList<>();
	/** Picture to draw when the word names no item or a different one; defaults to the first item. */
	public String icon;
	public String entity;
	public String biome;
	public String sentenceKo;
	public String sentenceDe;
	public String sentenceEn;
	public String grammar;
	public String descKo;
	public List<String> aliases = new ArrayList<>();

	public Category categoryEnum() {
		try {
			return Category.valueOf(category);
		} catch (RuntimeException unknown) {
			return Category.MISC;
		}
	}

	/** False when {@code category} names no {@link Category}, so {@link #categoryEnum()} would fall back to MISC. */
	public boolean hasKnownCategory() {
		try {
			Category.valueOf(category);
			return true;
		} catch (RuntimeException unknown) {
			return false;
		}
	}

	/** The picture for this word: the explicit icon, else the first item it names. */
	public String iconItem() {
		if (icon != null && !icon.isBlank()) {
			return icon;
		}
		return items == null || items.isEmpty() ? null : items.get(0);
	}

	public Word toWord() {
		List<String> referents = items == null ? List.of() : items;
		// A blank English gloss stays blank here; Word marks it as untranslated instead of
		// passing the German text off as English.
		return new Word("concept:" + id, ko, de, en, de, categoryEnum(), tier, id, iconItem(), entity, biome,
				descKo, Origin.CURATED, false, grammar, referents);
	}

	public boolean isValid() {
		return id != null && !id.isBlank() && ko != null && !ko.isBlank()
				&& de != null && !de.isBlank();
	}
}
