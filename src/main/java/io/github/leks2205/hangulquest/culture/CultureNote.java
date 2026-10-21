package io.github.leks2205.hangulquest.culture;

import java.util.List;

/**
 * A short note about Korean culture, tied to something in the game. An item note ({@code anchors}) shows once,
 * when the player first crafts or gets one of the items; a holiday note ({@code holiday}) shows near the real
 * date. {@code ko} is the Korean summary at level 1 to 2; the title, the translation of the summary and the
 * explanation are the keys {@code hangulquest.culture.<id>.title}, {@code .summary} and {@code .text}.
 */
public record CultureNote(String id, String titleKo, String ko, String icon, List<String> anchors, String holiday) {
	public CultureNote {
		anchors = List.copyOf(anchors);
		holiday = holiday == null ? "" : holiday;
	}

	public boolean isHoliday() {
		return !holiday.isEmpty();
	}

	public String titleKey() {
		return "hangulquest.culture." + id + ".title";
	}

	public String summaryKey() {
		return "hangulquest.culture." + id + ".summary";
	}

	public String textKey() {
		return "hangulquest.culture." + id + ".text";
	}
}
