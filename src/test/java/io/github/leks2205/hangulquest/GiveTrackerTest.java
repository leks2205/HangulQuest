package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.GiveTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveTrackerTest {
	private static final UUID COW = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
	private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	@Test
	void feedingIsARightClickFollowedByTheHeldCountDropping() {
		GiveTracker tracker = new GiveTracker();
		tracker.used("minecraft:wheat", "minecraft:cow", COW, true, 1_000L);

		List<GiveFact> gave = tracker.inventoryChanged(Map.of("minecraft:wheat", 5), Map.of("minecraft:wheat", 4),
				1_050L, true);

		assertEquals(List.of(new GiveFact("minecraft:wheat", 1, "minecraft:cow", COW, GiveFact.Method.FEED)), gave);
	}

	@Test
	void anItemThatIsNotFoodIsUseWith() {
		GiveTracker tracker = new GiveTracker();
		tracker.used("minecraft:name_tag", "minecraft:cow", COW, false, 0L);
		List<GiveFact> gave = tracker.inventoryChanged(Map.of("minecraft:name_tag", 1), Map.of(), 100L, true);
		assertEquals(GiveFact.Method.USE_WITH, gave.get(0).method());
	}

	@Test
	void aRightClickThatDidNotUseUpTheItemGivesNothing() {
		// Creative mode, or an animal that was not hungry: the count never drops.
		GiveTracker tracker = new GiveTracker();
		tracker.used("minecraft:wheat", "minecraft:cow", COW, true, 0L);
		assertTrue(tracker.inventoryChanged(Map.of("minecraft:wheat", 5), Map.of("minecraft:wheat", 5), 100L, true)
				.isEmpty());
		assertTrue(tracker.inventoryChanged(Map.of("minecraft:wheat", 5), Map.of("minecraft:wheat", 4), 5_000L, true)
				.isEmpty(), "too late to be that use");
	}

	@Test
	void aLossOfAnotherItemIsNotCreditedToTheUse() {
		GiveTracker tracker = new GiveTracker();
		tracker.used("minecraft:wheat", "minecraft:cow", COW, true, 0L);
		assertTrue(tracker.inventoryChanged(Map.of("minecraft:dirt", 5), Map.of("minecraft:dirt", 4), 100L, true)
				.isEmpty());
	}

	@Test
	void aUseIsCreditedOnlyOnce() {
		GiveTracker tracker = new GiveTracker();
		tracker.used("minecraft:wheat", "minecraft:cow", COW, true, 0L);
		assertEquals(1, tracker.inventoryChanged(Map.of("minecraft:wheat", 5), Map.of("minecraft:wheat", 4), 50L, true)
				.size());
		assertTrue(tracker.inventoryChanged(Map.of("minecraft:wheat", 4), Map.of("minecraft:wheat", 3), 80L, true)
				.isEmpty());
	}

	@Test
	void aDroppedItemNextToACreatureIsGivenToIt() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bread", 3), Map.of("minecraft:bread", 2), 1_000L, true);
		assertTrue(tracker.hasDropCandidates(1_050L));

		GiveFact gave = tracker.itemSpawned(41, "minecraft:bread", 1, 1.2,
				List.of(new GiveTracker.Target("minecraft:pig", VILLAGER, 2.5)), 1_100L);

		assertEquals(new GiveFact("minecraft:bread", 1, "minecraft:pig", VILLAGER, GiveFact.Method.DROP_NEAR),
				gave);
		assertFalse(tracker.hasDropCandidates(1_100L), "the drop is used up");
	}

	@Test
	void theNearestCreatureWithinThreeBlocksWins() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bone", 1), Map.of(), 0L, true);
		GiveFact gave = tracker.itemSpawned(1, "minecraft:bone", 1, 1.0, List.of(
				new GiveTracker.Target("minecraft:villager", VILLAGER, 2.9),
				new GiveTracker.Target("minecraft:wolf", COW, 1.5)), 100L);
		assertEquals("minecraft:wolf", gave.targetType());
	}

	@Test
	void aDropWithNobodyWithinThreeBlocksIsNotAGive() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bread", 1), Map.of(), 0L, true);
		assertNull(tracker.itemSpawned(1, "minecraft:bread", 1, 1.0,
				List.of(new GiveTracker.Target("minecraft:villager", VILLAGER, 3.1)), 100L));
		assertFalse(tracker.hasDropCandidates(100L), "it was still a drop, so it is consumed");
	}

	@Test
	void itemEntitiesThatAreNotThePlayersDropAreIgnored() {
		GiveTracker tracker = new GiveTracker();
		List<GiveTracker.Target> near = List.of(new GiveTracker.Target("minecraft:cow", COW, 1.0));
		// Nothing was lost from the inventory: this is loot from a broken block or a dead mob.
		assertNull(tracker.itemSpawned(1, "minecraft:bread", 1, 1.0, near, 0L));

		tracker.inventoryChanged(Map.of("minecraft:bread", 1), Map.of(), 0L, true);
		assertNull(tracker.itemSpawned(2, "minecraft:leather", 1, 1.0, near, 10L), "another item");
		assertNull(tracker.itemSpawned(3, "minecraft:bread", 1, 9.0, near, 10L), "spawned far from the player");
		assertTrue(tracker.hasDropCandidates(10L));
	}

	@Test
	void aLateItemEntityNoLongerMatches() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bread", 1), Map.of(), 0L, true);
		List<GiveTracker.Target> near = List.of(new GiveTracker.Target("minecraft:cow", COW, 1.0));
		assertNull(tracker.itemSpawned(1, "minecraft:bread", 1, 1.0, near, GiveTracker.DROP_WINDOW_MS + 1));
	}

	@Test
	void oneItemEntityAnswersOneDropOnly() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bread", 2), Map.of("minecraft:bread", 1), 0L, true);
		tracker.inventoryChanged(Map.of("minecraft:bread", 1), Map.of(), 10L, true);
		List<GiveTracker.Target> near = List.of(new GiveTracker.Target("minecraft:cow", COW, 1.0));

		assertNotNull(tracker.itemSpawned(5, "minecraft:bread", 1, 1.0, near, 100L));
		assertNull(tracker.itemSpawned(5, "minecraft:bread", 1, 1.0, near, 110L), "same entity again");
		assertNotNull(tracker.itemSpawned(6, "minecraft:bread", 1, 1.0, near, 120L));
	}

	@Test
	void movingStacksAroundWithAScreenOpenIsNotADrop() {
		GiveTracker tracker = new GiveTracker();
		tracker.inventoryChanged(Map.of("minecraft:bread", 3), Map.of(), 0L, false);
		assertFalse(tracker.hasDropCandidates(10L));
	}

	@Test
	void anIdleTrackerAllocatesNoResults() {
		GiveTracker tracker = new GiveTracker();
		Map<String, Integer> inventory = Map.of("minecraft:dirt", 5);
		assertSame(tracker.inventoryChanged(inventory, inventory, 1L, true),
				tracker.inventoryChanged(inventory, inventory, 2L, true));
	}
}
