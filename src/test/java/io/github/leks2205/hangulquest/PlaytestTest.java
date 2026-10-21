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
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.sense.ContainerPutFact;
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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

	@Test
	void theVillageErrandsDemoUsesTheNewActionTypes() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		Questline trail = QuestLoader.loadAll().stream()
				.filter(t -> t.id().equals("village_errands_demo")).findFirst().orElseThrow();
		assertEquals(3, trail.koreanLevel());
		List<String> kinds = trail.allSteps().stream().map(step -> step.goal().getClass().getSimpleName()).toList();
		assertEquals(List.of("Craft", "Give", "PutIn", "Sequence", "HoldWhile"), kinds);
		System.out.println("PLAYTEST village_errands_demo -> " + play(trail, catalog));
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
			// A sequence is played part by part, a few ticks of each part's facts; timed goals need longer.
			List<Goal> phases = step.goal() instanceof Goal.Sequence sequence
					? sequence.parts() : List.of(step.goal());
			for (int tick = 0; tick < ticksFor(step.goal()) && !advanced; tick++) {
				now += 60L;
				if (phases.size() > 1) {
					obs = observation(phases.get(Math.min(phases.size() - 1, tick / 3)), step.conditions(), now);
				}
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
		learn.data().words.forEach((id, progress) -> {
			assertEquals(0, progress.level, trail.id() + " promoted " + id + " without a quiz");
			assertEquals(0, progress.correct, trail.id() + " counted a correct answer for " + id);
		});
		assertEquals(0, learn.totalReviews(), trail.id() + " recorded a review nobody answered");
		return "completed in " + steps + " steps";
	}

	private static boolean completed(QuestEngine engine) {
		return engine.activeTrail() == null && engine.currentStep() == null;
	}

	/** Ticks to drive a step. A goal that has to last needs its seconds: a tick is 50 ms of game time. */
	private static int ticksFor(Goal goal) {
		return switch (goal) {
			case Goal.HoldWhile g when g.innerCondition() != null -> g.seconds() * 20 + 20;
			case Goal.Avoid g -> g.windowSeconds() * 20 + 20;
			default -> 40;
		};
	}

	private static Observation observation(QuestStep step, long now) {
		return observation(step.goal(), step.conditions(), now);
	}

	private static Observation observation(Goal goal, List<Condition> conditions, long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.dimension = "minecraft:overworld";
		obs.y = 64;
		obs.clockTime = 1000L;
		for (Condition condition : conditions) {
			applyCondition(obs, condition);
		}
		applyGoal(obs, goal);
		return obs;
	}

	private static void applyCondition(Observation obs, Condition condition) {
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
			case Condition.Holding h -> obs.mainHand = h.item();
			case Condition.Sky s -> {
				obs.canSeeSky = s.state() == Condition.SkyState.OPEN;
				obs.roofed = s.state() == Condition.SkyState.ROOFED;
			}
			case Condition.InWater w -> obs.inWater = true;
			case Condition.PlayerRel r -> standNextTo(obs, r.relation(), r.anchor());
		}
	}

	/** Puts the player where PLAYER_REL is true: next to a looked-at block, or an entity that faces south. */
	private static void standNextTo(Observation obs, Relation relation, String anchor) {
		obs.lookedBlock = anchor;
		obs.lookedBlockPos = new BlockPos(10, 64, 10);
		obs.position = switch (relation) {
			case ON_TOP_OF -> new Vec3(10.5, 65.0, 10.5);
			case UNDER -> new Vec3(10.5, 62.0, 10.5);
			case NEXT_TO -> new Vec3(12.0, 64.0, 10.5);
			case INSIDE -> new Vec3(10.5, 64.0, 10.5);
			case OUTSIDE -> new Vec3(14.5, 64.0, 10.5);
			case IN_FRONT_OF -> new Vec3(10.0, 64.0, 12.0);
			case BEHIND -> new Vec3(10.0, 64.0, 8.0);
			default -> new Vec3(13.0, 64.0, 10.5);
		};
		if (relation == Relation.IN_FRONT_OF || relation == Relation.BEHIND) {
			obs.addEntityPosition(new EntityFact(anchor, UUID.randomUUID(), 10.0, 64.0, 10.0, 0.0F, 0.0F));
		}
	}

	private static void applyGoal(Observation obs, Goal goal) {
		switch (goal) {
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
			case Goal.PlaceRel g -> spatial(obs, g);
			case Goal.BuildPattern g -> spatial(obs, g);
			case Goal.PlaceOnPattern g -> spatial(obs, g);
			case Goal.Craft g -> obs.addCrafted(new CraftFact(g.item(), Math.max(1, g.count()),
					g.station() == null ? "crafting_table" : g.station()));
			case Goal.Smelt g -> obs.addCrafted(new CraftFact(g.output(), Math.max(1, g.count()), "furnace"));
			case Goal.Give g -> obs.addGave(new GiveFact(g.item(), Math.max(1, g.count()), g.entity(),
					UUID.randomUUID(), g.method() == null ? GiveFact.Method.USE_WITH
					: GiveFact.Method.valueOf(g.method())));
			case Goal.PutIn g -> obs.addPutIn(new ContainerPutFact(g.item(), Math.max(1, g.count()),
					g.container(), BlockPos.ZERO));
			case Goal.HoldWhile g -> {
				obs.mainHand = g.item();
				obs.idle = false;
				if (g.innerGoal() != null) {
					applyGoal(obs, g.innerGoal());
				}
				if (g.innerCondition() != null) {
					applyCondition(obs, g.innerCondition());
				}
			}
			// The play loop feeds a sequence part by part; this is only the first part.
			case Goal.Sequence g -> applyGoal(obs, g.parts().get(0));
			// Nothing to do for an avoid: the window just has to run out.
			case Goal.Avoid g -> {
				if (g.during() != null) {
					applyCondition(obs, g.during());
				}
			}
		}
	}

	/** One placement, with the blocks around it that make the spatial goal true. */
	private static void spatial(Observation obs, Goal goal) {
		obs.addPlacement(SpatialWorlds.placementFor(goal, obs.phase() == Condition.Phase.NIGHT));
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
