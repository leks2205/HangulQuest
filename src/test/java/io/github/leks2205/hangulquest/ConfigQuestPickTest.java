package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Config 5 adds "pick quests automatically" (on) and "show all trails" (off); older files get those defaults. */
class ConfigQuestPickTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void theDefaultsArePicksOnAndTheCalmTree() {
		Config config = new Config();
		assertTrue(config.autoPickQuests);
		assertFalse(config.showAllTrails);
		assertTrue(Config.CURRENT_VERSION >= 5);
	}

	@Test
	void anOlderConfigGetsTheDefaultsAndKeepsEverythingElse() {
		Config config = Config.parse(json("{\"version\":4,\"sound\":false,\"typedCards\":false,\"questTrackerRows\":2}"));

		assertTrue(config.autoPickQuests);
		assertFalse(config.showAllTrails);
		assertFalse(config.sound);
		assertFalse(config.typedCards);
		assertEquals(2, config.questTrackerRows);
		assertEquals(Config.CURRENT_VERSION, config.version);
	}

	@Test
	void theChoicesAreKept() {
		Config config = Config.parse(json("{\"version\":5,\"autoPickQuests\":false,\"showAllTrails\":true}"));
		assertFalse(config.autoPickQuests);
		assertTrue(config.showAllTrails);
	}

	@Test
	void aBrokenValueFallsBackToTheDefault() {
		Config config = Config.parse(json("{\"version\":5,\"autoPickQuests\":\"no\",\"showAllTrails\":null}"));
		assertTrue(config.autoPickQuests);
		assertFalse(config.showAllTrails);
	}

	@Test
	void loadWritesTheMigratedFileBackAndTheChoicesSurviveARestart(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":4,\"sound\":false}", StandardCharsets.UTF_8);

		Config loaded = Config.load(file);

		JsonObject written = json(Files.readString(file, StandardCharsets.UTF_8));
		assertTrue(written.get("autoPickQuests").getAsBoolean());
		assertFalse(written.get("showAllTrails").getAsBoolean());
		loaded.autoPickQuests = false;
		loaded.showAllTrails = true;
		loaded.save(file);
		Config again = Config.load(file);
		assertFalse(again.autoPickQuests);
		assertTrue(again.showAllTrails);
	}
}
