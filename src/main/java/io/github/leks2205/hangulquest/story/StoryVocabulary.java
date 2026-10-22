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
		EOYO("-아/어요", "L1.eoyo"), SEYO("-(으)세요", "L2.seyo"), GO("-고", "L2.go_sequence"),
		// the forms of levels 3 to 6 that stand in one eojeol; the particles after them (전에, 동안) are lexicon words
		PAST("-았/었어요", "L3.past"), JI("-지", "L3.ji_anayo"), GI("-기", "L3.gi_jeone"),
		MYEON("-(으)면", "L4.myeon"), ASEO("-아/어서", "L4.eoseo"), EURO("-(으)러", "L4.eureo_gayo"),
		JIMAN("-지만", "L4.jiman"), EOYA("-아/어야", "L4.eoya_haeyo"), EULGE("-(으)ㄹ게요", "L4.eulgeyo"),
		ADN_PAST("-(으)ㄴ", "L5.adn_past_eun"), ADN_ADJ("-(으)ㄴ", "L5.adn_adj_eun"),
		ADN_PRESENT("-는", "L5.adn_present_neun"), ADN_FUTURE("-(으)ㄹ", "L5.adn_future_eul"),
		NIKKA("-(으)니까", "L5.eunikka"), ADO("-아/어도", "L5.eodo"), NEUNDE("-는데", "L5.neunde"),
		EULSUROK("-(으)ㄹ수록", "L6.eulsurok"), DEUSI("-듯이", "L6.deusi");

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
	/** Names of more than one word (엔더 드래곤, 철 주괴), by their spelling with single spaces. */
	private final Map<String, Hit> phrases = new HashMap<>();
	/** The forms of levels 3 to 6: a noun or function word that is spelled the same wins over them (눈, 안, 말). */
	private final Map<String, VerbMatch> later = new HashMap<>();

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
			if (word.phrase || word.ko == null || word.ko.isBlank()) {
				continue;
			}
			Hit hit = new Hit(word.id, word.gloss(lang), knows.test(word.id));
			if (word.ko.contains(" ")) {
				Hit before = vocabulary.phrases.get(word.ko);
				if (before == null || (!before.known() && hit.known())) {
					vocabulary.phrases.put(word.ko, hit);
				}
				continue;
			}
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
		vocabulary.later.forEach((form, match) -> {
			if (!vocabulary.nouns.containsKey(form)) {
				vocabulary.verbs.putIfAbsent(form, match);
			}
		});
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
			// a story may say 먹으세요 where the generator says 드세요
			verbs.putIfAbsent(word.plainSeyo(), new VerbMatch(hit, dictionary, word.stem(), Ending.SEYO,
					Ending.SEYO.grammar()));
		}
		verbs.putIfAbsent(word.go(), new VerbMatch(hit, dictionary, word.stem(), Ending.GO, Ending.GO.grammar()));
		laterForm(hit, word, Ending.PAST, word.past());
		laterForm(hit, word, Ending.JI, word.stem() + "지");
		laterForm(hit, word, Ending.GI, word.stem() + "기");
		laterForm(hit, word, Ending.MYEON, word.myeon());
		laterForm(hit, word, Ending.ASEO, word.aseo());
		laterForm(hit, word, Ending.EURO, word.eureo());
		laterForm(hit, word, Ending.JIMAN, word.jiman());
		laterForm(hit, word, Ending.EOYA, word.eo() + "야");
		laterForm(hit, word, Ending.EULGE, word.eulgeyo());
		laterForm(hit, word, word.adjective() ? Ending.ADN_ADJ : Ending.ADN_PAST, word.eun());
		laterForm(hit, word, Ending.ADN_PRESENT, word.neun());
		laterForm(hit, word, Ending.ADN_FUTURE, word.future());
		laterForm(hit, word, Ending.NIKKA, word.nikka());
		laterForm(hit, word, Ending.ADO, word.ado());
		laterForm(hit, word, Ending.NEUNDE, word.neunde());
		laterForm(hit, word, Ending.EULSUROK, word.eulsurok());
		laterForm(hit, word, Ending.DEUSI, word.deusi());
	}

	private void laterForm(Hit hit, Conjugator.Word word, Ending ending, String form) {
		if (form != null && !form.isBlank() && !form.contains(" ")) {
			later.putIfAbsent(form, new VerbMatch(hit, word.dictionary(), word.stem(), ending, ending.grammar()));
		}
	}

	@Override
	public Hit find(String stem) {
		List<Reading> readings = nouns.get(stem);
		return readings == null ? null : readings.get(0).hit();
	}

	/** The name of several words (엔더 드래곤) written with single spaces, or null. */
	public Hit findPhrase(String name) {
		return phrases.get(name);
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
