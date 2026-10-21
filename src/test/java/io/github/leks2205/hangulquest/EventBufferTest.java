package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.ConsumptionTracker;
import io.github.leks2205.hangulquest.sense.EventBuffer;
import io.github.leks2205.hangulquest.sense.LookPick;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBufferTest {
	@Test
	void anEventReachesExactlyOneObservation() {
		EventBuffer buffer = new EventBuffer();
		buffer.blockBroken("minecraft:stone");
		buffer.itemUsed("minecraft:bread");
		buffer.entityAttacked("minecraft:zombie");
		buffer.entityInteracted("minecraft:villager");

		Observation first = new Observation();
		buffer.drainInto(first);
		assertEquals(List.of("minecraft:stone"), first.brokenBlocks);
		assertEquals(List.of("minecraft:bread"), first.usedItems);
		assertEquals(List.of("minecraft:zombie"), first.attackedEntities);
		assertEquals(List.of("minecraft:villager"), first.interactedEntities);

		Observation second = new Observation();
		buffer.drainInto(second);
		assertTrue(second.brokenBlocks.isEmpty());
		assertTrue(second.usedItems.isEmpty());
		assertTrue(second.attackedEntities.isEmpty());
		assertTrue(second.interactedEntities.isEmpty());
	}

	@Test
	void oneRightClickIsOneBlockUseEvenThoughTheCallbackFiresPerHand() {
		EventBuffer buffer = new EventBuffer();
		buffer.blockUsed("minecraft:chest", false, true);
		buffer.blockUsed("minecraft:chest", false, false); // the off-hand pass of the same click
		Observation obs = new Observation();
		buffer.drainInto(obs);
		assertEquals(List.of("minecraft:chest"), obs.usedBlocks);
	}

	@Test
	void nightUsesAreKeptSeparatelyAndOnce() {
		EventBuffer buffer = new EventBuffer();
		buffer.blockUsed("minecraft:red_bed", true, true);
		buffer.blockUsed("minecraft:red_bed", true, false);
		Observation obs = new Observation();
		buffer.drainInto(obs);
		assertEquals(1, obs.usedBlocks.size());
		assertEquals(1, obs.usedBlocksAtNight.size());
	}

	@Test
	void clearDropsPendingEventsFromTheOldWorld() {
		EventBuffer buffer = new EventBuffer();
		buffer.blockBroken("minecraft:stone");
		buffer.blockUsed("minecraft:chest", true, true);
		buffer.clear();
		Observation obs = new Observation();
		buffer.drainInto(obs);
		assertTrue(obs.brokenBlocks.isEmpty());
		assertTrue(obs.usedBlocks.isEmpty());
		assertTrue(obs.usedBlocksAtNight.isEmpty());
	}

	@Test
	void oneBiteCountsOnceAcrossTwoObservations() {
		EventBuffer buffer = new EventBuffer();
		ConsumptionTracker tracker = new ConsumptionTracker();
		Map<String, Integer> before = Map.of("minecraft:bread", 2);
		Map<String, Integer> after = Map.of("minecraft:bread", 1);
		tracker.used("minecraft:bread", 1_000L);

		Observation first = new Observation();
		first.now = 2_000L;
		buffer.drainInto(first);
		tracker.credit(first, before, after, false);

		Observation second = new Observation();
		second.now = 2_050L;
		buffer.drainInto(second);
		tracker.credit(second, after, after, false);

		assertEquals(1, first.consumedItems.size() + second.consumedItems.size(),
				"the bite must show up in one observation only");
		assertEquals(List.of("minecraft:bread"), first.consumedItems);
	}

	@Test
	void nightConsumptionIsRecordedOnceInBothLists() {
		ConsumptionTracker tracker = new ConsumptionTracker();
		tracker.used("minecraft:bread", 1_000L);
		Observation obs = new Observation();
		obs.now = 1_500L;
		tracker.credit(obs, Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), true);
		assertEquals(List.of("minecraft:bread"), obs.consumedItems);
		assertEquals(List.of("minecraft:bread"), obs.consumedItemsAtNight);
	}

	@Test
	void farLookReachesFurtherThroughASpyglass() {
		assertEquals(64.0, LookPick.range(false));
		assertTrue(LookPick.range(true) > LookPick.range(false));
	}

	@Test
	void aBlockInFrontHidesTheEntityBehindIt() {
		assertTrue(LookPick.visible(30.0 * 30.0, 40.0 * 40.0));
		assertFalse(LookPick.visible(30.0 * 30.0, 10.0 * 10.0));
		assertTrue(LookPick.visible(30.0 * 30.0, Double.MAX_VALUE), "no block on the ray");
	}
}
