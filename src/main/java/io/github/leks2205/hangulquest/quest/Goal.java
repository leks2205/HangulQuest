package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Relation;

import java.util.List;

/** The closed set of verifiable quest goals. */
public sealed interface Goal {
	record BreakBlock(String block, int count, String verb) implements Goal {
	}

	record PlaceBlock(String block, int count) implements Goal {
	}

	record AcquireItem(String item, int count) implements Goal {
	}

	record ConsumeItem(String item, int count) implements Goal {
	}

	record LookAtBlock(String block, long dwellMs) implements Goal {
	}

	record LookAtEntity(String entity, long dwellMs) implements Goal {
	}

	record DefeatEntity(String entity, int count) implements Goal {
	}

	record ReachBiome(String biome, long dwellMs) implements Goal {
	}

	record ReachDimension(String dimension) implements Goal {
	}

	record ReachHeight(int minY, int maxY) implements Goal {
	}

	record UseBlock(String block) implements Goal {
	}

	record InteractEntity(String entity) implements Goal {
	}

	// Spatial goals (quest/spatial): where a block goes relative to an anchor, and small structures.
	/** Place {@code count} of a block {@code relation} to an anchor block, family or tag (see BlockSpec). */
	record PlaceRel(String block, Relation relation, String anchor, int count) implements Goal {
	}

	/** Build a small structure (pillar, pyramid, ring, row) out of a block or family. */
	record BuildPattern(Pattern pattern, String material, int size) implements Goal {
	}

	/** Place a block {@code relation} to a structure the player built from {@code material}. */
	record PlaceOnPattern(String block, Relation relation, Pattern pattern, String material) implements Goal {
	}
}
