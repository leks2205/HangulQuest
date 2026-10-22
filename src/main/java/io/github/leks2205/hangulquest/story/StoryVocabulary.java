package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage.Hit;
import io.github.leks2205.hangulquest.quest.grammar.Conjugator;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a story's words are looked up in: nouns and function words by stem, verbs and adjectives by every
 * conjugated form. The verb side is the reverse analysis: the surface forms of each dictionary form are
 * generated with the {@link Conjugator} (-아/어요, -(으)세요, -고, the forms levels 1 and 2 teach) and
 * indexed, so 써요 finds 쓰다, 도와요 finds 돕다 and 지어요 finds 짓다, irregulars included. Whether the
 * player knows a word comes from {@code knows}; a lexicon entry is known when its file says so.
 */
public final class StoryVocabulary implements DialogueCoverage.Vocabulary {
	/** An ending a verb takes, with the grammar.json construction that teaches it. */
	public enum Ending {
		EOYO("-아/어요", "L1.eoyo"), SEYO("-(으)세요", "L2.seyo"), GO("-고", "L2.go_sequence");

		private final String label;
		private final String grammar;

		Ending(String label, String grammar) {
			this.label = label;
			this.grammar = grammar;
		}

		public String label() {
			return label;
		}

		public String grammar() {
			return grammar;
		}
	}

	/** A conjugated form that was found: the word, its dictionary form and stem, the ending and its construction. */
	public record VerbMatch(Hit hit, String dictionary, String stem, Ending ending, String grammar) {
	}

	/** A stem's reading; a null {@code tails} accepts any particle. */
	private record Reading(Hit hit, Set<String> tails) {
		boolean accepts(String tail) {
			return tails == null || tails.contains(tail);
		}
	}

	private final Map<String, List<Reading>> nouns = new HashMap<>();
	/** Function words that carry a construction, by stem; each with the particles it is meant for (empty: any). */
	private final Map<String, List<Map.Entry<Set<String>, String>>> formGrammar = new HashMap<>();
	private final Map<String, VerbMatch> verbs = new HashMap<>();

	private StoryVocabulary() {
	}

	public static StoryVocabulary of(Catalog catalog, StoryLexicon lexicon, Predicate<String> knows, Lang lang) {
		StoryVocabulary vocabulary = new StoryVocabulary();
		Set<String> functionForms = new java.util.HashSet<>();
		for (StoryLexicon.Entry entry : lexicon.entries()) {
			Hit hit = new Hit("lexicon:" + entry.id(), entry.gloss(lang), entry.known());
			if (entry.isVerb()) {
				vocabulary.addVerb(hit, entry.verb(), entry.grammar());
			} else {
				functionForms.addAll(entry.forms());
			}
			Set<String> tails = entry.tails().isEmpty() ? null : Set.copyOf(entry.tails());
			for (String form : entry.forms()) {
				vocabulary.nouns.computeIfAbsent(form, key -> new ArrayList<>()).add(new Reading(hit, tails));
				if (!entry.grammar().isEmpty()) {
					vocabulary.formGrammar.computeIfAbsent(form, key -> new ArrayList<>())
							.add(Map.entry(tails == null ? Set.of() : tails, entry.grammar()));
				}
			}
		}
		// Words that share a spelling (two kinds of 문): the player knows the spelling when he knows either.
		Map<String, Hit> byKo = new HashMap<>();
		for (Word word : catalog.all()) {
			if (word.phrase || word.ko == null || word.ko.isBlank() || word.ko.contains(" ")) {
				continue;
			}
			Hit hit = new Hit(word.id, word.gloss(lang), knows.test(word.id));
			if (word.category == Category.ACTIONS && word.ko.endsWith("다")) {
				vocabulary.addVerb(hit, word.ko, "");
			} else {
				Hit before = byKo.get(word.ko);
				if (before == null || (!before.known() && hit.known())) {
					byKo.put(word.ko, hit);
				}
			}
		}
		byKo.forEach((ko, hit) -> vocabulary.nouns.computeIfAbsent(ko, key -> new ArrayList<>())
				.add(new Reading(hit, null)));
		// 그리고 is the conjunction before it is the -고 form of 그리다: a function word of the lexicon wins.
		vocabulary.verbs.entrySet().removeIf(entry -> functionForms.contains(entry.getKey())
				&& !entry.getValue().hit().id().startsWith("lexicon:"));
		return vocabulary;
	}

	private void addVerb(Hit hit, String dictionary, String grammar) {
		Conjugator.Word word;
		try {
			word = Conjugator.of(dictionary);
		} catch (IllegalArgumentException notAVerb) {
			return;
		}
		boolean existential = dictionary.equals("있다") || dictionary.equals("없다");
		String eoyoGrammar = !grammar.isEmpty() ? grammar : existential ? "L1.itda_eopda" : Ending.EOYO.grammar();
		verbs.putIfAbsent(word.eoyo(), new VerbMatch(hit, dictionary, word.stem(), Ending.EOYO, eoyoGrammar));
		if (!word.adjective()) {
			verbs.putIfAbsent(word.seyo(), new VerbMatch(hit, dictionary, word.stem(), Ending.SEYO, Ending.SEYO.grammar()));
		}
		verbs.putIfAbsent(word.go(), new VerbMatch(hit, dictionary, word.stem(), Ending.GO, Ending.GO.grammar()));
	}

	@Override
	public Hit find(String stem) {
		List<Reading> readings = nouns.get(stem);
		return readings == null ? null : readings.get(0).hit();
	}

	/** The word this stem is when the particle {@code tail} follows (it may be empty): the first reading that accepts it. */
	public Hit find(String stem, String tail) {
		List<Reading> readings = nouns.get(stem);
		if (readings != null) {
			for (Reading reading : readings) {
				if (reading.accepts(tail)) {
					return reading.hit();
				}
			}
		}
		return null;
	}

	/** The verb form this eojeol is, or null. */
	public VerbMatch verb(String surface) {
		return verbs.get(surface);
	}

	/** The construction a function word carries (안, 앞에), or null. */
	public String grammarOf(String form, String tail) {
		List<Map.Entry<Set<String>, String>> candidates = formGrammar.get(form);
		if (candidates != null) {
			for (Map.Entry<Set<String>, String> candidate : candidates) {
				if (candidate.getKey().isEmpty() || candidate.getKey().contains(tail)) {
					return candidate.getValue();
				}
			}
		}
		return null;
	}

	/** Every conjugated form that can be recognised, for tests and tools. */
	public List<String> verbForms() {
		return new ArrayList<>(verbs.keySet());
	}
}
