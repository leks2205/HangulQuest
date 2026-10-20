package io.github.leks2205.hangulquest.data;

/**
 * One learnable entry. Curated concepts, dynamically named vanilla items/entities
 * and imported Lute rows all become Words; {@code conceptId} links a word to the
 * curated metadata (icon, easy-Korean description, trail eligibility).
 */
public final class Word {
	public final String id;
	public final String ko;
	public final String de;
	public final String en;
	public final String deShort;
	public final Category category;
	public final int tier;
	public final String conceptId;
	public final String iconItem;
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

	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar) {
		this.id = id;
		this.ko = ko;
		this.de = de == null ? "" : de;
		this.en = en == null || en.isBlank() ? this.de : en;
		this.deShort = deShort == null || deShort.isBlank() ? this.de : deShort;
		this.category = category == null ? Category.MISC : category;
		this.tier = tier;
		this.conceptId = conceptId;
		this.iconItem = iconItem;
		this.entityId = entityId;
		this.biomeId = biomeId;
		this.descKo = descKo;
		this.origin = origin;
		this.phrase = phrase;
		this.grammar = grammar;
	}

	public boolean hasDescKo() {
		return descKo != null && !descKo.isBlank();
	}

	/** Icon stack id: item first, then a spawn egg-ish entity placeholder. */
	public String iconId() {
		return iconItem;
	}

	@Override
	public String toString() {
		return id + "(" + ko + "/" + deShort + ")";
	}
}
