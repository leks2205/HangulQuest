package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportTest {
	private static final String ACCOUNT = "0f9d2c6e-1b7a-4c3d-9e5f-a1b2c3d4e5f6";
	private static final long JANUARY = 1_768_000_000_000L; // 2026-01
	private static final long FEBRUARY = 1_770_800_000_000L; // 2026-02

	@TempDir
	Path temp;

	private Path profile() throws IOException {
		Path profile = temp.resolve("profiles").resolve(ACCOUNT);
		try (ReviewLog log = ReviewLog.open(profile, Clock.system(ZoneOffset.UTC))) {
			log.append(new ReviewLog.Event(JANUARY, "concept:stone", ReviewKind.WORD, ReviewMode.MC4, 3, 900, -1,
					"quiz", "retention:0.85,productive:3d", 1));
			log.append(new ReviewLog.Event(JANUARY + 1000, "concept:dirt", ReviewKind.WORD, ReviewMode.QUEST_UNAIDED, 3,
					-1, 2, "my_secret_trail/step_one/teach", null, 1));
			log.append(new ReviewLog.Event(JANUARY + 2000, "concept:stone", ReviewKind.WORD, ReviewMode.SELF, 4, -1, 3,
					"card:self", "retention:0.85,productive:3d", 1));
			log.append(new ReviewLog.Event(FEBRUARY, "concept:stone", ReviewKind.WORD, ReviewMode.PROBE, 1, 800, 30,
					"probe:30", "retention:0.85,productive:3d", 1));
			log.append(new ReviewLog.Event(FEBRUARY + 1000, "grammar:topic", ReviewKind.GRAMMAR, ReviewMode.MC4, 3, 500,
					-1, "Some Player's World", null, 1));
		}
		return profile;
	}

	private static Map<String, String> unzip(Path zip) throws IOException {
		Map<String, String> files = new LinkedHashMap<>();
		try (ZipFile archive = new ZipFile(zip.toFile())) {
			for (ZipEntry entry : java.util.Collections.list(archive.entries())) {
				files.put(entry.getName(), new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		return files;
	}

	private static List<JsonObject> lines(String text) {
		List<JsonObject> out = new ArrayList<>();
		for (String line : text.split("\n")) {
			if (!line.isBlank()) {
				out.add(JsonParser.parseString(line).getAsJsonObject());
			}
		}
		return out;
	}

	@Test
	void theZipHoldsTheLogAndTheConsentTextAndIsNamedByDate() throws IOException {
		Path zip = Export.write(profile(), temp.resolve("config"), LocalDate.of(2026, 10, 1), null);
		assertEquals("hangulquest-research-2026-10-01.zip", zip.getFileName().toString());
		assertEquals(temp.resolve("config"), zip.getParent());
		assertEquals(Set.of("consent.txt", "reviews.ndjson"), unzip(zip).keySet());
		Path second = Export.write(profile(), temp.resolve("config"), LocalDate.of(2026, 10, 1), null);
		assertEquals("hangulquest-research-2026-10-01-2.zip", second.getFileName().toString(), "never overwrites");
	}

	@Test
	void noRawWordIdAndNoNameIsInTheFile() throws IOException {
		Path zip = Export.write(profile(), temp.resolve("config"), LocalDate.of(2026, 10, 1), id -> true);
		String everything = String.join("\n", unzip(zip).values());
		for (String forbidden : new String[] {"concept:", "stone", "dirt", "grammar:", "topic", "my_secret_trail", "step_one",
				"Some Player", "World", ACCOUNT, "profiles", temp.toString(), System.getProperty("user.name")}) {
			assertFalse(everything.contains(forbidden), "the export must not contain \"" + forbidden + "\"");
		}
		List<JsonObject> events = lines(unzip(zip).get("reviews.ndjson"));
		assertEquals(5, events.size(), "the archived month and the current file both go in");
		for (JsonObject event : events) {
			assertTrue(event.get("item").getAsString().matches("[0-9a-f]{16}"), event.toString());
		}
	}

	@Test
	void theSameWordHasTheSameHashInOneExportAndAnotherInTheNext() throws IOException {
		Path profile = profile();
		List<JsonObject> one = lines(unzip(Export.write(profile, temp.resolve("a"), LocalDate.of(2026, 10, 1), null, "key-one"))
				.get("reviews.ndjson"));
		List<JsonObject> two = lines(unzip(Export.write(profile, temp.resolve("b"), LocalDate.of(2026, 10, 1), null, "key-two"))
				.get("reviews.ndjson"));
		Set<String> itemsOne = new HashSet<>();
		for (JsonObject event : one) {
			itemsOne.add(event.get("item").getAsString());
		}
		assertEquals(3, itemsOne.size(), "three different words, three different hashes");
		assertEquals(one.get(0).get("item"), one.get(2).get("item"), "stone twice in January");
		assertEquals(one.get(0).get("item"), one.get(3).get("item"), "and in February");
		assertEquals(Export.hashId("key-one", "concept:stone"), one.get(0).get("item").getAsString());
		assertNotEquals(one.get(0).get("item"), two.get(0).get("item"), "a new key for every export");
	}

	@Test
	void whatTheAnalysisNeedsSurvivesAndPlaceNamesAreReduced() throws IOException {
		Path zip = Export.write(profile(), temp.resolve("config"), LocalDate.of(2026, 10, 1),
				id -> id.equals("concept:stone"));
		List<JsonObject> events = lines(unzip(zip).get("reviews.ndjson"));
		assertEquals(JANUARY, events.get(0).get("t").getAsLong());
		assertEquals("MC4", events.get(0).get("mode").getAsString());
		assertEquals(3, events.get(0).get("grade").getAsInt());
		assertEquals(900, events.get(0).get("latencyMs").getAsLong());
		assertEquals("retention:0.85,productive:3d", events.get(0).get("arm").getAsString());
		assertEquals("quiz", events.get(0).get("ctx").getAsString());
		assertEquals("quest", events.get(1).get("ctx").getAsString(), "trail names become \"quest\"");
		assertEquals("card:self", events.get(2).get("ctx").getAsString());
		assertEquals("probe:30", events.get(3).get("ctx").getAsString());
		assertEquals("other", events.get(4).get("ctx").getAsString(), "anything unexpected is not copied");
		assertTrue(events.get(0).get("core").getAsBoolean());
		assertFalse(events.get(1).get("core").getAsBoolean());
		assertTrue(events.get(1).get("arm").isJsonNull());
	}

	@Test
	void theConsentTextIsInEnglishAndGermanAndSaysSharingIsUpToYou() throws IOException {
		String consent = unzip(Export.write(profile(), temp.resolve("config"), LocalDate.of(2026, 10, 1), null))
				.get("consent.txt");
		assertTrue(consent.contains("Hangul Quest research export"));
		assertTrue(consent.contains("Hangul Quest – Forschungsexport"));
		assertTrue(consent.contains("Nothing was sent anywhere"));
		assertTrue(consent.contains("Es wurde nichts gesendet"));
		assertTrue(consent.contains("voluntary and manual"));
		assertTrue(consent.contains("freiwillig und geschieht von Hand"));
		assertTrue(consent.contains("hashed"), "says the ids are hashed");
		assertTrue(consent.contains("gehasht"));
		assertTrue(consent.contains("No player, account, world, server or chat names"));
		assertTrue(consent.contains("Keine Namen von Spielern, Konten, Welten, Servern oder Chats"));
		assertFalse(consent.contains("%s"));
		assertEquals(consent, Export.consent());
	}

	@Test
	void anEmptyLogStillGivesAValidZip() throws IOException {
		Path zip = Export.write(temp.resolve("nothing-here"), temp.resolve("config"), LocalDate.of(2026, 10, 1), null);
		Map<String, String> files = unzip(zip);
		assertEquals("", files.get("reviews.ndjson"));
		assertFalse(files.get("consent.txt").isBlank());
	}
}
