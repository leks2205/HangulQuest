package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Progress written by every released version must load and survive a save without losing anything. */
class ProgressCompatibilityTest {
	private static final Logger LOG = LoggerFactory.getLogger("progress-compat-test");

	@TempDir
	Path tempDir;

	private Path install(String fixture) throws IOException {
		Path dir = tempDir.resolve(fixture);
		Files.createDirectories(dir);
		try (InputStream in = ProgressCompatibilityTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
			assertNotNull(in, "missing fixture " + fixture);
			Files.write(dir.resolve("progress.json"), in.readAllBytes());
		}
		return dir;
	}

	private static JsonObject read(Path file) throws IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@ParameterizedTest
	@ValueSource(strings = {"v1.0.0", "v1.1.0", "v1.1.1", "v1.2.0", "v1.3.0", "v1.3.1", "v2.0.0", "v2.0.1", "v2.1.0", "v3-sample", "v4-sample", "v4-placed", "v4-autotrack", "v3.0.0-rc.1"})
	void everyReleaseSurvivesLoadAndSave(String tag) throws IOException {
		Path dir = install(tag + ".json");
		JsonObject original = read(dir.resolve("progress.json"));

		ProgressStore store = new ProgressStore(dir, LOG);
		ProgressData data = store.data();
		assertFalse(store.readOnly());
		assertEquals(original.getAsJsonObject("words").size(), data.words.size());
		assertEquals(Map.of("steinpfad", 1792300100000L, "holzpfad", 1792300200000L), data.quests.completed);
		assertEquals("s2", data.quests.active.stepId);
		assertEquals(2, data.quests.active.sidequest.stage);
		assertTrue(data.words.get("lute:고양이").known);
		if (original.getAsJsonObject("quests").has("tracked")) {
			assertNotNull(data.quests.tracked);
		} else {
			assertNull(data.quests.tracked, "an old save must keep meaning 'every saved trail is tracked'");
		}

		for (Map.Entry<String, WordProgress> entry : data.words.entrySet()) {
			WordProgress word = entry.getValue();
			assertNotNull(word.fsrs, entry.getKey() + " has a card after loading");
			assertEquals(original.getAsJsonObject("words").getAsJsonObject(entry.getKey()).get("level").getAsInt(),
					word.level, "the old level is kept for display and downgrades");
		}

		data.totals.reviews++;
		store.markDirty();
		store.flush();
		JsonObject saved = read(dir.resolve("progress.json"));
		original.getAsJsonObject("totals").addProperty("reviews", data.totals.reviews);
		original.addProperty("version", ProgressData.CURRENT_VERSION);
		original.getAsJsonObject("meta").remove("schemaHistory"); // checked below, it grows with each migration
		assertContains(original, saved, "$");
		assertEquals(4, saved.get("version").getAsInt(), "every release ends up on the current schema");
		JsonObject meta = saved.getAsJsonObject("meta");
		assertFalse(meta.get("installSalt").getAsString().isBlank());
		assertEquals(4, meta.getAsJsonArray("schemaHistory").asList().getLast().getAsInt());
		assertTrue(saved.getAsJsonObject("totals").has("sidequestsCompleted"));
		assertTrue(saved.getAsJsonObject("totals").has("questStepsCompleted"));

		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertEquals(data.words.keySet(), reloaded.words.keySet());
		assertEquals(data.quests.saved.keySet(), reloaded.quests.saved.keySet());
		assertNoTempFiles(dir);
	}

	@Test
	void aV3FileKeepsItsSaltHistoryAndCounters() throws IOException {
		Path dir = install("v3-sample.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		assertEquals("9f3c1a7be2d44e60a1b8c5d07f26e913", store.data().meta.installSalt);
		assertEquals(List.of(2, 3, 4), store.data().meta.schemaHistory);
		assertEquals(4, store.data().totals.sidequestsCompleted);
		assertEquals(11, store.data().totals.questStepsCompleted);
		store.markDirty();
		store.flush();
		JsonObject saved = read(dir.resolve("progress.json"));
		assertEquals("9f3c1a7be2d44e60a1b8c5d07f26e913", saved.getAsJsonObject("meta").get("installSalt").getAsString());
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("progress.v3-backup-")),
					"a v3 file is backed up before it becomes v4");
		}
	}

	@Test
	void aV3FileGetsCardsSeededFromItsLevels() throws IOException {
		Path dir = install("v3-sample.json");
		ProgressData data = new ProgressStore(dir, LOG).data();
		// level 0 -> 0.1 d, 1 -> 0.2, 3 -> 1, 4 -> 2, known -> 30
		assertEquals(1.0, data.words.get("concept:stone").fsrs.stability, 0);
		assertEquals(0.2, data.words.get("concept:torch").fsrs.stability, 0);
		assertEquals(0.1, data.words.get("concept:cat").fsrs.stability, 0);
		assertEquals(2.0, data.words.get("lute:나무").fsrs.stability, 0);
		assertEquals(30.0, data.words.get("lute:고양이").fsrs.stability, 0, "a word marked known starts at 30 days");
		for (WordProgress word : data.words.values()) {
			assertEquals(CardState.State.REVIEW, word.fsrs.state);
			assertEquals(5.0, word.fsrs.difficulty, 0);
			assertTrue(word.fsrs.due >= word.dueAt, "due is never earlier than the old due time");
			assertTrue(word.fsrs.due >= word.lastSeenAt);
		}
		assertEquals(3, data.words.get("concept:stone").level, "the level stays for display and downgrades");
	}

	@Test
	void aV4FileIsLoadedAsItIsAndNeedsNoBackup() throws IOException {
		Path dir = install("v4-sample.json");
		ProgressData data = new ProgressStore(dir, LOG).data();
		CardState stone = data.words.get("concept:stone").fsrs;
		assertEquals(24.5, stone.stability, 0);
		assertEquals(4.8, stone.difficulty, 0);
		assertEquals(CardState.State.REVIEW, stone.state);
		assertEquals(7, stone.reps);
		assertEquals(1, stone.lapses);
		assertEquals(1794400000000L, stone.due);
		assertEquals(CardState.State.RELEARNING, data.words.get("concept:torch").fsrs.state);
		assertTrue(data.words.get("concept:cat").fsrs.state == CardState.State.LEARNING);
		assertEquals(Long.MAX_VALUE, data.words.get("lute:고양이").fsrs.due);
		ExposureLedger.Entry ledger = data.words.get("concept:stone").ledger;
		assertEquals(9, ledger.encounters);
		assertEquals(5, ledger.retrievals);
		assertEquals(3, ledger.contexts.size());
		assertEquals(List.of("2026-10-18", "2026-10-19"), ledger.recallDays);
		assertEquals(List.of(2, 3, 4), data.meta.schemaHistory);
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.noneMatch(path -> path.getFileName().toString().contains("-backup-")),
					"a current file needs no migration backup");
		}
	}

	@Test
	void everyFixtureEndsUpWithACardOnEveryWord() throws IOException {
		for (String tag : List.of("v1.0.0", "v1.1.0", "v1.1.1", "v1.2.0", "v1.3.0", "v1.3.1", "v2.0.0", "v2.0.1",
				"v2.1.0", "v3-sample", "schema-v1", "unknown-fields", "bom")) {
			Path dir = install(tag + ".json");
			ProgressData data = new ProgressStore(dir, LOG).data();
			assertFalse(data.words.isEmpty(), tag);
			data.words.forEach((id, word) -> assertNotNull(word.fsrs, tag + " " + id));
		}
	}

	@Test
	void aSavedCardSurvivesASaveAndReload() throws IOException {
		Path dir = install("v4-sample.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		store.data().words.get("concept:stone").fsrs.stability = 99.25;
		store.data().words.get("concept:stone").ledger.recallDays.add("2026-10-20");
		store.markDirty();
		store.flush();
		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertEquals(99.25, reloaded.words.get("concept:stone").fsrs.stability, 0);
		assertEquals(3, reloaded.words.get("concept:stone").ledger.recallDays.size());
	}

	@ParameterizedTest
	@ValueSource(strings = {"v1.0.0", "v2.1.0", "schema-v1"})
	void theSaltIsMadeOnceAndSurvivesRestarts(String fixture) throws IOException {
		Path dir = install(fixture + ".json");
		ProgressStore first = new ProgressStore(dir, LOG);
		String salt = first.data().meta.installSalt;
		assertEquals(32, salt.length());
		first.flush();
		ProgressStore second = new ProgressStore(dir, LOG);
		second.flush();
		assertEquals(salt, second.data().meta.installSalt);
		assertEquals(salt, new ProgressStore(dir, LOG).data().meta.installSalt);
		assertEquals(salt, read(dir.resolve("progress.json")).getAsJsonObject("meta").get("installSalt").getAsString());
	}

	@Test
	void aV1FileRecordsEveryVersionItWentThrough() throws IOException {
		Path dir = install("schema-v1.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		assertEquals(List.of(1, 2, 3, 4), store.data().meta.schemaHistory);
		store.flush();
		assertEquals(List.of(1, 2, 3, 4), new ProgressStore(dir, LOG).data().meta.schemaHistory);
	}

	@Test
	void aFreshInstallGetsASaltAndHistory() {
		ProgressStore store = new ProgressStore(tempDir.resolve("fresh"), LOG);
		assertEquals(4, store.data().version);
		assertEquals(List.of(4), store.data().meta.schemaHistory);
		assertFalse(store.data().meta.installSalt.isBlank());
	}

	@Test
	void fieldsThisBuildDoesNotKnowAreWrittenBack() throws IOException {
		Path dir = install("unknown-fields.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		store.data().words.remove("concept:cat");
		store.data().quests.active = null;
		store.markDirty();
		store.flush();

		JsonObject saved = read(dir.resolve("progress.json"));
		assertEquals("A", saved.getAsJsonObject("experiments").get("arm").getAsString());
		assertEquals("abc123", saved.getAsJsonObject("meta").get("installSalt").getAsString());
		assertEquals(12.5, saved.getAsJsonObject("words").getAsJsonObject("concept:stone")
				.getAsJsonObject("fsrs").get("stability").getAsDouble());
		assertEquals("kept", saved.getAsJsonObject("quests").getAsJsonObject("saved")
				.getAsJsonObject("wasserpfad").get("note").getAsString());
		assertFalse(saved.getAsJsonObject("words").has("concept:cat"), "a removed word must stay removed");
		assertFalse(saved.getAsJsonObject("quests").has("active"), "a cleared field must stay cleared");
	}

	@Test
	void aFileFromANewerVersionIsNeverOverwritten() throws IOException {
		Path dir = install("future-v99.json");
		byte[] before = Files.readAllBytes(dir.resolve("progress.json"));
		ProgressStore store = new ProgressStore(dir, LOG);
		assertTrue(store.readOnly());
		assertEquals(99, store.data().version);
		store.data().totals.reviews++;
		store.markDirty();
		store.flush();
		assertArrayEquals(before, Files.readAllBytes(dir.resolve("progress.json")));
	}

	@Test
	void anOlderSchemaIsBackedUpBeforeMigration() throws IOException {
		Path dir = install("schema-v1.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		assertEquals(ProgressData.CURRENT_VERSION, store.data().version);
		store.flush();
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("progress.v1-backup-")));
		}
		assertEquals(ProgressData.CURRENT_VERSION, read(dir.resolve("progress.json")).get("version").getAsInt());
	}

	@Test
	void aCorruptFileIsMovedAsideNotOverwritten() throws IOException {
		Path dir = install("corrupt.json");
		byte[] broken = Files.readAllBytes(dir.resolve("progress.json"));
		ProgressStore store = new ProgressStore(dir, LOG);
		assertTrue(store.data().words.isEmpty());
		List<Path> aside;
		try (Stream<Path> files = Files.list(dir)) {
			aside = files.filter(path -> path.getFileName().toString().startsWith("progress.json.broken-")).toList();
		}
		assertEquals(1, aside.size());
		assertArrayEquals(broken, Files.readAllBytes(aside.get(0)));
	}

	@Test
	void aByteOrderMarkDoesNotBreakLoading() throws IOException {
		Path dir = install("bom.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		assertEquals(7, store.data().words.size());
		assertFalse(store.readOnly());
	}

	@Test
	void loadingKeepsADailyAndAWeeklyBackup() throws IOException {
		Path dir = install("v2.1.0.json");
		new ProgressStore(dir, LOG);
		new ProgressStore(dir, LOG);
		List<String> names;
		try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
			names = files.map(path -> path.getFileName().toString()).sorted().toList();
		}
		assertEquals(2, names.size(), names.toString());
		assertTrue(names.get(0).startsWith("progress-day-"));
		assertTrue(names.get(1).startsWith("progress-week-"));
	}

	@Test
	void oldBackupsArePruned() throws IOException {
		Path dir = install("v2.1.0.json");
		Path backups = Files.createDirectories(dir.resolve("backups"));
		for (int day = 1; day <= 12; day++) {
			Files.writeString(backups.resolve(String.format("progress-day-2020-01-%02d.json", day)), "{}");
		}
		new ProgressStore(dir, LOG);
		try (Stream<Path> files = Files.list(backups)) {
			assertEquals(ProgressStore.DAILY_BACKUPS,
					files.filter(path -> path.getFileName().toString().startsWith("progress-day-")).count());
		}
	}

	/** Every value in {@code expected} must be present and equal in {@code actual}; extra keys are fine. */
	private static void assertContains(JsonElement expected, JsonElement actual, String path) {
		if (actual == null) {
			fail("lost " + path);
		}
		if (expected.isJsonObject()) {
			assertTrue(actual.isJsonObject(), path + " is no longer an object");
			for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject().entrySet()) {
				assertContains(entry.getValue(), actual.getAsJsonObject().get(entry.getKey()), path + "." + entry.getKey());
			}
		} else if (expected.isJsonArray()) {
			assertTrue(actual.isJsonArray(), path + " is no longer an array");
			JsonArray want = expected.getAsJsonArray();
			JsonArray got = actual.getAsJsonArray();
			assertEquals(want.size(), got.size(), path + " changed length");
			for (int i = 0; i < want.size(); i++) {
				assertContains(want.get(i), got.get(i), path + "[" + i + "]");
			}
		} else {
			assertEquals(expected, actual, path);
		}
	}

	private static void assertNoTempFiles(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}
}
