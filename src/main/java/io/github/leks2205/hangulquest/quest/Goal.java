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

	// ---- Action variety: craft, smelt, give, put in a container, hold, sequences, avoid.

	/** Take {@code count} of an item out of a crafting result slot. {@code with} only shapes the sentence. */
	record Craft(String item, int count, String station, List<String> with) implements Goal {
		public Craft {
			with = with == null ? List.of() : List.copyOf(with);
		}
	}

	/** Take {@code count} of an item out of a furnace, blast furnace or smoker. {@code input} only shapes the sentence. */
	record Smelt(String input, String output, int count) implements Goal {
	}

	/** Hand an item to an entity. {@code method} is a GiveFact.Method name, or null for any. */
	record Give(String item, String entity, String method, int count) implements Goal {
	}

	/** Move {@code count} of an item into a container block. */
	record PutIn(String item, String container, int count) implements Goal {
	}

	/**
	 * Hold an item in either hand while {@code innerGoal} happens, or, with {@code innerCondition},
	 * while walking for {@code seconds} under that condition. Exactly one of the two is set.
	 */
	record HoldWhile(String item, Goal innerGoal, Condition innerCondition, int seconds) implements Goal {
	}

	/** Do the parts one after the other ({@code ordered}) or in any order. {@code connector} is BEFORE, AFTER, THEN or null. */
	record Sequence(List<Goal> parts, boolean ordered, String connector) implements Goal {
		public Sequence {
			parts = parts == null ? List.of() : List.copyOf(parts);
		}
	}

	/** Stay without the action for the window; doing it restarts the window. */
	record Avoid(Goal action, int windowSeconds, Condition during) implements Goal {
	}
}
