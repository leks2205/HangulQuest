package io.github.leks2205.hangulquest.data;

import java.util.List;

/**
 * One learnable entry. Curated concepts, dynamically named vanilla items/entities
 * and imported Lute rows all become Words; {@code conceptId} links a word to the
 * curated metadata (icon, easy-Korean description, trail eligibility).
 *
 * <p>{@code iconItem} is only what the interface draws next to the word. The items the
 * word actually names are {@code referents}; tooltips and look-at cards resolve by
 * those, never by the icon.
 */
public final class Word {
	/** Shown in front of the German text when a word has no English gloss yet. */
	public static final String UNTRANSLATED_PREFIX = "[DE] ";

	public final String id;
	public final String ko;
	public final String de;
	/** English gloss; {@link #UNTRANSLATED_PREFIX} plus the German text when none exists. */
	public final String en;
	/** True when {@link #en} is only a placeholder built from the German text. */
	public final boolean untranslated;
	public final String deShort;
	public final Category category;
	public final int tier;
	public final String conceptId;
	/** Item drawn as the word's picture; not necessarily something the word names. */
	public final String iconItem;
	/** Item ids the word really names; the icon is in here only if it is one of them. */
	public final List<String> referents;
	public final String entityId;
	public final String biomeId;
	public final String descKo;
	public final Origin origin;
	public final boolean phrase;
	public final String grammar;

	public Word(String id, String ko, String de, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar) {
		this(id, ko, de, de, deShort, category, tier, conceptId, iconItem, entityId, biomeId,
				descKo, origin, phrase, grammar);
	}

	/** A word that is an item itself: the icon is also the referent. */
	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar) {
		this(id, ko, de, en, deShort, category, tier, conceptId, iconItem, entityId, biomeId,
				descKo, origin, phrase, grammar, iconItem == null ? List.of() : List.of(iconItem));
	}

	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar, List<String> referents) {
		this.id = id;
		this.ko = ko;
		this.de = de == null ? "" : de;
		this.untranslated = en == null || en.isBlank();
		this.en = untranslated ? UNTRANSLATED_PREFIX + this.de : en;
		this.deShort = deShort == null || deShort.isBlank() ? this.de : deShort;
		this.category = category == null ? Category.MISC : category;
		this.tier = tier;
		this.conceptId = conceptId;
		this.iconItem = iconItem;
		this.referents = referents == null ? List.of() : List.copyOf(referents);
		this.entityId = entityId;
		this.biomeId = biomeId;
		this.descKo = descKo;
		this.origin = origin;
		this.phrase = phrase;
		this.grammar = grammar;
	}

	/** Short gloss for quizzes and cards in the given interface language. */
	public String gloss(io.github.leks2205.hangulquest.util.Lang lang) {
		if (lang == io.github.leks2205.hangulquest.util.Lang.DE) {
			return deShort;
		}
		return en == null || en.isBlank() ? deShort : en;
	}

	public boolean hasDescKo() {
		return descKo != null && !descKo.isBlank();
	}

	/** Icon stack id: what the interface draws, not necessarily what the word names. */
	public String iconId() {
		return iconItem;
	}

	@Override
	public String toString() {
		return id + "(" + ko + "/" + deShort + ")";
	}
}
