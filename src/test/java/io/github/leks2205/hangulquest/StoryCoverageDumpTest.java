package io.github.leks2205.hangulquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import io.github.leks2205.hangulquest.story.StoryLexicon;
import io.github.leks2205.hangulquest.story.StoryText;
import io.github.leks2205.hangulquest.story.StoryVocabulary;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes {@code build/reports/content/story-tokens.json}: for every story its unlock rule, its running words and,
 * for a player who knows no catalog word, how often each catalog word stands in the text. tools/story_coverage.py
 * joins that with the learner simulation of tools/coverage.py to say whether a story can open at its unlock point.
 */
class StoryCoverageDumpTest {
	static final Path DUMP = Path.of("build", "reports", "content", "story-tokens.json");
	private static final Gson PLAIN = new GsonBuilder().create();

	@Test
	void writesWhatEachStoryNeeds() throws IOException {
		Catalog catalog = KoreanFixtures.catalog();
		StoryLexicon lexicon = StoryFixtures.lexicon();
		StoryVocabulary nothingKnown = StoryVocabulary.of(catalog, lexicon, id -> false, Lang.EN);
		JsonArray out = new JsonArray();
		for (Story story : StoryFixtures.shipped()) {
			List<StoryText.Token> tokens = new ArrayList<>();
			story.paragraphs().forEach(paragraph -> tokens.addAll(StoryText.analyze(paragraph, nothingKnown)));
			int total = 0;
			Map<String, Integer> needs = new LinkedHashMap<>();
			List<String> unresolved = new ArrayList<>();
			for (StoryText.Token token : tokens) {
				if (!token.word()) {
					continue;
				}
				total++;
				if (token.known()) {
					continue;
				}
				if (token.id() == null) {
					unresolved.add(token.text());
				} else {
					needs.merge(token.id(), 1, Integer::sum);
				}
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", story.id());
			entry.addProperty("level", story.level());
			entry.addProperty("minWords", story.unlock().minWords());
			entry.add("trails", PLAIN.toJsonTree(story.unlock().trails()));
			entry.addProperty("total", total);
			entry.add("needs", PLAIN.toJsonTree(needs));
			entry.add("unresolved", PLAIN.toJsonTree(unresolved));
			out.add(entry);
			assertTrue(total > 0, story.id());
			assertTrue(unresolved.isEmpty(), story.id() + " has words nothing explains: " + unresolved);
		}
		Files.createDirectories(DUMP.getParent());
		Files.writeString(DUMP, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out) + "\n",
				StandardCharsets.UTF_8);
		assertEquals(StoryFixtures.shipped().size(), out.size());
	}
}
