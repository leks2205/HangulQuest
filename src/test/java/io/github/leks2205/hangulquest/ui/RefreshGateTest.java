package io.github.leks2205.hangulquest.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the quest log is rebuilt: once per tick, or when its layout inputs change. */
class RefreshGateTest {
	@Test
	void theFirstAskIsDue() {
		assertTrue(new RefreshGate().due(10, 3, 400));
	}

	@Test
	void manyFramesInOneTickRebuildOnce() {
		RefreshGate gate = new RefreshGate();
		assertTrue(gate.due(10, 3, 400));

		for (int frame = 0; frame < 50; frame++) {
			assertFalse(gate.due(10, 3, 400), "frame " + frame + " of the same tick");
		}
	}

	@Test
	void aNewTickIsDue() {
		RefreshGate gate = new RefreshGate();
		gate.due(10, 3, 400);

		assertTrue(gate.due(11, 3, 400));
		assertFalse(gate.due(11, 3, 400));
	}

	@Test
	void aChangedRowLimitOrWindowWidthIsDueWithoutANewTick() {
		RefreshGate gate = new RefreshGate();
		gate.due(10, 3, 400);

		assertTrue(gate.due(10, 4, 400), "the player raised the number of rows");
		assertTrue(gate.due(10, 4, 320), "the window was resized");
		assertFalse(gate.due(10, 4, 320));
	}

	@Test
	void resetForcesTheNextRebuild() {
		RefreshGate gate = new RefreshGate();
		gate.due(10, 3, 400);
		gate.reset();

		assertTrue(gate.due(10, 3, 400));
	}
}
