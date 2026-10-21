package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays five random new quests through the real engine with synthetic observations. */
class PlaytestTest {
	private static final List<String> NEW_TRAILS = List.of(
			"feuerpfad", "tierfreund", "brotpfad", "haustierpfad", "hoehlenlicht",
			"kraeuterpfad", "wollpfad", "flusslicht", "waldlicht", "feuerstelle",
			"schmelzofen", "saatfeld", "wanderpfad", "nachthafen", "bogenpfad",
			"kronenlicht", "kronenwacht", "kronenpfad", "kronenglanz", "kronenfeier",
			"letzte_reise", "weltenschoepfer", "wurzelpfad", "steinherz", "gartenwacht");

	@TempDir
	Path tempDir;

	@Test
	void fiveRandomNewQuestsCanBePlayedToTheEnd() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		List<String> picks = new ArrayList<>(NEW_TRAILS);
		Collections.shuffle(picks, new Random(20261018L));
		List<String> chosen = picks.subList(0, 5);
		System.out.println("PLAYTEST " + chosen);
		for (String id : chosen) {
			Questline trail = QuestLoader.loadAll().stream()
					.filter(t -> t.id().equals(id)).findFirst().orElseThrow();
			System.out.println("PLAYTEST " + id + " -> " + play(trail, catalog));
		}
	}

	@Test
	void allShippedTrailsCanBeDrivenToTheEnd() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		List<String> failures = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			try {
				play(trail, catalog);
			} catch (AssertionError failed) {
				failures.add(failed.getMessage());
			}
		}
		assertTrue(failures.isEmpty(), "unplayable trails:\n" + String.join("\n", failures));
	}

	private String play(Questline trail, Catalog catalog) {
		Path dir = tempDir.resolve(trail.id() + "-" + Math.abs(trail.id().hashCode()));
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("playtest"));
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(List.of(trail), catalog, learn,
				LoggerFactory.getLogger("playtest"));
		Observation start = new Observation();
		start.now = 10_000L;
		engine.startTrail(trail.id(), start, new Feedback(engine));
		long now = 10_100L;
		int steps = 0;
		while (!completed(engine) && steps < 20) {
			QuestStep step = engine.currentStep();
			assertTrue(step != null, trail.id() + " lost its active step");
			Observation obs = observation(step, now);
			if (step.goal() instanceof Goal.AcquireItem) {
				// Items already held do not count: the first tick of the step takes the baseline.
				Observation baseline = new Observation();
				baseline.now = now;
				engine.tick(baseline, new Feedback(engine));
			}
			boolean advanced = false;
			for (int tick = 0; tick < 40 && !advanced; tick++) {
				now += 60L;
				obs.now = now;
				engine.tick(obs, new Feedback(engine));
				advanced = completed(engine)
						|| (engine.currentStep() != null
						&& !engine.currentStep().id().equals(step.id()));
			}
			assertTrue(advanced, trail.id() + "/" + step.id() + " did not advance; status="
					+ engine.statusText());
			steps++;
		}
		assertTrue(completed(engine), trail.id() + " was not completed");
		return "completed in " + steps + " steps";
	}

	private static boolean completed(QuestEngine engine) {
		return engine.activeTrail() == null && engine.currentStep() == null;
	}

	private static Observation observation(QuestStep step, long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.dimension = "minecraft:overworld";
		obs.y = 64;
		obs.clockTime = 1000L;
		for (Condition condition : step.conditions()) {
			switch (condition) {
				case Condition.Time t -> obs.clockTime = switch (t.phase()) {
					case DAY -> 1000L;
					case NIGHT -> 14000L;
					case DAWN -> 23000L;
					case DUSK -> 12000L;
				};
				case Condition.WeatherState w -> {
					obs.rain = w.weather() != Condition.Weather.CLEAR;
					obs.thunder = w.weather() == Condition.Weather.THUNDER;
				}
				case Condition.YRange y -> obs.y = y.minY();
				case Condition.InBiome b -> {
					obs.biome = b.biome().startsWith("#") ? "minecraft:stony_peaks" : b.biome();
					obs.biomeTags.add(b.biome().startsWith("#")
							? b.biome().substring(1) : b.biome());
				}
				case Condition.InDimension d -> obs.dimension = d.dimension();
				case Condition.NearEntity n -> obs.nearbyEntities.put(n.entity(), 1);
				case Condition.NearBlock n -> obs.lookedBlock = n.block();
			}
		}
		switch (step.goal()) {
			case Goal.BreakBlock g -> {
				for (int i = 0; i < Math.max(1, g.count()); i++) obs.brokenBlocks.add(g.block());
			}
			case Goal.PlaceBlock g -> {
				for (int i = 0; i < Math.max(1, g.count()); i++) obs.placedBlocks.add(g.block());
			}
			case Goal.AcquireItem g -> obs.inventory.put(g.item(), Math.max(1, g.count()));
			case Goal.ConsumeItem g -> {
				for (int i = 0; i < Math.max(1, g.count()); i++) obs.consumedItems.add(g.item());
			}
			case Goal.LookAtBlock g -> obs.lookedBlock = g.block();
			case Goal.LookAtEntity g -> obs.lookedEntity = g.entity();
			case Goal.ReachBiome g -> {
				obs.biome = g.biome().startsWith("#") ? "minecraft:stony_peaks" : g.biome();
				if (g.biome().startsWith("#")) obs.biomeTags.add(g.biome().substring(1));
			}
			case Goal.ReachDimension g -> obs.dimension = g.dimension();
			case Goal.ReachHeight g -> obs.y = g.minY();
			case Goal.UseBlock g -> {
				obs.usedBlocks.add(g.block());
				if (g.block().endsWith("_bed")) {
					obs.sleeping = true;
					obs.sleepingBed = g.block();
				}
			}
			case Goal.DefeatEntity g -> obs.defeatedEntities.add(g.entity());
			case Goal.InteractEntity g -> obs.interactedEntities.add(g.entity());
		}
		return obs;
	}

	private static final class Feedback implements QuestEngine.Feedback {
		private final QuestEngine engine;

		private Feedback(QuestEngine engine) {
			this.engine = engine;
		}

		@Override
		public void wordCard(io.github.leks2205.hangulquest.data.Word word) {
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void banner(String korean, String german, boolean side) {
		}

		@Override
		public void chooseNext(List<Questline> options) {
		}

	}
}
