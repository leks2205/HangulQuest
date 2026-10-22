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

	/**
	 * Right-click a block. With {@code change} set ({@code OPEN} or {@code CLOSE}) the door, trapdoor or gate
	 * must really change state in that direction; without it any use of the block counts.
	 */
	record UseBlock(String block, String change) implements Goal {
		public UseBlock(String block) {
			this(block, null);
		}
	}

	record InteractEntity(String entity) implements Goal {
	}

	// Spatial goals (quest/spatial): where a block goes relative to an anchor, and small structures.
	/**
	 * Place {@code count} of a block {@code relation} to an anchor block, family or tag (see BlockSpec), or to
	 * the block placed in an earlier step ({@code "@s2"}, see StepRef). {@code anchor2} is the second anchor
	 * of a BETWEEN ("상자와 화로 사이에"); null otherwise.
	 */
	record PlaceRel(String block, Relation relation, String anchor, int count, String anchor2) implements Goal {
		public PlaceRel(String block, Relation relation, String anchor, int count) {
			this(block, relation, anchor, count, null);
		}
	}

	/** Build a small structure (pillar, pyramid, ring, row, wall, cross, stairs) out of a block or family. */
	record BuildPattern(Pattern pattern, String material, int size) implements Goal {
	}

	/**
	 * Place a block {@code relation} to a structure the player built from {@code material}. {@code size} is the
	 * size of that structure (a row of four); 0 means the size of its kind.
	 */
	record PlaceOnPattern(String block, Relation relation, Pattern pattern, String material, int size) implements Goal {
		public PlaceOnPattern {
			size = size <= 0 && pattern != null ? pattern.defaultSize() : size;
		}

		public PlaceOnPattern(String block, Relation relation, Pattern pattern, String material) {
			this(block, relation, pattern, material, 0);
		}
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

	/** Move {@code count} of an item out of a container block: the counterpart of {@link PutIn}. */
	record TakeOut(String item, String container, int count) implements Goal {
	}

	// ---- Wave 3: moving, resting and waiting.

	/** Go into a roofed space ({@code IN}) or out under the open sky ({@code OUT}): the sky above the player flips. */
	record Transit(String direction) implements Goal {
	}

	/** Stay within {@code radius} blocks of a playing jukebox for {@code dwellMs}, or strike a note block. */
	record Hear(String source, int radius, long dwellMs) implements Goal {
	}

	/** Lie in a bed for {@code dwellMs}, without waiting for the night to pass. */
	record LieDown(String block, long dwellMs) implements Goal {
	}

	/** Stay in one place for {@code seconds}, and until the {@code until} phase (NIGHT, DAY) when it is set. */
	record Wait(int seconds, String until) implements Goal {
	}

	/** Breed {@code count} young of an animal: it is fed, then a baby of its kind appears. */
	record Breed(String entity, int count) implements Goal {
	}

	/** Sit in a boat or minecart, or ride a horse or pig, for {@code dwellMs}. */
	record Mount(String entity, long dwellMs) implements Goal {
	}

	/** Swim {@code blocks} blocks in water. */
	record Swim(int blocks) implements Goal {
	}

	/**
	 * Trade with a merchant: {@code BUY} gets {@code count} of {@code item} out of the result slot (사요),
	 * {@code SELL} pays with it (팔아요). {@code entity} is the merchant (a villager when it is not written).
	 */
	record Trade(String item, String entity, String direction, int count) implements Goal {
	}

	/**
	 * Put an armour piece on ({@code ON}, or no change given) or take it off ({@code OFF}): the armour slots of
	 * the player decide. 투구를 써요, 갑옷을 입어요, 바지를 입어요, 신발을 신어요; 투구를 벗어요.
	 */
	record Equip(String item, String change) implements Goal {
		public Equip(String item) {
			this(item, null);
		}
	}

	/**
	 * Hold an item in either hand while {@code innerGoal} happens, or, with {@code innerCondition},
	 * while walking for {@code seconds} under that condition. Exactly one of the two is set.
	 */
	record HoldWhile(String item, Goal innerGoal, Condition innerCondition, int seconds) implements Goal {
	}

	/** Do the parts one after the other ({@code ordered}) or in any order. {@code connector} is BEFORE, AFTER, THEN, AND (the plain -고) or null. */
	record Sequence(List<Goal> parts, boolean ordered, String connector) implements Goal {
		public Sequence {
			parts = parts == null ? List.of() : List.copyOf(parts);
		}
	}

	/** Stay without the action for the window; doing it restarts the window. */
	record Avoid(Goal action, int windowSeconds, Condition during) implements Goal {
	}
}
