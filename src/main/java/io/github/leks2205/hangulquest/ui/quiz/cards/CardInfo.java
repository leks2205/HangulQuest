package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

/**
 * What every card needs to know about its word: the word itself and, when the concept has one,
 * an example sentence in Korean with its translations.
 */
public record CardInfo(Word word, String sentenceKo, String sentenceEn, String sentenceDe) {
	public static CardInfo of(Catalog catalog, Word word) {
		Concept concept = word.conceptId == null || catalog == null ? null : catalog.concept(word.conceptId).orElse(null);
		if (concept == null) {
			return new CardInfo(word, null, null, null);
		}
		return new CardInfo(word, concept.sentenceKo, concept.sentenceEn, concept.sentenceDe);
	}

	public static CardInfo of(Word word) {
		return new CardInfo(word, null, null, null);
	}

	public boolean hasSentence() {
		return sentenceKo != null && !sentenceKo.isBlank();
	}

	/** The example sentence in the interface language: German for German, English for every other. */
	public String sentenceTranslation() {
		return I18n.pick(sentenceEn, sentenceDe);
	}

	public String gloss() {
		return word.gloss(Lang.current());
	}

	/**
	 * The picture for the card: the word's icon, else the spawn egg of its entity (a portrait would
	 * need a world to render in), else none.
	 */
	public String icon() {
		if (word.iconItem != null && !word.iconItem.isBlank()) {
			return word.iconItem;
		}
		if (word.entityId != null && !word.entityId.isBlank()) {
			return word.entityId + "_spawn_egg";
		}
		return null;
	}
}
