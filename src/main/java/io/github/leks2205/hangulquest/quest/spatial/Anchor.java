package io.github.leks2205.hangulquest.quest.spatial;

/** What a relation is measured against: a block (or family, or tag), a structure the player built, or a block placed earlier. */
public sealed interface Anchor {
	/** A block id, a family such as the doors, or a tag-style pattern, see {@link BlockSpec}. */
	record Block(String spec) implements Anchor {
		@Override
		public String key() {
			return spec;
		}
	}

	/** A small structure made of one material, see {@link Pattern}. */
	record Built(Pattern pattern, String material, int size) implements Anchor {
		public Built(Pattern pattern, String material) {
			this(pattern, material, pattern.defaultSize());
		}

		@Override
		public String key() {
			return pattern + "/" + material + (size == pattern.defaultSize() ? "" : "/" + size);
		}
	}

	/** Two different blocks that a BETWEEN is measured against: 상자와 화로 사이. */
	record Pair(Block first, Block second) implements Anchor {
		@Override
		public String key() {
			return first.key() + "+" + second.key();
		}
	}

	/** The block placed to finish an earlier step ({@code "@s2"}), remembered by its position. */
	record Placed(String step, int x, int y, int z) implements Anchor {
		@Override
		public String key() {
			return "@" + step;
		}
	}

	/** Stable text for task signatures and progress keys: two anchors are the same task only if this matches. */
	String key();
}
