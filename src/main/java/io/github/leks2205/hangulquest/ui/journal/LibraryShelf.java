package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.culture.CultureNote;
import io.github.leks2205.hangulquest.culture.CultureSet;
import io.github.leks2205.hangulquest.story.ChapterSet;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryCoverage;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the Library tab lists, without any drawing: the stories grouped by chapter, each open, read or locked
 * with how much of it the player already knows, then the culture notes. A locked story names its percentage
 * but nothing else; its words appear on its own page.
 */
public final class LibraryShelf {
	public enum Kind {
		HEADING, STORY, NOTE
	}

	public enum State {
		/** A heading, or a story that can be read. */
		OPEN,
		/** A story read at least once, or a note that was shown. */
		DONE,
		LOCKED
	}

	/**
	 * One line. {@code title} is Korean, {@code gloss} the interface language; {@code percent} is the known share of a
	 * story and {@code tag} the short text at the end of its line ("Level 1", or "96% known" while it is locked).
	 */
	public record Row(Kind kind, String id, String title, String gloss, State state, int level, int percent, String tag) {
	}

	private LibraryShelf() {
	}

	/**
	 * @param statuses  one per story, in any order
	 * @param storyRead whether a story was read
	 * @param noteShown whether a note's card was shown
	 */
	public static List<Row> rows(List<StoryCoverage.Status> statuses, ChapterSet chapters, CultureSet culture,
								 Predicate<String> storyRead, Predicate<String> noteShown, Lang lang) {
		List<Row> rows = new ArrayList<>();
		Map<String, List<StoryCoverage.Status>> byChapter = new LinkedHashMap<>();
		for (ChapterSet.Chapter chapter : chapters.all()) {
			byChapter.put(chapter.id(), new ArrayList<>());
		}
		for (StoryCoverage.Status status : statuses) {
			byChapter.computeIfAbsent(status.story().chapter(), id -> new ArrayList<>()).add(status);
		}
		for (Map.Entry<String, List<StoryCoverage.Status>> group : byChapter.entrySet()) {
			if (group.getValue().isEmpty()) {
				continue;
			}
			String titleKo = chapters.byId(group.getKey()).map(ChapterSet.Chapter::titleKo).orElse(group.getKey());
			String gloss = chapters.byId(group.getKey()).map(chapter -> I18n.t(lang, chapter.titleKey())).orElse("");
			rows.add(new Row(Kind.HEADING, group.getKey(), titleKo, gloss, State.OPEN, 0, 0, ""));
			group.getValue().stream().sorted((a, b) -> a.story().id().compareTo(b.story().id()))
					.forEach(status -> rows.add(storyRow(status, storyRead, lang)));
		}
		if (!culture.notes().isEmpty()) {
			rows.add(new Row(Kind.HEADING, "culture", "문화", I18n.t(lang, "hangulquest.ui.library.culture"), State.OPEN, 0, 0, ""));
			for (CultureNote note : culture.notes()) {
				rows.add(new Row(Kind.NOTE, note.id(), note.titleKo(), I18n.t(lang, note.titleKey()),
						noteShown.test(note.id()) ? State.DONE : State.OPEN, 0, 0, ""));
			}
		}
		return rows;
	}

	private static Row storyRow(StoryCoverage.Status status, Predicate<String> storyRead, Lang lang) {
		Story story = status.story();
		State state = !status.open() ? State.LOCKED : storyRead.test(story.id()) ? State.DONE : State.OPEN;
		int percent = status.coverage().percent();
		String tag = state == State.LOCKED ? I18n.t(lang, "hangulquest.ui.library.coverage", percent)
				: I18n.t(lang, "hangulquest.ui.library.level", story.level());
		return new Row(Kind.STORY, story.id(), story.titleKo(), I18n.t(lang, story.titleKey()), state, story.level(),
				percent, tag);
	}

	public static long openStories(List<StoryCoverage.Status> statuses) {
		return statuses.stream().filter(StoryCoverage.Status::open).count();
	}
}
