package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.sense.Observation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the player is right now, and which trails fit it: the situational quest slot reads this.
 *
 * <p>{@link #of} turns an observation into a set of {@link Tag}s. {@link #stepHits} counts, per tag, the
 * steps of a trail that hint at it (the biome, height, anchor blocks, creatures, the night); {@link #weak}
 * adds the tags its topic and chapter point at. The pre-computed result lives in {@link TrailTraits}.
 */
public final class Situation {
	/** A kind of place or time. SEA covers ocean, beach, river and swimming. */
	public enum Tag {
		SNOW, MOUNTAIN, CAVE, SEA, JUNGLE, SWAMP, DESERT, VILLAGE, NIGHT
	}

	/** Mountain trails fit from here up; sea level is 63. */
	public static final int MOUNTAIN_Y = 100;
	/** Underground below this height counts as a cave, so a shallow pit does not. */
	public static final int CAVE_Y = 56;

	private Situation() {
	}

	/** The tags that describe the observation; empty on an ordinary day in the open. */
	public static Set<Tag> of(Observation obs) {
		EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
		if (obs == null || !"minecraft:overworld".equals(obs.dimension)) {
			return tags;
		}
		String biome = Reach.path(obs.biome);
		if (biome.contains("cave") || (obs.underground && obs.y <= CAVE_Y)) {
			tags.add(Tag.CAVE);
		}
		if (isSnowy(biome)) {
			tags.add(Tag.SNOW);
		}
		if (obs.biomeTags.contains("minecraft:is_mountain") || isPeak(biome) || obs.y >= MOUNTAIN_Y) {
			tags.add(Tag.MOUNTAIN);
		}
		if (obs.inWater || obs.biomeTags.contains("minecraft:is_ocean") || obs.biomeTags.contains("minecraft:is_beach")
				|| obs.biomeTags.contains("minecraft:is_river") || biome.contains("ocean") || biome.contains("beach")) {
			tags.add(Tag.SEA);
		}
		if (obs.biomeTags.contains("minecraft:is_jungle") || biome.contains("jungle") || biome.contains("bamboo")) {
			tags.add(Tag.JUNGLE);
		}
		if (obs.biomeTags.contains("minecraft:is_swamp") || biome.contains("swamp") || biome.contains("mangrove")) {
			tags.add(Tag.SWAMP);
		}
		if (obs.biomeTags.contains("minecraft:is_desert") || biome.contains("desert")) {
			tags.add(Tag.DESERT);
		}
		if (obs.nearbyEntities.containsKey("minecraft:villager") || obs.nearbyEntities.containsKey("minecraft:wandering_trader")
				|| (obs.lookedBlock != null && obs.lookedBlock.endsWith(":bell"))) {
			tags.add(Tag.VILLAGE);
		}
		if (obs.phase() == Condition.Phase.NIGHT) {
			tags.add(Tag.NIGHT);
		}
		return tags;
	}

	private static boolean isSnowy(String biome) {
		return biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.equals("grove");
	}

	private static boolean isPeak(String biome) {
		return biome.contains("peaks") || biome.contains("slopes") || biome.contains("windswept");
	}

	// ------------------------------------------------------------------ trails

	/** Tags a biome id or biome tag points at. */
	static Set<Tag> ofBiome(String biome) {
		EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
		String path = Reach.path(biome);
		if (isSnowy(path)) {
			tags.add(Tag.SNOW);
		}
		if (isPeak(path) || path.equals("is_mountain") || path.equals("is_hill")) {
			tags.add(Tag.MOUNTAIN);
		}
		if (path.contains("ocean") || path.contains("beach") || path.contains("river") || path.contains("shore")) {
			tags.add(Tag.SEA);
		}
		if (path.contains("jungle") || path.contains("bamboo")) {
			tags.add(Tag.JUNGLE);
		}
		if (path.contains("swamp") || path.contains("mangrove")) {
			tags.add(Tag.SWAMP);
		}
		if (path.contains("desert")) {
			tags.add(Tag.DESERT);
		}
		if (path.contains("cave") || path.contains("dripstone")) {
			tags.add(Tag.CAVE);
		}
		return tags;
	}

	/** Tags a block, item or creature id points at. */
	static Set<Tag> ofThing(String id) {
		EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
		String path = Reach.path(id);
		if (path.equals("snow") || path.equals("snow_block") || path.equals("powder_snow") || path.equals("ice") || path.endsWith("_ice")
				|| path.equals("snowball") || path.equals("snow_golem") || path.equals("polar_bear")
				|| path.equals("stray") || path.equals("powder_snow_bucket")) {
			tags.add(Tag.SNOW);
		}
		if (path.equals("goat") || path.contains("goat_horn")) {
			tags.add(Tag.MOUNTAIN);
		}
		if (path.endsWith("_ore") || path.startsWith("deepslate") || path.contains("dripstone") || path.equals("glow_berries")
				|| path.contains("amethyst") || path.equals("bat") || path.equals("cave_spider") || path.equals("glow_lichen")
				|| path.equals("raw_iron") || path.equals("raw_copper") || path.equals("tuff")) {
			tags.add(Tag.CAVE);
		}
		if (path.equals("cod") || path.equals("salmon") || path.equals("squid") || path.equals("glow_squid") || path.equals("dolphin")
				|| path.equals("turtle") || path.equals("kelp") || path.equals("seagrass") || path.equals("sea_pickle")
				|| path.equals("tropical_fish") || path.equals("pufferfish") || path.equals("drowned") || path.equals("guardian")
				|| path.equals("fishing_rod") || path.equals("prismarine") || path.equals("nautilus_shell")
				|| path.endsWith("_boat") || path.equals("water_bucket") || path.equals("cod_bucket") || path.equals("lily_pad")) {
			tags.add(Tag.SEA);
		}
		if (path.contains("jungle") || path.equals("bamboo") || path.equals("cocoa_beans") || path.equals("cocoa")
				|| path.equals("parrot") || path.equals("ocelot") || path.equals("panda") || path.equals("melon_slice")
				|| path.equals("melon")) {
			tags.add(Tag.JUNGLE);
		}
		if (path.contains("mangrove") || path.equals("slime_ball") || path.equals("slime") || path.equals("frog")
				|| path.equals("lily_pad") || path.equals("witch") || path.equals("blue_orchid")) {
			tags.add(Tag.SWAMP);
		}
		if (path.equals("cactus") || path.contains("sandstone") || path.equals("camel") || path.equals("husk")
				|| path.equals("dead_bush")) {
			tags.add(Tag.DESERT);
		}
		if (path.equals("villager") || path.equals("bell") || path.equals("wandering_trader") || path.equals("iron_golem")
				|| path.equals("lectern") || path.equals("composter") || path.equals("hay_block")) {
			tags.add(Tag.VILLAGE);
		}
		if (path.endsWith("_bed") || path.equals("clock") || path.equals("phantom") || path.equals("daylight_detector")
				|| path.equals("spyglass")) {
			tags.add(Tag.NIGHT);
		}
		return tags;
	}

	/**
	 * For each tag, how many steps of the trail hint at it. The finale does not count for the night: very many
	 * trails end with a torch set in the dark, which says nothing about where they are played.
	 */
	public static Map<Tag, Integer> stepHits(Questline trail) {
		Map<Tag, Integer> hits = new EnumMap<>(Tag.class);
		List<QuestStep> steps = trail.allSteps();
		for (int i = 0; i < steps.size(); i++) {
			QuestStep step = steps.get(i);
			boolean finale = step == trail.finale();
			EnumSet<Tag> here = EnumSet.noneOf(Tag.class);
			for (Condition condition : step.conditions()) {
				condition(condition, here);
			}
			goal(step.goal(), here);
			if (finale) {
				here.remove(Tag.NIGHT);
			}
			for (Tag tag : here) {
				hits.merge(tag, 1, Integer::sum);
			}
		}
		return hits;
	}

	private static void condition(Condition condition, Set<Tag> out) {
		switch (condition) {
			case Condition.InBiome b -> out.addAll(ofBiome(b.biome()));
			case Condition.YRange y -> {
				if (y.minY() >= MOUNTAIN_Y) {
					out.add(Tag.MOUNTAIN);
				} else if (y.maxY() <= CAVE_Y) {
					out.add(Tag.CAVE);
				}
			}
			case Condition.Time t -> {
				if (t.phase() == Condition.Phase.NIGHT) {
					out.add(Tag.NIGHT);
				}
			}
			case Condition.InWater w -> out.add(Tag.SEA);
			case Condition.NearEntity n -> out.addAll(ofThing(n.entity()));
			case Condition.NearBlock n -> out.addAll(ofThing(n.block()));
			default -> {
			}
		}
	}

	private static void goal(Goal goal, Set<Tag> out) {
		switch (goal) {
			case null -> {
			}
			case Goal.ReachBiome g -> out.addAll(ofBiome(g.biome()));
			case Goal.ReachHeight g -> {
				if (g.minY() >= MOUNTAIN_Y) {
					out.add(Tag.MOUNTAIN);
				} else if (g.maxY() <= CAVE_Y) {
					out.add(Tag.CAVE);
				}
			}
			case Goal.Swim g -> out.add(Tag.SEA);
			case Goal.LieDown g -> out.add(Tag.NIGHT);
			case Goal.Wait g -> {
				if ("NIGHT".equalsIgnoreCase(g.until())) {
					out.add(Tag.NIGHT);
				}
			}
			case Goal.Trade g -> out.add(Tag.VILLAGE);
			case Goal.LookAtEntity g -> out.addAll(ofThing(g.entity()));
			case Goal.DefeatEntity g -> out.addAll(ofThing(g.entity()));
			case Goal.InteractEntity g -> out.addAll(ofThing(g.entity()));
			case Goal.Breed g -> out.addAll(ofThing(g.entity()));
			case Goal.Mount g -> out.addAll(ofThing(g.entity()));
			case Goal.Give g -> {
				out.addAll(ofThing(g.entity()));
				out.addAll(ofThing(g.item()));
			}
			case Goal.Sequence g -> g.parts().forEach(part -> goal(part, out));
			case Goal.HoldWhile g -> {
				out.addAll(ofThing(g.item()));
				goal(g.innerGoal(), out);
				if (g.innerCondition() != null) {
					condition(g.innerCondition(), out);
				}
			}
			case Goal.Avoid g -> goal(g.action(), out);
			default -> {
				for (String target : Reach.targets(goal)) {
					out.addAll(ofThing(target));
				}
			}
		}
	}

	/** Tags the branch topic and the chapter point at; a looser hint than the steps. */
	public static Set<Tag> weak(Questline trail, String chapterId) {
		EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
		switch (trail.topicKey()) {
			case "산" -> tags.add(Tag.MOUNTAIN);
			case "밤" -> tags.add(Tag.NIGHT);
			case "마을" -> tags.add(Tag.VILLAGE);
			case "물" -> tags.add(Tag.SEA);
			default -> {
			}
		}
		if (chapterId != null) {
			switch (chapterId) {
				case "cave" -> tags.add(Tag.CAVE);
				case "sea" -> tags.add(Tag.SEA);
				case "market" -> tags.add(Tag.VILLAGE);
				case "weather" -> tags.add(Tag.SNOW);
				default -> {
				}
			}
		}
		return tags;
	}

	/** Priority between simultaneous contexts, high first: what is rarest and most specific wins a tie. */
	static int priority(Tag tag) {
		return switch (tag) {
			case CAVE -> 6;
			case SNOW, MOUNTAIN -> 5;
			case SEA -> 4;
			case JUNGLE, SWAMP, DESERT -> 3;
			case VILLAGE -> 2;
			case NIGHT -> 0;
		};
	}
}
