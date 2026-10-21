package io.github.leks2205.hangulquest.ui.quiz.cards;

/**
 * The feedback after an attempt: the correct form with its picture, gloss and an example sentence.
 * A wrong answer first gets a prompt and one retry (the cards do that); this is what comes after.
 *
 * @param correct          whether the player's last attempt was right
 * @param ko               the correct Korean
 * @param gloss            its gloss in the interface language
 * @param icon             picture item id, or null
 * @param sentenceKo       an example sentence, or null
 * @param sentenceGloss    its translation, or null
 * @param yours            what the player put, when that differs from {@code ko}; else null
 */
public record FeedbackCard(boolean correct, String ko, String gloss, String icon, String sentenceKo,
						   String sentenceGloss, String yours) {
	public static FeedbackCard of(CardInfo info, boolean correct, String yours) {
		boolean sentence = info.hasSentence();
		String translated = sentence ? info.sentenceTranslation() : null;
		return new FeedbackCard(correct, info.word().ko, info.gloss(), info.icon(), sentence ? info.sentenceKo() : null,
				translated == null || translated.isBlank() ? null : translated,
				yours == null || yours.equals(info.word().ko) ? null : yours);
	}
}
