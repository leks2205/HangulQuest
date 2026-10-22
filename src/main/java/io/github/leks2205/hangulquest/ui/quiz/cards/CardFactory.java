package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;

import java.util.ArrayList;
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

	/** Chance that a review is a grammar cloze, when the player has met a construction that has one. */
	public static final double GRAMMAR_SHARE = 0.25;

	/** Typed cards wait for a memory that has held for a week: typing is the hardest card. */
	public static final double TYPED_STABILITY_DAYS = 7;
	/** Chance that a productive turn is a typed card, when the word and the player allow it. */
	public static final double TYPED_SHARE = 1.0 / 3;

	public enum Kind { MC4, SELF_GRADE, TILES, TYPED }

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
		return choose(productiveUnlocked, tilesPossible, false, random);
	}

	/** True when the word may be asked by typing: stable for a week, typeable, and the player has not turned it off. */
	public static boolean typedAllowed(WordProgress progress, Word word, boolean typedCards) {
		return typedCards && productiveUnlocked(progress, TYPED_STABILITY_DAYS) && TypedCard.eligible(word.ko);
	}

	/** Same as {@link #choose(boolean, boolean, Random)}, with typed cards in the mix when {@code typedAllowed}. */
	public static Kind choose(boolean productiveUnlocked, boolean tilesPossible, boolean typedAllowed, Random random) {
		if (!productiveUnlocked || random.nextDouble() >= PRODUCTIVE_SHARE) {
			return Kind.MC4;
		}
		if (typedAllowed && random.nextDouble() < TYPED_SHARE) {
			return Kind.TYPED;
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
		return create(word, progress, catalog, tags, random, unlockDays, false);
	}

	/** Same, and a typed card may come up when {@code typedCards} (the player's setting) is on. */
	public static Card create(Word word, WordProgress progress, Catalog catalog, LexiconTags tags, Random random,
							  double unlockDays, boolean typedCards) {
		CardInfo info = CardInfo.of(catalog, word);
		Kind kind = choose(productiveUnlocked(progress, unlockDays), SyllableTileCard.eligible(word.ko),
				typedAllowed(progress, word, typedCards), random);
		return create(kind, info, catalog.all(), tags, random);
	}

	public static Card create(Kind kind, CardInfo info, List<Word> pool, LexiconTags tags, Random random) {
		switch (kind) {
			case TYPED -> {
				TypedCard typed = TypedCard.create(info);
				return typed != null ? typed : new RecallSelfGradeCard(info);
			}
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

	/** True when this review should try a grammar cloze first. */
	public static boolean grammarTurn(Random random) {
		return random.nextDouble() < GRAMMAR_SHARE;
	}

	/**
	 * A cloze for a construction the player has met and not yet mastered, the least secure first (lowest
	 * p(L), a random one among equals). Null when the player has met none.
	 */
	public static ClozeGrammarCard grammar(GrammarProgress progress, GrammarBook book, Random random) {
		List<GrammarBook.Construction> weakest = new ArrayList<>();
		double lowest = Double.MAX_VALUE;
		for (GrammarBook.Construction construction : book.all()) {
			if (construction.clozes().isEmpty() || !progress.met(construction.id()) || progress.mastered(construction.id())) {
				continue;
			}
			double pL = progress.pL(construction.id());
			if (pL < lowest - 1e-9) {
				lowest = pL;
				weakest.clear();
			}
			if (pL <= lowest + 1e-9) {
				weakest.add(construction);
			}
		}
		return weakest.isEmpty() ? null : ClozeGrammarCard.create(weakest.get(random.nextInt(weakest.size())), random);
	}
}
