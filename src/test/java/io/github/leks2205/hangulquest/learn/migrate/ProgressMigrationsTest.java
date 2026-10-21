package io.github.leks2205.hangulquest.learn.migrate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.ProgressData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressMigrationsTest {
	private static JsonObject fixture(String name) throws IOException {
		try (InputStream in = ProgressMigrationsTest.class.getResourceAsStream("/progress-fixtures/" + name + ".json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static ProgressMigration step(int from, int to, List<String> log) {
		return new ProgressMigration() {
			@Override
			public int from() {
				return from;
			}

			@Override
			public int to() {
				return to;
			}

			@Override
			public void apply(JsonObject root) {
				log.add(from + "->" + to);
			}
		};
	}

	@Test
	void theChainEndsAtTheCurrentSchema() {
		assertEquals(ProgressData.CURRENT_VERSION, ProgressMigrations.standard().latest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"schema-v1", "v1.0.0", "v2.1.0", "unknown-fields", "v3-sample", "v4-sample", "v4-placed"})
	void runningTwiceChangesNothingTheSecondTime(String name) throws IOException {
		JsonObject tree = fixture(name);
		ProgressMigrations.standard().run(tree);
		JsonObject once = tree.deepCopy();
		assertFalse(ProgressMigrations.standard().run(tree), "already current");
		assertEquals(once, tree);
	}

	@ParameterizedTest
	@ValueSource(strings = {"schema-v1", "v2.1.0"})
	void everyStepIsIdempotentOnItsOwn(String name) throws IOException {
		JsonObject tree = fixture(name);
		ProgressMigrations.standard().run(tree);
		JsonObject once = tree.deepCopy();
		new V2ToV3().apply(tree);
		new V1ToV2().apply(tree);
		new V2ToV3().apply(tree);
		JsonObject meta = tree.getAsJsonObject("meta");
		assertEquals(once.getAsJsonObject("meta").get("installSalt"), meta.get("installSalt"));
		assertEquals(once.getAsJsonObject("totals"), tree.getAsJsonObject("totals"));
	}

	@Test
	void aV2TreeGainsTheV3Fields() throws IOException {
		JsonObject tree = fixture("v2.1.0");
		assertTrue(ProgressMigrations.standard().run(tree));
		assertEquals(4, tree.get("version").getAsInt());
		assertEquals("[2,3,4]", tree.getAsJsonObject("meta").get("schemaHistory").toString());
		assertEquals(32, tree.getAsJsonObject("meta").get("installSalt").getAsString().length());
		assertEquals(0, tree.getAsJsonObject("totals").get("sidequestsCompleted").getAsInt());
		assertEquals(0, tree.getAsJsonObject("totals").get("questStepsCompleted").getAsInt());
	}

	private static final long NOW = 1_800_000_000_000L;

	private static JsonObject word(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static JsonObject v3WithWords(String... entries) {
		JsonObject root = word("{\"version\": 3, \"words\": {}, \"meta\": {\"installSalt\": \"s\", \"schemaHistory\": [2, 3]}}");
		for (int i = 0; i < entries.length; i += 2) {
			root.getAsJsonObject("words").add(entries[i], word(entries[i + 1]));
		}
		return root;
	}

	private static JsonObject runV3ToV4(JsonObject root) {
		new V3ToV4(() -> NOW).apply(root);
		return root;
	}

	@ParameterizedTest
	@ValueSource(doubles = {0.1, 0.2, 0.5, 1, 2, 4, 8, 16, 35})
	void everyOldLevelSeedsItsStability(double stability) {
		double[] table = {0.1, 0.2, 0.5, 1, 2, 4, 8, 16, 35};
		int level = 0;
		for (int i = 0; i < table.length; i++) {
			if (table[i] == stability) {
				level = i;
			}
		}
		JsonObject root = runV3ToV4(v3WithWords("w", "{\"level\": " + level + ", \"dueAt\": " + (NOW + 5_000) + ", \"known\": false}"));
		JsonObject card = root.getAsJsonObject("words").getAsJsonObject("w").getAsJsonObject("fsrs");
		assertEquals(stability, card.get("stability").getAsDouble(), 0);
		assertEquals(5.0, card.get("difficulty").getAsDouble(), 0);
		assertEquals("REVIEW", card.get("state").getAsString());
		assertEquals(level, root.getAsJsonObject("words").getAsJsonObject("w").get("level").getAsInt(), "level is kept");
	}

	@Test
	void dueIsTheLaterOfTheOldDueTimeAndNow() {
		JsonObject root = runV3ToV4(v3WithWords(
				"future", "{\"level\": 3, \"dueAt\": " + (NOW + 777) + "}",
				"past", "{\"level\": 3, \"dueAt\": " + (NOW - 777) + "}"));
		JsonObject words = root.getAsJsonObject("words");
		assertEquals(NOW + 777, words.getAsJsonObject("future").getAsJsonObject("fsrs").get("due").getAsLong());
		assertEquals(NOW, words.getAsJsonObject("past").getAsJsonObject("fsrs").get("due").getAsLong());
	}

	@Test
	void aKnownWordStartsAtThirtyDaysWhateverItsLevel() {
		JsonObject root = runV3ToV4(v3WithWords("k", "{\"level\": 2, \"dueAt\": 9223372036854775807, \"known\": true}"));
		JsonObject card = root.getAsJsonObject("words").getAsJsonObject("k").getAsJsonObject("fsrs");
		assertEquals(30.0, card.get("stability").getAsDouble(), 0);
		assertEquals(Long.MAX_VALUE, card.get("due").getAsLong());
	}

	@Test
	void anOutOfRangeLevelIsClampedAndMissingFieldsDoNotBreak() {
		JsonObject root = runV3ToV4(v3WithWords("high", "{\"level\": 99}", "low", "{\"level\": -4}", "bare", "{}"));
		JsonObject words = root.getAsJsonObject("words");
		assertEquals(35.0, words.getAsJsonObject("high").getAsJsonObject("fsrs").get("stability").getAsDouble(), 0);
		assertEquals(0.1, words.getAsJsonObject("low").getAsJsonObject("fsrs").get("stability").getAsDouble(), 0);
		assertEquals(0.1, words.getAsJsonObject("bare").getAsJsonObject("fsrs").get("stability").getAsDouble(), 0);
		assertEquals(NOW, words.getAsJsonObject("bare").getAsJsonObject("fsrs").get("due").getAsLong());
	}

	@Test
	void anExistingCardIsNotReseeded() {
		JsonObject root = runV3ToV4(v3WithWords("c", "{\"level\": 6, \"dueAt\": 5, \"fsrs\": {\"stability\": 12.5, \"difficulty\": 5.1}}"));
		JsonObject card = root.getAsJsonObject("words").getAsJsonObject("c").getAsJsonObject("fsrs");
		assertEquals(12.5, card.get("stability").getAsDouble(), 0, "the card that was there stays");
		assertEquals(5.1, card.get("difficulty").getAsDouble(), 0);
		assertEquals("REVIEW", card.get("state").getAsString(), "only the missing fields are filled in");
	}

	@Test
	void theV3ToV4StepIsIdempotent() {
		JsonObject root = runV3ToV4(v3WithWords("w", "{\"level\": 4, \"dueAt\": 1, \"correct\": 3, \"wrong\": 1}"));
		JsonObject once = root.deepCopy();
		new V3ToV4(() -> NOW + 99_999).apply(root);
		assertEquals(once.getAsJsonObject("words"), root.getAsJsonObject("words"));
		assertEquals(once.getAsJsonObject("meta"), root.getAsJsonObject("meta"));
	}

	@Test
	void everyOldFixtureGetsACardOnEveryWord() throws IOException {
		for (String name : List.of("schema-v1", "v1.0.0", "v1.1.0", "v1.1.1", "v1.2.0", "v1.3.0", "v1.3.1", "v2.0.0",
				"v2.0.1", "v2.1.0", "v3-sample", "unknown-fields")) {
			JsonObject tree = fixture(name);
			ProgressMigrations.standard().run(tree);
			tree.getAsJsonObject("words").entrySet().forEach(entry ->
					assertTrue(entry.getValue().getAsJsonObject().has("fsrs"), name + " " + entry.getKey()));
		}
	}

	@Test
	void anExistingSaltIsKept() throws IOException {
		JsonObject tree = fixture("unknown-fields");
		ProgressMigrations.standard().run(tree);
		assertEquals("abc123", tree.getAsJsonObject("meta").get("installSalt").getAsString());
	}

	@Test
	void aTreeWithoutAVersionCountsAsV1() {
		assertEquals(1, ProgressMigrations.versionOf(new JsonObject()));
		assertEquals(1, ProgressMigrations.versionOf(JsonParser.parseString("{\"version\": \"x\"}").getAsJsonObject()));
	}

	@Test
	void stepsRunInOrderAndANewerTreeIsLeftAlone() {
		List<String> log = new ArrayList<>();
		ProgressMigrations chain = ProgressMigrations.of(List.of(step(1, 2, log), step(2, 4, log), step(4, 5, log)));
		JsonObject tree = JsonParser.parseString("{\"version\": 2}").getAsJsonObject();
		assertTrue(chain.run(tree));
		assertEquals(List.of("2->4", "4->5"), log);
		assertEquals(5, tree.get("version").getAsInt());

		log.clear();
		JsonObject newer = JsonParser.parseString("{\"version\": 9}").getAsJsonObject();
		assertFalse(chain.run(newer));
		assertTrue(log.isEmpty());
		assertEquals(9, newer.get("version").getAsInt());
	}

	@Test
	void aGapInTheChainIsRejected() {
		List<String> log = new ArrayList<>();
		assertThrows(IllegalArgumentException.class,
				() -> ProgressMigrations.of(List.of(step(1, 2, log), step(3, 4, log))));
		assertThrows(IllegalArgumentException.class, () -> ProgressMigrations.of(List.of(step(2, 2, log))));
	}

	@Test
	void aMissingStepFailsLoudly() {
		ProgressMigrations chain = ProgressMigrations.of(List.of(step(2, 3, new ArrayList<>())));
		JsonObject tree = JsonParser.parseString("{\"version\": 1}").getAsJsonObject();
		assertThrows(IllegalStateException.class, () -> chain.run(tree));
	}
}
