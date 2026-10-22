package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest and word text lives in {@code lang/content/<code>.json} under {@code hangulquest.trail.*} and
 * {@code hangulquest.word.*} keys. English is the reference and must be complete; German is reported
 * and may be incomplete (it falls back to English). A questline file's inline fields still win.
 */
class ContentTextTest {
	private static final String TRAIL = "hangulquest.trail.";
	private static final String WORD = "hangulquest.word.";
	private static final Lang SPANISH = Lang.of("es_es");
	/** A shipped trail with a German title, a German first clue and no German hints. */
	private static final String SAMPLE = "abenteuerpfad";

	private static Map<String, String> english() {
		return I18n.bundle("en_us");
	}

	private static Map<String, String> german() {
		return I18n.bundle("de_de");
	}

	/** Every key the shipped trails and words can have a text for. */
	private static Set<String> ownedKeys() {
		Set<String> owned = new LinkedHashSet<>();
		for (Questline trail : QuestLoader.loadAll()) {
			owned.add(TRAIL + trail.id() + ".title");
			owned.add(TRAIL + trail.id() + ".finale");
			for (QuestStep step : trail.allSteps()) {
				owned.add(TRAIL + trail.id() + ".step." + step.id() + ".clue");
				owned.add(TRAIL + trail.id() + ".step." + step.id() + ".hint");
			}
		}
		for (Concept concept : ConceptLoader.load()) {
			owned.add(Concept.key(concept.id, "gloss"));
			owned.add(Concept.key(concept.id, "sentence"));
		}
		return owned;
	}

	private static boolean isContentKey(String key) {
		return key.startsWith(TRAIL) || key.startsWith(WORD);
	}

	/** The shipped sample trail's file, changed by the caller and parsed like a player's own file. */
	private static Questline sample(Consumer<JsonObject> change) {
		byte[] file = QuestLoader.classpathFiles().get(SAMPLE + ".json");
		assertNotNull(file, SAMPLE + ".json is not shipped");
		JsonObject root = JsonParser.parseString(new String(file, StandardCharsets.UTF_8)).getAsJsonObject();
		change.accept(root);
		Questline trail = QuestLoader.parse(root);
		assertNotNull(trail);
		return trail;
	}

	@Test
	void everyShippedTrailAndStepHasEnglishText() {
		List<String> missing = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			List<String> keys = new ArrayList<>(List.of(TRAIL + trail.id() + ".title", TRAIL + trail.id() + ".finale"));
			for (QuestStep step : trail.allSteps()) {
				keys.add(TRAIL + trail.id() + ".step." + step.id() + ".clue");
				keys.add(TRAIL + trail.id() + ".step." + step.id() + ".hint");
			}
			for (String key : keys) {
				if (!english().containsKey(key) || english().get(key).isBlank()) {
					missing.add(key);
				}
			}
		}
		assertTrue(missing.isEmpty(), missing.size() + " English texts missing, for example " + missing.stream().limit(10).toList());
	}

	@Test
	void everyConceptHasAnEnglishGloss() {
		List<String> missing = new ArrayList<>();
		for (Concept concept : ConceptLoader.load()) {
			if (I18n.find("en_us", Concept.key(concept.id, "gloss")) == null) {
				missing.add(concept.id);
			}
		}
		assertTrue(missing.isEmpty(), "no English gloss: " + missing);
	}

	@Test
	void germanCoverageIsReportedAndMayBeIncomplete() throws IOException {
		long needed = english().keySet().stream().filter(ContentTextTest::isContentKey).count();
		long translated = german().keySet().stream().filter(ContentTextTest::isContentKey).count();
		long covered = german().keySet().stream().filter(ContentTextTest::isContentKey)
				.filter(key -> english().containsKey(key)).count();
		String report = "content text, German: " + covered + " of " + needed + " English keys translated ("
				+ (needed == 0 ? 0 : covered * 100 / needed) + "%), " + translated + " German keys in all";
		Path out = Path.of("build", "reports", "content-text", "coverage.txt");
		Files.createDirectories(out.getParent());
		Files.writeString(out, report + System.lineSeparator(), StandardCharsets.UTF_8);
		System.out.println(report);
		// Incomplete is fine; a German key that nothing owns is a typo (everyContentKeyBelongsTo...).
		assertTrue(needed > 0 && translated > 0, "the content lang files are empty");
	}

	@Test
	void everyContentKeyBelongsToATrailStepOrWord() {
		Set<String> owned = ownedKeys();
		for (String code : List.of("en_us", "de_de")) {
			List<String> orphans = new ArrayList<>();
			for (String key : I18n.bundle(code).keySet()) {
				if (isContentKey(key) && !owned.contains(key)) {
					orphans.add(key);
				}
			}
			assertTrue(orphans.isEmpty(), code + " has keys no trail, step or word owns: "
					+ orphans.stream().limit(10).toList());
		}
	}

	@Test
	void formatArgumentsMatchBetweenLanguages() {
		List<String> mismatched = new ArrayList<>();
		for (String code : List.of("de_de", "es_es")) {
			for (Map.Entry<String, String> entry : I18n.bundle(code).entrySet()) {
				String reference = english().get(entry.getKey());
				if (isContentKey(entry.getKey()) && reference != null
						&& I18n.argCount(reference) != I18n.argCount(entry.getValue())) {
					mismatched.add(code + " " + entry.getKey());
				}
			}
		}
		assertTrue(mismatched.isEmpty(), "argument counts differ from English: " + mismatched);
	}

	@Test
	void shippedFilesCarryNoInlineEnglishOrGerman() throws IOException {
		List<String> inline = new ArrayList<>();
		for (Map.Entry<String, byte[]> file : QuestLoader.classpathFiles().entrySet()) {
			String text = new String(file.getValue(), StandardCharsets.UTF_8);
			if (text.contains("\"titleEn\"") || text.contains("\"titleDe\"") || text.contains("\"textEn\"")
					|| text.contains("\"textDe\"") || text.contains("\"en\":") || text.contains("\"de\":")) {
				inline.add(file.getKey());
			}
		}
		assertTrue(inline.isEmpty(), "inline English/German text in shipped files, run "
				+ "tools/i18n/extract_content.py: " + inline.stream().limit(10).toList());
		try (var stream = getClass().getResourceAsStream("/assets/hangulquest/data/concepts.json")) {
			String concepts = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertFalse(concepts.contains("\"en\":") || concepts.contains("\"de\":")
					|| concepts.contains("\"sentenceDe\""), "concepts.json carries glosses inline");
		}
	}

	@Test
	void trailsRenderTheirKeysInEnglishAndGerman() {
		for (Questline trail : QuestLoader.loadAll()) {
			String id = TRAIL + trail.id();
			assertEquals(english().get(id + ".title"), trail.title(Lang.EN), id);
			assertEquals(german().getOrDefault(id + ".title", english().get(id + ".title")), trail.title(Lang.DE), id);
			assertEquals(english().get(id + ".finale"), trail.finale(Lang.EN), id);
			assertEquals(german().getOrDefault(id + ".finale", english().get(id + ".finale")), trail.finale(Lang.DE), id);
			for (QuestStep step : trail.allSteps()) {
				String base = id + ".step." + step.id();
				assertEquals(english().get(base + ".clue"), step.clue(Lang.EN), base);
				assertEquals(english().get(base + ".hint"), step.hint(Lang.EN), base);
				assertEquals(german().getOrDefault(base + ".clue", english().get(base + ".clue")), step.clue(Lang.DE), base);
				assertEquals(german().getOrDefault(base + ".hint", english().get(base + ".hint")), step.hint(Lang.DE), base);
			}
		}
	}

	@Test
	void missingGermanFallsBackToEnglish() {
		// A trail written for this test, so the check never depends on which shipped hints still lack German.
		Questline trail = QuestLoader.parse(JsonParser.parseString("""
				{"id":"hint_without_german","titleKo":"길","steps":[{"id":"s1","teach":["stone"],
				 "goal":{"type":"BREAK_BLOCK","block":"minecraft:stone","count":1},
				 "hint":{"en":"Mine a stone."}}],
				 "finale":{"step":{"id":"finale","goal":{"type":"PLACE_BLOCK","block":"minecraft:torch","count":1}}}}""")
				.getAsJsonObject());
		QuestStep step = trail.steps().get(0);
		assertEquals("Mine a stone.", step.hint(Lang.DE));
		assertNull(step.hintDe(), "hintDe() is the German text only");
		assertEquals("Mine a stone.", step.hintEn());
		// ... and a shipped hint that has German text shows it
		boolean shown = false;
		for (Questline shipped : QuestLoader.loadAll()) {
			for (QuestStep other : shipped.allSteps()) {
				String key = TRAIL + shipped.id() + ".step." + other.id() + ".hint";
				if (german().containsKey(key)) {
					assertEquals(german().get(key), other.hint(Lang.DE), key);
					shown = true;
				}
			}
		}
		assertTrue(shown, "no shipped hint has German text at all");
	}

	@Test
	void aTrailWithNoTextAtAllShowsItsId() {
		Questline trail = QuestLoader.parse(JsonParser.parseString("""
				{"id":"nobody_wrote_this","titleKo":"길","finale":{"step":{"id":"finale",
				 "goal":{"type":"PLACE_BLOCK","block":"minecraft:torch","count":1}}}}""").getAsJsonObject());
		assertEquals("nobody_wrote_this", trail.title(Lang.EN));
		assertEquals("nobody_wrote_this", trail.title(Lang.DE));
		assertEquals("", trail.finale().clue(Lang.EN));
	}

	@Test
	void inlineFieldsBeatTheKeysOfAShippedId() {
		Questline trail = sample(root -> {
			root.addProperty("titleEn", "Own title");
			root.getAsJsonObject("finale").addProperty("textEn", "Own ceremony.");
			JsonObject clue = new JsonObject();
			clue.addProperty("en", "Own clue.");
			root.getAsJsonArray("steps").get(0).getAsJsonObject().add("clue", clue);
		});
		String id = TRAIL + SAMPLE;
		assertNotEquals("Own title", english().get(id + ".title"));
		assertEquals("Own title", trail.title(Lang.EN));
		assertEquals("Own title", trail.titleEnRaw());
		assertEquals("Own ceremony.", trail.finale(Lang.EN));
		assertEquals("Own clue.", trail.steps().get(0).clue(Lang.EN));
		assertEquals("Own clue.", trail.steps().get(0).clueEn());
		// A field that was not rewritten keeps its keys, in both languages.
		assertEquals(english().get(id + ".step.s2.clue"), trail.steps().get(1).clue(Lang.EN));
		assertEquals(german().get(id + ".step.s2.clue"), trail.steps().get(1).clue(Lang.DE));
		// A rewritten field hides the shipped German line next to it, so the two cannot disagree.
		assertTrue(german().containsKey(id + ".title"));
		assertEquals("Own title", trail.title(Lang.DE));
		assertEquals("Own clue.", trail.steps().get(0).clue(Lang.DE));
		assertNull(trail.steps().get(0).clueDe());
		assertEquals("Own title", trail.title(SPANISH));
	}

	@Test
	void inlineGermanAloneStillShowsInEnglish() {
		Questline trail = sample(root -> root.addProperty("titleDe", "Nur Deutsch"));
		assertEquals("Nur Deutsch", trail.title(Lang.DE));
		assertEquals("Nur Deutsch", trail.title(Lang.EN));
		assertNull(trail.titleEnRaw());
	}

	@Test
	void otherLanguagesReadTheirOwnContentFileThenEnglish() {
		Questline trail = QuestLoader.loadAll().stream().filter(t -> t.id().equals(SAMPLE)).findFirst().orElseThrow();
		String id = TRAIL + SAMPLE;
		assertEquals("El camino de la aventura", trail.title(SPANISH));
		assertEquals("Consigue un disco.", trail.steps().get(0).clue(SPANISH));
		// No Spanish for the second clue: English, never German.
		assertEquals(english().get(id + ".step.s2.clue"), trail.steps().get(1).clue(SPANISH));
		assertEquals(english().get(id + ".finale"), trail.finale(SPANISH));
		// A language the mod has no file for is English, too.
		assertEquals(english().get(id + ".title"), trail.title(Lang.of("la_la")));
	}

	@Test
	void glossesComeFromTheLangFilesAndOtherLanguagesToo() {
		Concept stone = ConceptLoader.load().stream().filter(c -> c.id.equals("stone")).findFirst().orElseThrow();
		assertEquals(english().get(WORD + "stone.gloss"), stone.en);
		assertEquals(german().get(WORD + "stone.gloss"), stone.de);
		assertEquals(stone.en, stone.toWord().gloss(Lang.EN));
		assertEquals(stone.de, stone.toWord().gloss(Lang.DE));
		assertEquals("piedra", stone.toWord().gloss(SPANISH));
		assertEquals("piedra", stone.toWord().glossFull(SPANISH));
		assertEquals(stone.en, stone.toWord().gloss(Lang.of("la_la")));
		Concept dirt = ConceptLoader.load().stream().filter(c -> c.id.equals("dirt")).findFirst().orElseThrow();
		assertEquals(dirt.en, dirt.toWord().gloss(SPANISH));
	}

	@Test
	void aConceptWithInlineGlossesKeepsThem() {
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.en = "Rock";
		concept.fillText();
		assertEquals("Rock", concept.en);
		assertEquals(german().get(WORD + "stone.gloss"), concept.de);
		concept.de = "Fels";
		concept.fillText();
		assertEquals("Fels", concept.de);
		assertEquals("Fels", concept.toWord().gloss(Lang.DE));
		assertEquals("Rock", concept.toWord().gloss(Lang.EN));
	}
}
