package io.github.leks2205.hangulquest.quest;

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
}
