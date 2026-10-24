package io.github.leks2205.hangulquest.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schema v5 sample: the villager requests load, survive a save, and a current file needs no backup. */
class RequestsFixtureTest {
	private static final Logger LOG = LoggerFactory.getLogger("requests-fixture-test");

	@TempDir
	Path dir;

	private Path install(String fixture) throws IOException {
		try (InputStream in = RequestsFixtureTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
			assertNotNull(in, fixture);
			Files.write(dir.resolve("progress.json"), in.readAllBytes());
		}
		return dir;
	}

	@Test
	void theOpenRequestsLoadInTheirOrderWithEveryField() throws IOException {
		ProgressData data = new ProgressStore(install("v5-sample.json"), LOG).data();
		assertEquals(ProgressData.CURRENT_VERSION, data.version);
		List<ProgressData.OpenRequest> open = data.requests.open;
		assertEquals(List.of("req_plains_01", "req_desert_01", "req_plains_02"), open.stream().map(o -> o.id).toList());
		ProgressData.OpenRequest first = open.get(0);
		assertEquals("00000000-0000-0001-0000-000000000001", first.villager);
		assertEquals("민준", first.name);
		assertEquals(1792300300000L, first.acceptedAt);
		assertTrue(first.tracked);
		assertFalse(open.get(1).tracked, "a request that waits outside the full quest log");
		assertTrue(open.get(2).done);
		assertEquals(3, open.get(2).progress);
		assertEquals(Map.of("req_plains_01", 2, "req_taiga_04", 1), data.requests.finished);
		assertEquals(List.of(2, 3, 4, 5), data.meta.schemaHistory);
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.noneMatch(path -> path.getFileName().toString().contains("-backup-")),
					"a current file needs no migration backup");
		}
	}

	@Test
	void theRequestsSurviveASaveAndReload() throws IOException {
		ProgressStore store = new ProgressStore(install("v5-sample.json"), LOG);
		store.data().requests.open.get(0).progress = 1;
		store.data().requests.open.remove(1);
		store.markDirty();
		store.flush();
		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertEquals(2, reloaded.requests.open.size());
		assertEquals(1, reloaded.requests.open.get(0).progress);
		assertEquals("req_plains_02", reloaded.requests.open.get(1).id);
		assertEquals(2, reloaded.requests.finished.get("req_plains_01"));
	}

	@Test
	void aBrokenEntryIsDroppedNotTheFile() throws IOException {
		Files.writeString(dir.resolve("progress.json"), "{\"version\":5,\"requests\":{\"open\":[{\"id\":\"req_x\"},"
				+ "{\"id\":\"req_y\",\"villager\":\"00000000-0000-0001-0000-000000000001\"}]}}");
		ProgressData data = new ProgressStore(dir, LOG).data();
		assertEquals(List.of("req_y"), data.requests.open.stream().map(o -> o.id).toList());
		assertTrue(data.requests.finished.isEmpty());
	}
}
