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
	@ValueSource(strings = {"schema-v1", "v1.0.0", "v2.1.0", "unknown-fields", "v3-sample"})
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
		assertEquals(3, tree.get("version").getAsInt());
		assertEquals("[2,3]", tree.getAsJsonObject("meta").get("schemaHistory").toString());
		assertEquals(32, tree.getAsJsonObject("meta").get("installSalt").getAsString().length());
		assertEquals(0, tree.getAsJsonObject("totals").get("sidequestsCompleted").getAsInt());
		assertEquals(0, tree.getAsJsonObject("totals").get("questStepsCompleted").getAsInt());
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
