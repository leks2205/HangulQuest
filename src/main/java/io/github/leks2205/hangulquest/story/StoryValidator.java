package io.github.leks2205.hangulquest.story;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Checks a story against the game it is loaded into: chapter, trails, words, grammar and language keys. */
public final class StoryValidator {
	/** What a story may refer to. A null part is not checked. */
	public record Env(Set<String> chapterIds, Set<String> trailIds, Set<String> grammarIds,
					  Predicate<String> wordExists, Predicate<String> keyExists) {
		public static Env none() {
			return new Env(null, null, null, null, null);
		}
	}

	private StoryValidator() {
	}

	public static List<String> validate(Story story, Env env) {
		List<String> problems = new ArrayList<>();
		String where = story.id();
		if (env.chapterIds() != null && !env.chapterIds().contains(story.chapter())) {
			problems.add(where + ": unknown chapter " + story.chapter());
		}
		if (env.trailIds() != null) {
			for (String trail : story.unlock().trails()) {
				if (!env.trailIds().contains(trail)) {
					problems.add(where + ": unlock names the unknown trail " + trail);
				}
			}
		}
		if (env.grammarIds() != null) {
			for (String id : story.grammar()) {
				if (!env.grammarIds().contains(id)) {
					problems.add(where + ": unknown grammar point " + id);
				}
			}
		}
		if (env.wordExists() != null) {
			for (String id : story.words()) {
				if (!env.wordExists().test(id)) {
					problems.add(where + ": unknown word " + id);
				}
			}
		}
		if (new HashSet<>(story.words()).size() != story.words().size()) {
			problems.add(where + ": a word is listed twice");
		}
		if (story.glossed().size() > Story.MAX_GLOSSED) {
			problems.add(where + ": glossed lists " + story.glossed().size() + " words, at most " + Story.MAX_GLOSSED);
		}
		if (new HashSet<>(story.glossed()).size() != story.glossed().size()) {
			problems.add(where + ": a glossed word is listed twice");
		}
		for (String id : story.glossed()) {
			if (env.wordExists() != null && !env.wordExists().test(id)) {
				problems.add(where + ": glossed names the unknown word " + id);
			}
			if (!story.words().contains(id)) {
				problems.add(where + ": glossed word " + id + " is not in the words list");
			}
		}
		if (env.keyExists() != null) {
			if (!env.keyExists().test(story.titleKey())) {
				problems.add(where + ": missing language key " + story.titleKey());
			}
			for (int i = 0; i < story.paragraphs().size(); i++) {
				if (!env.keyExists().test(story.paragraphKey(i))) {
					problems.add(where + ": missing language key " + story.paragraphKey(i));
				}
			}
		}
		return problems;
	}
}
