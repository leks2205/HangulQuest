package io.github.leks2205.hangulquest.learn.hvpt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sound forge's record ({@code hvpt}) was added without a version bump: a file without it reads as "never
 * played", and a build that does not know it, or a later one that adds to it, loses nothing.
 */
class HvptFixtureTest {
	private static final Logger LOG = LoggerFactory.getLogger("hvpt-fixture");

	@TempDir
	Path tempDir;

	private Path install(String fixture) throws IOException {
		Path dir = tempDir.resolve(fixture);
		Files.createDirectories(dir);
		try (InputStream in = HvptFixtureTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
			assertNotNull(in, "missing fixture " + fixture);
			Files.write(dir.resolve("progress.json"), in.readAllBytes());
		}
		return dir;
	}

	private static JsonObject read(Path dir) throws IOException {
		return JsonParser.parseString(Files.readString(dir.resolve("progress.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
	}

	@Test
	void theRecordLoads() throws IOException {
		ProgressData data = new ProgressStore(install("v4-hvpt.json"), LOG).data();
		assertEquals(io.github.leks2205.hangulquest.learn.ProgressData.CURRENT_VERSION, data.version, "the record needed no bump of its own");
		assertEquals(2, data.hvpt.sessions.size());
		ProgressData.HvptSession train = data.hvpt.sessions.get("1792400000000");
		assertEquals("TRAIN", train.phase);
		assertEquals(60, train.trials);
		assertEquals(40, train.correct);
		assertEquals(5, train.byContrast.get("gk").correct);
		assertEquals(8, data.hvpt.contrasts.get("vo").correct);
		assertEquals(1792300000000L, data.hvpt.startedAt);
	}

	@Test
	void theProgressPageReadsTheFixture() throws IOException {
		ProgressData data = new ProgressStore(install("v4-hvpt.json"), LOG).data();
		HvptProgress progress = HvptProgress.of(data.hvpt, HvptSet.shared(), 1792500000000L, ZoneOffset.UTC);
		assertTrue(progress.preDone());
		assertEquals(1, progress.trainingRuns());
		assertEquals(0.5, progress.preAccuracy(), 1e-9);
		assertEquals(40.0 / 60.0, progress.recentAccuracy(), 1e-9);
		assertFalse(progress.postDone());
	}

	@Test
	void anOlderFileReadsAsNeverPlayedAndGainsTheRecordOnSave() throws IOException {
		for (String name : new String[] {"v4-autotrack.json", "v4-sample.json", "v3-sample.json", "v2.1.0.json"}) {
			Path dir = install(name);
			ProgressStore store = new ProgressStore(dir, LOG);
			assertTrue(store.data().hvpt.sessions.isEmpty(), name);
			assertTrue(store.data().hvpt.contrasts.isEmpty(), name);
			assertEquals(HvptPhase.PRE, HvptProgress.of(store.data().hvpt, HvptSet.shared(), 1, ZoneOffset.UTC).next());
			store.markDirty();
			store.flush();
			assertTrue(read(dir).has("hvpt"), name + " now carries the record");
		}
	}

	@Test
	void whatABuildDoesNotKnowInsideTheRecordSurvivesASave() throws IOException {
		Path dir = install("v4-hvpt.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		store.data().hvpt.contrasts.get("gk").trials += 10;
		store.markDirty();
		store.flush();

		JsonObject hvpt = read(dir).getAsJsonObject("hvpt");
		assertEquals("a later build wrote this", hvpt.get("packNote").getAsString());
		JsonObject train = hvpt.getAsJsonObject("sessions").getAsJsonObject("1792400000000");
		assertEquals("speakers", train.get("heardVia").getAsString(), "a field of a run");
		assertEquals("kept", hvpt.getAsJsonObject("sessions").getAsJsonObject("1792300000000")
				.getAsJsonObject("byContrast").getAsJsonObject("ss").get("note").getAsString(), "a field of a tally");
		assertEquals(20, hvpt.getAsJsonObject("contrasts").getAsJsonObject("gk").get("trials").getAsInt());
	}

	@Test
	void theRecordSurvivesASaveAndReload() throws IOException {
		Path dir = install("v4-hvpt.json");
		ProgressStore store = new ProgressStore(dir, LOG);
		new HvptRecorder(store.data(), io.github.leks2205.hangulquest.learn.ReviewLog.disabled(), store::markDirty)
				.finished(new HvptResult(HvptPhase.POST, 30, 27, java.util.Map.of("gk", new HvptResult.Tally(5, 5))), 1792600000000L);
		store.flush();
		ProgressData reloaded = new ProgressStore(dir, LOG).data();
		assertEquals(3, reloaded.hvpt.sessions.size());
		assertEquals("POST", reloaded.hvpt.sessions.get("1792600000000").phase);
		assertEquals(27, reloaded.hvpt.sessions.get("1792600000000").correct);
	}
}
