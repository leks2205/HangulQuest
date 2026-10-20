package de.zirks.hangulquest.data;

import java.util.ArrayList;
import java.util.List;

/** Curated vocabulary entry as stored in concepts.json. */
public final class Concept {
	public String id;
	public String ko;
	public String de;
	public String category = "MISC";
	public int tier = 1;
	public List<String> items = new ArrayList<>();
	public String entity;
	public String biome;
	public String sentenceKo;
	public String sentenceDe;
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

	public Word toWord() {
		String icon = items == null || items.isEmpty() ? null : items.get(0);
		return new Word("concept:" + id, ko, de, de, categoryEnum(), tier, id, icon, entity, biome,
				descKo, Origin.CURATED, false, grammar);
	}

	public boolean isValid() {
		return id != null && !id.isBlank() && ko != null && !ko.isBlank()
				&& de != null && !de.isBlank();
	}
}
