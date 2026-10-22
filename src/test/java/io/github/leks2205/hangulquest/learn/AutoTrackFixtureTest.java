package io.github.leks2205.hangulquest.learn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quest picker's save fields ({@code auto}, {@code situational}, {@code dismissed}, {@code playMs}) are added
 * without a version bump: an older file reads as "the player chose every tracked trail", and nothing the player
 * tracked changes when an old save meets the picker.
 */
class AutoTrackFixtureTest {
	private static final Logger LOG = LoggerFactory.getLogger("auto-track-fixture");

	@TempDir
	Path tempDir;

	private Path install(String fixture) throws IOException {
		Path dir = tempDir.resolve(fixture);
		Files.createDirectories(dir);
		try (InputStream in = AutoTrackFixtureTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
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
	void theFieldsLoadAndNoVersionBumpIsNeeded() throws IOException {
		ProgressData data = new ProgressStore(install("v4-autotrack.json"), LOG).data();

		assertEquals(4, data.version);
		assertEquals(List.of("druckerei", "wasserpfad", "rotwerk"), data.quests.tracked);
		assertEquals(List.of("wasserpfad", "rotwerk"), data.quests.auto);
		assertEquals("rotwerk", data.quests.situational);
		assertEquals(Map.of("tierpfad", 600000L), data.quests.dismissed);
		assertEquals(1200000L, data.quests.playMs);
	}

	@Test
	void theFieldsSurviveASaveAndReload() throws IOException {
		Path dir = install("v4-autotrack.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		store.data().quests.playMs += 5_000;
		store.markDirty();
		store.flush();

		JsonObject written = read(dir).getAsJsonObject("quests");
		assertEquals(3, written.getAsJsonArray("tracked").size());
		assertEquals(2, written.getAsJsonArray("auto").size());
		assertEquals("rotwerk", written.get("situational").getAsString());
		assertEquals(600000L, written.getAsJsonObject("dismissed").get("tierpfad").getAsLong());
		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertEquals(1205000L, reloaded.quests.playMs);
		assertEquals(List.of("wasserpfad", "rotwerk"), reloaded.quests.auto);
	}

	@Test
	void anOlderFileHasNoneOfThem() throws IOException {
		for (String name : List.of("v4-placed.json", "v4-sample.json", "v3-sample.json")) {
			ProgressData data = new ProgressStore(install(name), LOG).data();
			assertNull(data.quests.auto, name);
			assertNull(data.quests.situational, name);
			assertNull(data.quests.dismissed, name);
			assertEquals(0L, data.quests.playMs, name);
		}
	}

	private static Questline trail(String id, String... steps) {
		List<QuestStep> clue = new ArrayList<>();
		for (String step : steps) {
			clue.add(new QuestStep(step, List.of(), new Goal.BreakBlock("minecraft:" + id + step, 1, null), List.of(),
					null, null, null, null, null));
		}
		return new Questline(id, id, id, 1, List.of(), Questline.Requires.none(), clue,
				new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
						null, null, null, null, null), "", "", id, 100, 1, false);
	}

	@Test
	void anOldSaveKeepsTheTrackedTrailsAfterTheEngineMeetsIt() throws IOException {
		Path dir = install("v4-placed.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		List<String> before = List.copyOf(store.data().quests.tracked);
		LearningService learn = new LearningService(store.data(), store, Catalog.ofConcepts(List.of()));
		List<Questline> trails = List.of(trail("druckerei", "s1", "s2"), trail("wasserpfad", "s1"),
				trail("rotwerk", "s1", "s2", "s3"), trail("a"), trail("b"));
		QuestEngine engine = new QuestEngine(trails, Catalog.ofConcepts(List.of()), learn, LOG);
		engine.setAutoPick(() -> true);

		Observation obs = new Observation();
		obs.now = 1_792_300_100_000L;
		engine.autoStart(obs, new QuestEngine.Feedback() {
			@Override
			public void banner(String ko, String de, boolean success) {
			}

			@Override
			public void wordCard(io.github.leks2205.hangulquest.data.Word word) {
			}

			@Override
			public void sound(boolean success) {
			}
		});
		store.flush();

		assertEquals(before, store.data().quests.tracked, "three tracked trails fill the regular slots: nothing is added or removed");
		assertTrue(store.data().quests.auto == null || store.data().quests.auto.isEmpty(), "the player's own stay the player's own");
		assertFalse(engine.isAutoTracked("druckerei"));
		assertEquals("s2", store.data().quests.active.stepId, "progress untouched");
		JsonObject saved = read(dir).getAsJsonObject("quests");
		assertEquals(before.size(), saved.getAsJsonArray("tracked").size());
		assertTrue(saved.has("saved"));
	}
}
