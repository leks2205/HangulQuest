package de.zirks.hangulquest.data;

/** Coarse grouping used by the journal and by distractor selection. */
public enum Category {
	BLOCKS("Blöcke"),
	TOOLS("Werkzeuge"),
	FOOD("Nahrung"),
	ANIMALS("Tiere"),
	ENVIRONMENT("Umgebung"),
	ACTIONS("Handlungen"),
	MATERIALS("Materialien"),
	COMBAT("Kampf"),
	MISC("Sonstiges"),
	PHRASE("Redewendung");

	private final String german;

	Category(String german) {
		this.german = german;
	}

	public String german() {
		return german;
	}
}
