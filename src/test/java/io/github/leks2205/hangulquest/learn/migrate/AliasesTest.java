package io.github.leks2205.hangulquest.learn.migrate;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A renamed trail, step or word must not cost anybody their progress. */
class AliasesTest {
	private static final Logger LOG = LoggerFactory.getLogger("aliases-test");

	@TempDir
	Path tempDir;

	private static Aliases aliases(String json) {
		return Aliases.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private Path install(String fixture) throws IOException {
		Path dir = tempDir.resolve(fixture);
		Files.createDirectories(dir);
		try (InputStream in = AliasesTest.class.getResourceAsStream("/progress-fixtures/" + fixture)) {
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
	void theShippedFileIsValid() {
		Aliases bundled = Aliases.bundled();
		assertNotNull(bundled);
		assertTrue(bundled.isEmpty(), "nothing has been renamed yet");
	}

	@Test
	void aRenamedTrailKeepsItsCompletedStatusAndActiveStep() throws IOException {
		Path dir = install("v2.1.0.json");
		Aliases renamed = aliases("{\"trails\": {\"steinpfad\": \"steinweg\", \"druckerei\": \"setzerei\"}}");
		ProgressStore store = new ProgressStore(dir, LOG, renamed);
		ProgressData data = store.data();

		assertTrue(data.quests.completed.containsKey("steinweg"));
		assertFalse(data.quests.completed.containsKey("steinpfad"));
		assertEquals(1792300100000L, data.quests.completed.get("steinweg"));
		assertEquals("setzerei", data.quests.active.trailId);
		assertEquals("s2", data.quests.active.stepId);
		assertEquals(1, data.quests.active.goalProgress);
		assertEquals(2, data.quests.active.sidequest.stage);
		assertTrue(data.quests.tracked.contains("setzerei"));
		assertFalse(data.quests.tracked.contains("druckerei"));
		assertEquals("steinweg", data.quests.history.get(0).trailId);
		assertEquals(2, data.quests.completed.size());

		store.flush();
		JsonObject saved = read(dir);
		assertTrue(saved.getAsJsonObject("quests").getAsJsonObject("completed").has("steinweg"));
		ProgressData again = new ProgressStore(dir, LOG, renamed).data();
		assertEquals("setzerei", again.quests.active.trailId);
		assertEquals(data.quests.completed, again.quests.completed);
	}

	@Test
	void aSavedQuestMovesToTheNewTrailKey() throws IOException {
		Path dir = install("v2.1.0.json");
		ProgressData data = new ProgressStore(dir, LOG, aliases("{\"trails\": {\"wasserpfad\": \"wassersteg\"}}")).data();
		assertFalse(data.quests.saved.containsKey("wasserpfad"));
		assertEquals("wassersteg", data.quests.saved.get("wassersteg").trailId);
		assertEquals("s1", data.quests.saved.get("wassersteg").stepId);
	}

	@Test
	void anAliasChainResolvesToTheEnd() throws IOException {
		Aliases chain = aliases("{\"trails\": {\"a\": \"b\", \"b\": \"c\"}, \"words\": {\"w1\": \"w2\", \"w2\": \"w3\"},"
				+ " \"steps\": {\"x/s1\": \"x/s2\", \"x/s2\": \"x/s3\"}}");
		assertEquals("c", chain.trail("a"));
		assertEquals("c", chain.trail("b"));
		assertEquals("c", chain.trail("c"));
		assertEquals("w3", chain.word("w1"));
		assertArrayEquals(new String[] {"x", "s3"}, chain.step("x", "s1"));

		Path dir = install("v2.1.0.json");
		ProgressData data = new ProgressStore(dir, LOG,
				aliases("{\"trails\": {\"steinpfad\": \"t2\", \"t2\": \"t3\"}}")).data();
		assertTrue(data.quests.completed.containsKey("t3"));
		assertFalse(data.quests.completed.containsKey("t2"));
	}

	@Test
	void aStepAndATrailRenamedOverTimeBothResolve() {
		Aliases both = aliases("{\"trails\": {\"alt\": \"neu\"}, \"steps\": {\"alt/s1\": \"alt/s2\", \"neu/s2\": \"neu/s3\"}}");
		assertArrayEquals(new String[] {"neu", "s3"}, both.step("alt", "s1"));
		assertArrayEquals(new String[] {"neu", "s9"}, both.step("alt", "s9"));
	}

	@Test
	void aRenamedStepKeepsTheActiveQuestOnTheRightStep() throws IOException {
		Path dir = install("v2.1.0.json");
		ProgressData data = new ProgressStore(dir, LOG,
				aliases("{\"steps\": {\"druckerei/s2\": \"druckerei/s2b\"}}")).data();
		assertEquals("druckerei", data.quests.active.trailId);
		assertEquals("s2b", data.quests.active.stepId);
		assertEquals(1, data.quests.active.goalProgress);
	}

	@Test
	void renamedWordsKeepTheirScheduleAndUnknownFields() throws IOException {
		Path dir = install("unknown-fields.json");
		ProgressStore store = new ProgressStore(dir, LOG, aliases("{\"words\": {\"concept:stone\": \"concept:rock\","
				+ " \"concept:lectern\": \"concept:reading_desk\"}}"));
		ProgressData data = store.data();
		assertFalse(data.words.containsKey("concept:stone"));
		assertEquals(3, data.words.get("concept:rock").level);
		assertEquals(9, data.words.get("concept:rock").seen);
		assertEquals("concept:reading_desk", data.quests.active.sidequest.wordId);
		assertTrue(data.quests.adaptive.wordIds.contains("concept:rock"));
		assertFalse(data.quests.adaptive.wordIds.contains("concept:stone"));

		store.flush();
		JsonObject words = read(dir).getAsJsonObject("words");
		assertFalse(words.has("concept:stone"));
		assertEquals(12.5, words.getAsJsonObject("concept:rock").getAsJsonObject("fsrs").get("stability").getAsDouble());
	}

	@Test
	void wordOrderIsKeptWhenARenameHappens() throws IOException {
		Path dir = install("v2.1.0.json");
		ProgressData before = new ProgressStore(install("v2.1.0.json"), LOG, Aliases.NONE).data();
		List<String> keys = List.copyOf(before.words.keySet());
		ProgressData data = new ProgressStore(dir, LOG, aliases("{\"words\": {\"concept:torch\": \"concept:lamp\"}}")).data();
		List<String> renamed = keys.stream().map(key -> key.equals("concept:torch") ? "concept:lamp" : key).toList();
		assertEquals(renamed, List.copyOf(data.words.keySet()));
	}

	@Test
	void whenOldAndNewIdBothExistTheOneWithMoreProgressWins() {
		JsonObject root = JsonParser.parseString("{\"words\": {\"old\": {\"seen\": 9, \"level\": 4},"
				+ " \"new\": {\"seen\": 2, \"level\": 1}}, \"quests\": {\"completed\": {\"t1\": 500, \"t2\": 100},"
				+ " \"tracked\": [\"t1\", \"t2\"]}}").getAsJsonObject();
		assertTrue(aliases("{\"words\": {\"old\": \"new\"}, \"trails\": {\"t1\": \"t2\"}}").apply(root));
		assertEquals(1, root.getAsJsonObject("words").size());
		assertEquals(4, root.getAsJsonObject("words").getAsJsonObject("new").get("level").getAsInt());
		assertEquals(100, root.getAsJsonObject("quests").getAsJsonObject("completed").get("t2").getAsLong());
		assertEquals(1, root.getAsJsonObject("quests").getAsJsonArray("tracked").size());
	}

	@Test
	void applyingTwiceChangesNothingTheSecondTime() throws IOException {
		Aliases renamed = aliases("{\"trails\": {\"steinpfad\": \"steinweg\"}, \"words\": {\"concept:cat\": \"concept:kitten\"}}");
		JsonObject tree = JsonParser.parseString(Files.readString(
				install("v2.1.0.json").resolve("progress.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(renamed.apply(tree));
		JsonObject once = tree.deepCopy();
		assertFalse(renamed.apply(tree));
		assertEquals(once, tree);
	}

	@Test
	void aFileFromANewerVersionIsNotRewritten() throws IOException {
		Path dir = install("future-v99.json");
		byte[] before = Files.readAllBytes(dir.resolve("progress.json"));
		ProgressStore store = new ProgressStore(dir, LOG, aliases("{\"words\": {\"concept:stone\": \"concept:rock\"}}"));
		assertTrue(store.readOnly());
		assertTrue(store.data().words.containsKey("concept:stone"));
		store.markDirty();
		store.flush();
		assertArrayEquals(before, Files.readAllBytes(dir.resolve("progress.json")));
	}

	@Test
	void brokenAliasFilesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> aliases("{\"trails\": {\"a\": \"b\", \"b\": \"a\"}}"));
		assertThrows(IllegalArgumentException.class, () -> aliases("{\"words\": {\"a\": \"a\"}}"));
		assertThrows(IllegalArgumentException.class, () -> aliases("{\"steps\": {\"a\": \"b/c\"}}"));
	}
}
