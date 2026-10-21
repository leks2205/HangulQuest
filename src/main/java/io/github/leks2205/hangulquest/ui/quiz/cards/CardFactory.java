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

	public enum Kind { MC4, SELF_GRADE, TILES }

	private CardFactory() {
	}

	/** True once the word's stability is at least three days. Words never reviewed are not unlocked. */
	public static boolean productiveUnlocked(WordProgress progress) {
		return progress != null && !progress.known && progress.fsrs != null && !progress.fsrs.isNew()
				&& progress.fsrs.stability >= UNLOCK_STABILITY_DAYS;
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
		CardInfo info = CardInfo.of(catalog, word);
		Kind kind = choose(productiveUnlocked(progress), SyllableTileCard.eligible(word.ko), random);
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
