package io.github.leks2205.hangulquest.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Whether a story is open for this player. Extended reading only works when almost every word is known
 * (Laufer and Ravenhorst-Kalovski: 95% for the gist, 98% for comfortable reading), so a story opens when its
 * trails are done, enough words are met and at least {@value #REQUIRED_PERCENT}% of its running words are
 * known. A word that matches nothing counts as unknown.
 */
public final class StoryCoverage {
	public static final int REQUIRED_PERCENT = 98;

	/** The share of known words among the running words of a text. */
	public record Coverage(int known, int total) {
		/** Whole percent, rounded down so that 97.9 never shows as 98. */
		public int percent() {
			return total == 0 ? 100 : known * 100 / total;
		}

		public boolean meets() {
			return known * 100 >= total * REQUIRED_PERCENT;
		}
	}

	/** Everything the Library shows about one story. */
	public record Status(Story story, List<String> missingTrails, int wordsMet, boolean wordsEnough,
						 Coverage coverage, List<StoryText.Token> newWords, List<List<StoryText.Token>> paragraphs,
						 Set<String> glossed) {
		/** True when the story can be read. */
		public boolean open() {
			return missingTrails.isEmpty() && wordsEnough && coverage.meets();
		}

		/** True for a word the reader shows with its gloss: glossed by the story and still new to the player. */
		public boolean isGlossed(StoryText.Token token) {
			return token.word() && !token.known() && token.id() != null && glossed.contains(token.id());
		}
	}

	private StoryCoverage() {
	}

	public static Coverage of(List<StoryText.Token> tokens) {
		return of(tokens, Set.of());
	}

	/** Like {@link #of(List)}, with the glossed words counted as known: the reader explains them in place. */
	public static Coverage of(List<StoryText.Token> tokens, Set<String> glossed) {
		int total = 0;
		int known = 0;
		for (StoryText.Token token : tokens) {
			if (token.word()) {
				total++;
				if (token.known() || (token.id() != null && glossed.contains(token.id()))) {
					known++;
				}
			}
		}
		return new Coverage(known, total);
	}

	/**
	 * @param trailDone says whether a trail is finished
	 * @param wordsMet  how many words the player has met so far
	 */
	public static Status status(Story story, StoryVocabulary vocabulary, Predicate<String> trailDone, int wordsMet) {
		List<List<StoryText.Token>> paragraphs = new ArrayList<>();
		List<StoryText.Token> all = new ArrayList<>();
		for (String paragraph : story.paragraphs()) {
			List<StoryText.Token> tokens = StoryText.analyze(paragraph, vocabulary);
			paragraphs.add(tokens);
			all.addAll(tokens);
		}
		List<String> missing = new ArrayList<>();
		for (String trail : story.unlock().trails()) {
			if (!trailDone.test(trail)) {
				missing.add(trail);
			}
		}
		Set<String> glossed = Set.copyOf(story.glossed());
		return new Status(story, missing, wordsMet, wordsMet >= story.unlock().minWords(), of(all, glossed),
				newWords(all, glossed), paragraphs, glossed);
	}

	/** The words that keep a story closed, each once and in reading order; a glossed word does not. */
	static List<StoryText.Token> newWords(List<StoryText.Token> tokens, Set<String> glossed) {
		Map<String, StoryText.Token> seen = new LinkedHashMap<>();
		for (StoryText.Token token : tokens) {
			if (token.word() && !token.known() && (token.id() == null || !glossed.contains(token.id()))) {
				seen.putIfAbsent(token.id() != null ? token.id() : token.text(), token);
			}
		}
		return List.copyOf(seen.values());
	}
}
