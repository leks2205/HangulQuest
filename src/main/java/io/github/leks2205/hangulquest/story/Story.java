package io.github.leks2205.hangulquest.story;

import java.util.List;

/**
 * A short graded text for the Library. {@code words} are the catalog ids it uses and {@code grammar}
 * the construction ids; both are checked against what the text really contains. Titles and the English
 * and German paragraph glosses are language keys ({@link #titleKey}, {@link #paragraphKey}).
 *
 * <p>{@code glossed} are at most {@value #MAX_GLOSSED} of those words that a graded reader explains in place: they count
 * as known for the unlock rule, the reader shows their gloss, and reading the story teaches them.
 */
public record Story(String id, String chapter, int level, String titleKo, Unlock unlock,
					List<String> paragraphs, List<String> words, List<String> grammar, List<String> glossed) {
	public static final int MAX_GLOSSED = 3;

	/** What opens a story besides its words: finished trails and a number of words met. */
	public record Unlock(List<String> trails, int minWords) {
		public Unlock {
			trails = List.copyOf(trails);
		}
	}

	public Story {
		paragraphs = List.copyOf(paragraphs);
		words = List.copyOf(words);
		grammar = List.copyOf(grammar);
		glossed = List.copyOf(glossed);
	}

	/** A story without glossed words. */
	public Story(String id, String chapter, int level, String titleKo, Unlock unlock,
				 List<String> paragraphs, List<String> words, List<String> grammar) {
		this(id, chapter, level, titleKo, unlock, paragraphs, words, grammar, List.of());
	}

	public String titleKey() {
		return "hangulquest.story." + id + ".title";
	}

	/** The gloss of paragraph {@code index} (0-based); the registry line id is the same without the prefix. */
	public String paragraphKey(int index) {
		return "hangulquest.story." + id + ".p" + (index + 1);
	}

	/** Hangul syllables in the paragraphs: the length measure of the roadmap (80 to 200 for the first stories). */
	public int syllables() {
		int count = 0;
		for (String paragraph : paragraphs) {
			for (int i = 0; i < paragraph.length(); i++) {
				char c = paragraph.charAt(i);
				if (c >= '가' && c <= '힣') {
					count++;
				}
			}
		}
		return count;
	}
}
