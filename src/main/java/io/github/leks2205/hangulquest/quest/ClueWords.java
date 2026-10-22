package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which of the nouns a step teaches the clue may say. The first one is the word of the step and always stands
 * in the clue. A further noun stands there too when that keeps the clue to at most one unknown word: it counts as
 * known when the player knows it or an earlier step of the trail already taught it. A new one stays in the
 * hint, which names every noun the goal has a place for.
 */
public final class ClueWords {
	private ClueWords() {
	}

	/** The nouns after the first that the clue of {@code step} names as well, in teaching order. */
	public static List<Word> extras(QuestStep step, Catalog catalog, Predicate<String> known) {
		List<Word> extras = new ArrayList<>();
		if (step == null || step.teach().size() < 2) {
			return extras;
		}
		Word first = catalog.byConceptId(step.teach().get(0)).orElse(null);
		int unknown = first == null || met(step, first, known) ? 0 : 1;
		for (int i = 1; i < step.teach().size(); i++) {
			Word word = catalog.byConceptId(step.teach().get(i)).orElse(null);
			if (word == null || word == first || word.category == Category.ACTIONS && word.ko.endsWith("다")) {
				continue;
			}
			if (met(step, word, known)) {
				extras.add(word);
			} else if (unknown < 1) {
				unknown++;
				extras.add(word);
			}
		}
		return extras;
	}

	private static boolean met(QuestStep step, Word word, Predicate<String> known) {
		if (known.test(word.id)) {
			return true;
		}
		for (QuestStep earlier : step.earlier()) {
			if (earlier.teach().contains(word.conceptId)) {
				return true;
			}
		}
		return false;
	}

	/** {@code base} extended by the words, so the sentence says them for the ids they name. */
	public static SentenceBuilder.Namer namer(SentenceBuilder.Namer base, List<Word> extras) {
		SentenceBuilder.Namer all = base;
		for (Word extra : extras) {
			all = SentenceContext.taught(extra, all);
		}
		return all;
	}
}
