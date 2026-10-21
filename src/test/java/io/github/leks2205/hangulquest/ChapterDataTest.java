package io.github.leks2205.hangulquest;

import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.dialogue.DialogueFixtures;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.story.ChapterSet;
import io.github.leks2205.hangulquest.story.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** data/chapters.json against the trails: every id exists, no trail is in two chapters, levels are plausible. */
class ChapterDataTest {
	/** The chapters of the roadmap, in order, with their level ranges. */
	private static final Map<String, int[]> ROADMAP = Map.ofEntries(
			Map.entry("house", new int[] {1, 2}), Map.entry("fire_light", new int[] {1, 2}),
			Map.entry("field", new int[] {1, 2}), Map.entry("animals", new int[] {1, 2}),
			Map.entry("colors", new int[] {1, 2}), Map.entry("market", new int[] {1, 2}),
			Map.entry("meals", new int[] {2, 3}), Map.entry("weather", new int[] {2, 3}),
			Map.entry("cave", new int[] {2, 3}), Map.entry("journey", new int[] {3, 4}),
			Map.entry("sea", new int[] {3, 4}), Map.entry("festival", new int[] {4, 6}),
			Map.entry("nether", new int[] {4, 6}), Map.entry("end", new int[] {4, 6}));

	private final ChapterSet chapters = StoryFixtures.chapters();
	private final Map<String, Questline> trails = QuestLoader.loadAll().stream()
			.collect(Collectors.toMap(Questline::id, trail -> trail));

	@Test
	void theChaptersAreTheOnesOfTheRoadmapInOrderWithTheirLevelRanges() {
		assertEquals(List.of("house", "fire_light", "field", "animals", "colors", "market", "meals", "weather", "cave",
				"journey", "sea", "festival", "nether", "end"),
				chapters.all().stream().map(ChapterSet.Chapter::id).toList());
		for (ChapterSet.Chapter chapter : chapters.all()) {
			int[] range = ROADMAP.get(chapter.id());
			assertEquals(range[0], chapter.minLevel(), chapter.id());
			assertEquals(range[1], chapter.maxLevel(), chapter.id());
		}
	}

	@Test
	void everyTrailIdExists() {
		List<String> missing = new ArrayList<>();
		for (ChapterSet.Chapter chapter : chapters.all()) {
			for (String trail : chapter.trails()) {
				if (!trails.containsKey(trail)) {
					missing.add(chapter.id() + ": " + trail);
				}
			}
		}
		assertTrue(missing.isEmpty(), "chapters name trails that do not exist:\n" + String.join("\n", missing));
	}

	@Test
	void everyTrailIsInAtMostOneChapter() {
		// read the file as written: ChapterSet.parse already drops a repeated trail, which would hide the mistake
		Map<String, String> first = new HashMap<>();
		List<String> twice = new ArrayList<>();
		var root = JsonParser.parseString(StoryFixtures.chaptersJson()).getAsJsonObject();
		for (var element : root.getAsJsonArray("chapters")) {
			var chapter = element.getAsJsonObject();
			Set<String> inThis = new LinkedHashSet<>();
			for (var trail : chapter.getAsJsonArray("trails")) {
				String id = trail.getAsString();
				String before = first.putIfAbsent(id, chapter.get("id").getAsString());
				if (before != null || !inThis.add(id)) {
					twice.add(id + " in " + before + " and " + chapter.get("id").getAsString());
				}
			}
		}
		assertTrue(twice.isEmpty(), String.join("\n", twice));
		assertEquals(first.size(), chapters.all().stream().mapToInt(chapter -> chapter.trails().size()).sum());
	}

	@Test
	void everyChapterHasTrailsAnIconAndTitlesInBothLanguages() {
		Map<String, String> english = DialogueFixtures.lang("en_us");
		Map<String, String> german = DialogueFixtures.lang("de_de");
		for (ChapterSet.Chapter chapter : chapters.all()) {
			assertFalse(chapter.trails().isEmpty(), chapter.id() + " has no trails");
			assertTrue(chapter.icon().startsWith("minecraft:"), chapter.id() + " icon " + chapter.icon());
			assertFalse(chapter.titleKo().isBlank(), chapter.id());
			assertTrue(english.containsKey(chapter.titleKey()) && german.containsKey(chapter.titleKey()), chapter.titleKey());
		}
	}

	@Test
	void aTrailSitsAtMostTwoLevelsOutsideItsChapterRange() {
		List<String> far = new ArrayList<>();
		for (ChapterSet.Chapter chapter : chapters.all()) {
			for (String id : chapter.trails()) {
				int level = trails.get(id).koreanLevel();
				if (level < chapter.minLevel() - 2 || level > chapter.maxLevel() + 2) {
					far.add(chapter.id() + " L" + chapter.minLevel() + "-" + chapter.maxLevel() + ": " + id + " is level " + level);
				}
			}
		}
		assertTrue(far.isEmpty(), String.join("\n", far));
	}

	@Test
	void mostTrailsAreInAChapterAndEveryStoryChapterHasTrailsToUnlockIt() {
		long placed = chapters.all().stream().mapToLong(chapter -> chapter.trails().size()).sum();
		assertTrue(placed > trails.size() * 0.5, placed + " of " + trails.size() + " trails are in a chapter");
		for (var story : StoryFixtures.shipped()) {
			assertFalse(chapters.byId(story.chapter()).orElseThrow().trails().isEmpty(), story.id());
		}
	}

	@Test
	void finishingEveryTrailCompletesEveryChapter() {
		for (ChapterSet.Progress progress : chapters.progress(trails::containsKey, id -> true)) {
			assertTrue(progress.complete(), progress.chapter().id());
		}
	}
}
