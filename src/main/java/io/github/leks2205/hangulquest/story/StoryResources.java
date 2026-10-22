package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.culture.CultureSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the Library files in the resource manager, so the mod's own files and a resource pack are read the
 * same way: stories in {@code stories/<chapter>/<id>.json}, the word list, the chapters and the culture notes.
 * Files whose name starts with an underscore are not stories.
 */
public final class StoryResources {
	private static final String OWN = "hangulquest";
	private static final String FOLDER = "stories";
	private static final Identifier LEXICON = Identifier.fromNamespaceAndPath(OWN, "stories/_lexicon.json");
	private static final Identifier CHAPTERS = Identifier.fromNamespaceAndPath(OWN, "data/chapters.json");
	private static final Identifier CULTURE = Identifier.fromNamespaceAndPath(OWN, "data/culture.json");

	/** One story file: the chapter folder it sits in, its id (the file name) and its bytes. */
	public record File(String chapter, String id, byte[] bytes) {
	}

	private StoryResources() {
	}

	/** The story files, the mod's own namespace first so it wins over a pack for the same id. */
	public static List<File> read(ResourceManager manager, Logger log) {
		List<File> files = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<Map.Entry<Identifier, Resource>> listed = manager.listResources(FOLDER, id -> isStoryFile(id.getPath()))
				.entrySet().stream()
				.sorted(Comparator.comparing((Map.Entry<Identifier, Resource> entry) ->
						!entry.getKey().getNamespace().equals(OWN)).thenComparing(entry -> entry.getKey().toString()))
				.toList();
		for (Map.Entry<Identifier, Resource> entry : listed) {
			String[] parts = entry.getKey().getPath().split("/");
			if (parts.length != 3) {
				continue;
			}
			String chapter = parts[1];
			String id = parts[2].substring(0, parts[2].length() - ".json".length());
			if (!seen.add(id)) {
				continue;
			}
			try (InputStream stream = entry.getValue().open()) {
				files.add(new File(chapter, id, stream.readAllBytes()));
			} catch (Exception unreadable) {
				log.warn("story resource {} could not be read: {}", entry.getKey(), unreadable.toString());
			}
		}
		files.sort(Comparator.comparing(File::chapter).thenComparing(File::id));
		return files;
	}

	public static boolean isStoryFile(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.endsWith(".json") && !name.startsWith("_");
	}

	/** The word list; empty when it is missing or broken. */
	public static StoryLexicon lexicon(ResourceManager manager, Logger log) {
		try {
			Optional<String> text = text(manager, LEXICON);
			if (text.isPresent()) {
				return StoryLexicon.parse(text.get());
			}
		} catch (Exception broken) {
			log.warn("story word list not loaded: {}", broken.toString());
		}
		return StoryLexicon.EMPTY;
	}

	public static ChapterSet chapters(ResourceManager manager, Set<String> trailIds, Logger log) {
		try {
			Optional<String> text = text(manager, CHAPTERS);
			if (text.isPresent()) {
				return ChapterSet.parse(text.get(), trailIds, log);
			}
		} catch (Exception broken) {
			log.warn("chapters not loaded: {}", broken.toString());
		}
		return ChapterSet.EMPTY;
	}

	public static CultureSet culture(ResourceManager manager, Logger log) {
		try {
			Optional<String> text = text(manager, CULTURE);
			if (text.isPresent()) {
				return CultureSet.parse(text.get());
			}
		} catch (Exception broken) {
			log.warn("culture notes not loaded: {}", broken.toString());
		}
		return CultureSet.EMPTY;
	}

	private static Optional<String> text(ResourceManager manager, Identifier id) throws IOException {
		Optional<Resource> resource = manager.getResource(id);
		if (resource.isEmpty()) {
			return Optional.empty();
		}
		try (InputStream stream = resource.get().open()) {
			return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
