package io.github.leks2205.hangulquest.story;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.ExposureLedger;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.WordProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading a story logs an encounter for each of its words: never a review, once per study day. */
class StoryReadingTest {
	private static final Logger LOG = LoggerFactory.getLogger("story-test");
	private static final long T0 = 1_700_000_000_000L;

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;
	private MarkFile readLog;
	private final Story story = new Story("house_first", "house", 1, "나의 첫 집", new Story.Unlock(List.of(), 0),
			List.of("나무를 베어요."), List.of("concept:tree", "concept:stone", "concept:gone"), List.of());

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		return concept;
	}

	@BeforeEach
	void setUp() {
		ProgressStore store = new ProgressStore(tempDir.resolve("progress"), LOG);
		catalog = Catalog.ofConcepts(List.of(concept("tree", "나무", "Baum"), concept("stone", "돌", "Stein")));
		learn = new LearningService(store.data(), store, catalog);
		readLog = MarkFile.open(tempDir, "stories-read.json", LOG);
	}

	@Test
	void everyCatalogWordOfTheStoryIsAnEncounterInTheStoryContext() {
		int exposed = StoryReading.open(learn, catalog, readLog, story, T0, "2026-10-21");

		assertEquals(2, exposed, "the word the catalog does not have is skipped");
		for (String id : List.of("concept:tree", "concept:stone")) {
			WordProgress progress = learn.data().words.get(id);
			assertNotNull(progress, id);
			assertEquals(1, ExposureLedger.encounters(progress), id);
			assertEquals(List.of("story||reading|house_first"), ExposureLedger.of(progress).contexts);
			assertEquals(ExposureLedger.Context.parse("story||reading|house_first"), StoryReading.context(story));
		}
		assertNull(learn.data().words.get("concept:gone"));
	}

	@Test
	void readingIsAnEncounterAndNeverAReview() {
		StoryReading.open(learn, catalog, readLog, story, T0, "2026-10-21");

		WordProgress progress = learn.data().words.get("concept:tree");
		assertEquals(0, ExposureLedger.retrievals(progress), "an encounter is not a retrieval");
		assertEquals(0, progress.level);
		assertEquals(T0, progress.dueAt, "the schedule is untouched");
		assertEquals(0, progress.correct);
		assertEquals(1, progress.seen);
		assertEquals(0, learn.totalReviews());
		assertTrue(ExposureLedger.recentEncounters(progress, T0, 1) >= 1);
	}

	@Test
	void aStoryCountsOnceAStudyDayAndAgainOnTheNext() {
		assertEquals(2, StoryReading.open(learn, catalog, readLog, story, T0, "2026-10-21"));
		assertEquals(0, StoryReading.open(learn, catalog, readLog, story, T0 + 1000, "2026-10-21"), "the same day");
		assertEquals(1, ExposureLedger.encounters(learn.data().words.get("concept:stone")));

		assertEquals(2, StoryReading.open(learn, catalog, readLog, story, T0 + 86_400_000L, "2026-10-22"));
		assertEquals(2, ExposureLedger.encounters(learn.data().words.get("concept:stone")));
		assertEquals(1, ExposureLedger.distinctContexts(learn.data().words.get("concept:stone")),
				"the same story is one context");
	}

	@Test
	void twoStoriesAreTwoContextsForTheSameWord() {
		Story other = new Story("fire_night", "fire_light", 1, "첫 밤", new Story.Unlock(List.of(), 0),
				List.of("돌이 있어요."), List.of("concept:stone"), List.of());
		StoryReading.open(learn, catalog, readLog, story, T0, "2026-10-21");
		StoryReading.open(learn, catalog, readLog, other, T0, "2026-10-21");
		assertEquals(2, ExposureLedger.distinctContexts(learn.data().words.get("concept:stone")));
	}

	@Test
	void theReadLogSurvivesARestartAndIgnoresABrokenFile() throws Exception {
		StoryReading.open(learn, catalog, readLog, story, T0, "2026-10-21");

		MarkFile reopened = MarkFile.open(tempDir, "stories-read.json", LOG);
		assertTrue(reopened.is("house_first", "2026-10-21"));
		assertEquals(0, StoryReading.open(learn, catalog, reopened, story, T0, "2026-10-21"));

		Files.writeString(tempDir.resolve("stories-read.json"), "{ not json");
		MarkFile broken = MarkFile.open(tempDir, "stories-read.json", LOG);
		assertEquals(0, broken.size(), "a damaged file starts empty");
		assertEquals(2, StoryReading.open(learn, catalog, broken, story, T0, "2026-10-21"));
	}

	/** The calls a story makes on the learning side, by word id: "expose" or "questStep". */
	private static java.util.Map<String, String> calls(java.util.function.Consumer<io.github.leks2205.hangulquest.learn.LearningSink> body,
													  io.github.leks2205.hangulquest.learn.LearningSink real) {
		java.util.Map<String, String> seen = new java.util.LinkedHashMap<>();
		io.github.leks2205.hangulquest.learn.LearningSink spy = (io.github.leks2205.hangulquest.learn.LearningSink)
				java.lang.reflect.Proxy.newProxyInstance(StoryReadingTest.class.getClassLoader(),
						new Class<?>[] {io.github.leks2205.hangulquest.learn.LearningSink.class}, (proxy, method, args) -> {
							if (method.getName().equals("expose") || method.getName().equals("questStep")) {
								String id = ((io.github.leks2205.hangulquest.data.Word) args[0]).id;
								seen.put(id, method.getName() + (method.getName().equals("questStep") ? ":assisted=" + args[2] : ""));
							}
							try {
								return method.invoke(real, args);
							} catch (java.lang.reflect.InvocationTargetException wrapped) {
								throw wrapped.getCause();
							}
						});
		body.accept(spy);
		return seen;
	}

	@Test
	void aGlossedWordIsTaughtTheWayAQuestStepTeachesAndNeverGradedAsRecall() {
		Story glossed = new Story("house_first", "house", 1, "나의 첫 집", new Story.Unlock(List.of(), 0),
				List.of("나무를 베어요."), List.of("concept:tree", "concept:stone"), List.of(), List.of("concept:stone"));
		java.util.Map<String, String> seen = calls(spy -> StoryReading.open(spy, catalog, readLog, glossed, T0, "2026-10-21"), learn);

		assertEquals("expose", seen.get("concept:tree"));
		assertEquals("questStep:assisted=true", seen.get("concept:stone"), "the gloss was an aid, so it is not a recall");
		WordProgress progress = learn.data().words.get("concept:stone");
		assertNotNull(progress, "the glossed word is introduced");
		assertEquals(1, progress.seen);
		assertEquals(0, progress.level);
		assertEquals(0, learn.totalReviews());
		assertEquals(List.of("story||reading|house_first"), ExposureLedger.of(progress).contexts);
	}

	@Test
	void readingTwiceTheSameDayDoesNotTeachTheGlossedWordAgain() {
		Story glossed = new Story("house_first", "house", 1, "나의 첫 집", new Story.Unlock(List.of(), 0),
				List.of("나무를 베어요."), List.of("concept:stone"), List.of(), List.of("concept:stone"));
		StoryReading.open(learn, catalog, readLog, glossed, T0, "2026-10-21");
		StoryReading.open(learn, catalog, readLog, glossed, T0, "2026-10-21");
		assertEquals(1, learn.data().words.get("concept:stone").seen);
	}
}
