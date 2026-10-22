package io.github.leks2205.hangulquest.sense;

import java.util.LinkedHashMap;
import java.util.Map;

/** The armour slots of the player as {@link Observation#worn}: slot name to item id, empty slots left out. */
public final class WornItems {
	public static final String HEAD = "head";
	public static final String CHEST = "chest";
	public static final String LEGS = "legs";
	public static final String FEET = "feet";

	private WornItems() {
	}

	/** The map for four slot contents; a null or blank id is an empty slot. */
	public static Map<String, String> of(String head, String chest, String legs, String feet) {
		Map<String, String> worn = new LinkedHashMap<>(4);
		put(worn, HEAD, head);
		put(worn, CHEST, chest);
		put(worn, LEGS, legs);
		put(worn, FEET, feet);
		return worn.isEmpty() ? Map.of() : Map.copyOf(worn);
	}

	private static void put(Map<String, String> worn, String slot, String id) {
		if (id != null && !id.isBlank()) {
			worn.put(slot, id);
		}
	}
}
