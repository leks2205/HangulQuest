package de.zirks.hangulquest;

import de.zirks.hangulquest.sense.ConsumptionTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionTrackerTest {
	@Test
	void countsAnItemThatDisappearsAfterUse() {
		ConsumptionTracker tracker = new ConsumptionTracker();
		tracker.used("minecraft:bread", 1_000L);
		List<String> eaten = tracker.update(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), 3_000L);
		assertEquals(List.of("minecraft:bread"), eaten);
	}

	@Test
	void toleratesServerLatency() {
		ConsumptionTracker tracker = new ConsumptionTracker();
		tracker.used("minecraft:bread", 1_000L);
		assertTrue(tracker.update(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 2), 14_000L).isEmpty());
		assertEquals(List.of("minecraft:bread"),
				tracker.update(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), 15_500L));
	}

	@Test
	void ignoresOldUsesAndUnusedItems() {
		ConsumptionTracker tracker = new ConsumptionTracker();
		tracker.used("minecraft:bread", 1_000L);
		assertTrue(tracker.update(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), 20_000L).isEmpty());

		ConsumptionTracker other = new ConsumptionTracker();
		assertTrue(other.update(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), 2_000L).isEmpty());
	}
}
