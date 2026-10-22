package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A quest may only ask for things a survival player can really get: no creative-only items, no
 * blocks that cannot be broken or picked up, no ids that do not exist in the game.
 */
class ObtainabilityTest {
	/** Exact ids that exist only for creative mode, commands or world generation. */
	static final Set<String> UNOBTAINABLE = Set.of(
			"air", "cave_air", "void_air", "bedrock", "barrier", "light", "structure_void", "structure_block",
			"jigsaw", "command_block", "chain_command_block", "repeating_command_block",
			"command_block_minecart", "debug_stick", "knowledge_book", "test_block", "test_instance_block",
			"reinforced_deepslate", "budding_amethyst", "end_portal", "end_portal_frame", "nether_portal",
			"spawner", "trial_spawner", "vault", "petrified_oak_slab", "player_head", "frogspawn",
			"farmland", "moving_piston");

	/** Patterns for whole families (infested blocks, spawn eggs). */
	static final List<Pattern> UNOBTAINABLE_PATTERNS = List.of(
			Pattern.compile("infested_.*"), Pattern.compile(".*_spawn_egg"));

	/** Ids that exist in the game but have no entry in names.json. */
	private static final Set<String> NO_VANILLA_NAME_IN_TABLE = Set.of("tnt");

	static boolean unobtainable(String path) {
		if (UNOBTAINABLE.contains(path)) {
			return true;
		}
		return UNOBTAINABLE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
	}

	private static String path(String id) {
		return id.substring(id.indexOf(':') + 1);
	}

	@Test
	void noGoalAsksForAnUnobtainableItemOrBlock() {
		List<String> bad = new ArrayList<>();
		for (Questline trail : QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				String target = KoreanFixtures.targetOf(step.goal());
				if (target == null || target.startsWith("#")) {
					continue;
				}
				if (isEntityOrPlace(step.goal())) {
					continue;
				}
				if (unobtainable(path(target))) {
					bad.add(trail.id() + "/" + step.id() + " (level " + trail.koreanLevel() + "): "
							+ KoreanFixtures.describe(step.goal()));
				}
			}
		}
		assertTrue(bad.isEmpty(), "goals that point at creative-only or unobtainable things:\n"
				+ String.join("\n", bad));
	}

	/** Whether the goal points at an entity, a biome or a dimension: an AVOID is what its action is. */
	static boolean isEntityOrPlace(Goal goal) {
		return switch (goal) {
			case Goal.Avoid avoid -> avoid.action() != null && isEntityOrPlace(avoid.action());
			case Goal.ReachBiome g -> true;
			case Goal.ReachDimension g -> true;
			case Goal.LookAtEntity g -> true;
			case Goal.DefeatEntity g -> true;
			case Goal.InteractEntity g -> true;
			case Goal.Shoot g -> g.entity() != null;
			default -> false;
		};
	}

	/** Whether the path is a name the game knows for what this goal points at: a creature for an entity goal, else an item or block. */
	static boolean known(Goal goal, String p, java.util.Map<String, ?> names) {
		return switch (goal) {
			case Goal.Avoid avoid -> avoid.action() == null || known(avoid.action(), p, names);
			case Goal.ReachBiome g -> names.containsKey("biome.minecraft." + p);
			case Goal.LookAtEntity g -> names.containsKey("entity.minecraft." + p);
			case Goal.DefeatEntity g -> names.containsKey("entity.minecraft." + p);
			case Goal.InteractEntity g -> names.containsKey("entity.minecraft." + p);
			case Goal.Breed g -> names.containsKey("entity.minecraft." + p);
			case Goal.Mount g -> names.containsKey("entity.minecraft." + p);
			case Goal.Shoot g -> names.containsKey((g.entity() != null ? "entity.minecraft." : "block.minecraft.") + p);
			default -> names.containsKey("block.minecraft." + p) || names.containsKey("item.minecraft." + p);
		};
	}

	@Test
	void anAvoidedEntityActionIsLookedUpAsAnEntity() {
		var names = KoreanFixtures.koNames();
		for (Goal action : List.of(new Goal.DefeatEntity("minecraft:villager", 1),
				new Goal.LookAtEntity("minecraft:villager", 1000L), new Goal.InteractEntity("minecraft:villager"))) {
			Goal avoid = new Goal.Avoid(action, 60, null);
			assertTrue(known(avoid, "villager", names), avoid.toString());
			assertFalse(known(avoid, "no_such_mob", names), avoid.toString());
			assertTrue(isEntityOrPlace(avoid), avoid.toString());
		}
		assertFalse(isEntityOrPlace(new Goal.Avoid(new Goal.BreakBlock("minecraft:hay_block", 1, null), 60, null)));
	}

	@Test
	void everyGoalTargetIsARealVanillaId() {
		List<String> bad = new ArrayList<>();
		var names = KoreanFixtures.koNames();
		for (Questline trail : QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				String target = KoreanFixtures.targetOf(step.goal());
				if (target == null || target.startsWith("#") || step.goal() instanceof Goal.ReachDimension) {
					continue;
				}
				String p = path(target);
				if (NO_VANILLA_NAME_IN_TABLE.contains(p)) {
					continue;
				}
				if (!known(step.goal(), p, names)) {
					bad.add(trail.id() + "/" + step.id() + ": " + target);
				}
			}
		}
		assertTrue(bad.isEmpty(), "goal ids without a vanilla name (typo or removed id):\n" + String.join("\n", bad));
	}

	@Test
	void theFilterCatchesWhatItShould() {
		// The test above can only fail if the filter works; pin the filter itself.
		for (String id : List.of("test_block", "test_instance_block", "infested_stone", "infested_deepslate",
				"structure_block", "jigsaw", "barrier", "command_block", "chain_command_block", "light",
				"debug_stick", "cow_spawn_egg", "reinforced_deepslate", "budding_amethyst", "bedrock")) {
			assertTrue(unobtainable(id), id + " must be flagged");
		}
		for (String id : List.of("stone", "oak_log", "torch", "amethyst_block", "deepslate", "lightning_rod",
				"light_blue_wool", "daylight_detector", "ancient_debris", "sculk_sensor", "dragon_egg")) {
			assertFalse(unobtainable(id), id + " must not be flagged");
		}
		assertEquals(false, unobtainable("infest"));
	}
}
