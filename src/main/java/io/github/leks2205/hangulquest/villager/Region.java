package io.github.leks2205.hangulquest.villager;

/**
 * The village region a villager comes from: the vanilla villager type, read when the player first talks to it
 * and kept. Conversations and requests can be written for a region.
 */
public enum Region {
	PLAINS("minecraft:plains", "평원"),
	DESERT("minecraft:desert", "사막"),
	SAVANNA("minecraft:savanna", "사바나"),
	SNOW("minecraft:snow", "눈"),
	TAIGA("minecraft:taiga", "타이가"),
	JUNGLE("minecraft:jungle", "정글"),
	SWAMP("minecraft:swamp", "늪");

	private final String villagerType;
	private final String ko;

	Region(String villagerType, String ko) {
		this.villagerType = villagerType;
		this.ko = ko;
	}

	/** The Korean name the journal shows: the vanilla biome name (눈 for the snowy villages). */
	public String ko() {
		return ko;
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
