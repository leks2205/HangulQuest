package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.Questline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes {@code build/reports/content/steps.json}: every step with its task signature, shape,
 * verb, object, relation, conditions, mechanics, grammar points, the words it targets and the Korean
 * lines the engine shows. tools/variety.py and tools/coverage.py measure from that file.
 */
class StepsDumpTest {
	@TempDir
	Path scratch;

	@Test
	void writesEveryStepWithItsSignature() throws IOException {
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		ContentFacts.write(trails, ContentFacts.DUMP);

		List<Questline> loaded = QuestLoader.loadAll();
		assertEquals(loaded.size(), trails.size());
		int steps = loaded.stream().mapToInt(trail -> trail.allSteps().size()).sum();
		assertEquals(steps, trails.stream().mapToInt(trail -> trail.steps().size()).sum());
		for (ContentFacts.Trail trail : trails) {
			for (ContentFacts.Step step : trail.steps()) {
				String where = trail.id() + "." + step.id();
				assertFalse(step.signature().isBlank(), where + " has no signature");
				assertTrue(step.signature().startsWith(step.goalType() + ":"), where + ": " + step.signature());
				assertFalse(step.shape().isBlank(), where + " has no shape");
				assertFalse(step.grammar().isEmpty(), where + " carries no grammar point");
				assertFalse(step.clueKo().isBlank(), where + " has no clue text");
			}
		}
	}

	@Test
	void theFileReadsBackAsTheSameFacts() throws IOException {
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		ContentFacts.write(trails, ContentFacts.DUMP);
		assertEquals(trails, ContentFacts.read(ContentFacts.DUMP));
	}

	@Test
	void theShapeDropsTheObjectAndKeepsTheTask() {
		var torch = new io.github.leks2205.hangulquest.quest.Goal.PlaceRel("minecraft:torch",
				io.github.leks2205.hangulquest.quest.spatial.Relation.NEXT_TO, "minecraft:oak_door", 1);
		var lantern = new io.github.leks2205.hangulquest.quest.Goal.PlaceRel("minecraft:lantern",
				io.github.leks2205.hangulquest.quest.spatial.Relation.NEXT_TO, "minecraft:chest", 1);
		var under = new io.github.leks2205.hangulquest.quest.Goal.PlaceRel("minecraft:torch",
				io.github.leks2205.hangulquest.quest.spatial.Relation.UNDER, "minecraft:oak_door", 1);
		assertEquals(ContentFacts.shape(torch), ContentFacts.shape(lantern), "only the words differ");
		assertFalse(ContentFacts.shape(torch).equals(ContentFacts.shape(under)), "another relation is another task");
	}
}
