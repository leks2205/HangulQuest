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
		assertEquals("de_de", config.language);
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
		assertEquals("en_us", Config.parse(json("{\"language\":\"EN\",\"questGerman\":true}")).language);
		assertEquals("de_de", Config.parse(json("{\"language\":\"de\"}")).language);
		assertEquals("es_es", Config.parse(json("{\"language\":\"es_ES\"}")).language);
		assertEquals("AUTO", Config.parse(json("{\"language\":\"auto\"}")).language);
	}

	@Test
	void aBlankOrNullLanguageCountsAsMissing() {
		assertEquals("de_de", Config.parse(json("{\"language\":\"\",\"questGerman\":true}")).language);
		assertEquals("AUTO", Config.parse(json("{\"language\":null}")).language);
	}

	@Test
	void anUnknownLanguageFallsBackToEnglish() {
		assertEquals("en_us", Config.parse(json("{\"language\":\"fr\"}")).language);
	}

	@Test
	void loadWritesTheMigratedLanguageBack(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":1,\"questGerman\":true}", StandardCharsets.UTF_8);
		Config loaded = Config.load(file);
		assertEquals("de_de", loaded.language);
		assertEquals("de_de", json(Files.readString(file)).get("language").getAsString());
		// A second start reads the stored value, not the legacy flag.
		assertEquals("de_de", Config.load(file).language);
	}

	@Test
	void theOldTwoLetterLanguageValuesAreMigratedToCodesAndWrittenBack(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":2,\"language\":\"DE\"}", StandardCharsets.UTF_8);
		assertEquals("de_de", Config.load(file).language);
		assertEquals("de_de", json(Files.readString(file)).get("language").getAsString());
		Files.writeString(file, "{\"version\":2,\"language\":\"EN\"}", StandardCharsets.UTF_8);
		assertEquals("en_us", Config.load(file).language);
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
		assertEquals("ko_kr", Lang.current().code());
		Lang.set("DE");
		assertEquals(Lang.DE, Lang.current());
	}

	// ------------------------------------------------------------------ learning settings (config v3)

	@Test
	void anOldConfigGetsTheLearningDefaults() {
		Config config = Config.parse(json("{\"version\":2,\"language\":\"en_us\",\"sound\":false}"));
		assertEquals(0.88, config.retention, 1e-9);
		assertEquals(10, config.newWordsPerDay);
		assertEquals(3, config.version);
		assertFalse(config.sound, "other settings are untouched");
	}

	@Test
	void retentionIsKeptInsideEightyToNinetyFivePercent() {
		assertEquals(0.80, Config.parse(json("{\"retention\":0.5}")).retention, 1e-9);
		assertEquals(0.95, Config.parse(json("{\"retention\":0.999}")).retention, 1e-9);
		assertEquals(0.90, Config.parse(json("{\"retention\":0.9}")).retention, 1e-9);
		assertEquals(0.88, Config.parse(json("{\"retention\":null}")).retention, 1e-9, "no number");
		assertEquals(0.92, Config.parse(json("{\"retention\":92}")).retention, 1e-9, "written as a percentage");
	}

	@Test
	void newWordsPerDayIsOneOfFiveTenFifteen() {
		assertEquals(5, Config.parse(json("{\"newWordsPerDay\":5}")).newWordsPerDay);
		assertEquals(15, Config.parse(json("{\"newWordsPerDay\":15}")).newWordsPerDay);
		assertEquals(10, Config.parse(json("{\"newWordsPerDay\":12}")).newWordsPerDay);
		assertEquals(15, Config.parse(json("{\"newWordsPerDay\":40}")).newWordsPerDay);
		assertEquals(5, Config.parse(json("{\"newWordsPerDay\":0}")).newWordsPerDay);
		assertEquals(10, Config.parse(json("{\"newWordsPerDay\":null}")).newWordsPerDay);
	}

	@Test
	void loadingAVersionTwoFileWritesTheNewFieldsBack(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"version\":2,\"language\":\"en_us\"}", StandardCharsets.UTF_8);

		Config loaded = Config.load(file);

		assertEquals(3, loaded.version);
		JsonObject written = json(Files.readString(file));
		assertEquals(3, written.get("version").getAsInt());
		assertEquals(0.88, written.get("retention").getAsDouble(), 1e-9);
		assertEquals(10, written.get("newWordsPerDay").getAsInt());
	}

	@Test
	void chosenLearningSettingsSurviveARoundTrip(@TempDir Path dir) {
		Path file = dir.resolve("config.json");
		Config config = new Config();
		config.retention = 0.92;
		config.newWordsPerDay = 15;
		config.save(file);

		Config loaded = Config.load(file);

		assertEquals(0.92, loaded.retention, 1e-9);
		assertEquals(15, loaded.newWordsPerDay);
	}
}
