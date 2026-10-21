package io.github.leks2205.hangulquest.story;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading chapters.json and counting a player's progress in each chapter. */
class ChapterSetTest {
	private static final String JSON = """
			{"schema": 1, "chapters": [
			 {"id": "house", "titleKo": "집", "icon": "minecraft:oak_door", "levels": [1, 2], "trails": ["holzpfad", "dorfhaus", "gone"]},
			 {"id": "field", "titleKo": "밭", "icon": "minecraft:wheat", "levels": [1, 2], "trails": ["apfelpfad", "holzpfad"]},
			 {"id": "end", "titleKo": "엔드", "icon": "minecraft:ender_eye", "levels": [4, 6], "trails": []}
			]}""";
	private static final Set<String> TRAILS = Set.of("holzpfad", "dorfhaus", "apfelpfad");

	private final ChapterSet chapters = ChapterSet.parse(JSON, TRAILS, LoggerFactory.getLogger("chapter-test"));

	@Test
	void chaptersKeepTheirOrderTitleIconAndLevels() {
		assertEquals(List.of("house", "field", "end"), chapters.all().stream().map(ChapterSet.Chapter::id).toList());
		ChapterSet.Chapter house = chapters.byId("house").orElseThrow();
		assertEquals("집", house.titleKo());
		assertEquals("minecraft:oak_door", house.icon());
		assertEquals(1, house.minLevel());
		assertEquals(2, house.maxLevel());
		assertEquals("hangulquest.chapter.house.title", house.titleKey());
	}

	@Test
	void aTrailThatDoesNotExistAnymoreIsDropped() {
		assertEquals(List.of("holzpfad", "dorfhaus"), chapters.byId("house").orElseThrow().trails());
	}

	@Test
	void aTrailListedTwiceStaysInItsFirstChapter() {
		assertEquals(List.of("apfelpfad"), chapters.byId("field").orElseThrow().trails());
		assertEquals("house", chapters.ofTrail("holzpfad").orElseThrow().id());
		assertTrue(chapters.ofTrail("unknown").isEmpty());
	}

	@Test
	void progressCountsFinishedTrailsOfTheTrailsThatExist() {
		List<ChapterSet.Progress> progress = chapters.progress(TRAILS::contains, Set.of("holzpfad", "apfelpfad")::contains);
		assertEquals(1, progress.get(0).done());
		assertEquals(2, progress.get(0).total());
		assertFalse(progress.get(0).complete());
		assertTrue(progress.get(1).complete());
		assertEquals(0, progress.get(2).total());
		assertFalse(progress.get(2).complete(), "a chapter without trails is not complete");
	}

	@Test
	void aTrailMissingFromTheGameIsNotCounted() {
		List<ChapterSet.Progress> progress = chapters.progress(id -> id.equals("holzpfad"), id -> true);
		assertEquals(1, progress.get(0).total());
		assertEquals(1, progress.get(0).done());
	}
}
