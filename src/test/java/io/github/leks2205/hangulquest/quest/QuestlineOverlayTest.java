package io.github.leks2205.hangulquest.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The local overlay folder: change detection, the identical-copy skip and the merge rules. */
class QuestlineOverlayTest {
	private static final Logger LOG = LoggerFactory.getLogger("overlay-test");

	@TempDir
	Path dir;

	private static String json(String id, String titleKo) {
		return """
				{"schema": 1, "id": "%s", "titleKo": "%s", "titleDe": "Testpfad", "tier": 1,
				 "concepts": ["stone"], "requires": {"completed": []},
				 "steps": [{"id": "s1", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}],
				 "finale": {"step": {"id": "finale", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}}}
				""".formatted(id, titleKo);
	}

	private void write(String name, String content) throws Exception {
		Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
	}

	private static QuestlineOverlay overlay(Map<String, byte[]> shipped) {
		return new QuestlineOverlay(QuestlineStore.readShipped(shipped, LOG).digests(), LOG);
	}

	private static Map<String, byte[]> shippedFiles(String... ids) {
		java.util.LinkedHashMap<String, byte[]> files = new java.util.LinkedHashMap<>();
		for (String id : ids) {
			files.put(id + ".json", json(id, "배송 " + id).getBytes(StandardCharsets.UTF_8));
		}
		return files;
	}

	@Test
	void refreshOnlyParsesNewAndChangedFiles() throws Exception {
		QuestlineOverlay overlay = overlay(Map.of());
		assertFalse(overlay.refresh(dir), "an empty folder is no change");

		write("a.json", json("a", "하나"));
		write("b.json", json("b", "둘"));
		write("c.json", json("c", "셋"));
		assertTrue(overlay.refresh(dir));
		assertEquals(3, overlay.parsedCount());

		assertFalse(overlay.refresh(dir), "nothing changed");
		assertEquals(3, overlay.parsedCount(), "unchanged files are not parsed again");

		write("b.json", json("b", "둘 둘 둘"));
		assertTrue(overlay.refresh(dir));
		assertEquals(4, overlay.parsedCount(), "only the edited file is parsed again");

		Files.delete(dir.resolve("a.json"));
		assertTrue(overlay.refresh(dir));
		assertEquals(List.of("b.json", "c.json"), overlay.files().stream().map(QuestFile::name).toList());
	}

	@Test
	void sameSizeEditIsSeenThroughTheModificationTime() throws Exception {
		QuestlineOverlay overlay = overlay(Map.of());
		write("a.json", json("a", "가가"));
		overlay.refresh(dir);
		write("a.json", json("a", "나나"));
		Files.setLastModifiedTime(dir.resolve("a.json"),
				FileTime.fromMillis(System.currentTimeMillis() + 5000));
		assertTrue(overlay.refresh(dir));
		assertEquals("나나", overlay.files().get(0).trail().titleKo());
	}

	@Test
	void invalidateRereadsEverything() throws Exception {
		QuestlineOverlay overlay = overlay(Map.of());
		write("a.json", json("a", "하나"));
		overlay.refresh(dir);
		overlay.invalidate();
		assertTrue(overlay.refresh(dir));
		assertEquals(2, overlay.parsedCount());
	}

	@Test
	void byteIdenticalCopiesOfShippedFilesAreSkippedWithoutParsing() throws Exception {
		Map<String, byte[]> shipped = shippedFiles("alpha", "beta");
		QuestlineOverlay overlay = overlay(shipped);
		// An old install: the shipped files exported under their own names, plus one renamed copy.
		for (Map.Entry<String, byte[]> entry : shipped.entrySet()) {
			Files.write(dir.resolve(entry.getKey()), entry.getValue());
		}
		Files.write(dir.resolve("kopie.json"), shipped.get("alpha.json"));
		overlay.refresh(dir);

		assertEquals(0, overlay.parsedCount());
		assertTrue(overlay.files().stream().allMatch(QuestFile::identical));
		List<Questline> merged = QuestlineStore.merge(
				QuestlineStore.readShipped(shipped, LOG).trails(), overlay.files(), LOG);
		assertEquals(List.of("alpha", "beta"), merged.stream().map(Questline::id).toList());
	}

	@Test
	void editedCopyIsNotSkipped() throws Exception {
		Map<String, byte[]> shipped = shippedFiles("alpha");
		QuestlineOverlay overlay = overlay(shipped);
		write("alpha.json", json("alpha", "내 버전").replace("\"schema\": 1,", "\"schema\": 1, \"override\": true,"));
		overlay.refresh(dir);
		QuestFile file = overlay.files().get(0);
		assertFalse(file.identical());
		assertTrue(file.override());
		List<Questline> merged = QuestlineStore.merge(
				QuestlineStore.readShipped(shipped, LOG).trails(), overlay.files(), LOG);
		assertEquals("내 버전", merged.get(0).titleKo());
	}

	@Test
	void mergeRules() throws Exception {
		Map<String, byte[]> shipped = shippedFiles("alpha");
		QuestlineOverlay overlay = overlay(shipped);
		write("alpha.json", json("alpha", "ohne Opt-in"));          // same id, no override: ignored
		write("neu.json", json("neu", "새 길"));                     // new id: added
		write("kaputt.json", "{ not json");                          // broken: ignored
		write("ohneid.json", "{\"titleKo\": \"x\"}");                // no id: ignored
		write("_vorlage.json", json("vorlage", "x"));                // underscore: not a trail file
		write("index.json", "[\"alpha.json\"]");                     // old index: not a trail file
		overlay.refresh(dir);
		List<Questline> merged = QuestlineStore.merge(
				QuestlineStore.readShipped(shipped, LOG).trails(), overlay.files(), LOG);
		assertEquals(List.of("alpha", "neu"), merged.stream().map(Questline::id).toList());
		assertEquals("배송 alpha", merged.get(0).titleKo());
		assertEquals("새 길", merged.get(1).titleKo());
	}

	@Test
	void brokenFileKeepsItsError() throws Exception {
		QuestlineOverlay overlay = overlay(Map.of());
		write("kaputt.json", "{ not json");
		overlay.refresh(dir);
		QuestFile file = overlay.files().get(0);
		assertNull(file.trail());
		assertNotNull(file.error());
	}

	@Test
	void laterShippedFileWithTheSameIdWins() {
		java.util.LinkedHashMap<String, byte[]> files = new java.util.LinkedHashMap<>();
		files.put("hangulquest:data/questlines/a.json", json("same", "mod").getBytes(StandardCharsets.UTF_8));
		files.put("pack:hangulquest/questlines/b.json", json("same", "pack").getBytes(StandardCharsets.UTF_8));
		files.put("pack:hangulquest/questlines/_vorlage.json", json("tmpl", "x").getBytes(StandardCharsets.UTF_8));
		QuestlineStore.Shipped shipped = QuestlineStore.readShipped(files, LOG);
		assertEquals(1, shipped.trails().size());
		assertEquals("pack", shipped.trails().get(0).titleKo());
	}

	@Test
	void ownNamespaceComesBeforePacks() {
		List<String> ids = new java.util.ArrayList<>(List.of("zed:data/questlines/a.json",
				"hangulquest:hangulquest/questlines/b.json", "abc:data/questlines/c.json",
				"hangulquest:data/questlines/d.json"));
		ids.sort(QuestResources::compareIds);
		assertEquals(List.of("hangulquest:data/questlines/d.json", "hangulquest:hangulquest/questlines/b.json",
				"abc:data/questlines/c.json", "zed:data/questlines/a.json"), ids);
	}

	@Test
	void classpathListingFindsTheShippedTrailsWithoutAnIndexFile() {
		List<String> names = QuestLoader.index();
		assertTrue(names.size() >= 400, "shipped trails found: " + names.size());
		assertFalse(names.contains("index.json"));
		assertTrue(names.stream().noneMatch(name -> name.startsWith("_")));
		assertEquals(names.size(), QuestLoader.loadAll().size());
	}
}
