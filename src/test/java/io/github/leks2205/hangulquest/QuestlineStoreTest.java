package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.QuestlineStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The external questline folder: export, add, edit and override without a restart. */
class QuestlineStoreTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("questline-store-test");

	@TempDir
	Path tempDir;

	private static String json(String id, String titleKo) {
		return """
				{
				  "schema": 1,
				  "id": "%s",
				  "titleKo": "%s",
				  "titleDe": "Testpfad",
				  "tier": 1,
				  "order": 5,
				  "koreanLevel": 1,
				  "concepts": ["stone", "torch"],
				  "requires": {"minWordsKnown": 0, "dimension": "minecraft:overworld", "completed": []},
				  "steps": [
				    {"id": "s1", "teach": ["stone"],
				     "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1},
				     "clue": {"ko": "돌을 하나 캐요.", "de": "Baue einen Stein ab."}}
				  ],
				  "finale": {
				    "step": {"id": "finale", "teach": ["torch"],
				             "goal": {"type": "PLACE_BLOCK", "block": "minecraft:torch", "count": 1}},
				    "textKo": "끝났어요.", "textDe": "Fertig."
				  },
				  "reward": {"badge": "%s", "words": ["stone", "torch"]}
				}
				""".formatted(id, titleKo, id);
	}

	private static Questline find(List<Questline> trails, String id) {
		return trails.stream().filter(trail -> trail.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void exportWritesOnlyTemplateAndReadme() throws Exception {
		Path dir = tempDir.resolve("questlines");
		QuestlineStore.exportShipped(dir, LOG);
		assertTrue(Files.exists(dir.resolve("README.md")));
		assertTrue(Files.exists(dir.resolve("_template.json")));
		try (var files = Files.list(dir)) {
			assertEquals(2, files.count(), "no shipped copies may be exported");
		}
		// An edited README or template is never overwritten.
		Files.writeString(dir.resolve("README.md"), "mine", StandardCharsets.UTF_8);
		QuestlineStore.exportShipped(dir, LOG);
		assertEquals("mine", Files.readString(dir.resolve("README.md")));
	}

	@Test
	void readsNewAndChangedFiles() throws Exception {
		Path dir = tempDir.resolve("questlines");
		QuestlineStore.exportShipped(dir, LOG);
		List<Questline> shipped = QuestlineStore.loadAll(dir, LOG);
		assertTrue(shipped.size() >= 34);

		Path file = dir.resolve("hottrail.json");
		Files.writeString(file, json("hottrail", "새 길"), StandardCharsets.UTF_8);
		List<Questline> loaded = QuestlineStore.loadAll(dir, LOG);
		assertEquals(shipped.size() + 1, loaded.size());
		assertEquals("새 길", find(loaded, "hottrail").titleKo());

		Files.writeString(file, json("hottrail", "바뀐 길"), StandardCharsets.UTF_8);
		assertEquals("바뀐 길", find(QuestlineStore.loadAll(dir, LOG), "hottrail").titleKo());
	}

	@Test
	void externalFileOverridesShippedTrail() throws Exception {
		Path dir = tempDir.resolve("override");
		QuestlineStore.exportShipped(dir, LOG);
		Files.writeString(dir.resolve("steinpfad.json"),
				json("steinpfad", "내 돌길").replace("\"schema\": 1,", "\"schema\": 1, \"override\": true,"),
				StandardCharsets.UTF_8);
		List<Questline> loaded = QuestlineStore.loadAll(dir, LOG);
		assertEquals("내 돌길", find(loaded, "steinpfad").titleKo());
	}

	@Test
	void oldExternalFileGetsShippedMetadataAndLinks() throws Exception {
		Path dir = tempDir.resolve("migrate");
		QuestlineStore.exportShipped(dir, LOG);
		Files.writeString(dir.resolve("baeckerei.json"),
				json("baeckerei", "Mein Brotweg").replace("\"schema\": 1,", "\"schema\": 1, \"override\": true,"),
				StandardCharsets.UTF_8);
		List<Questline> loaded = QuestlineStore.loadAll(dir, LOG);
		Questline migrated = find(loaded, "baeckerei");
		assertEquals("Mein Brotweg", migrated.titleKo());
		assertEquals("음식", migrated.topic());
		assertTrue(migrated.requires().completed().contains("brotpfad"),
				"new branch link missing: " + migrated.requires().completed());
	}


	@Test
	void externalFileWithTopicStillGetsNewBranchLinks() throws Exception {
		Path dir = tempDir.resolve("migrate2");
		QuestlineStore.exportShipped(dir, LOG);
		String old = json("baeckerei", "Mein Brotweg")
				.replace("\"schema\": 1,", "\"schema\": 1, \"override\": true,")
				.replace("\"titleDe\": \"Testpfad\",", "\"titleDe\": \"Testpfad\", \"topic\": \"음식\",");
		Files.writeString(dir.resolve("baeckerei.json"), old, StandardCharsets.UTF_8);
		Questline migrated = find(QuestlineStore.loadAll(dir, LOG), "baeckerei");
		assertTrue(migrated.requires().completed().contains("brotpfad"),
				"branch link missing with topic present: " + migrated.requires().completed());
	}


	@Test
	void exportedCopyWithoutOptInDoesNotShadowShippedFixes() throws Exception {
		Path dir = tempDir.resolve("nooverride");
		QuestlineStore.exportShipped(dir, LOG);
		Files.writeString(dir.resolve("steinpfad.json"), json("steinpfad", "Alte Kopie"),
				StandardCharsets.UTF_8);
		Questline loaded = find(QuestlineStore.loadAll(dir, LOG), "steinpfad");
		assertNotEquals("Alte Kopie", loaded.titleKo());
	}

}
