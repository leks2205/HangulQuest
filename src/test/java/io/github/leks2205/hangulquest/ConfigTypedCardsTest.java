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

/** Config 4 adds the switch for typed review cards; older files get it on. */
class ConfigTypedCardsTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void aFreshConfigHasTypedCardsOn() {
		assertTrue(new Config().typedCards);
		assertEquals(4, Config.CURRENT_VERSION);
	}

	@Test
	void aVersionThreeConfigWithoutTheFieldGetsItOnAndKeepsEverythingElse() {
		Config config = Config.parse(json("{\"version\":3,\"sound\":false,\"newWordsPerDay\":5,\"retention\":0.9}"));

		assertTrue(config.typedCards);
		assertEquals(Config.CURRENT_VERSION, config.version);
		assertFalse(config.sound);
		assertEquals(5, config.newWordsPerDay);
		assertEquals(0.9, config.retention, 1e-9);
	}

	@Test
	void anOldestConfigGetsItOnToo() {
		assertTrue(Config.parse(json("{\"version\":1,\"questGerman\":true}")).typedCards);
	}

	@Test
	void aChoiceToTurnItOffIsKept() {
		assertFalse(Config.parse(json("{\"version\":4,\"typedCards\":false}")).typedCards);
	}

	@Test
	void aBrokenValueFallsBackToOn() {
		assertTrue(Config.parse(json("{\"version\":4,\"typedCards\":\"no\"}")).typedCards);
		assertTrue(Config.parse(json("{\"version\":4,\"typedCards\":null}")).typedCards);
	}

	@Test
	void loadWritesTheMigratedFileBackAndTheChoiceSurvivesARestart(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":3,\"sound\":false}", StandardCharsets.UTF_8);

		Config loaded = Config.load(file);

		assertTrue(loaded.typedCards);
		JsonObject written = json(Files.readString(file, StandardCharsets.UTF_8));
		assertEquals(Config.CURRENT_VERSION, written.get("version").getAsInt());
		assertTrue(written.get("typedCards").getAsBoolean(), "the migrated field is on disk");

		loaded.typedCards = false;
		loaded.save(file);
		assertFalse(Config.load(file).typedCards);
	}
}
