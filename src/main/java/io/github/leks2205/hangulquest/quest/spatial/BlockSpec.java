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

	/** A tag: which blocks it holds and the block that stands for it when it needs a name. */
	private record Tag(java.util.function.Predicate<String> holds, String representative) {
	}

	private static Tag family(String family, String representative) {
		return new Tag(id -> family.equals(Matching.blockFamily(id)), representative);
	}

	private static Tag suffix(String suffix, String representative) {
		return new Tag(id -> id.substring(id.indexOf(':') + 1).endsWith(suffix), representative);
	}

	/** Tag path to its members and representative; the ones a goal may name. */
	private static final Map<String, Tag> TAGS = Map.ofEntries(
			Map.entry("doors", family("door", "minecraft:oak_door")),
			Map.entry("planks", family("planks", "minecraft:oak_planks")),
			Map.entry("logs", family("log", "minecraft:oak_log")),
			Map.entry("wool", family("wool", "minecraft:white_wool")),
			Map.entry("fences", family("fence", "minecraft:oak_fence")),
			Map.entry("beds", family("bed", "minecraft:red_bed")),
			Map.entry("torches", family("torch", "minecraft:torch")),
			Map.entry("chests", family("chest", "minecraft:chest")),
			Map.entry("flowers", family("flower", "minecraft:poppy")),
			Map.entry("saplings", suffix("_sapling", "minecraft:oak_sapling")),
			Map.entry("leaves", suffix("_leaves", "minecraft:oak_leaves")));

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
		if (spec.indexOf('|') >= 0) { // a step that accepts every referent of its taught word (see Accepted)
			for (String part : spec.split("\\|")) {
				if (matches(part, observed)) {
					return true;
				}
			}
			return false;
		}
		if (isTag(spec)) {
			Tag tag = TAGS.get(tagPath(spec));
			return tag != null && tag.holds().test(observed);
		}
		return Matching.matchesBlock(spec, observed);
	}

	/** A concrete block that stands for the spec when a name is needed: the oak door for {@code #minecraft:doors}. */
	public static String representative(String spec) {
		if (spec != null && spec.indexOf('|') >= 0) {
			spec = spec.substring(0, spec.indexOf('|'));
		}
		if (spec != null && isTag(spec)) {
			Tag tag = TAGS.get(tagPath(spec));
			return tag == null ? null : tag.representative();
		}
		return spec;
	}

	/**
	 * The everyday word for a tag: 통나무 for {@code #minecraft:logs}, 꽃 for {@code #minecraft:flowers}.
	 * It is the curated concept whose blocks (items and covers) are the largest set inside the tag, so the
	 * clue and the finale say the word the player was taught. Null for a spec that is no tag or has no word.
	 */
	public static String tagWord(String spec) {
		if (!isTag(spec) || !TAGS.containsKey(tagPath(spec))) {
			return null;
		}
		return TagWords.WORDS.get(tagPath(spec));
	}

	private static final class TagWords {
		static final Map<String, String> WORDS = build();

		private static Map<String, String> build() {
			Map<String, String> words = new java.util.HashMap<>();
			java.util.List<io.github.leks2205.hangulquest.data.Concept> concepts;
			try {
				concepts = io.github.leks2205.hangulquest.data.ConceptLoader.load();
			} catch (RuntimeException unreadable) {
				return words;
			}
			for (Map.Entry<String, Tag> entry : TAGS.entrySet()) {
				io.github.leks2205.hangulquest.data.Concept best = null;
				int bestSize = 0;
				for (io.github.leks2205.hangulquest.data.Concept concept : concepts) {
					java.util.List<String> blocks = new java.util.ArrayList<>(concept.items);
					blocks.addAll(concept.covers);
					// only a concept that stays inside the tag can name it
					if (blocks.size() > bestSize && blocks.stream().allMatch(entry.getValue().holds())) {
						best = concept;
						bestSize = blocks.size();
					}
				}
				if (best != null) {
					words.put(entry.getKey(), best.ko);
				}
			}
			return words;
		}
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
