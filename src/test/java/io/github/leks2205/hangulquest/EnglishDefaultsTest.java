package io.github.leks2205.hangulquest;

import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.AdaptiveTrailGenerator;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Generated and defaulted text must not fall back to German in the English interface. */
class EnglishDefaultsTest {
	@TempDir
	Path tempDir;

	@AfterEach
	void resetLang() {
		Lang.set("EN");
	}

	@Test
	void adaptiveTrailSpeaksEnglishInTheEnglishInterface() {
		ProgressStore store = new ProgressStore(tempDir.resolve("run"), LoggerFactory.getLogger("test"));
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		LearningService learn = new LearningService(store.data(), store, catalog);
		Questline trail = AdaptiveTrailGenerator.generate(catalog, learn, "world", 1_000L);
		assertNotNull(trail);

		Lang.set("EN");
		assertEquals("The review path", trail.title());
		assertEquals("Night has fallen. A light is lit: today's review is done.", trail.finale(Lang.EN));

		Lang.set("DE");
		assertEquals("Der Wiederholungspfad", trail.title());
		assertEquals("Die Nacht ist da. Ein Licht entzündet: die Wiederholung ist geschafft.",
				trail.finale(Lang.DE));
	}

	@Test
	void aQuestlineWithoutFinaleTextGetsAnEnglishDefault() {
		Questline trail = QuestLoader.parse(JsonParser.parseString("""
				{"id":"bare","finale":{"step":{"id":"finale",
				 "goal":{"type":"PLACE_BLOCK","block":"minecraft:torch","count":1}}}}""").getAsJsonObject());
		assertNotNull(trail);
		assertEquals("You reached the goal.", trail.finale(Lang.EN));
		assertEquals("Du hast das Ziel erreicht.", trail.finale(Lang.DE));
	}

	@Test
	void anAuthoredEnglishFinaleIsKept() {
		Questline trail = QuestLoader.parse(JsonParser.parseString("""
				{"id":"own","finale":{"step":{"id":"finale",
				 "goal":{"type":"PLACE_BLOCK","block":"minecraft:torch","count":1}},
				 "textDe":"Fertig."},"textEn":"All done."}""").getAsJsonObject());
		assertEquals("All done.", trail.finale(Lang.EN));
	}
}
