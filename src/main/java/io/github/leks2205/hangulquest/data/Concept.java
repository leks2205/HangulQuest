package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Curated vocabulary entry as stored in concepts.json. The English and German gloss and the sentence
 * translation live in the lang files ({@code hangulquest.word.<id>.gloss} and {@code .sentence});
 * a concepts.json that still carries them inline (a resource pack) wins over the keys.
 */
public final class Concept {
	/** Key of a word field in the lang files: {@code gloss} or {@code sentence}. */
	public static String key(String conceptId, String field) {
		return "hangulquest.word." + conceptId + "." + field;
	}

	public String id;
	public String ko;
	public String de;
	public String en;
	/** Optional glosses by language code ({@code "es_es": "gato"}); not used yet, here so content can grow into it. */
	public Map<String, String> glosses;
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

	/** Fills the glosses and sentence translations that concepts.json leaves to the lang files. */
	public void fillText() {
		if (id == null || id.isBlank()) {
			return;
		}
		de = blank(de) ? I18n.find(Lang.DE.code(), key(id, "gloss")) : de;
		en = blank(en) ? I18n.find(Lang.EN.code(), key(id, "gloss")) : en;
		sentenceDe = blank(sentenceDe) ? I18n.find(Lang.DE.code(), key(id, "sentence")) : sentenceDe;
		sentenceEn = blank(sentenceEn) ? I18n.find(Lang.EN.code(), key(id, "sentence")) : sentenceEn;
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	public Word toWord() {
		List<String> referents = items == null ? List.of() : items;
		// A blank English gloss stays blank here; Word marks it as untranslated instead of
		// passing the German text off as English.
		return new Word("concept:" + id, ko, de, en, de, categoryEnum(), tier, id, iconItem(), entity, biome,
				descKo, Origin.CURATED, false, grammar, referents, glosses);
	}

	public boolean isValid() {
		return id != null && !id.isBlank() && ko != null && !ko.isBlank()
				&& de != null && !de.isBlank();
	}
}
