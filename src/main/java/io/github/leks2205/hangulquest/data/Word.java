package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.List;
import java.util.Map;

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
	/** The German gloss only; use {@link #gloss(Lang)} for text shown to the player. */
	public final String deShort;
	/** Glosses by language code ({@code es_es}) for languages beyond English and German; may be empty. */
	public final Map<String, String> glosses;
	public final Category category;
	public final int tier;
	public final String conceptId;
	/** Item drawn as the word's picture; not necessarily something the word names. */
	public final String iconItem;
	/** Item ids the word really names; the icon is in here only if it is one of them. */
	public final List<String> referents;
	/**
	 * Further ids a goal teaching this word accepts and the sentence names with the word (any sapling for 묘목,
	 * the lush caves for 동굴): the {@code covers} of the concept. They are not tooltips: those keep the vanilla name.
	 */
	public final List<String> covers;
	public final String entityId;
	/** Further creature ids the word names in a sentence (the cod, the salmon and the tropical fish for 물고기). */
	public final List<String> entityCovers;
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
		this(id, ko, de, en, deShort, category, tier, conceptId, iconItem, entityId, biomeId,
				descKo, origin, phrase, grammar, referents, null);
	}

	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar, List<String> referents,
				Map<String, String> glosses) {
		this(id, ko, de, en, deShort, category, tier, conceptId, iconItem, entityId, biomeId, descKo, origin, phrase,
				grammar, referents, glosses, List.of());
	}

	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar, List<String> referents,
				Map<String, String> glosses, List<String> covers) {
		this(id, ko, de, en, deShort, category, tier, conceptId, iconItem, entityId, biomeId, descKo, origin, phrase,
				grammar, referents, glosses, covers, List.of());
	}

	public Word(String id, String ko, String de, String en, String deShort, Category category, int tier,
				String conceptId, String iconItem, String entityId, String biomeId,
				String descKo, Origin origin, boolean phrase, String grammar, List<String> referents,
				Map<String, String> glosses, List<String> covers, List<String> entityCovers) {
		this.id = id;
		this.ko = ko;
		this.de = de == null ? "" : de;
		this.untranslated = en == null || en.isBlank();
		this.en = untranslated ? (this.de.isBlank() ? "" : UNTRANSLATED_PREFIX + this.de) : en;
		this.deShort = deShort == null || deShort.isBlank() ? this.de : deShort;
		this.category = category == null ? Category.MISC : category;
		this.tier = tier;
		this.conceptId = conceptId;
		this.iconItem = iconItem;
		this.referents = referents == null ? List.of() : List.copyOf(referents);
		this.covers = covers == null ? List.of() : List.copyOf(covers);
		this.entityId = entityId;
		this.entityCovers = entityCovers == null ? List.of() : List.copyOf(entityCovers);
		this.biomeId = biomeId;
		this.descKo = descKo;
		this.origin = origin;
		this.phrase = phrase;
		this.grammar = grammar;
		this.glosses = glosses == null ? Map.of() : Map.copyOf(glosses);
	}

	/**
	 * Short gloss for quizzes and cards in the given interface language: the gloss for that language
	 * code (an inline one, else the {@code hangulquest.word.<id>.gloss} key of that language's lang file),
	 * else English, else German. A word without an English gloss carries the German one with a
	 * visible marker (see the constructor). English and German are the {@link #en} and {@link #deShort}
	 * fields, which the concept loader has already filled from the lang files.
	 */
	public String gloss(Lang lang) {
		String own = glosses.get(lang.code());
		if (own != null && !own.isBlank()) {
			return own;
		}
		if (conceptId != null && lang != Lang.EN && lang != Lang.DE) {
			own = I18n.find(lang.code(), Concept.key(conceptId, "gloss"));
			if (own != null) {
				return own;
			}
		}
		if (lang == Lang.DE && !deShort.isBlank()) {
			return deShort;
		}
		return en.isBlank() ? deShort : en;
	}

	/** Like {@link #gloss(Lang)}, but with the long German text where the German gloss is shown. */
	public String glossFull(Lang lang) {
		return lang == Lang.DE && !de.isBlank() && !glosses.containsKey(lang.code()) ? de : gloss(lang);
	}

	/** True for a hand-made concept; the words made for every vanilla item (gen_, adv_) are not. */
	public boolean curated() {
		return conceptId != null && !conceptId.startsWith("gen_") && !conceptId.startsWith("adv_")
				&& !conceptId.startsWith("adv2_");
	}

	public boolean hasDescKo() {
		return descKo != null && !descKo.isBlank();
	}

	/**
	 * The voice pack event after {@code word.}: the concept, or for a word that only has the game's name
	 * {@code mc_<item>} / {@code mc_entity_<entity>} (vanilla ids only). Null when no clip can exist.
	 */
	public String audioId() {
		if (conceptId != null) {
			return conceptId;
		}
		String prefix = id == null ? null : id.startsWith("mc:item:minecraft:") ? "mc_"
				: id.startsWith("mc:entity:minecraft:") ? "mc_entity_" : null;
		return prefix == null ? null : prefix + id.substring(id.lastIndexOf(':') + 1);
	}

	/** Icon stack id: what the interface draws, not necessarily what the word names. */
	public String iconId() {
		return iconItem;
	}

	@Override
	public String toString() {
		return id + "(" + ko + "/" + (deShort.isBlank() ? en : deShort) + ")";
	}
}
