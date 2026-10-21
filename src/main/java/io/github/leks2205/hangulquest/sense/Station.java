package io.github.leks2205.hangulquest.sense;

/**
 * Screens with a result slot. The campfire has no screen (the food pops out of the block later),
 * so it cannot be told apart from a drop and is not covered.
 */
public enum Station {
	INVENTORY_2X2("inventory2x2", 0),
	CRAFTING_TABLE("crafting_table", 0),
	FURNACE("furnace", 2),
	BLAST_FURNACE("blast_furnace", 2),
	SMOKER("smoker", 2),
	STONECUTTER("stonecutter", 1),
	SMITHING("smithing", 3),
	LOOM("loom", 3),
	CARTOGRAPHY("cartography", 2),
	GRINDSTONE("grindstone", 2),
	/** A villager or wandering trader's screen: taking the result is a trade, not a craft. */
	MERCHANT("merchant", 2);

	private final String id;
	private final int resultSlot;

	Station(String id, int resultSlot) {
		this.id = id;
		this.resultSlot = resultSlot;
	}

	public String id() {
		return id;
	}

	/** Index of the result slot in the menu's slot list. */
	public int resultSlot() {
		return resultSlot;
	}
}
