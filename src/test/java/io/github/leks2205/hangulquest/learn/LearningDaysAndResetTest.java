package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The study day, the persisted counters and the reset. */
class LearningDaysAndResetTest {
	private static final Logger LOG = LoggerFactory.getLogger("learning-days-test");
	private static final String STONE = "concept:stone";

	@TempDir
	Path dir;

	private Catalog catalog;
	private MutableClock clock;
	private ProgressStore store;
	private LearningService learn;

	/** A clock that tests move by hand, in UTC so the local time is what the test writes. */
	private static final class MutableClock extends Clock {
		private LocalDateTime now;

		MutableClock(String start) {
			this.now = LocalDateTime.parse(start);
		}

		void set(String time) {
			now = LocalDateTime.parse(time);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.toInstant(ZoneOffset.UTC);
		}
	}

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	private LearningService service(ProgressStore with) {
		return new LearningService(with.data(), with, catalog, clock);
	}

	@BeforeEach
	void setUp() {
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		clock = new MutableClock("2026-10-20T12:00:00");
		store = new ProgressStore(dir, LOG);
		learn = service(store);
	}

	@Test
	void lookingAtTheCountsNeverCreatesADay() {
		for (int frame = 0; frame < 100; frame++) {
			assertEquals(0, learn.todaySeen());
			assertEquals(0, learn.todayCorrect());
			assertEquals(0, learn.streak().current());
		}
		assertTrue(learn.data().days.isEmpty());
	}

	@Test
	void onlyRealActivityCreatesADay() {
		learn.answer(STONE, true, 1L);
		assertEquals(List.of("2026-10-20"), List.copyOf(learn.data().days.keySet()));
		assertEquals(1, learn.todaySeen());
		assertEquals(1, learn.todayCorrect());
		assertEquals(1, learn.streak().current());
	}

	@Test
	void theDayKeyKeepsTheIsoFormat() {
		clock.set("2026-01-05T09:30:00");
		assertEquals("2026-01-05", learn.dayKey());
	}

	@Test
	void theDayTurnsOverAtFourInTheMorning() {
		clock.set("2026-10-20T23:00:00");
		learn.answer(STONE, true, 1L);
		clock.set("2026-10-21T01:00:00");
		learn.answer(STONE, true, 2L);
		clock.set("2026-10-21T03:59:59");
		learn.answer(STONE, false, 3L);
		assertEquals(List.of("2026-10-20"), List.copyOf(learn.data().days.keySet()),
				"a session from 23:00 to 04:00 is one day");
		assertEquals(3, learn.data().days.get("2026-10-20").seen);
		assertEquals(1, learn.streak().current());

		clock.set("2026-10-21T04:00:00");
		assertEquals(0, learn.todaySeen(), "04:00 starts a new day");
		learn.answer(STONE, true, 4L);
		assertEquals(List.of("2026-10-20", "2026-10-21"), List.copyOf(learn.data().days.keySet()));
		assertEquals(2, learn.streak().current());
	}

	@Test
	void theStreakDoesNotBreakAtMidnight() {
		clock.set("2026-10-20T22:00:00");
		learn.answer(STONE, true, 1L);
		clock.set("2026-10-21T02:00:00");
		assertEquals(1, learn.streak().current());
		clock.set("2026-10-21T20:00:00");
		assertEquals(1, learn.streak().current(), "yesterday still counts until something is played today");
		clock.set("2026-10-22T20:00:00");
		assertEquals(1, learn.streak().current(), "one missed day is covered by a freeze");
		clock.set("2026-10-25T20:00:00");
		assertEquals(0, learn.streak().current(), "four missed days are more than two freezes cover");
	}

	@Test
	void studyDayHelperMatchesTheRule() {
		assertEquals(LocalDate.of(2026, 10, 19), Streak.studyDay(LocalDateTime.of(2026, 10, 20, 3, 59)));
		assertEquals(LocalDate.of(2026, 10, 20), Streak.studyDay(LocalDateTime.of(2026, 10, 20, 4, 0)));
		assertEquals("2026-10-20", Streak.dayKey(LocalDateTime.of(2026, 10, 21, 0, 30)));
	}

	@Test
	void sidequestAndStepCountersSurviveARestart() {
		for (int i = 0; i < 5; i++) {
			learn.noteSidequestCompleted();
		}
		learn.noteQuestStepCompleted();
		learn.noteQuestStepCompleted();
		assertTrue(learn.checkAchievements().stream().anyMatch(a -> a.id().equals("sidequest_5")));
		store.flush();

		ProgressStore reopened = new ProgressStore(dir, LOG);
		LearningService next = service(reopened);
		assertEquals(5, next.sidequestsCompleted());
		assertEquals(2, next.questStepsCompleted());
		assertTrue(reopened.data().achievements.containsKey("sidequest_5"));

		for (int i = 0; i < 4; i++) {
			next.noteSidequestCompleted();
		}
		assertEquals(9, next.sidequestsCompleted(), "counting goes on across sessions");
	}

	@Test
	void fourSidequestsInOneSessionAndOneInTheNextUnlockTheAchievement() {
		for (int i = 0; i < 4; i++) {
			learn.noteSidequestCompleted();
		}
		assertTrue(learn.checkAchievements().stream().noneMatch(a -> a.id().equals("sidequest_5")));
		store.flush();

		LearningService next = service(new ProgressStore(dir, LOG));
		next.noteSidequestCompleted();
		assertTrue(next.checkAchievements().stream().anyMatch(a -> a.id().equals("sidequest_5")));
	}

	private void fillSomeProgress() {
		learn.touch(1000L);
		learn.answer(STONE, true, 1000L);
		learn.markKnown("concept:torch", true, 1001L);
		learn.noteSidequestCompleted();
		learn.noteQuestStepCompleted();
		learn.checkAchievements();
		learn.data().quests.adaptive = new ProgressData.Adaptive();
		learn.data().quests.completed.put("steinpfad", 5L);
		store.markDirty();
		store.flush();
	}

	@Test
	void resetClearsLearningStateAndKeepsFirstPlayed() throws IOException {
		fillSomeProgress();
		assertFalse(learn.data().achievements.isEmpty());
		List<String> heard = new ArrayList<>();
		learn.onReset(() -> heard.add("dictionary"));

		assertTrue(learn.reset());

		assertTrue(learn.data().words.isEmpty());
		assertTrue(learn.data().days.isEmpty());
		assertTrue(learn.data().achievements.isEmpty());
		assertEquals(0, learn.data().totals.seen);
		assertEquals(0, learn.data().totals.reviews);
		assertEquals(0, learn.sidequestsCompleted());
		assertEquals(0, learn.questStepsCompleted());
		assertNull(learn.data().quests.adaptive);
		assertEquals(1000L, learn.data().meta.firstPlayed);
		assertEquals(List.of("dictionary"), heard);
		assertTrue(learn.data().quests.completed.containsKey("steinpfad"), "finished trails are not part of the reset");

		JsonObject saved = JsonParser.parseString(Files.readString(dir.resolve("progress.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
		assertEquals(0, saved.getAsJsonObject("words").size());
		assertEquals(1000L, saved.getAsJsonObject("meta").get("firstPlayed").getAsLong());
		assertFalse(saved.getAsJsonObject("meta").get("installSalt").getAsString().isBlank());
	}

	@Test
	void resetKeepsABackupOfWhatWasThere() throws IOException {
		fillSomeProgress();
		learn.answer(STONE, true, 2000L); // not yet flushed when reset starts
		assertTrue(learn.reset());

		List<Path> backups;
		try (Stream<Path> files = Files.list(dir)) {
			backups = files.filter(path -> path.getFileName().toString().startsWith("progress.json.backup-pre-reset-")).toList();
		}
		assertEquals(1, backups.size());
		JsonObject before = JsonParser.parseString(Files.readString(backups.get(0), StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(before.getAsJsonObject("words").has(STONE));
		assertEquals(2, before.getAsJsonObject("words").getAsJsonObject(STONE).get("seen").getAsInt(),
				"the backup holds the latest state, not the last autosave");
	}

	@Test
	void resetDoesNothingToAReadOnlyFile() throws IOException {
		Path future = dir.resolve("future");
		Files.createDirectories(future);
		Files.writeString(future.resolve("progress.json"), "{\"version\": 99, \"words\": {\"concept:stone\": {\"level\": 2}}}");
		ProgressStore readOnly = new ProgressStore(future, LOG);
		LearningService service = service(readOnly);
		AtomicInteger heard = new AtomicInteger();
		service.onReset(heard::incrementAndGet);

		assertFalse(service.reset());
		assertEquals(1, service.data().words.size());
		assertEquals(0, heard.get());
		assertNotNull(Files.readString(future.resolve("progress.json")));
		assertTrue(Files.readString(future.resolve("progress.json")).contains("concept:stone"));
	}
}
