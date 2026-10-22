package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.quest.goal.Tiers;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The text of a step in the language the player sees: the Korean clue and hint (authored or
 * generated), their translation and the finale line. The generated Korean does not depend on the
 * interface language, so it is built once per step and level.
 *
 * <p>A trail asks for a Korean level, but the text is written at most one level above what the player
 * has met (see {@link #effectiveLevel}), so a new player on a hard trail still reads sentences built from
 * what they have been shown.
 */
final class QuestTextService {
	private static final int LEVELS = 6;

	private final Catalog catalog;
	private final LearningSink learn;
	private final SentenceBuilder.Namer namer;
	/** Generated clue sentences, by step and Korean level (index 1..6). */
	private final Map<QuestStep, Map<String, String>> clues = new IdentityHashMap<>();
	/** Generated hint sentences, by step. */
	private final Map<QuestStep, String> hints = new IdentityHashMap<>();
	/** The grammar.json constructions of a generated clue, by step and written level. */
	private final Map<QuestStep, Map<Integer, List<String>>> grammar = new IdentityHashMap<>();

	QuestTextService(Catalog catalog, LearningSink learn) {
		this.catalog = catalog;
		this.learn = learn;
		this.namer = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return catalog.byBiomeId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String entity(String id) {
				return catalog.byEntityId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String block(String id) {
				return catalog.byItemId(id).map(word -> Tiers.qualify(id, word.ko)).orElse(null);
			}

			@Override
			public String owner(String id) {
				return catalog.byItemId(id).filter(Word::curated).map(word -> word.conceptId).orElse(null);
			}
		};
	}

	SentenceBuilder.Namer namer() {
		return namer;
	}

	/** The namer for one step: the registry names, plus what the steps before it placed and built. */
	private SentenceBuilder.Namer namerFor(QuestStep step) {
		return io.github.leks2205.hangulquest.quest.spatial.StepNamer.of(namer, step);
	}

	/** Drops the cached sentences, for when the trails were replaced. */
	void invalidate() {
		clues.clear();
		hints.clear();
		grammar.clear();
	}

	/**
	 * The level the clue is written at: the trail's, but no more than one above the player's grammar
	 * level (the new construction), and never below 1.
	 */
	int effectiveLevel(int koreanLevel) {
		int level = Math.max(1, Math.min(LEVELS, koreanLevel));
		return Math.min(level, Math.max(1, learn.grammarLevel() + 1));
	}

	/** True when the clue is generated from the goal (so it follows the ladder) and not authored. */
	private static boolean generated(QuestStep step) {
		return !step.hasClueKo();
	}

	/** The constructions of grammar.json that the clue of this step carries; none for an authored clue. */
	List<String> grammarIds(QuestStep step, int koreanLevel) {
		if (step == null || !generated(step)) {
			return List.of();
		}
		int level = effectiveLevel(koreanLevel);
		SentenceBuilder.Namer clueNamer = clueNamer(step);
		return grammar.computeIfAbsent(step, key -> new java.util.HashMap<>()).computeIfAbsent(level, key -> {
			List<String> emitted = SentenceBuilder.grammar(step.goal(), firstWord(step), verbWord(step),
					step.conditions(), key, "finale".equals(step.id()), clueNamer);
			return GrammarBook.shared().resolve(emitted, Ladder.shared());
		});
	}

	/** The step's namer plus the extra nouns the clue may say ({@link ClueWords}). */
	private SentenceBuilder.Namer clueNamer(QuestStep step) {
		return ClueWords.namer(namerFor(step), ClueWords.extras(step, catalog, learn::isKnown));
	}

	Word firstWord(QuestStep step) {
		if (step == null || step.teach().isEmpty()) {
			return null;
		}
		return catalog.byConceptId(step.teach().get(0)).orElse(null);
	}

	/** The first verb the step teaches, in any place of its list (요리하다 next to 닭고기), or null. */
	Word verbWord(QuestStep step) {
		if (step == null) {
			return null;
		}
		for (String concept : step.teach()) {
			Word word = catalog.byConceptId(concept).orElse(null);
			if (word != null && word.category == io.github.leks2205.hangulquest.data.Category.ACTIONS
					&& word.ko.endsWith("다")) {
				return word;
			}
		}
		return null;
	}

	List<Word> words(QuestStep step) {
		List<Word> words = new java.util.ArrayList<>();
		if (step != null) {
			for (String concept : step.teach()) {
				catalog.byConceptId(concept).ifPresent(words::add);
			}
		}
		return words;
	}

	/**
	 * The Korean clue at the trail's level. An authored clue wins, also on a step with conditions, where
	 * it gets the condition phrases it lacks in front. A step without authored text gets the generated one.
	 */
	String clueKo(QuestStep step, int koreanLevel) {
		if (step == null) {
			return "";
		}
		boolean authored = !generated(step);
		if (authored && step.conditions().isEmpty()) {
			return step.clueKo();
		}
		int level = effectiveLevel(koreanLevel);
		SentenceBuilder.Namer clueNamer = clueNamer(step);
		// what the player knows can change which nouns the clue says, so the key holds them
		List<Word> extras = ClueWords.extras(step, catalog, learn::isKnown);
		String cacheKey = level + extras.stream().map(word -> word.id).toList().toString();
		Map<String, String> byLevel = clues.computeIfAbsent(step, key -> new java.util.HashMap<>());
		String cached = byLevel.get(cacheKey);
		if (cached == null) {
			// Every condition must show up in the Korean sentence: generated text guarantees it, and an
			// authored clue gets the phrases it lacks.
			cached = authored
					? SentenceBuilder.authored(step.goal(), step.clueKo(), step.conditions(), firstWord(step),
							clueNamer, level)
					: SentenceBuilder.normal(step.goal(), firstWord(step), verbWord(step), step.conditions(),
							level, "finale".equals(step.id()), clueNamer);
			byLevel.put(cacheKey, cached);
		}
		return cached;
	}

	/** Translation of the clue in the interface language (English or German). */
	String clueTranslation(QuestStep step) {
		if (step == null || step.reverse()) {
			return "";
		}
		return step.clue();
	}

	/** The easy sentence: the authored hint, else one generated at level 1. */
	String hintKo(QuestStep step, int koreanLevel) {
		return hintKo(step, koreanLevel, firstWord(step));
	}

	/** Same, built around {@code word} (a running sidequest keeps its own word). */
	String hintKo(QuestStep step, int koreanLevel, Word word) {
		if (step == null) {
			return "";
		}
		if (step.hintKo() != null && !step.hintKo().isBlank()) {
			// Authored hints win even when the step has conditions; they are written to include them.
			return step.hintKo();
		}
		if (word == null) {
			return clueKo(step, koreanLevel);
		}
		if (word != firstWord(step)) {
			return SentenceBuilder.hint(step.goal(), word, verbWord(step), words(step), step.conditions(),
					namerFor(step));
		}
		return hints.computeIfAbsent(step, key -> SentenceBuilder.hint(key.goal(), word, verbWord(key), words(key),
				key.conditions(), namerFor(key)));
	}

	/** The finale line of a finished trail in the interface language. */
	String finaleText(Questline trail) {
		return trail.finale(Lang.current());
	}
}
