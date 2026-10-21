package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.DeathTracker;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathTrackerTest {
	/** A tiny world: entity id to (type, dying). Counts lookups to prove nothing else is scanned. */
	private static final class FakeWorld implements DeathTracker.World {
		final Map<Integer, DeathTracker.Subject> entities = new HashMap<>();
		final List<Integer> asked = new java.util.ArrayList<>();

		void put(int id, String type, boolean dying) {
			entities.put(id, new DeathTracker.Subject(type, dying));
		}

		@Override
		public DeathTracker.Subject find(int entityId) {
			asked.add(entityId);
			return entities.get(entityId);
		}
	}

	@Test
	void creditsAnAttackedEntityOnceWhenItDies() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(7, "minecraft:zombie", false);
		tracker.attacked(7, 1_000L);
		assertTrue(tracker.poll(1_100L, world).isEmpty());

		world.put(7, "minecraft:zombie", true);
		List<DeathTracker.Kill> kills = tracker.poll(1_200L, world);
		assertEquals(1, kills.size());
		assertEquals("minecraft:zombie", kills.get(0).type());
		assertTrue(tracker.poll(1_250L, world).isEmpty(), "still dying, already credited");
	}

	@Test
	void onlyAttackedEntitiesAreLookedUp() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(1, "minecraft:cow", true); // dies on its own, never attacked
		world.put(2, "minecraft:zombie", false);
		tracker.attacked(2, 1_000L);
		assertTrue(tracker.poll(1_050L, world).isEmpty());
		assertEquals(List.of(2), world.asked);
	}

	@Test
	void unattackedDeathsAreNotCredited() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(1, "minecraft:cow", true);
		assertTrue(tracker.poll(1_000L, world).isEmpty());
	}

	@Test
	void anAttackExpiresAfterTheWindow() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(3, "minecraft:skeleton", false);
		tracker.attacked(3, 1_000L);
		world.put(3, "minecraft:skeleton", true);
		assertTrue(tracker.poll(1_000L + DeathTracker.WINDOW_MS + 1, world).isEmpty());
	}

	@Test
	void aFreshHitKeepsTheWindowOpen() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(3, "minecraft:skeleton", false);
		tracker.attacked(3, 1_000L);
		tracker.attacked(3, 5_500L);
		world.put(3, "minecraft:skeleton", true);
		assertEquals(1, tracker.poll(8_000L, world).size());
	}

	@Test
	void anEntityNotLoadedIsSimplyKeptUntilTheWindowEnds() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		tracker.attacked(9, 1_000L);
		assertTrue(tracker.poll(1_100L, world).isEmpty());
		world.put(9, "minecraft:creeper", true);
		assertEquals(1, tracker.poll(1_200L, world).size());
	}

	@Test
	void aCreditedEntityStaysCreditedPastTheOldClearThreshold() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(0, "minecraft:ender_dragon", true);
		tracker.attacked(0, 1_000L);
		assertEquals(1, tracker.poll(1_050L, world).size());

		// 200 other kills used to wipe the whole "seen" set at 128 entries.
		for (int id = 1; id <= 200; id++) {
			world.put(id, "minecraft:zombie", true);
			tracker.attacked(id, 1_100L);
			assertEquals(1, tracker.poll(1_100L, world).size());
		}
		// The dragon is still in its death animation and is hit again.
		tracker.attacked(0, 1_200L);
		assertTrue(tracker.poll(1_250L, world).isEmpty(), "the dragon must not be counted twice");
	}

	@Test
	void theCreditedSetIsBoundedAndDropsTheOldestFirst() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		int total = DeathTracker.CAP + 10;
		for (int id = 0; id < total; id++) {
			world.put(id, "minecraft:zombie", true);
			tracker.attacked(id, 1_000L);
			assertEquals(1, tracker.poll(1_000L, world).size());
		}
		// The newest entries are still remembered...
		tracker.attacked(total - 1, 1_100L);
		assertTrue(tracker.poll(1_100L, world).isEmpty());
		// ...the very oldest was evicted (ids are not reused in a real world, so this is the bound).
		tracker.attacked(0, 1_100L);
		assertFalse(tracker.poll(1_100L, world).isEmpty());
	}

	@Test
	void theAttackedMapIsBounded() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		for (int id = 0; id < DeathTracker.CAP * 2; id++) {
			tracker.attacked(id, 1_000L);
		}
		tracker.poll(1_000L, world);
		assertEquals(DeathTracker.CAP, world.asked.size());
	}

	@Test
	void clearForgetsEverythingForANewWorld() {
		DeathTracker tracker = new DeathTracker();
		FakeWorld world = new FakeWorld();
		world.put(4, "minecraft:zombie", true);
		tracker.attacked(4, 1_000L);
		tracker.rangedWeaponUsed(1_000L);
		tracker.clear();
		assertTrue(tracker.poll(1_050L, world).isEmpty());
		assertFalse(tracker.rangedActive(1_050L));
		// A new world may reuse entity id 4: it can be credited again after the reset.
		tracker.attacked(4, 2_000L);
		assertEquals(1, tracker.poll(2_000L, world).size());
	}

	@Test
	void rangedWindowOpensOnUseAndCloses() {
		DeathTracker tracker = new DeathTracker();
		assertFalse(tracker.rangedActive(1_000L));
		tracker.rangedWeaponUsed(1_000L);
		assertTrue(tracker.rangedActive(1_000L + DeathTracker.RANGED_WINDOW_MS - 1));
		assertFalse(tracker.rangedActive(1_000L + DeathTracker.RANGED_WINDOW_MS + 1));
	}
}
