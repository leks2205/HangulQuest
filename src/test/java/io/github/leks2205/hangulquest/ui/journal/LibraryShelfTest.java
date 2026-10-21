package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryCoverage;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import io.github.leks2205.hangulquest.story.StoryLexicon;
import io.github.leks2205.hangulquest.story.StoryVocabulary;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Library tab lists: stories by chapter as open, read or locked, then the culture notes. */
class LibraryShelfTest {
	private static Concept concept(String id, String ko, String en) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.en = en;
		concept.de = en;
		concept.category = "BLOCKS";
		return concept;
	}

	private static final Catalog CATALOG = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stone")));

	private static Story story(String id, String chapter, String text) {
		return new Story(id, chapter, 1, "제목", new Story.Unlock(List.of(), 0), List.of(text), List.of(), List.of());
	}

	private static StoryCoverage.Status status(Story story, boolean knowsStone) {
		StoryVocabulary vocabulary = StoryVocabulary.of(CATALOG, StoryLexicon.EMPTY,
				id -> knowsStone && id.equals("concept:stone"), Lang.EN);
		return StoryCoverage.status(story, vocabulary, trail -> true, 0);
	}

	private static List<LibraryShelf.Row> rows(List<StoryCoverage.Status> statuses, Set<String> read, Set<String> shown) {
		return LibraryShelf.rows(statuses, StoryFixtures.chapters(), StoryFixtures.culture(), read::contains, shown::contains,
				Lang.EN);
	}

	@Test
	void storiesAreGroupedUnderTheirChapterInChapterOrder() {
		List<LibraryShelf.Row> rows = rows(List.of(status(story("b_animals", "animals", "돌을"), true),
				status(story("a_house", "house", "돌을"), true)), Set.of(), Set.of());
		List<String> kinds = new ArrayList<>();
		for (LibraryShelf.Row row : rows) {
			kinds.add(row.kind() + ":" + row.id());
		}
		assertEquals(List.of("HEADING:house", "STORY:a_house", "HEADING:animals", "STORY:b_animals", "HEADING:culture"),
				kinds.subList(0, 5));
		assertEquals("집", rows.get(0).title());
		assertEquals("House", rows.get(0).gloss());
	}

	@Test
	void aStoryIsOpenReadOrLockedAndLockedOnesShowTheirPercentOnly() {
		Story open = story("s_open", "house", "돌을 돌이");
		Story read = story("s_read", "house", "돌을");
		Story locked = story("s_locked", "house", "돌을 횃불을");
		List<LibraryShelf.Row> rows = rows(List.of(status(open, true), status(read, true), status(locked, true)),
				Set.of("s_read"), Set.of());
		LibraryShelf.Row openRow = rows.stream().filter(row -> row.id().equals("s_open")).findFirst().orElseThrow();
		LibraryShelf.Row readRow = rows.stream().filter(row -> row.id().equals("s_read")).findFirst().orElseThrow();
		LibraryShelf.Row lockedRow = rows.stream().filter(row -> row.id().equals("s_locked")).findFirst().orElseThrow();
		assertEquals(LibraryShelf.State.OPEN, openRow.state());
		assertEquals(LibraryShelf.State.DONE, readRow.state());
		assertEquals(LibraryShelf.State.LOCKED, lockedRow.state());
		assertEquals(50, lockedRow.percent());
		assertEquals(100, openRow.percent());
	}

	@Test
	void theCultureNotesFollowTheStoriesAndMarkTheOnesAlreadyShown() {
		List<LibraryShelf.Row> rows = rows(List.of(), Set.of(), Set.of("ondol"));
		assertEquals(LibraryShelf.Kind.HEADING, rows.get(0).kind());
		assertEquals("culture", rows.get(0).id());
		List<LibraryShelf.Row> notes = rows.subList(1, rows.size());
		assertEquals(8, notes.size());
		assertTrue(notes.stream().allMatch(row -> row.kind() == LibraryShelf.Kind.NOTE));
		assertEquals(LibraryShelf.State.DONE, notes.get(0).state());
		assertEquals("ondol", notes.get(0).id());
		assertEquals("온돌", notes.get(0).title());
		assertEquals("Ondol: heated floors", notes.get(0).gloss());
		assertEquals(LibraryShelf.State.OPEN, notes.get(1).state());
	}

	@Test
	void aChapterWithoutStoriesHasNoHeading() {
		List<LibraryShelf.Row> rows = rows(List.of(status(story("only", "field", "돌을"), true)), Set.of(), Set.of());
		assertEquals(1, rows.stream().filter(row -> row.kind() == LibraryShelf.Kind.HEADING && !row.id().equals("culture")).count());
	}

	@Test
	void theCountOfOpenStoriesIsTheCountOfStatusesThatCanBeRead() {
		List<StoryCoverage.Status> statuses = List.of(status(story("a", "house", "돌을"), true),
				status(story("b", "house", "돌을"), false));
		assertEquals(1, LibraryShelf.openStories(statuses));
	}
}
