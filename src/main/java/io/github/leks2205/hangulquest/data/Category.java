package io.github.leks2205.hangulquest.data;

import io.github.leks2205.hangulquest.util.I18n;

import java.util.Locale;

/** Coarse grouping used by the journal and by distractor selection. */
public enum Category {
	BLOCKS,
	TOOLS,
	FOOD,
	ANIMALS,
	ENVIRONMENT,
	ACTIONS,
	MATERIALS,
	COMBAT,
	ITEMS,
	BUILDING,
	MISC,
	PHRASE;

	/** Lang key of the label: {@code hangulquest.ui.category.<name>}. */
	public String key() {
		return "hangulquest.ui.category." + name().toLowerCase(Locale.ROOT);
	}

	/** Label in the interface language. */
	public String label() {
		return I18n.t(key());
	}
}
