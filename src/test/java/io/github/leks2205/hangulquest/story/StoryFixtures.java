package io.github.leks2205.hangulquest.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.culture.CultureSet;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The shipped Library files as the tests see them: read from the source tree, like the other content tests. */
public final class StoryFixtures {
	public static final Path DIR = Path.of("src/main/resources/assets/hangulquest/stories");
	public static final Path DATA = Path.of("src/main/resources/assets/hangulquest/data");

	private StoryFixtures() {
	}

	/** Every shipped story, parsed; fails the test on a parse problem. */
	public static List<Story> shipped() {
		List<Story> stories = new ArrayList<>();
		try (Stream<Path> chapters = Files.list(DIR)) {
			for (Path chapter : chapters.filter(Files::isDirectory).sorted().toList()) {
				try (Stream<Path> files = Files.list(chapter)) {
					for (Path file : files.sorted().toList()) {
						String name = file.getFileName().toString();
						if (!StoryResources.isStoryFile(name)) {
							continue;
						}
						StoryParser.Parsed parsed = StoryParser.parse(chapter.getFileName().toString(),
								name.substring(0, name.length() - ".json".length()), Files.readAllBytes(file));
						if (!parsed.problems().isEmpty() || parsed.story() == null) {
							throw new AssertionError(file + ": " + parsed.problems());
						}
						stories.add(parsed.story());
					}
				}
			}
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
		return stories;
	}

	/**
	 * The story word list with the conversation list, as the game reads them. The conversation list only adds
	 * words: a form that the story reader would otherwise resolve to a concept a story names keeps that concept.
	 * Otherwise a conversation gloss that reuses, say, 밤에 or 나와요 would hide the concept id (and its
	 * taught/known state) from the story tests and the coverage dump.
	 */
	public static StoryLexicon lexicon() {
		try {
			StoryLexicon story = StoryLexicon.parse(
					Files.readString(DIR.resolve("_lexicon.json"), StandardCharsets.UTF_8));
			StoryVocabulary storyWords = StoryVocabulary.of(
					io.github.leks2205.hangulquest.data.Catalog.ofConcepts(
							io.github.leks2205.hangulquest.data.ConceptLoader.load()),
					story, id -> true, io.github.leks2205.hangulquest.util.Lang.EN);
			List<Story> allStories = shipped();
			JsonObject dialogue = JsonParser.parseString(Files.readString(DialogueFixtures.DIR.resolve("_lexicon.json"),
					StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray words = new JsonArray();
			for (JsonElement element : dialogue.getAsJsonArray("words")) {
				JsonObject entry = element.getAsJsonObject();
				JsonArray forms = new JsonArray();
				for (JsonElement form : entry.getAsJsonArray("forms")) {
					String text = form.getAsString();
					boolean shadowsAStoryWord = false;
					for (Story each : allStories) {
						Set<String> needed = new HashSet<>(each.words());
						needed.addAll(each.glossed());
						if (!String.join(" ", each.paragraphs()).contains(text)) {
							continue;
						}
						if (StoryText.analyze(text, storyWords).stream()
								.anyMatch(token -> token.id() != null && needed.contains(token.id())
										|| token.breakdown() != null
										&& each.grammar().contains(token.breakdown().grammar()))) {
							shadowsAStoryWord = true;
							break;
						}
					}
					if (!shadowsAStoryWord) {
						forms.add(text);
					}
				}
				if (forms.size() == 0) {
					continue;
				}
				JsonObject copy = entry.deepCopy();
				copy.add("forms", forms);
				words.add(copy);
			}
			JsonObject filtered = new JsonObject();
			filtered.addProperty("schema", 1);
			filtered.add("words", words);
			return story.with(DialogueLexicon.parse(filtered.toString()));
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}

	public static String chaptersJson() {
		return read(DATA.resolve("chapters.json"));
	}

	public static ChapterSet chapters() {
		return ChapterSet.parse(chaptersJson(), null, LoggerFactory.getLogger("test"));
	}

	public static String cultureJson() {
		return read(DATA.resolve("culture.json"));
	}

	public static CultureSet culture() {
		return CultureSet.parse(cultureJson());
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
	}
}
