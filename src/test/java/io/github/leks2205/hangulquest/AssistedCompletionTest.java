package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creative and spectator steps complete, but the engine reports them as assisted. */
class AssistedCompletionTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("assisted-test");

	@TempDir
	Path tempDir;

	/** Reads the flag from inside the step-completed callback, as the HUD or learning code would. */
	private static final class Watcher implements QuestEngine.Feedback {
		final QuestEngine[] engine = new QuestEngine[1];
		final java.util.List<Boolean> assistedAtStep = new java.util.ArrayList<>();

		@Override
		public void banner(String ko, String de, boolean success) {
		}


		@Override
		public void sound(boolean success) {
		}

		@Override
		public void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
			assistedAtStep.add(engine[0].lastCompletionAssisted());
		}
	}

	private QuestEngine engine(String name, Watcher watcher) {
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:stone");
		concept.descKo = "땅에서 나와요.";
		Catalog catalog = Catalog.ofConcepts(List.of(concept));
		ProgressStore store = new ProgressStore(tempDir.resolve(name), LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestStep first = new QuestStep("s1", List.of("stone"),
				new Goal.BreakBlock("minecraft:stone", 1, null), List.of(), null, null, null, null, null);
		QuestStep second = new QuestStep("s2", List.of("stone"),
				new Goal.BreakBlock("minecraft:stone", 1, null), List.of(), null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("stone"),
				new Goal.PlaceBlock("minecraft:stone", 1), List.of(), null, null, null, null, null);
		Questline trail = new Questline("t", "길", "Pfad", 1, List.of("stone"),
				Questline.Requires.none(), List.of(first, second), finale, "끝", "Ende", "t", 10, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn, LOG);
		watcher.engine[0] = engine;
		return engine;
	}

	private static Observation breaking(long now, boolean creative) {
		Observation obs = new Observation();
		obs.now = now;
		obs.clockTime = 6000L;
		obs.creative = creative;
		obs.brokenBlocks.add("minecraft:stone");
		return obs;
	}

	@Test
	void creativeCompletionIsAssistedAndTheNextSurvivalStepIsNot() {
		Watcher watcher = new Watcher();
		QuestEngine engine = engine("flag", watcher);
		Observation start = new Observation();
		start.now = 1_000L;
		engine.startTrail("t", start, watcher);

		engine.tick(breaking(1_100L, true), watcher);
		assertEquals("s2", engine.currentStep().id(), "creative breaking still progresses");
		assertTrue(engine.lastCompletionAssisted());

		engine.tick(breaking(1_200L, false), watcher);
		assertEquals("finale", engine.currentStep().id());
		assertFalse(engine.lastCompletionAssisted());
		assertEquals(List.of(true, false), watcher.assistedAtStep);
	}
}
