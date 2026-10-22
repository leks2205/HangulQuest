package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ObjectKind;
import io.github.leks2205.hangulquest.quest.goal.SentenceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which of the nouns a step teaches the clue may say. The first one is the word of the step and always stands
 * in the clue. A further noun stands there too when that keeps the clue to at most one unknown word: it counts as
 * known when the player knows it or an earlier step of the trail already taught it. A new one stays in the
 * hint, which names every noun the goal has a place for. A word that names the item in the player's hand always
 * stands in the clue. In a sequence a noun that is part of the exact name the clue would say anyway (묘목 in
 * 가문비나무 묘목) stands there too, so the clue and the hint pick the same word for each part.
 */
public final class ClueWords {
	private ClueWords() {
	}

	/** The nouns after the first that the clue of {@code step} names as well, in teaching order. */
	public static List<Word> extras(QuestStep step, Catalog catalog, Predicate<String> known) {
		return extras(step, catalog, known, SentenceBuilder.Namer.NONE);
	}

	/** Same, with the namer that knows the registry names the clue would say otherwise. */
	public static List<Word> extras(QuestStep step, Catalog catalog, Predicate<String> known,
									SentenceBuilder.Namer registry) {
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
			if (met(step, word, known) || namesHeldItem(step, word) || insideRegistryName(step, word, registry)) {
				extras.add(word);
			} else if (unknown < 1) {
				unknown++;
				extras.add(word);
			}
		}
		return extras;
	}

	/**
	 * A word for the item in the player's hand is spoken in the clue as in the hint: "배를 들고", not "참나무 보트를
	 * 들고". It stands in for the registry name, so it adds no word the clue would not have.
	 */
	private static boolean namesHeldItem(QuestStep step, Word word) {
		for (Condition condition : step.conditions()) {
			if (condition instanceof Condition.Holding holding
					&& SentenceContext.wordNames(word, ObjectKind.ITEM, holding.item())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Only in a sequence: the word is the head of the registry name of a thing a part points at, so saying the word
	 * adds no word the clue would not have ("묘목" for the spruce sapling, whose name is 가문비나무 묘목).
	 */
	private static boolean insideRegistryName(QuestStep step, Word word, SentenceBuilder.Namer registry) {
		if (!(step.goal() instanceof Goal.Sequence)) {
			return false;
		}
		for (GoalType.Mention mention : GoalTypes.of(step.goal()).mentions(step.goal())) {
			ObjectKind kind = mention.kind();
			if (kind != ObjectKind.BLOCK && kind != ObjectKind.ITEM && kind != ObjectKind.ENTITY
					|| !SentenceContext.wordNames(word, kind, mention.id(), registry)) {
				continue;
			}
			String name = switch (kind) {
				case BLOCK -> registry.block(mention.id());
				case ITEM -> registry.item(mention.id());
				default -> registry.entity(mention.id());
			};
			if (name != null && name.endsWith(word.ko)) {
				return true;
			}
		}
		return false;
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
