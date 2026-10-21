package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Beginner trails (Korean level 1-2) must stay in the Overworld and within reach of a new
 * player: no Nether or End dimension, no Nether/End/Deep Dark biome, no block that only exists
 * there, no mob that lives there.
 *
 * Trails that break the rule and have not been fixed yet are listed in
 * {@code src/test/resources/content-baseline.json} ("reachabilityDebt"). That list can only
 * shrink: a trail that is not on it must be clean, and a trail on it that is clean again must be
 * removed from it.
 */
class ReachabilityGateTest {
	static final int MAX_GATED_LEVEL = 2;

	private static final Set<String> NETHER_BIOMES = Set.of("nether_wastes", "crimson_forest", "warped_forest",
			"soul_sand_valley", "basalt_deltas");
	private static final Set<String> END_BIOMES = Set.of("the_end", "small_end_islands", "end_highlands",
			"end_midlands", "end_barrens");
	private static final Set<String> DEEP_DARK_BIOMES = Set.of("deep_dark");

	/** Blocks and items that are only found (not crafted) in the Nether, the End or the Deep Dark. */
	private static final Set<String> GATED_BLOCKS = Set.of(
			"ancient_debris", "netherrack", "nether_quartz_ore", "nether_gold_ore", "soul_sand", "soul_soil",
			"basalt", "blackstone", "gilded_blackstone", "glowstone", "glowstone_dust", "shroomlight",
			"crimson_nylium", "warped_nylium", "crimson_stem", "warped_stem", "crimson_fungus", "warped_fungus",
			"crimson_roots", "warped_roots", "nether_sprouts", "nether_wart", "nether_wart_block",
			"warped_wart_block", "weeping_vines", "twisting_vines", "magma_cream", "ghast_tear", "blaze_rod",
			"nether_star", "end_stone", "chorus_plant", "chorus_flower", "chorus_fruit", "dragon_egg",
			"dragon_head", "elytra", "shulker_shell", "sculk", "sculk_sensor", "calibrated_sculk_sensor",
			"sculk_catalyst", "sculk_shrieker", "sculk_vein", "echo_shard", "reinforced_deepslate",
			"wither_skeleton_skull", "dried_ghast", "crying_obsidian");

	private static final Set<String> GATED_ENTITIES = Set.of("ghast", "blaze", "piglin", "piglin_brute", "hoglin",
			"zoglin", "strider", "wither_skeleton", "wither", "ender_dragon", "shulker", "warden", "magma_cube");

	private static final Set<String> OVERWORLD = Set.of("minecraft:overworld");

	private static String path(String id) {
		return id.substring(id.indexOf(':') + 1);
	}

	/** Why a step is out of reach for a beginner, or null. */
	static String gate(QuestStep step) {
		for (Condition condition : step.conditions()) {
			if (condition instanceof Condition.InDimension d && !OVERWORLD.contains(d.dimension())) {
				return "condition: dimension " + d.dimension();
			}
			if (condition instanceof Condition.InBiome b && gatedBiome(path(b.biome()))) {
				return "condition: biome " + b.biome();
			}
		}
		return switch (step.goal()) {
			case Goal.ReachDimension g -> OVERWORLD.contains(g.dimension()) ? null : "dimension " + g.dimension();
			case Goal.ReachBiome g -> gatedBiome(path(g.biome())) ? "biome " + g.biome() : null;
			case Goal.LookAtEntity g -> gatedEntity(g.entity());
			case Goal.DefeatEntity g -> gatedEntity(g.entity());
			case Goal.InteractEntity g -> gatedEntity(g.entity());
			case Goal.ReachHeight g -> null;
			default -> {
				String target = KoreanFixtures.targetOf(step.goal());
				yield target != null && GATED_BLOCKS.contains(path(target)) ? "block/item " + target : null;
			}
		};
	}

	private static boolean gatedBiome(String biome) {
		return NETHER_BIOMES.contains(biome) || END_BIOMES.contains(biome) || DEEP_DARK_BIOMES.contains(biome);
	}

	private static String gatedEntity(String entity) {
		return GATED_ENTITIES.contains(path(entity)) ? "mob " + entity : null;
	}

	/** Trail id -> first reasons, for every beginner trail that breaks the rule. */
	static TreeMap<String, List<String>> violations() {
		TreeMap<String, List<String>> result = new TreeMap<>();
		for (Questline trail : QuestLoader.loadAll()) {
			if (trail.koreanLevel() > MAX_GATED_LEVEL) {
				continue;
			}
			List<String> reasons = new ArrayList<>();
			if (trail.requires().dimension() != null && !OVERWORLD.contains(trail.requires().dimension())) {
				reasons.add("requires.dimension " + trail.requires().dimension());
			}
			for (QuestStep step : trail.allSteps()) {
				String why = gate(step);
				if (why != null) {
					reasons.add(step.id() + ": " + why);
				}
			}
			if (!reasons.isEmpty()) {
				result.put(trail.id(), reasons);
			}
		}
		return result;
	}

	static Set<String> debt() {
		try (InputStream stream = ReachabilityGateTest.class.getResourceAsStream("/content-baseline.json")) {
			assertTrue(stream != null, "src/test/resources/content-baseline.json is missing");
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				Set<String> ids = new TreeSet<>();
				JsonArray array = root.getAsJsonArray("reachabilityDebt");
				for (JsonElement element : array) {
					ids.add(element.getAsString());
				}
				return ids;
			}
		} catch (java.io.IOException broken) {
			throw new IllegalStateException(broken);
		}
	}

	@Test
	void beginnerTrailsStayInReach() {
		TreeMap<String, List<String>> violations = violations();
		Set<String> debt = debt();
		List<String> fresh = new ArrayList<>();
		for (var entry : violations.entrySet()) {
			if (!debt.contains(entry.getKey())) {
				fresh.add(entry.getKey() + " " + entry.getValue());
			}
		}
		assertTrue(fresh.isEmpty(), "level 1-2 trails that need the Nether, the End, the Deep Dark or "
				+ "blocks and mobs found only there:\n" + String.join("\n", fresh));
	}

	@Test
	void theDebtListOnlyShrinks() {
		Set<String> violating = violations().keySet();
		List<String> fixed = new ArrayList<>();
		for (String id : debt()) {
			if (!violating.contains(id)) {
				fixed.add(id);
			}
		}
		assertTrue(fixed.isEmpty(), "these trails are in reach now; remove them from reachabilityDebt in "
				+ "content-baseline.json: " + fixed);
		System.out.println("reachability: " + violating.size() + " beginner trails still gated (baseline "
				+ debt().size() + ")");
	}

	@Test
	void theGateRecognisesTheUnreachableThings() {
		QuestStep nether = new QuestStep("s", List.of(), new Goal.ReachDimension("minecraft:the_nether"),
				List.of(), null, null, null, null, null);
		assertEquals("dimension minecraft:the_nether", gate(nether));
		QuestStep debris = new QuestStep("s", List.of(), new Goal.BreakBlock("minecraft:ancient_debris", 1, null),
				List.of(), null, null, null, null, null);
		assertTrue(gate(debris) != null);
		QuestStep barrens = new QuestStep("s", List.of(), new Goal.ReachBiome("minecraft:end_barrens", 1000),
				List.of(), null, null, null, null, null);
		assertTrue(gate(barrens) != null);
		QuestStep conditionNether = new QuestStep("s", List.of(), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(new Condition.InDimension("minecraft:the_nether")), null, null, null, null, null);
		assertTrue(gate(conditionNether) != null);
		QuestStep stone = new QuestStep("s", List.of(), new Goal.BreakBlock("minecraft:stone", 1, null),
				List.of(), null, null, null, null, null);
		assertEquals(null, gate(stone));
		QuestStep plains = new QuestStep("s", List.of(), new Goal.ReachBiome("minecraft:plains", 1000),
				List.of(), null, null, null, null, null);
		assertFalse(gate(plains) != null);
	}
}
