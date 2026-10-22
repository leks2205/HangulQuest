package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.goal.Matching;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.sense.ShotFact;
import io.github.leks2205.hangulquest.quest.spatial.SpatialWorlds;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.sense.ContainerPutFact;
import io.github.leks2205.hangulquest.sense.ContainerTakeFact;
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

	/** A one-step trail around the goal, for the goals no shipped trail uses (yet). */
	private static Questline probe(String id, String goalJson) {
		String json = """
				{"schema": 1, "id": "%s", "titleKo": "시험", "topic": "시험", "tier": 2, "order": 1, "koreanLevel": 3,
				 "concepts": ["stone"], "requires": {"minWordsKnown": 0, "dimension": "minecraft:overworld"},
				 "steps": [{"id": "s1", "teach": ["stone"], "goal": %s}],
				 "finale": {"step": {"id": "finale", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}, "textKo": "끝"}}
				""".formatted(id, goalJson);
		return QuestLoader.parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void aPlacementNextToACreatureCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (Relation relation : List.of(Relation.IN_FRONT_OF, Relation.BEHIND, Relation.NEXT_TO, Relation.NEAR)) {
			for (String creature : List.of("minecraft:villager", "minecraft:cat", "minecraft:iron_golem", "minecraft:rabbit",
					"minecraft:frog", "minecraft:goat", "minecraft:turtle", "minecraft:bee", "minecraft:mule",
					"minecraft:parrot", "minecraft:axolotl", "minecraft:llama")) {
				Questline trail = probe("creature_" + relation.name().toLowerCase(), """
						{"type": "PLACE_REL", "block": "minecraft:torch", "relation": "%s", "anchor": "%s", "count": 1}
						""".formatted(relation.name(), creature));
				assertEquals("completed in 2 steps", play(trail, catalog), relation + " " + creature);
			}
		}
	}

	@Test
	void aSequenceWithAnAcquireATransitAndAMountPartCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		String acquire = "{\"type\": \"ACQUIRE_ITEM\", \"item\": \"minecraft:bread\", \"count\": 1}";
		String inside = "{\"type\": \"TRANSIT\", \"direction\": \"IN\"}";
		String mount = "{\"type\": \"MOUNT\", \"entity\": \"minecraft:oak_boat\", \"dwellMs\": 1500}";
		String place = "{\"type\": \"PLACE_BLOCK\", \"block\": \"minecraft:torch\", \"count\": 1}";
		for (String ordered : List.of("true", "false")) {
			for (List<String> parts : List.of(List.of(acquire, inside), List.of(place, mount),
					List.of(inside, acquire), List.of(mount, acquire))) {
				Questline trail = probe("sequence_" + ordered, """
						{"type": "SEQUENCE", "ordered": %s, "parts": [%s]}
						""".formatted(ordered, String.join(", ", parts)));
				assertEquals("completed in 2 steps", play(trail, catalog), ordered + " " + parts);
			}
		}
	}

	@Test
	void aHeldItemWhileAWaitOrAnAcquireCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String inner : List.of("{\"type\": \"WAIT\", \"seconds\": 8}",
				"{\"type\": \"ACQUIRE_ITEM\", \"item\": \"minecraft:bread\", \"count\": 1}",
				"{\"type\": \"WAIT\", \"seconds\": 5, \"until\": \"NIGHT\"}")) {
			Questline trail = probe("hold_inner", """
					{"type": "HOLD_WHILE", "item": "minecraft:spyglass", "inner": %s}
					""".formatted(inner));
			try {
				assertEquals("completed in 2 steps", play(trail, catalog), inner);
			} catch (AssertionError failed) {
				throw new AssertionError(inner + ": " + failed.getMessage(), failed);
			}
		}
	}

	@Test
	void aJumpFromAHeightCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String fall : List.of("{\"type\": \"FALL\", \"blocks\": 10}",
				"{\"type\": \"FALL\", \"blocks\": 6, \"landing\": \"minecraft:powder_snow\"}",
				"{\"type\": \"FALL\", \"blocks\": 6, \"landing\": \"minecraft:water\"}")) {
			assertEquals("completed in 2 steps", play(probe("fall", fall), catalog), fall);
		}
	}

	@Test
	void aShotAThrowAWalkAndAFacingCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String goal : List.of(
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:bow\", \"block\": \"minecraft:target\"}",
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:crossbow\", \"entity\": \"minecraft:zombie\", \"count\": 3}",
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:crossbow\", \"entity\": \"minecraft:zombie\", \"pierce\": 2}",
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:bow\", \"entity\": \"minecraft:zombie\", \"minDistance\": 20}",
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:trident\"}",
				"{\"type\": \"SHOOT\", \"weapon\": \"minecraft:snowball\", \"count\": 4}",
				"{\"type\": \"WALK\", \"direction\": \"NORTH\", \"blocks\": 20}",
				"{\"type\": \"WALK\", \"direction\": \"SOUTH\", \"blocks\": 20}",
				"{\"type\": \"WALK\", \"direction\": \"EAST\", \"blocks\": 20}",
				"{\"type\": \"WALK\", \"direction\": \"WEST\", \"blocks\": 20}")) {
			assertEquals("completed in 2 steps", play(probe("shoot", goal), catalog), goal);
		}
		Questline facing = QuestLoader.parse(com.google.gson.JsonParser.parseString("""
				{"schema": 1, "id": "facing", "titleKo": "시험", "topic": "시험", "tier": 2, "order": 1, "koreanLevel": 3,
				 "concepts": ["stone"], "requires": {"minWordsKnown": 0, "dimension": "minecraft:overworld"},
				 "steps": [{"id": "s1", "teach": ["stone"], "goal": {"type": "PLACE_BLOCK", "block": "minecraft:torch", "count": 1},
				            "conditions": [{"type": "FACING", "direction": "EAST"}]}],
				 "finale": {"step": {"id": "finale", "teach": ["stone"],
				   "goal": {"type": "BREAK_BLOCK", "block": "minecraft:stone", "count": 1}}, "textKo": "끝"}}
				""").getAsJsonObject());
		assertEquals("completed in 2 steps", play(facing, catalog));
	}

	@Test
	void aLongWalkCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String goal : List.of("{\"type\": \"WALK\", \"direction\": \"NORTH\", \"blocks\": 60}",
				"{\"type\": \"WALK\", \"direction\": \"EAST\", \"blocks\": 150}")) {
			assertEquals("completed in 2 steps", play(probe("longwalk", goal), catalog), goal);
		}
	}

	@Test
	void aLongPillarOfLogsAndALongRowCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String goal : List.of(
				"{\"type\": \"BUILD_PATTERN\", \"pattern\": \"PILLAR\", \"material\": \"#minecraft:logs\", \"size\": 7}",
				"{\"type\": \"BUILD_PATTERN\", \"pattern\": \"PILLAR\", \"material\": \"minecraft:oak_log\", \"size\": 5}",
				"{\"type\": \"BUILD_PATTERN\", \"pattern\": \"ROW\", \"material\": \"minecraft:stone\", \"size\": 7}",
				"{\"type\": \"PLACE_ON_PATTERN\", \"block\": \"minecraft:torch\", \"relation\": \"ON_TOP_OF\", \"pattern\": \"PILLAR\", \"material\": \"#minecraft:logs\", \"size\": 6}")) {
			assertEquals("completed in 2 steps", play(probe("longpillar", goal), catalog), goal);
		}
	}

	@Test
	void anAvoidOnACreatureAndAnEquipOnASlotTagCanBePlayed() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		for (String goal : List.of(
				"{\"type\": \"AVOID\", \"action\": {\"type\": \"DEFEAT_ENTITY\", \"entity\": \"minecraft:villager\"}, \"windowSeconds\": 5}",
				"{\"type\": \"EQUIP\", \"item\": \"#minecraft:head_armor\"}",
				"{\"type\": \"EQUIP\", \"item\": \"#minecraft:chest_armor\"}",
				"{\"type\": \"EQUIP\", \"item\": \"#minecraft:foot_armor\", \"change\": \"OFF\"}")) {
			try {
				assertEquals("completed in 2 steps", play(probe("avoid_equip", goal), catalog), goal);
			} catch (AssertionError failed) {
				throw new AssertionError(goal + ": " + failed.getMessage(), failed);
			}
		}
	}

	@Test
	void aPatternIsBuiltAtTheSizeTheGoalAsksFor() {
		int built = 0;
		int other = 0;
		for (io.github.leks2205.hangulquest.quest.spatial.Pattern pattern : io.github.leks2205.hangulquest.quest.spatial.Pattern.values()) {
			for (int size = pattern.minSize(); size <= pattern.maxSize(); size++) {
				for (Relation relation : List.of(Relation.ON_TOP_OF, Relation.NEXT_TO, Relation.UNDER)) {
					Goal.PlaceOnPattern goal = new Goal.PlaceOnPattern("minecraft:torch", relation, pattern,
							"minecraft:stone", size);
					io.github.leks2205.hangulquest.sense.PlacementFact fact;
					try {
						fact = SpatialWorlds.placementFor(goal, false); // refuses a fixture that does not hold
					} catch (AssertionError tooBig) {
						continue; // the structure does not fit the 7x7x7 view of a placement: nothing to build
					}
					// the structure stands at the asked size, not at the usual one
					assertEquals(pattern.cells(size, 0).size(), fact.around().count("minecraft:stone"), goal.toString());
					built++;
					if (size != pattern.defaultSize()) {
						other++;
					}
				}
			}
		}
		assertTrue(built > 10 && other > 5, "built " + built + ", of another size " + other);
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
			if (step.goal() instanceof Goal.AcquireItem
					|| step.goal() instanceof Goal.HoldWhile hold && hold.innerGoal() instanceof Goal.AcquireItem
					|| step.goal() instanceof Goal.Sequence sequence && sequence.parts().get(0) instanceof Goal.AcquireItem) {
				// Items already held do not count: the first tick of the step takes the baseline.
				Observation baseline = new Observation();
				baseline.now = now;
				if (step.goal() instanceof Goal.HoldWhile hold) {
					baseline.mainHand = hold.item(); // the inner goal only runs while the item is held
				}
				engine.tick(baseline, new Feedback(engine));
			}
			boolean advanced = false;
			// A sequence is played part by part, each part with the simulation of the same goal on its own: its
			// own facts, its own movement and the ticks its seconds need. The part that is not up yet sees the
			// facts of the one before, which is also what takes the baseline of an ACQUIRE_ITEM part.
			List<Goal> phases = step.goal() instanceof Goal.Sequence sequence
					? sequence.parts() : List.of(step.goal());
			for (int phase = 0; phase < phases.size() && !advanced; phase++) {
				Goal part = phases.get(phase);
				if (phases.size() > 1) {
					obs = observation(part, step.conditions(), now);
				}
				for (int tick = 0; tick < ticksFor(part) && !advanced; tick++) {
					now += 60L;
					obs.now = now;
					moveWithGoal(obs, part, tick);
					engine.tick(obs, new Feedback(engine));
					advanced = completed(engine)
							|| (engine.currentStep() != null
							&& !engine.currentStep().id().equals(step.id()));
				}
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
			// the inner goal has its own rhythm (a wait lasts its seconds), driven while the item is in the hand
			case Goal.HoldWhile g when g.innerGoal() != null -> ticksFor(g.innerGoal());
			case Goal.Fall g -> 60;
			case Goal.Avoid g -> g.windowSeconds() * 20 + 20;
			case Goal.Wait g -> g.seconds() * 20 + 40;
			case Goal.Hear g -> (int) (g.dwellMs() / 50) + 40;
			case Goal.LieDown g -> (int) (g.dwellMs() / 50) + 40;
			case Goal.Mount g -> (int) (g.dwellMs() / 50) + 40;
			case Goal.Swim g -> g.blocks() + 40;
			case Goal.Walk g -> g.blocks() + 40; // one block a tick
			case Goal.Transit g -> 60;
			case Goal.Breed g -> 40;
			default -> 40;
		};
	}

	/** The goals that need a state to change over time: the player walks, swims, goes in, the baby appears. */
	private static void moveWithGoal(Observation obs, Goal goal, int tick) {
		switch (goal) {
			case Goal.Swim g -> obs.position = new Vec3(tick, 64.0, 0.0);
			case Goal.Walk g -> {
				// one block a tick along the direction
				obs.position = switch (g.direction()) {
					case "NORTH" -> new Vec3(0.0, 64.0, -tick);
					case "SOUTH" -> new Vec3(0.0, 64.0, tick);
					case "EAST" -> new Vec3(tick, 64.0, 0.0);
					default -> new Vec3(-tick, 64.0, 0.0);
				};
			}
			case Goal.Shoot g -> {
				obs.shots = List.of();
				if (tick == 0) {
					int shots = Math.max(1, g.count());
					for (int shot = 0; shot < shots; shot++) {
						List<ShotFact.Hit> hits = new ArrayList<>();
						for (int victim = 0; victim < Math.max(1, g.pierce()); victim++) {
							if (g.entity() != null) {
								hits.add(new ShotFact.Hit(g.entity(), new UUID(9L, shot * 10L + victim).toString(),
										g.minDistance() + 5.0));
							}
						}
						obs.addShot(new ShotFact(g.weapon(), hits, g.block(), g.minDistance() + 5.0));
					}
				}
			}
			case Goal.HoldWhile g when g.innerGoal() != null -> moveWithGoal(obs, g.innerGoal(), tick);
			case Goal.Fall g -> {
				// ten ticks in the air with the distance growing, then the ground: the game resets the distance
				boolean air = tick < 10;
				obs.onGround = !air;
				obs.fallDistance = air ? (tick + 1) * (g.blocks() + 2.0F) / 10.0F : 0.0F;
				obs.landedOn = g.landing() == null ? "minecraft:grass_block" : g.landing();
				obs.inWater = !air && "minecraft:water".equals(g.landing());
			}
			case Goal.Transit g -> obs.canSeeSky = g.direction().equals("IN") == tick < 30; // first the other side
			case Goal.Equip g -> {
				// putting on: the slot is seen bare first, the change that follows is the player's;
				// taking off: the piece is worn first
				boolean off = "OFF".equals(g.change());
				wearing(obs, g, tick < 2 ? off : !off);
			}
			case Goal.Breed g -> {
				// fed at the start, a baby of the kind in the sight from the fifth tick on
				obs.interactedEntities.clear();
				obs.interactedUuids.clear();
				if (tick == 0) { // two parents are fed
					obs.interactedEntities.add(g.entity());
					obs.interactedEntities.add(g.entity());
					obs.interactedUuids.add(new UUID(6L, 1L));
					obs.interactedUuids.add(new UUID(6L, 2L));
				}
				if (tick >= 1) { // both stand there from now on
					obs.nearbyEntityPositions = new ArrayList<>(List.of(
							new EntityFact(g.entity(), new UUID(6L, 1L), 0.0, 64.0, 0.0, 0.0F, 0.0F, false),
							new EntityFact(g.entity(), new UUID(6L, 2L), 2.0, 64.0, 0.0, 0.0F, 0.0F, false)));
				}
				if (tick >= 5) {
					obs.nearbyEntityPositions.add(new EntityFact(g.entity(),
							new UUID(7L, 7L), 1.0, 64.0, 1.0, 0.0F, 0.0F, true));
				}
			}
			default -> {
			}
		}
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
		if (goal instanceof Goal.PlaceBlock place && obs.roofed) {
			// a block placed under a roof is checked on the block: the sensor reports a roofed placement
			obs.addPlacement(new io.github.leks2205.hangulquest.sense.PlacementFact(place.block(), new BlockPos(10, 64, 10),
					1L, obs.phase() == Condition.Phase.NIGHT, new Vec3(10.5, 64, 10.5),
					io.github.leks2205.hangulquest.sense.BlockNeighborhood.capture(10, 64, 10, (x, y, z) -> null), true));
		}
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
			case Condition.NearEntity n -> {
				obs.nearbyEntities.put(n.entity(), Math.max(1, n.count()));
				for (int i = 0; i < Math.max(1, n.count()); i++) { // each one stands a few blocks away
					obs.addEntityPosition(new EntityFact(n.entity(), UUID.randomUUID(), 2.0 + i, 64.0, 0.0, 0.0F, 0.0F));
				}
				obs.position = new Vec3(0.0, 64.0, 0.0);
			}
			case Condition.NearBlock n -> obs.lookedBlock = n.block();
			case Condition.Holding h -> obs.mainHand = h.item();
			case Condition.Sky s -> {
				obs.canSeeSky = s.state() == Condition.SkyState.OPEN;
				obs.roofed = s.state() == Condition.SkyState.ROOFED;
			}
			case Condition.InWater w -> obs.inWater = true;
			case Condition.PlayerRel r -> standNextTo(obs, r.relation(), r.anchor());
			case Condition.Facing f -> obs.yaw = io.github.leks2205.hangulquest.quest.goal.Directions.yaw(f.direction());
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
				if ("OPEN".equals(g.change())) {
					obs.addOpened(g.block());
				} else if ("CLOSE".equals(g.change())) {
					obs.addClosed(g.block());
				} else if ("LIT".equals(g.change()) || g.change() == null && Matching.isCandle(g.block())) {
					obs.addLit(g.block());
				} else if ("WRITE".equals(g.change()) || g.change() == null && Matching.blockFamily(g.block()).equals("sign")) {
					obs.addSignWritten(g.block());
				}
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
					UUID.randomUUID(), g.method() == null
					? "minecraft:villager".equals(g.entity()) ? GiveFact.Method.DROP_NEAR
					: "minecraft:wandering_trader".equals(g.entity()) ? GiveFact.Method.TRADE : GiveFact.Method.USE_WITH
					: GiveFact.Method.valueOf(g.method())));
			case Goal.PutIn g -> obs.addPutIn(new ContainerPutFact(g.item(), Math.max(1, g.count()),
					g.container(), BlockPos.ZERO));
			case Goal.TakeOut g -> obs.addTakenOut(new ContainerTakeFact(g.item(), Math.max(1, g.count()),
					g.container(), BlockPos.ZERO));
			case Goal.Hear g -> {
				obs.jukeboxDistance = 1.0;
				obs.bellDistance = 1.0;
				obs.usedBlocks.add(g.source());
			}
			case Goal.LieDown g -> {
				obs.sleeping = true;
				obs.sleepingBed = g.block();
			}
			case Goal.Wait g -> {
				if (g.until() != null) {
					obs.clockTime = g.until().equals("NIGHT") ? 14000L : 1000L;
				}
			}
			case Goal.Mount g -> obs.vehicle = g.entity();
			case Goal.Fall g -> {
			}
			case Goal.Swim g -> obs.inWater = true;
			case Goal.Shoot g -> {
				// the shots come in moveWithGoal, once
			}
			case Goal.Walk g -> {
			}
			case Goal.Trade g -> obs.addTraded(new io.github.leks2205.hangulquest.sense.TradeFact(g.entity(),
					g.direction().equals("BUY") ? g.item() : "minecraft:emerald", Math.max(1, g.count()),
					g.direction().equals("SELL") ? g.item() : "minecraft:emerald", Math.max(1, g.count())));
			case Goal.Transit g -> {
			}
			case Goal.Breed g -> {
			}
			// the armour is on in this observation; moveWithGoal shows the slot bare first
			case Goal.Equip g -> wearing(obs, g, !"OFF".equals(g.change()));
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

	/** The armour slot is worn (on) or bare; a slot tag (#minecraft:head_armor) is worn as the piece it stands for. */
	private static void wearing(Observation obs, Goal.Equip goal, boolean on) {
		String piece = BlockSpec.representative(goal.item());
		obs.worn = !on ? java.util.Map.of()
				: java.util.Map.of(piece.contains("helmet") ? "head" : piece.contains("chestplate") ? "chest"
				: piece.contains("leggings") ? "legs" : "feet", piece);
	}

	/** One placement, with the blocks around it that make the spatial goal true. */
	private static void spatial(Observation obs, Goal goal) {
		obs.addPlacement(SpatialWorlds.placementFor(goal, obs.phase() == Condition.Phase.NIGHT));
		SpatialWorlds.entitiesFor(goal).forEach(obs::addEntityPosition);
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
