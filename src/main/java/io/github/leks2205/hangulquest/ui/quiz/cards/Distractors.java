package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Wrong options for a multiple-choice card. Similar words in one question make each other harder to
 * learn, so they come from other semantic sets than the right answer; the same category is preferred
 * because those are the plausible mistakes. No two options show the same text.
 */
public final class Distractors {
	private Distractors() {
	}

	public static List<Word> pick(Word word, Collection<Word> pool, LexiconTags tags, Lang lang, int count,
								  Random random) {
		String own = key(word.gloss(lang));
		String ownSet = tags.semSet(word);
		List<Word> sameCategory = new ArrayList<>();
		List<Word> otherCategory = new ArrayList<>();
		for (Word candidate : pool) {
			if (candidate.id.equals(word.id) || candidate.phrase || candidate.ko == null
					|| candidate.gloss(lang).isBlank() || key(candidate.gloss(lang)).equals(own)
					|| tags.semSet(candidate).equals(ownSet)) {
				continue;
			}
			(candidate.category == word.category ? sameCategory : otherCategory).add(candidate);
		}
		Collections.shuffle(sameCategory, random);
		Collections.shuffle(otherCategory, random);

		List<Word> picked = new ArrayList<>();
		Set<String> shown = new HashSet<>();
		shown.add(own);
		for (List<Word> tier : List.of(sameCategory, otherCategory)) {
			for (Word candidate : tier) {
				if (picked.size() >= count) {
					return picked;
				}
				if (shown.add(key(candidate.gloss(lang)))) {
					picked.add(candidate);
				}
			}
		}
		return picked;
	}

	private static String key(String gloss) {
		return gloss.strip().toLowerCase(Locale.ROOT);
	}
}
