package io.github.leks2205.hangulquest.villager;

/**
 * The village region a villager comes from: the vanilla villager type, read when the player first talks to it
 * and kept. Conversations and requests can be written for a region.
 */
public enum Region {
	PLAINS("minecraft:plains"),
	DESERT("minecraft:desert"),
	SAVANNA("minecraft:savanna"),
	SNOW("minecraft:snow"),
	TAIGA("minecraft:taiga"),
	JUNGLE("minecraft:jungle"),
	SWAMP("minecraft:swamp");

	private final String villagerType;

	Region(String villagerType) {
		this.villagerType = villagerType;
	}

	/** The registry id of the vanilla villager type. */
	public String villagerType() {
		return villagerType;
	}

	/** The lower-case id used in conversation ids and language keys. */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The region of a villager type id such as {@code minecraft:snow}; null for a type that is none of these. */
	public static Region ofVillagerType(String type) {
		if (type == null) {
			return null;
		}
		for (Region region : values()) {
			if (region.villagerType.equals(type)) {
				return region;
			}
		}
		return null;
	}

	/** The region of a data file name (any case), or null. */
	public static Region fromId(String id) {
		if (id == null) {
			return null;
		}
		for (Region region : values()) {
			if (region.name().equalsIgnoreCase(id.trim())) {
				return region;
			}
		}
		return null;
	}
}
