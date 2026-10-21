package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.culture.CultureSet;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

	/** The story word list with the conversation list, as the game reads them. */
	public static StoryLexicon lexicon() {
		try {
			return StoryLexicon.parse(Files.readString(DIR.resolve("_lexicon.json"), StandardCharsets.UTF_8))
					.with(DialogueFixtures.lexicon());
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
