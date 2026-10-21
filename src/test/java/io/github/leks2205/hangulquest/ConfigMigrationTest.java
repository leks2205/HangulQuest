package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationTest {
	@AfterEach
	void resetLang() {
		Lang.setLocaleSource(null);
		Lang.set("EN");
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void freshDefaultFollowsMinecraft() {
		Config config = new Config();
		assertEquals("AUTO", config.language);
		assertFalse(config.welcomeDone);
		assertEquals(0, config.welcomeShown);
	}

	@Test
	void oldConfigWithoutLanguageButGermanQuestsBecomesGerman() {
		Config config = Config.parse(json("{\"version\":1,\"questGerman\":true}"));
		assertEquals("DE", config.language);
		assertTrue(config.questTranslations);
	}

	@Test
	void oldConfigWithoutLanguageAndWithoutGermanFollowsMinecraft() {
		Config config = Config.parse(json("{\"version\":1,\"questGerman\":false,\"sound\":false}"));
		assertEquals("AUTO", config.language);
		assertFalse(config.sound);
		assertFalse(config.questTranslations);
	}

	@Test
	void anExplicitLanguageIsNeverOverridden() {
		assertEquals("EN", Config.parse(json("{\"language\":\"EN\",\"questGerman\":true}")).language);
		assertEquals("DE", Config.parse(json("{\"language\":\"de\"}")).language);
		assertEquals("AUTO", Config.parse(json("{\"language\":\"auto\"}")).language);
	}

	@Test
	void aBlankOrNullLanguageCountsAsMissing() {
		assertEquals("DE", Config.parse(json("{\"language\":\"\",\"questGerman\":true}")).language);
		assertEquals("AUTO", Config.parse(json("{\"language\":null}")).language);
	}

	@Test
	void anUnknownLanguageFallsBackToEnglish() {
		assertEquals("EN", Config.parse(json("{\"language\":\"fr\"}")).language);
	}

	@Test
	void loadWritesTheMigratedLanguageBack(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":1,\"questGerman\":true}", StandardCharsets.UTF_8);
		Config loaded = Config.load(file);
		assertEquals("DE", loaded.language);
		assertEquals("DE", json(Files.readString(file)).get("language").getAsString());
		// A second start reads the stored value, not the legacy flag.
		assertEquals("DE", Config.load(file).language);
	}

	@Test
	void aBrokenFileIsKeptAndReplaced(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "this is not json", StandardCharsets.UTF_8);
		Config loaded = Config.load(file);
		assertEquals("AUTO", loaded.language);
		try (var siblings = Files.list(dir)) {
			assertTrue(siblings.anyMatch(path -> path.getFileName().toString().startsWith("config.json.broken-")));
		}
	}

	@Test
	void savingToAnUnwritableLocationDoesNotThrow(@TempDir Path dir) throws IOException {
		Path blocker = dir.resolve("file");
		Files.writeString(blocker, "x");
		new Config().save(blocker.resolve("config.json"));
	}

	@Test
	void autoFollowsTheGameLanguageAtRuntime() {
		String[] locale = {"de_de"};
		Lang.setLocaleSource(() -> locale[0]);
		Lang.set("AUTO");
		assertEquals(Lang.DE, Lang.current());
		locale[0] = "de_at";
		assertEquals(Lang.DE, Lang.current());
		locale[0] = "en_us";
		assertEquals(Lang.EN, Lang.current());
		locale[0] = "ko_kr";
		assertEquals(Lang.EN, Lang.current());
		Lang.set("DE");
		assertEquals(Lang.DE, Lang.current());
	}
}
