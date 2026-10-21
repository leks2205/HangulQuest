package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The text of a step in the language the player sees: the Korean clue and hint (authored or
 * generated), their translation and the finale line. The generated Korean does not depend on the
 * interface language, so it is built once per step and level.
 */
final class QuestTextService {
	private static final int LEVELS = 6;

	private final Catalog catalog;
	private final SentenceBuilder.Namer namer;
	/** Generated clue sentences, by step and Korean level (index 1..6). */
	private final Map<QuestStep, String[]> clues = new IdentityHashMap<>();
	/** Generated hint sentences, by step. */
	private final Map<QuestStep, String> hints = new IdentityHashMap<>();

	QuestTextService(Catalog catalog) {
		this.catalog = catalog;
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
				return catalog.byItemId(id).map(word -> word.ko).orElse(null);
			}
		};
	}

	SentenceBuilder.Namer namer() {
		return namer;
	}

	/** Drops the cached sentences, for when the trails were replaced. */
	void invalidate() {
		clues.clear();
		hints.clear();
	}

	Word firstWord(QuestStep step) {
		if (step == null || step.teach().isEmpty()) {
			return null;
		}
		return catalog.byConceptId(step.teach().get(0)).orElse(null);
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

	/** The Korean clue at the trail's level. A step with conditions always gets the generated text. */
	String clueKo(QuestStep step, int koreanLevel) {
		if (step == null) {
			return "";
		}
		if (step.conditions().isEmpty() && step.hasClueKo()) {
			return step.clueKo();
		}
		int level = Math.max(1, Math.min(LEVELS, koreanLevel));
		String[] byLevel = clues.computeIfAbsent(step, key -> new String[LEVELS + 1]);
		String cached = byLevel[level];
		if (cached == null) {
			// Every condition must show up in the Korean sentence; generated text guarantees it.
			cached = SentenceBuilder.normal(step.goal(), firstWord(step), step.conditions(),
					level, "finale".equals(step.id()), namer);
			byLevel[level] = cached;
		}
		return cached;
	}

	/** Translation of the clue in the interface language (English or German). */
	String clueTranslation(QuestStep step) {
		if (step == null) {
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
			return SentenceBuilder.hint(step.goal(), word, step.conditions(), namer);
		}
		return hints.computeIfAbsent(step,
				key -> SentenceBuilder.hint(key.goal(), word, key.conditions(), namer));
	}

	/** The finale line of a finished trail in the interface language. */
	String finaleText(Questline trail) {
		return trail.finale(Lang.current());
	}
}
