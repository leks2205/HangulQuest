package io.github.leks2205.hangulquest.quest.spatial;

/** What a relation is measured against: a block (or family, or tag) or a structure the player built. */
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

	/** Stable text for task signatures and progress keys: two anchors are the same task only if this matches. */
	String key();
}
