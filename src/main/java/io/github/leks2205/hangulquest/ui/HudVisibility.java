package io.github.leks2205.hangulquest.ui;

/** When the mod's HUD may draw at all. F1 hides all of it; F3 only makes room for the debug text. */
final class HudVisibility {
	private HudVisibility() {
	}

	/** F1 (the whole HUD hidden) hides the cards, banners and the quest log. */
	static boolean cards(boolean hudHidden) {
		return !hudHidden;
	}

	/** The quest log sits where the debug overlay writes, so it steps aside while F3 is shown. */
	static boolean questLog(boolean hudHidden, boolean debugOverlay, boolean questsOn, boolean trackerOn) {
		return !hudHidden && !debugOverlay && questsOn && trackerOn;
	}
}
