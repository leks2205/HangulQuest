package io.github.leks2205.hangulquest.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
						 Coverage coverage, List<StoryText.Token> newWords, List<List<StoryText.Token>> paragraphs) {
		/** True when the story can be read. */
		public boolean open() {
			return missingTrails.isEmpty() && wordsEnough && coverage.meets();
		}
	}

	private StoryCoverage() {
	}

	public static Coverage of(List<StoryText.Token> tokens) {
		int total = 0;
		int known = 0;
		for (StoryText.Token token : tokens) {
			if (token.word()) {
				total++;
				if (token.known()) {
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
		return new Status(story, missing, wordsMet, wordsMet >= story.unlock().minWords(), of(all), newWords(all), paragraphs);
	}

	/** The words that keep a story closed, each once and in reading order. */
	static List<StoryText.Token> newWords(List<StoryText.Token> tokens) {
		Map<String, StoryText.Token> seen = new LinkedHashMap<>();
		for (StoryText.Token token : tokens) {
			if (token.word() && !token.known()) {
				seen.putIfAbsent(token.id() != null ? token.id() : token.text(), token);
			}
		}
		return List.copyOf(seen.values());
	}
}
