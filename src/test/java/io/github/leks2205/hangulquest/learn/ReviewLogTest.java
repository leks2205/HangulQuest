package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewLogTest {
	private static final Clock UTC = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

	@TempDir
	Path dir;

	private static long at(String instant) {
		return Instant.parse(instant).toEpochMilli();
	}

	private static ReviewLog.Event event(long t, String item, ReviewMode mode, Integer grade) {
		return new ReviewLog.Event(t, item, ReviewKind.WORD, mode, grade, 1200, 3, "t/s1/primary");
	}

	@Test
	void eachEventIsOneJsonLineWithTheRoadmapFields() throws IOException {
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			log.append(event(at("2026-01-05T10:00:00Z"), "concept:stone", ReviewMode.MC4, 3));
			log.append(event(at("2026-01-05T10:00:05Z"), "concept:stone", ReviewMode.EXPOSURE, null));
			log.flush();
		}
		List<String> lines = Files.readAllLines(dir.resolve("reviews.ndjson"), StandardCharsets.UTF_8);
		assertEquals(2, lines.size());
		JsonObject first = JsonParser.parseString(lines.get(0)).getAsJsonObject();
		assertEquals(List.of("t", "item", "kind", "mode", "grade", "latencyMs", "elapsedDays", "ctx", "arm", "v"),
				first.keySet().stream().toList());
		assertEquals("WORD", first.get("kind").getAsString());
		assertEquals("MC4", first.get("mode").getAsString());
		assertEquals(3, first.get("grade").getAsInt());
		assertEquals(1200, first.get("latencyMs").getAsLong());
		assertTrue(first.get("arm").isJsonNull());
		JsonObject exposure = JsonParser.parseString(lines.get(1)).getAsJsonObject();
		assertTrue(exposure.get("grade").isJsonNull(), "an exposure has no grade");
	}

	@Test
	void nothingIsCreatedUntilTheFirstEvent() throws IOException {
		try (ReviewLog log = ReviewLog.open(dir.resolve("profile"), UTC)) {
			log.flush();
		}
		assertFalse(Files.exists(dir.resolve("profile")));
	}

	@Test
	void manyAppendsKeepTheirOrderAndAllArrive() {
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			for (int i = 0; i < 2000; i++) {
				log.append(event(at("2026-01-05T10:00:00Z") + i, "w" + (i % 7), ReviewMode.MC4, 1 + i % 4));
			}
			log.flush();
		}
		List<ReviewLog.Event> events = ReviewLog.readAll(dir);
		assertEquals(2000, events.size());
		for (int i = 0; i < events.size(); i++) {
			assertEquals(at("2026-01-05T10:00:00Z") + i, events.get(i).t());
		}
	}

	@Test
	void aNewMonthCompressesTheOldFileIntoAnArchiveNamedAfterIt() throws IOException {
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			log.append(event(at("2026-01-20T10:00:00Z"), "a", ReviewMode.MC4, 3));
			log.append(event(at("2026-01-31T23:00:00Z"), "b", ReviewMode.MC4, 3));
			log.flush();
			assertFalse(Files.exists(dir.resolve("reviews-2026-01.ndjson.gz")), "still the same month");
			log.append(event(at("2026-02-01T09:00:00Z"), "c", ReviewMode.MC4, 3));
			log.append(event(at("2026-03-02T09:00:00Z"), "d", ReviewMode.MC4, 3));
			log.flush();
		}
		assertTrue(Files.exists(dir.resolve("reviews-2026-01.ndjson.gz")));
		assertTrue(Files.exists(dir.resolve("reviews-2026-02.ndjson.gz")));
		List<String> current = Files.readAllLines(dir.resolve("reviews.ndjson"));
		assertEquals(1, current.size(), "only March is left in the plain file");
		assertEquals(List.of("a", "b", "c", "d"), ReviewLog.readAll(dir).stream().map(ReviewLog.Event::item).toList());
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp")));
		}
	}

	@Test
	void restartingInTheSameMonthDoesNotRotate() {
		try (ReviewLog first = ReviewLog.open(dir, UTC)) {
			first.append(event(at("2026-04-02T10:00:00Z"), "a", ReviewMode.MC4, 3));
			first.flush();
		}
		try (ReviewLog second = ReviewLog.open(dir, UTC)) {
			second.append(event(at("2026-04-09T10:00:00Z"), "b", ReviewMode.MC4, 3));
			second.flush();
		}
		assertFalse(Files.exists(dir.resolve("reviews-2026-04.ndjson.gz")));
		assertEquals(2, ReviewLog.readAll(dir).size());
	}

	@Test
	void restartingInALaterMonthRotatesTheFileLeftByTheLastRun() {
		try (ReviewLog first = ReviewLog.open(dir, UTC)) {
			first.append(event(at("2026-04-02T10:00:00Z"), "a", ReviewMode.MC4, 3));
			first.flush();
		}
		try (ReviewLog second = ReviewLog.open(dir, UTC)) {
			second.append(event(at("2026-05-09T10:00:00Z"), "b", ReviewMode.MC4, 3));
			second.flush();
		}
		assertTrue(Files.exists(dir.resolve("reviews-2026-04.ndjson.gz")));
		assertEquals(List.of("a", "b"), ReviewLog.readAll(dir).stream().map(ReviewLog.Event::item).toList());
	}

	@Test
	void anArchiveThatAlreadyExistsIsExtendedNotReplaced() throws IOException {
		// a crash between writing the archive and deleting the plain file leaves both behind
		try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dir.resolve("reviews-2026-01.ndjson.gz")))) {
			out.write((event(at("2026-01-03T10:00:00Z"), "old", ReviewMode.MC4, 3).toJson() + "\n").getBytes(StandardCharsets.UTF_8));
		}
		Files.writeString(dir.resolve("reviews.ndjson"),
				event(at("2026-01-25T10:00:00Z"), "newer", ReviewMode.MC4, 3).toJson() + "\n");
		try (ReviewLog log = ReviewLog.open(dir, UTC)) {
			log.append(event(at("2026-02-01T10:00:00Z"), "feb", ReviewMode.MC4, 3));
			log.flush();
		}
		assertEquals(List.of("old", "newer", "feb"), ReviewLog.readAll(dir).stream().map(ReviewLog.Event::item).toList());
	}

	@Test
	void aDamagedLineIsSkippedAndTheRestIsRead() throws IOException {
		Files.writeString(dir.resolve("reviews.ndjson"),
				event(at("2026-01-03T10:00:00Z"), "a", ReviewMode.MC4, 3).toJson() + "\n"
						+ "{\"t\": 17, \"item\": \"half\n"
						+ "not json at all\n"
						+ event(at("2026-01-04T10:00:00Z"), "b", ReviewMode.MC4, 3).toJson() + "\n"
						+ "{\"t\":");
		assertEquals(List.of("a", "b"), ReviewLog.readAll(dir).stream().map(ReviewLog.Event::item).toList());
	}

	@Test
	void aDisabledLogWritesNothing() {
		ReviewLog log = ReviewLog.disabled();
		log.append(event(1, "a", ReviewMode.MC4, 3));
		log.flush();
		assertTrue(ReviewLog.readAll(dir).isEmpty());
	}

	// ---------------------------------------------------------------- replay

	private static Catalog catalog() {
		List<Concept> concepts = new ArrayList<>();
		for (String id : List.of("stone", "torch", "dirt", "sand")) {
			Concept concept = new Concept();
			concept.id = id;
			concept.ko = id;
			concept.de = id;
			concept.category = "BLOCKS";
			concept.tier = 1;
			concept.items = List.of("minecraft:" + id);
			concept.descKo = "땅에서 나와요.";
			concepts.add(concept);
		}
		return Catalog.ofConcepts(concepts);
	}

	@Test
	void replayRebuildsTheSameCardsAsLiveScheduling() {
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("replay-test"));
		LearningService learn = new LearningService(store.data(), store, catalog(), UTC);
		Random random = new Random(42);
		List<String> ids = List.of("concept:stone", "concept:torch", "concept:dirt", "concept:sand");
		long now = at("2026-01-02T08:00:00Z");
		for (int i = 0; i < 400; i++) {
			now += (long) (random.nextDouble() * 3 * Srs.DAY / 4);
			String id = ids.get(random.nextInt(ids.size()));
			switch (random.nextInt(8)) {
				case 0 -> learn.answerMc(id, 3, random.nextBoolean(), 900, now);
				case 1 -> learn.answerMc(id, 2, random.nextBoolean(), 900, now);
				case 2 -> learn.expose(catalog().byId(id).orElseThrow(), now);
				case 3 -> learn.answerSelfGraded(id, 1 + random.nextInt(4), "self", now);
				case 4 -> learn.answerGraded(id, Grader.probe(random.nextBoolean()), 900, "probe", now);
				case 5 -> learn.answerGraded(id, Grader.typed(random.nextInt(3)), 900, "typed", now);
				default -> learn.answerMc(id, 4, random.nextInt(4) != 0, random.nextInt(12_000), now);
			}
		}
		learn.reviewLog().flush();
		assertTrue(Files.exists(dir.resolve("reviews-2026-01.ndjson.gz")), "the run spans several months");

		Map<String, CardState> replayed = ReviewLog.replay(dir, Fsrs.defaults(), null);
		assertEquals(ids.size(), replayed.size());
		for (String id : ids) {
			assertEquals(learn.data().words.get(id).fsrs, replayed.get(id), id);
		}
	}

	@Test
	void guessesProbesAndExposuresDoNotChangeAReplayedCard() {
		List<ReviewLog.Event> events = new ArrayList<>();
		long t = at("2026-01-02T08:00:00Z");
		events.add(event(t, "w", ReviewMode.MC4, 3));
		Map<String, CardState> base = replayOf(events);
		for (ReviewMode mode : List.of(ReviewMode.MC3, ReviewMode.MC2, ReviewMode.PROBE)) {
			events.add(event(t + Srs.DAY, "w", mode, 1));
		}
		events.add(event(t + Srs.DAY, "w", ReviewMode.EXPOSURE, null));
		assertEquals(base, replayOf(events));
		events.add(event(t + 2 * Srs.DAY, "w", ReviewMode.MC4, 1));
		assertFalse(base.equals(replayOf(events)), "a scheduling event does change it");
	}

	private Map<String, CardState> replayOf(List<ReviewLog.Event> events) {
		Path profile = dir.resolve("p" + events.size() + "-" + System.nanoTime());
		try (ReviewLog log = ReviewLog.open(profile, UTC)) {
			events.forEach(log::append);
			log.flush();
		}
		return ReviewLog.replay(profile, Fsrs.defaults(), null);
	}

	@Test
	void theLogCanRestoreLostProgress() {
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("restore-test"));
		LearningService learn = new LearningService(store.data(), store, catalog(), UTC);
		long now = at("2026-02-02T08:00:00Z");
		for (int i = 0; i < 12; i++) {
			learn.answerMc("concept:stone", 4, i % 5 != 0, 900, now);
			learn.answerMc("concept:torch", 4, true, 900, now + 1);
			now += Srs.DAY;
		}
		learn.reviewLog().flush();
		Map<String, CardState> expected = Map.of("concept:stone", learn.data().words.get("concept:stone").fsrs.copy(),
				"concept:torch", learn.data().words.get("concept:torch").fsrs.copy());

		ProgressData lost = new ProgressData();
		ReviewLog.restore(lost, ReviewLog.replay(dir, Fsrs.defaults(), null));
		assertEquals(expected.get("concept:stone"), lost.words.get("concept:stone").fsrs);
		assertEquals(expected.get("concept:torch"), lost.words.get("concept:torch").fsrs);
		assertEquals(lost.words.get("concept:torch").fsrs.due, lost.words.get("concept:torch").dueAt);
		assertEquals(Srs.levelFor(lost.words.get("concept:torch").fsrs.stability), lost.words.get("concept:torch").level);
		assertNull(lost.words.get("concept:dirt"));
	}

	@Test
	void replayStartsFromSeededCardsWhenGiven() {
		long t = at("2026-01-02T08:00:00Z");
		CardState seed = Srs.seed(5, t - Srs.DAY, Fsrs.defaults());
		List<ReviewLog.Event> events = List.of(event(t + 3 * Srs.DAY, "w", ReviewMode.MC4, 3));
		Path profile = dir.resolve("seeded");
		try (ReviewLog log = ReviewLog.open(profile, UTC)) {
			events.forEach(log::append);
			log.flush();
		}
		Map<String, CardState> replayed = ReviewLog.replay(profile, Fsrs.defaults(), Map.of("w", seed));
		assertEquals(Fsrs.defaults().review(seed, 3, t + 3 * Srs.DAY), replayed.get("w"));
		assertEquals(Srs.seed(5, t - Srs.DAY, Fsrs.defaults()), seed, "the start map is not changed");
	}
}
