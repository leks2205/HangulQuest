package io.github.leks2205.hangulquest.villager;

import java.util.Locale;

/** A villager's gender: it fixes the name pool, the personalities a villager can have and the voice. */
public enum Gender {
	MALE("male"),
	FEMALE("female");

	private final String id;

	Gender(String id) {
		this.id = id;
	}

	/** The name in data files: {@code male} or {@code female}. */
	public String id() {
		return id;
	}

	/** The child voice of this gender. */
	public String childVoice() {
		return this == MALE ? "v_child_m" : "v_child_f";
	}

	public static Gender fromId(String id) {
		if (id == null) {
			return null;
		}
		String wanted = id.trim().toLowerCase(Locale.ROOT);
		for (Gender gender : values()) {
			if (gender.id.equals(wanted) || gender.name().equalsIgnoreCase(wanted)) {
				return gender;
			}
		}
		return null;
	}
}
