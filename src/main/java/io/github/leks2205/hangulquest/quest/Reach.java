package io.github.leks2205.hangulquest.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How hard a trail is to start near the spawn, from its steps: the reachability tiers the quest picker
 * scores with. Mirrors {@code ReachabilityGateTest}, which keeps the beginner trails out of the Nether, the
 * End and the Deep Dark; a test checks that everything that gate flags is out of reach here too.
 *
 * <ul>
 * <li><b>out of reach</b>: another dimension, a Nether/End/Deep Dark biome, a block or mob that only lives
 * there, anything below y=0. Never picked automatically.</li>
 * <li><b>structure</b> (penalty {@value #STRUCTURE}): needs a village, a thunderstorm or a rare biome.</li>
 * <li><b>place</b> (penalty {@value #PLACE} each, at most {@value #PLACE_CAP}): needs a particular biome, a
 * creature, a height or the night, but nothing that is not around most spawns.</li>
 * <li><b>resources</b>: an advanced material (diamond, gold, ...) costs {@value #ADVANCED}, iron
 * {@value #IRON}, at most {@value #RESOURCE_CAP} together.</li>
 * </ul>
 */
public final class Reach {
	/** What the steps of a trail ask of the player, as score penalties (zero or negative). */
	public record Cost(boolean outOfReach, int place, int structure, int resources) {
		public static final Cost FREE = new Cost(false, 0, 0, 0);

		/** The total penalty, zero or negative. */
		public int penalty() {
			return place + structure + resources;
		}
	}

	public static final int PLACE = -8;
	public static final int PLACE_CAP = -16;
	public static final int STRUCTURE = -25;
	public static final int ADVANCED = -10;
	public static final int IRON = -4;
	public static final int RESOURCE_CAP = -20;

	private static final Set<String> OVERWORLD = Set.of("minecraft:overworld");
	private static final Set<String> GATED_BIOMES = Set.of("nether_wastes", "crimson_forest", "warped_forest",
			"soul_sand_valley", "basalt_deltas", "the_end", "small_end_islands", "end_highlands", "end_midlands",
			"end_barrens", "deep_dark");
	private static final Set<String> GATED_TAGS = Set.of("is_nether", "is_end");
	/** Biomes and biome tags that most spawns have within a walk. */
	private static final Set<String> COMMON_BIOMES = Set.of("plains", "sunflower_plains", "forest", "flower_forest",
			"birch_forest", "dark_forest", "taiga", "snowy_taiga", "snowy_plains", "river", "frozen_river", "beach",
			"snowy_beach", "stony_shore", "ocean", "deep_ocean", "cold_ocean", "lukewarm_ocean", "warm_ocean",
			"frozen_ocean", "savanna", "desert", "swamp", "mangrove_swamp", "meadow", "jungle", "grove",
			"snowy_slopes", "windswept_hills", "windswept_forest", "dripstone_caves", "lush_caves");
	private static final Set<String> COMMON_TAGS = Set.of("is_forest", "is_river", "is_ocean", "is_mountain",
			"is_beach", "is_taiga", "is_jungle", "is_desert", "is_swamp", "is_hill", "is_savanna");

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
	/** Creatures and blocks that mean a village (or a pillager outpost) is near. */
	private static final Set<String> STRUCTURE_ENTITIES = Set.of("villager", "wandering_trader", "iron_golem",
			"pillager", "vindicator", "evoker", "ravager", "witch", "zombie_villager", "trader_llama");
	private static final Set<String> STRUCTURE_BLOCKS = Set.of("bell", "lectern");
	private static final String[] ADVANCED_ITEMS = {"diamond", "emerald", "gold", "netherite", "lapis", "amethyst",
			"ender_", "trident", "totem", "heart_of_the_sea", "nautilus", "enchanted", "experience_bottle"};

	private Reach() {
	}

	/** Strips the namespace and a leading {@code #}. */
	static String path(String id) {
		if (id == null) {
			return "";
		}
		String s = id.startsWith("#") ? id.substring(1) : id;
		return s.substring(s.indexOf(':') + 1);
	}

	public static Cost of(Questline trail) {
		if (trail.requires().dimension() != null && !OVERWORLD.contains(trail.requires().dimension())) {
			return new Cost(true, 0, 0, 0);
		}
		Acc acc = new Acc();
		for (QuestStep step : trail.allSteps()) {
			step(step, acc);
		}
		if (acc.out) {
			return new Cost(true, 0, 0, 0);
		}
		return new Cost(false, Math.max(PLACE_CAP, acc.places * PLACE), acc.structure ? STRUCTURE : 0,
				Math.max(RESOURCE_CAP, acc.resources));
	}

	/** Why one step is out of reach, or null. Same verdicts as the gate test's {@code gate} for the same step. */
	public static String gate(QuestStep step) {
		Acc acc = new Acc();
		step(step, acc);
		return acc.out ? acc.why : null;
	}

	private static final class Acc {
		boolean out;
		String why;
		boolean structure;
		int places;
		int resources;
		final Set<String> seen = new LinkedHashSet<>();

		void out(String why) {
			if (!out) {
				this.why = why;
			}
			out = true;
		}

		void place(String key) {
			if (seen.add("p:" + key)) {
				places++;
			}
		}

		void resource(String key, int cost) {
			if (seen.add("r:" + key)) {
				resources += cost;
			}
		}
	}

	private static void step(QuestStep step, Acc acc) {
		for (Condition condition : step.conditions()) {
			condition(condition, acc);
		}
		goal(step.goal(), acc);
	}

	private static void condition(Condition condition, Acc acc) {
		switch (condition) {
			case Condition.InDimension d -> {
				if (!OVERWORLD.contains(d.dimension())) {
					acc.out("condition: dimension " + d.dimension());
				}
			}
			case Condition.InBiome b -> biome(b.biome(), acc);
			case Condition.YRange y -> {
				if (y.maxY() < 0) {
					acc.out("condition: below y=0");
				} else {
					acc.place("y");
				}
			}
			case Condition.WeatherState w -> {
				if (w.weather() == Condition.Weather.THUNDER) {
					acc.structure = true;
				} else if (w.weather() == Condition.Weather.RAIN) {
					acc.place("weather");
				}
			}
			case Condition.Time t -> {
				if (t.phase() == Condition.Phase.NIGHT) {
					acc.place("night");
				}
			}
			case Condition.NearEntity n -> entity(n.entity(), acc);
			case Condition.Holding h -> item(h.item(), acc);
			default -> {
			}
		}
	}

	private static void goal(Goal goal, Acc acc) {
		switch (goal) {
			case null -> {
			}
			case Goal.ReachDimension g -> {
				if (!OVERWORLD.contains(g.dimension())) {
					acc.out("dimension " + g.dimension());
				}
			}
			case Goal.ReachBiome g -> biome(g.biome(), acc);
			case Goal.ReachHeight g -> {
				if (g.maxY() < 0) {
					acc.out("below y=0");
				} else {
					acc.place("height");
				}
			}
			case Goal.LookAtEntity g -> entity(g.entity(), acc);
			case Goal.DefeatEntity g -> entity(g.entity(), acc);
			case Goal.InteractEntity g -> entity(g.entity(), acc);
			case Goal.Breed g -> entity(g.entity(), acc);
			case Goal.Mount g -> entity(g.entity(), acc);
			case Goal.Give g -> {
				entity(g.entity(), acc);
				item(g.item(), acc);
			}
			case Goal.Trade g -> {
				acc.structure = true;
				item(g.item(), acc);
			}
			case Goal.Hear g -> acc.place("music");
			case Goal.Swim g -> acc.place("water");
			case Goal.Fall g -> {
				acc.place("height");
				if (g.landing() != null) {
					block(g.landing(), acc);
				}
			}
			case Goal.LieDown g -> acc.place("night");
			case Goal.Wait g -> {
				if (g.until() != null) {
					acc.place("wait");
				}
			}
			case Goal.Sequence g -> g.parts().forEach(part -> goal(part, acc));
			case Goal.HoldWhile g -> {
				item(g.item(), acc);
				goal(g.innerGoal(), acc);
				if (g.innerCondition() != null) {
					condition(g.innerCondition(), acc);
				}
			}
			case Goal.Avoid g -> goal(g.action(), acc);
			default -> {
				for (String target : targets(goal)) {
					block(target, acc);
					item(target, acc);
				}
			}
		}
	}

	/** The blocks and items a goal names. */
	static List<String> targets(Goal goal) {
		List<String> out = new ArrayList<>();
		switch (goal) {
			case Goal.BreakBlock g -> out.add(g.block());
			case Goal.PlaceBlock g -> out.add(g.block());
			case Goal.AcquireItem g -> out.add(g.item());
			case Goal.ConsumeItem g -> out.add(g.item());
			case Goal.LookAtBlock g -> out.add(g.block());
			case Goal.UseBlock g -> out.add(g.block());
			case Goal.PlaceRel g -> {
				out.add(g.block());
				out.add(g.anchor());
				out.add(g.anchor2());
			}
			case Goal.PlaceOnPattern g -> out.add(g.block());
			case Goal.BuildPattern g -> out.add(g.material());
			case Goal.Craft g -> out.add(g.item());
			case Goal.Smelt g -> {
				out.add(g.input());
				out.add(g.output());
			}
			case Goal.PutIn g -> {
				out.add(g.item());
				out.add(g.container());
			}
			case Goal.TakeOut g -> {
				out.add(g.item());
				out.add(g.container());
			}
			case Goal.Equip g -> out.add(g.item());
			default -> {
			}
		}
		out.removeIf(target -> target == null || target.isBlank() || target.startsWith("@"));
		return out;
	}

	private static void biome(String biome, Acc acc) {
		String path = path(biome);
		boolean tag = biome != null && biome.startsWith("#");
		if (GATED_BIOMES.contains(path) || GATED_TAGS.contains(path)) {
			acc.out("biome " + biome);
		} else if (tag ? COMMON_TAGS.contains(path) : COMMON_BIOMES.contains(path)) {
			acc.place("biome:" + path);
		} else {
			acc.structure = true; // a rare biome
		}
	}

	private static void entity(String entity, Acc acc) {
		String path = path(entity);
		if (GATED_ENTITIES.contains(path)) {
			acc.out("mob " + entity);
		} else if (STRUCTURE_ENTITIES.contains(path)) {
			acc.structure = true;
		} else if (!path.isEmpty()) {
			acc.place("entity:" + path);
		}
	}

	private static void block(String block, Acc acc) {
		String path = path(block);
		if (GATED_BLOCKS.contains(path)) {
			acc.out("block/item " + block);
		} else if (STRUCTURE_BLOCKS.contains(path)) {
			acc.structure = true;
		}
	}

	private static void item(String item, Acc acc) {
		String path = path(item);
		if (path.isEmpty()) {
			return;
		}
		if (GATED_BLOCKS.contains(path)) {
			acc.out("block/item " + item);
			return;
		}
		for (String advanced : ADVANCED_ITEMS) {
			if (path.contains(advanced)) {
				acc.resource(advanced, ADVANCED);
				return;
			}
		}
		if (path.contains("iron")) {
			acc.resource("iron", IRON);
		}
	}
}
