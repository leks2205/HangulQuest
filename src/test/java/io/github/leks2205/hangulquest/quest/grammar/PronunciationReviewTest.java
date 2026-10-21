package io.github.leks2205.hangulquest.quest.grammar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.LexiconTags;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * content/korean/pronunciation-review.tsv holds the generated pronunciation of every concept, for a blind check
 * by a native speaker (and for tools/audio/qa.py, which compares the speech recogniser's text with it). The
 * test keeps it in step with the code:
 * {@code HANGULQUEST_WRITE_PRON=1 ./gradlew test --offline --tests '*PronunciationReviewTest'} rewrites it.
 *
 * <p>Columns: id, ko, pron (in brackets, also when it equals the spelling), rules, source (rule or lexical),
 * origin from the lexicon tags.
 */
class PronunciationReviewTest {
	static final Path FILE = Path.of("content/korean/pronunciation-review.tsv");
	private static final String CONCEPTS = "/assets/hangulquest/data/concepts.json";
	private static final String HEADER = "id\tko\tpron\trules\tsource\torigin";

	private static String generate() throws IOException {
		LexiconTags tags = LexiconTags.shared();
		StringBuilder out = new StringBuilder(HEADER).append('\n');
		try (InputStream stream = PronunciationReviewTest.class.getResourceAsStream(CONCEPTS)) {
			for (JsonElement element : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonArray()) {
				JsonObject concept = element.getAsJsonObject();
				String id = concept.get("id").getAsString();
				String ko = concept.get("ko").getAsString();
				Pronunciation.Result result = Pronunciation.of(ko, id, tags);
				LexiconTags.Tag tag = tags.tag(id);
				out.append(id).append('\t').append(ko).append('\t').append('[').append(result.pron()).append("]\t")
						.append(result.ruleIds()).append('\t').append(result.lexical() ? "lexical" : "rule").append('\t')
						.append(tag == null || tag.origin() == null ? "" : tag.origin()).append('\n');
			}
		}
		return out.toString();
	}

	@Test
	void theReviewTableMatchesTheCode() throws IOException {
		String expected = generate();
		if (System.getenv("HANGULQUEST_WRITE_PRON") != null) {
			Files.writeString(FILE, expected, StandardCharsets.UTF_8);
		}
		assertTrue(Files.exists(FILE), FILE + " is missing, run with HANGULQUEST_WRITE_PRON=1");
		assertEquals(expected, Files.readString(FILE, StandardCharsets.UTF_8),
				FILE + " is out of date, run with HANGULQUEST_WRITE_PRON=1");
	}

	@Test
	void everyConceptHasARowAndKeepsItsSyllables() throws IOException {
		String[] lines = generate().split("\n");
		assertTrue(lines.length > 1000, "only " + lines.length + " lines");
		for (int i = 1; i < lines.length; i++) {
			String[] f = lines[i].split("\t", -1);
			assertEquals(6, f.length, lines[i]);
			String pron = f[2].substring(1, f[2].length() - 1);
			assertEquals(f[1].length(), pron.length(), f[1] + " " + pron);
			assertEquals(f[1].chars().filter(c -> c == ' ').count(), pron.chars().filter(c -> c == ' ').count(), f[1]);
		}
	}
}
