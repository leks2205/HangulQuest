package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the block of an earlier step stands ({@code placed} on an active or saved quest) is saved without a
 * version bump: it is optional, a file without it means "nothing remembered", and no migration is needed.
 */
class PlacedMemoryFixtureTest {
	private static final Logger LOG = LoggerFactory.getLogger("placed-memory");

	@TempDir
	Path tempDir;

	private Path install(String fixture) throws IOException {
		Path dir = tempDir.resolve(fixture);
		Files.createDirectories(dir);
		try (InputStream in = PlacedMemoryFixtureTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
			assertNotNull(in, "missing fixture " + fixture);
			Files.write(dir.resolve("progress.json"), in.readAllBytes());
		}
		return dir;
	}

	private static JsonObject read(Path dir) throws IOException {
		return JsonParser.parseString(Files.readString(dir.resolve("progress.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
	}

	@Test
	void theSpotsOfEarlierStepsLoad() throws IOException {
		ProgressData data = new ProgressStore(install("v4-placed.json"), LOG).data();
		assertEquals(ProgressData.CURRENT_VERSION, data.version, "the spots needed no bump of their own");
		assertEquals(List.of("s1"), List.copyOf(data.quests.active.placed.keySet()));
		assertArrayEquals(new int[]{100, 64, 100}, data.quests.active.placed.get("s1"));
		ProgressData.Active saved = data.quests.saved.get("rotwerk");
		assertArrayEquals(new int[]{-12, 70, 33}, saved.placed.get("s1"));
		assertArrayEquals(new int[]{-11, 70, 33}, saved.placed.get("s2"), "negative coordinates are fine");
		assertNull(data.quests.saved.get("wasserpfad").placed, "a quest without any has none");
	}

	@Test
	void theSpotsSurviveASaveAndReload() throws IOException {
		Path dir = install("v4-placed.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		store.data().quests.active.placed.put("s3", new int[]{7, 8, 9});
		store.markDirty();
		store.flush();
		JsonObject written = read(dir).getAsJsonObject("quests").getAsJsonObject("active").getAsJsonObject("placed");
		assertEquals(2, written.size());
		assertEquals("[7,8,9]", written.get("s3").toString().replace(" ", ""));
		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertArrayEquals(new int[]{7, 8, 9}, reloaded.quests.active.placed.get("s3"));
		assertArrayEquals(new int[]{100, 64, 100}, reloaded.quests.active.placed.get("s1"));
		assertArrayEquals(new int[]{-11, 70, 33}, reloaded.quests.saved.get("rotwerk").placed.get("s2"));
	}

	@Test
	void anOlderFileHasNoSpotsAndSavesWithoutThem() throws IOException {
		Path dir = install("v4-sample.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		assertNull(store.data().quests.active.placed);
		store.markDirty();
		store.flush();
		JsonObject active = read(dir).getAsJsonObject("quests").getAsJsonObject("active");
		assertFalse(active.has("placed"), "nothing is written for a quest that remembers nothing");
		assertTrue(active.has("goalProgress"));
	}
}
