package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generator wave 5, item 10: a wait beside a block says how many seconds are left, in the status line and in the hint. */
class GeneratorWaveFiveWaitTest {
	@TempDir
	Path temp;

	private static final class Quiet implements QuestEngine.Feedback {
		@Override
		public void banner(String ko, String de, boolean success) {
		}

		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}
	}

	private static Observation beside(long now, boolean near) {
		Observation obs = new Observation();
		obs.now = now;
		obs.position = new Vec3(10.5, 64, 10.5);
		obs.nearestBlock = near ? Map.of("minecraft:wheat", 2.0) : Map.of();
		return obs;
	}

	private QuestEngine engine() {
		Catalog catalog = KoreanFixtures.catalog();
		ProgressStore store = new ProgressStore(temp, LoggerFactory.getLogger("wave5-wait"));
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.markKnown(catalog.byConceptId("wheat").orElseThrow().id, true, 1_000L); // no sidequest freezes the wait
		QuestStep wait = new QuestStep("s1", List.of("wheat"), new Goal.Wait(10, null),
				List.of(new Condition.NearBlock("minecraft:wheat", 4)), null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("wait-test", "길", "Pfad", 1, List.of("wheat", "torch"),
				Questline.Requires.none(), List.of(wait), finale, "끝났어요.", "Fertig.", "badge", 10, 1, false);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn, LoggerFactory.getLogger("wave5-wait"));
		engine.startTrail("wait-test", beside(1_000L, true), new Quiet());
		return engine;
	}

	@Test
	void theStatusLineCountsTheSecondsLeft() {
		QuestEngine engine = engine();
		Quiet feedback = new Quiet();
		long now = 100_000L;
		engine.tick(beside(now, true), feedback);
		assertEquals("10초 남았어요", engine.statusText());
		for (int i = 1; i <= 40; i++) { // two seconds beside the wheat, nothing in the crosshair
			engine.tick(beside(now + i * 50L, true), feedback);
		}
		assertEquals("8초 남았어요", engine.statusText());
		// away from the wheat the status asks for the wheat, as before
		engine.tick(beside(now + 2_100L, false), feedback);
		assertTrue(engine.statusText().contains("필요"), engine.statusText());
	}

	@Test
	void theHintSaysHowLongTheWaitStillTakes() {
		QuestEngine engine = engine();
		Quiet feedback = new Quiet();
		long now = 100_000L;
		engine.tick(beside(now, true), feedback);
		var first = engine.hint(beside(now, true), feedback);
		assertTrue(first.simplifiedKo().endsWith("10초 남았어요."), first.simplifiedKo());
		for (int i = 1; i <= 60; i++) { // three seconds
			engine.tick(beside(now + i * 50L, true), feedback);
		}
		var hint = engine.hint(beside(now + 3_000L, true), feedback);
		assertTrue(hint.simplifiedKo().endsWith("7초 남았어요."), hint.simplifiedKo());
	}
}
