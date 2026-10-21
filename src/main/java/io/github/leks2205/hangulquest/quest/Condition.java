package io.github.leks2205.hangulquest.quest;

/** Continuous gates: a goal only makes progress while all conditions hold. */
public sealed interface Condition {
	enum Phase {
		DAY, NIGHT, DAWN, DUSK
	}

	enum Weather {
		CLEAR, RAIN, THUNDER
	}

	record Time(Phase phase) implements Condition {
	}

	record WeatherState(Weather weather) implements Condition {
	}

	record YRange(int minY, int maxY) implements Condition {
	}

	record NearEntity(String entity, double radius) implements Condition {
	}

	record NearBlock(String block, double radius) implements Condition {
	}

	record InDimension(String dimension) implements Condition {
	}

	record InBiome(String biome) implements Condition {
	}

	// ---- Action variety.

	enum SkyState {
		OPEN, ROOFED
	}

	/** The item is in a hand. */
	record Holding(String item) implements Condition {
	}

	/** Open sky above the player, or a roof within reach. */
	record Sky(SkyState state) implements Condition {
	}

	record InWater() implements Condition {
	}

	/**
	 * The player stands in this relation to a block or an entity ("나무 아래에서"). {@code kind} says what
	 * the anchor is ("block", "item" or "entity"); null when the file does not say, and the name then
	 * prefers an entity (a chicken is the animal, not the raw meat).
	 */
	record PlayerRel(io.github.leks2205.hangulquest.quest.spatial.Relation relation, String anchor, String kind)
			implements Condition {
		public PlayerRel(io.github.leks2205.hangulquest.quest.spatial.Relation relation, String anchor) {
			this(relation, anchor, null);
		}
	}
}
