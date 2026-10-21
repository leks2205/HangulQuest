package io.github.leks2205.hangulquest.data;

/** Coarse grouping used by the journal and by distractor selection. */
public enum Category {
	BLOCKS("Blöcke", "Blocks"),
	TOOLS("Werkzeuge", "Tools"),
	FOOD("Nahrung", "Food"),
	ANIMALS("Tiere", "Animals"),
	ENVIRONMENT("Umgebung", "Surroundings"),
	ACTIONS("Handlungen", "Actions"),
	MATERIALS("Materialien", "Materials"),
	COMBAT("Kampf", "Combat"),
	ITEMS("Gegenstände", "Items"),
	BUILDING("Bauteile", "Building"),
	MISC("Sonstiges", "Other"),
	PHRASE("Redewendung", "Phrase");

	private final String german;
	private final String english;

	Category(String german, String english) {
		this.german = german;
		this.english = english;
	}

	public String german() {
		return german;
	}

	/** Label in the interface language. */
	public String label() {
		return io.github.leks2205.hangulquest.util.Tr.pick(english, german);
	}
}
