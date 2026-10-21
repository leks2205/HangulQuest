package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.QuestlineStore;
import io.github.leks2205.hangulquest.util.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped questlines must keep every English text through the loader, the validator and the
 * store's override merge. The English text is in {@code lang/content/en_us.json}; a questline file
 * that carries an inline field instead has that one win. The assertions read the English-only
 * accessors: the fallback getters would show the German text instead of failing when an English text
 * goes missing.
 */
class QuestlineRoundTripTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("questline-roundtrip-test");

	@TempDir
	Path tempDir;

	private static Map<String, JsonObject> shippedJson() throws Exception {
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (String file : QuestLoader.index()) {
			try (Reader reader = new InputStreamReader(
					QuestLoader.class.getResourceAsStream(QuestLoader.BASE + file), StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				byId.put(root.get("id").getAsString(), root);
			}
		}
		return byId;
	}

	private static String text(JsonObject object, String key) {
		return object != null && object.has(key) ? object.get(key).getAsString() : null;
	}

	private static JsonObject child(JsonObject object, String key) {
		return object != null && object.has(key) ? object.getAsJsonObject(key) : null;
	}

	/** The English text the player should see: the inline field, else the lang key. */
	private static String expected(String inline, String key) {
		return inline != null ? inline : I18n.bundle("en_us").get(key);
	}

	/** Compares every English text of the trail with the JSON it came from. */
	private static void assertEnglishMatches(JsonObject json, Questline trail, String stage) {
		String id = trail.id();
		assertEquals(expected(text(json, "titleEn"), "hangulquest.trail." + id + ".title"),
				trail.titleEnRaw(), id + " title after " + stage);
		JsonObject finale = child(json, "finale");
		assertEquals(expected(text(finale, "textEn"), "hangulquest.trail." + id + ".finale"),
				trail.finaleEnRaw(), id + " finale text after " + stage);

		JsonArray steps = json.getAsJsonArray("steps");
		assertEquals(steps.size(), trail.steps().size(), id + " step count after " + stage);
		for (int i = 0; i < steps.size(); i++) {
			assertStepEnglish(steps.get(i).getAsJsonObject(), trail.steps().get(i), id, stage);
		}
		assertStepEnglish(child(finale, "step"), trail.finale(), id, stage);
		assertNotNull(expected(null, "hangulquest.trail." + id + ".title"), id + " has no English title key");
	}

	private static void assertStepEnglish(JsonObject json, QuestStep step, String trail, String stage) {
		String where = trail + "/" + step.id() + " after " + stage;
		String base = "hangulquest.trail." + trail + ".step." + step.id();
		assertEquals(expected(text(child(json, "clue"), "en"), base + ".clue"), step.clueEn(), "clue " + where);
		assertEquals(expected(text(child(json, "hint"), "en"), base + ".hint"), step.hintEn(), "hint " + where);
	}

	@Test
	void loaderKeepsEveryEnglishText() throws Exception {
		Map<String, JsonObject> json = shippedJson();
		List<Questline> loaded = QuestLoader.loadAll();
		assertEquals(json.size(), loaded.size());
		for (Questline trail : loaded) {
			assertEnglishMatches(json.get(trail.id()), trail, "loading");
		}
	}

	@Test
	void validatorKeepsEveryEnglishText() throws Exception {
		Map<String, JsonObject> json = shippedJson();
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		QuestValidator.Result result = QuestValidator.validate(QuestLoader.loadAll(), catalog, LOG);
		assertEquals(json.size(), result.trails().size());
		for (Questline trail : result.trails()) {
			assertEnglishMatches(json.get(trail.id()), trail, "validation");
		}
	}

	@Test
	void storeKeepsEveryEnglishText() throws Exception {
		Map<String, JsonObject> json = shippedJson();
		Path folder = tempDir.resolve("exported");
		QuestlineStore.exportShipped(folder, LOG);
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		QuestValidator.Result result = QuestValidator.validate(
				QuestlineStore.loadAll(folder, LOG), catalog, LOG);
		assertEquals(json.size(), result.trails().size());
		for (Questline trail : result.trails()) {
			assertEnglishMatches(json.get(trail.id()), trail, "store load");
		}
	}

	@Test
	void overrideMergeKeepsEveryEnglishText() throws Exception {
		Map<String, JsonObject> json = shippedJson();
		Path folder = tempDir.resolve("overrides");
		Files.createDirectories(folder);
		// Every shipped trail copied back as an opted-in override takes the merge path.
		for (Map.Entry<String, JsonObject> entry : json.entrySet()) {
			JsonObject copy = entry.getValue().deepCopy();
			copy.addProperty("override", true);
			Files.writeString(folder.resolve(entry.getKey() + ".json"), copy.toString(), StandardCharsets.UTF_8);
		}
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		QuestValidator.Result result = QuestValidator.validate(
				QuestlineStore.loadAll(folder, LOG), catalog, LOG);
		assertEquals(json.size(), result.trails().size());
		for (Questline trail : result.trails()) {
			assertEnglishMatches(json.get(trail.id()), trail, "override merge");
		}
	}

	@Test
	void editedOverrideShowsItsOwnEnglishText() throws Exception {
		Map<String, JsonObject> json = shippedJson();
		JsonObject edited = json.values().iterator().next().deepCopy();
		String id = edited.get("id").getAsString();
		edited.addProperty("override", true);
		edited.addProperty("titleEn", "Edited English title");
		edited.getAsJsonObject("finale").addProperty("textEn", "Edited finale text.");
		JsonObject firstStep = edited.getAsJsonArray("steps").get(0).getAsJsonObject();
		JsonObject firstClue = child(firstStep, "clue");
		if (firstClue == null) {
			firstClue = new JsonObject();
			firstStep.add("clue", firstClue);
		}
		firstClue.addProperty("en", "Edited first clue.");
		Path folder = tempDir.resolve("edited");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("edited.json"), edited.toString(), StandardCharsets.UTF_8);

		Questline trail = QuestlineStore.loadAll(folder, LOG).stream()
				.filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
		assertEquals("Edited English title", trail.titleEnRaw());
		assertEquals("Edited finale text.", trail.finaleEnRaw());
		assertEquals("Edited first clue.", trail.steps().get(0).clueEn());
		// The ceremony text must not leak into the finale step's clue.
		assertEquals(expected(text(child(child(child(json.get(id), "finale"), "step"), "clue"), "en"),
				"hangulquest.trail." + id + ".step.finale.clue"), trail.finale().clueEn());
	}

	@Test
	void withEnglishCopiesInsteadOfMutatingAndLeavesTheFinaleStepAlone() {
		Questline original = QuestLoader.loadAll().get(0);
		QuestStep finale = original.finale();
		String clue = finale.clueEn();
		String hint = finale.hintEn();
		String title = original.titleEnRaw();
		String ceremony = original.finaleEnRaw();

		Questline copy = original.withEnglish("Another title", "Another ceremony");

		assertNotSame(original, copy);
		assertEquals(title, original.titleEnRaw(), "the original must not change");
		assertEquals(ceremony, original.finaleEnRaw(), "the original must not change");
		assertSame(finale, original.finale());
		assertEquals("Another title", copy.titleEnRaw());
		assertEquals("Another ceremony", copy.finaleEnRaw());
		assertEquals(clue, copy.finale().clueEn(), "the finale clue is not the ceremony text");
		assertEquals(hint, copy.finale().hintEn(), "the finale hint must survive");
		assertTrue(copy.finale().clueEn() == null || !copy.finale().clueEn().equals("Another ceremony"));
	}

	@Test
	void everyWithMethodKeepsTheOtherFields() {
		Questline original = QuestLoader.loadAll().get(0);
		Questline copy = original
				.withRequires(new Questline.Requires(3, null, List.of("x")))
				.withTopic("topic-x")
				.withLevels(4, 5)
				.withAdaptive(true)
				.withSteps(original.steps(), original.finale());
		assertEquals(original.titleEnRaw(), copy.titleEnRaw());
		assertEquals(original.finaleEnRaw(), copy.finaleEnRaw());
		assertEquals(original.titleKo(), copy.titleKo());
		assertEquals(original.titleDe(), copy.titleDe());
		assertEquals(original.finaleKo(), copy.finaleKo());
		assertEquals(original.finaleDe(), copy.finaleDe());
		assertEquals(original.badge(), copy.badge());
		assertEquals(original.order(), copy.order());
		assertEquals(original.concepts(), copy.concepts());
		assertEquals("topic-x", copy.topic());
		assertEquals(4, copy.tier());
		assertEquals(5, copy.koreanLevel());
		assertTrue(copy.adaptive());
		assertEquals(3, copy.requires().minWordsKnown());
	}
}
