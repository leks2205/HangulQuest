package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.WordProgress;

import java.util.List;
import java.util.Random;

/**
 * Chooses which card a due word gets. Recognition always works; the productive cards (tiles, self-graded
 * recall) wait until the word's memory has held for {@link #UNLOCK_STABILITY_DAYS} days, because producing a word
 * you cannot yet recognise only teaches frustration. The scheduler keeps one memory state per word,
 * so that state stands in for the receptive card's.
 */
public final class CardFactory {
	public static final double UNLOCK_STABILITY_DAYS = 3;
	/** Chance that an unlocked word is asked productively (the rest stays recognition). */
	public static final double PRODUCTIVE_SHARE = 0.5;

	public enum Kind { MC4, SELF_GRADE, TILES }

	private CardFactory() {
	}

	/** True once the word's stability is at least three days. Words never reviewed are not unlocked. */
	public static boolean productiveUnlocked(WordProgress progress) {
		return productiveUnlocked(progress, UNLOCK_STABILITY_DAYS);
	}

	/** Same with the stability the word's experiment arm asks for. */
	public static boolean productiveUnlocked(WordProgress progress, double unlockDays) {
		return progress != null && !progress.known && progress.fsrs != null && !progress.fsrs.isNew()
				&& progress.fsrs.stability >= unlockDays;
	}

	/** The kind of card for a word, given what is unlocked and whether tiles can spell it. */
	public static Kind choose(boolean productiveUnlocked, boolean tilesPossible, Random random) {
		if (!productiveUnlocked || random.nextDouble() >= PRODUCTIVE_SHARE) {
			return Kind.MC4;
		}
		if (!tilesPossible) {
			return Kind.SELF_GRADE;
		}
		return random.nextBoolean() ? Kind.TILES : Kind.SELF_GRADE;
	}

	public static Card create(Word word, WordProgress progress, Catalog catalog, LexiconTags tags, Random random) {
		return create(word, progress, catalog, tags, random, UNLOCK_STABILITY_DAYS);
	}

	/** Same, for a word whose experiment arm sets the unlock stability. */
	public static Card create(Word word, WordProgress progress, Catalog catalog, LexiconTags tags, Random random,
							  double unlockDays) {
		CardInfo info = CardInfo.of(catalog, word);
		Kind kind = choose(productiveUnlocked(progress, unlockDays), SyllableTileCard.eligible(word.ko), random);
		return create(kind, info, catalog.all(), tags, random);
	}

	public static Card create(Kind kind, CardInfo info, List<Word> pool, LexiconTags tags, Random random) {
		switch (kind) {
			case TILES -> {
				SyllableTileCard tiles = SyllableTileCard.create(info, random);
				if (tiles != null) {
					return tiles;
				}
				return RecognitionMc4Card.create(info, pool, tags, random);
			}
			case SELF_GRADE -> {
				return new RecallSelfGradeCard(info);
			}
			default -> {
				return RecognitionMc4Card.create(info, pool, tags, random);
			}
		}
	}
}
