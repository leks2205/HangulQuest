package io.github.leks2205.hangulquest.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F1 hides the HUD, F3 only moves the quest log out of the debug text's way. */
class HudVisibilityTest {
	@Test
	void theQuestLogShowsWhenNothingHidesIt() {
		assertTrue(HudVisibility.questLog(false, false, true, true));
	}

	@Test
	void theDebugOverlayHidesTheQuestLog() {
		assertFalse(HudVisibility.questLog(false, true, true, true));
	}

	@Test
	void hideGuiHidesTheQuestLogAndTheCards() {
		assertFalse(HudVisibility.questLog(true, false, true, true));
		assertFalse(HudVisibility.cards(true));
		assertTrue(HudVisibility.cards(false));
	}

	@Test
	void theDebugOverlayLeavesTheCardsAlone() {
		// Cards are centred or in a corner the debug text does not use; only the log is on the left edge.
		assertTrue(HudVisibility.cards(false));
	}

	@Test
	void theSettingsStillSwitchTheQuestLogOff() {
		assertFalse(HudVisibility.questLog(false, false, false, true));
		assertFalse(HudVisibility.questLog(false, false, true, false));
	}
}
