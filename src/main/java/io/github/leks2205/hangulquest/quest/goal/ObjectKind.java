package io.github.leks2205.hangulquest.quest.goal;

/** What kind of thing a goal points at. It decides how the thing is named, checked and matched. */
public enum ObjectKind {
	BLOCK("block"),
	ITEM("item"),
	ENTITY("entity"),
	BIOME(null),
	DIMENSION(null),
	NONE(null);

	private final String noun;

	ObjectKind(String noun) {
		this.noun = noun;
	}

	/** Word used in "bad block minecraft:..." problems; null when the id is not checked as a registry id. */
	public String noun() {
		return noun;
	}
}
