package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.goal.Matching;

import java.util.Map;

/**
 * A block id, a block family or a tag-style pattern, as a goal writes it: {@code minecraft:oak_door}
 * (the whole door family), {@code minecraft:stone} (just that block) or {@code #minecraft:doors}.
 * Tags are a short fixed list built on {@link Matching#blockFamily}, so they work without a loaded
 * registry and in tests.
 */
public final class BlockSpec {
	private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** Tag path to {family name, a block that stands for the family when it needs a name}. */
	private static final Map<String, String[]> TAGS = Map.of(
			"doors", new String[]{"door", "minecraft:oak_door"},
			"planks", new String[]{"planks", "minecraft:oak_planks"},
			"logs", new String[]{"log", "minecraft:oak_log"},
			"wool", new String[]{"wool", "minecraft:white_wool"},
			"fences", new String[]{"fence", "minecraft:oak_fence"},
			"beds", new String[]{"bed", "minecraft:red_bed"},
			"torches", new String[]{"torch", "minecraft:torch"},
			"chests", new String[]{"chest", "minecraft:chest"});

	private BlockSpec() {
	}

	public static boolean isTag(String spec) {
		return spec != null && spec.startsWith("#");
	}

	private static String tagPath(String spec) {
		int colon = spec.indexOf(':');
		return colon >= 0 ? spec.substring(colon + 1) : spec.substring(1);
	}

	/** True when the spec is a known tag or a well-formed id. */
	public static boolean valid(String spec) {
		if (spec == null || spec.isBlank()) {
			return false;
		}
		if (isTag(spec)) {
			return spec.startsWith("#minecraft:") && TAGS.containsKey(tagPath(spec));
		}
		return ID.matcher(spec).matches();
	}

	/** True when the observed block id satisfies the spec. */
	public static boolean matches(String spec, String observed) {
		if (spec == null || observed == null) {
			return false;
		}
		if (isTag(spec)) {
			String[] tag = TAGS.get(tagPath(spec));
			return tag != null && tag[0].equals(Matching.blockFamily(observed));
		}
		return Matching.matchesBlock(spec, observed);
	}

	/** A concrete block that stands for the spec when a name is needed: the oak door for {@code #minecraft:doors}. */
	public static String representative(String spec) {
		if (spec != null && isTag(spec)) {
			String[] tag = TAGS.get(tagPath(spec));
			return tag == null ? null : tag[1];
		}
		return spec;
	}

	/** Doors, chests, furnaces and beds have a clear front; 앞 and 뒤 only apply to them. */
	public static boolean hasFacing(String spec) {
		String id = representative(spec);
		if (id == null) {
			return false;
		}
		String family = Matching.blockFamily(id);
		if (family.equals("door") || family.equals("chest") || family.equals("bed")) {
			return true;
		}
		String path = id.substring(id.indexOf(':') + 1);
		return switch (path) {
			case "iron_door", "furnace", "blast_furnace", "smoker", "ender_chest" -> true;
			default -> false;
		};
	}

	/** Blocks a wall, a pen or a room is built from: 안 and 밖 are measured against them. */
	public static boolean encloses(String spec) {
		String id = representative(spec);
		if (id == null) {
			return false;
		}
		String family = Matching.blockFamily(id);
		if (family.equals("fence") || family.equals("planks") || family.equals("log") || family.equals("wool")) {
			return true;
		}
		String path = id.substring(id.indexOf(':') + 1);
		return path.endsWith("_wall") || path.endsWith("bricks") || path.endsWith("_fence_gate")
				|| path.equals("stone") || path.equals("cobblestone") || path.equals("deepslate")
				|| path.endsWith("_stone") && !path.equals("end_stone");
	}
}
