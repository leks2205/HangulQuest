package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.culture.CultureSet;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.dialogue.DialogueLexicon;
import io.github.leks2205.hangulquest.dialogue.DialogueResources;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything the Library tab, the chapter view and the culture cards need from a content load: the valid
 * stories with their word list, the chapters and the culture notes.
 */
public record LibraryContent(List<Story> stories, StoryLexicon lexicon, ChapterSet chapters, CultureSet culture) {
	public static final LibraryContent EMPTY = new LibraryContent(List.of(), StoryLexicon.EMPTY, ChapterSet.EMPTY,
			CultureSet.EMPTY);

	public LibraryContent {
		stories = List.copyOf(stories);
	}

	/** Never throws: the Library is optional, so a failure leaves it empty and the rest of the content intact. */
	public static LibraryContent load(ResourceManager manager, Catalog catalog, Set<String> trailIds,
									  DialogueLexicon conversation, Logger log) {
		try {
			ChapterSet chapters = StoryResources.chapters(manager, trailIds, log);
			StoryLexicon lexicon = StoryResources.lexicon(manager, log).with(conversation);
			Set<String> grammar = DialogueResources.grammarIds(manager, log);
			StoryValidator.Env env = new StoryValidator.Env(
					chapters.all().stream().map(ChapterSet.Chapter::id).collect(Collectors.toSet()), trailIds,
					grammar.isEmpty() ? null : grammar, id -> catalog.byId(id).isPresent(), I18n::has);
			List<Story> stories = new ArrayList<>();
			for (StoryResources.File file : StoryResources.read(manager, log)) {
				StoryParser.Parsed parsed = StoryParser.parse(file.chapter(), file.id(), file.bytes());
				List<String> problems = new ArrayList<>(parsed.problems());
				if (parsed.story() != null && problems.isEmpty()) {
					problems.addAll(StoryValidator.validate(parsed.story(), env));
				}
				if (problems.isEmpty()) {
					stories.add(parsed.story());
				} else {
					log.warn("story {}/{} not loaded: {}", file.chapter(), file.id(), String.join("; ", problems));
				}
			}
			return new LibraryContent(stories, lexicon, chapters, StoryResources.culture(manager, log));
		} catch (RuntimeException failed) {
			log.warn("library not loaded: {}", failed.toString());
			return EMPTY;
		}
	}
}
