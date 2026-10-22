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

/** Config 5 adds the switch that brings back timed HUD windows; it is off unless the player turned it on. */
class ConfigAutoCloseTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void windowsStayUntilClosedByDefault() {
		assertFalse(new Config().autoCloseWindows);
		assertEquals(5, Config.CURRENT_VERSION);
	}

	@Test
	void anOlderConfigGetsItOffAndKeepsEverythingElse() {
		Config config = Config.parse(json("{\"version\":4,\"typedCards\":false,\"cardDurationMs\":9000,\"sound\":false}"));

		assertFalse(config.autoCloseWindows);
		assertEquals(Config.CURRENT_VERSION, config.version);
		assertFalse(config.typedCards);
		assertEquals(9000, config.cardDurationMs);
		assertFalse(config.sound);
	}

	@Test
	void aChoiceToTurnItOnIsKept() {
		assertTrue(Config.parse(json("{\"version\":5,\"autoCloseWindows\":true}")).autoCloseWindows);
	}

	@Test
	void aBrokenValueFallsBackToOff() {
		assertFalse(Config.parse(json("{\"version\":5,\"autoCloseWindows\":\"yes\"}")).autoCloseWindows);
		assertFalse(Config.parse(json("{\"version\":5,\"autoCloseWindows\":null}")).autoCloseWindows);
	}

	@Test
	void loadWritesTheMigratedFileBackAndTheChoiceSurvivesARestart(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":4,\"sound\":false}", StandardCharsets.UTF_8);

		Config loaded = Config.load(file);

		assertFalse(loaded.autoCloseWindows);
		JsonObject written = json(Files.readString(file, StandardCharsets.UTF_8));
		assertEquals(Config.CURRENT_VERSION, written.get("version").getAsInt());
		assertFalse(written.get("autoCloseWindows").getAsBoolean(), "the migrated field is on disk");

		loaded.autoCloseWindows = true;
		loaded.save(file);
		assertTrue(Config.load(file).autoCloseWindows);
	}
}
