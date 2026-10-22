package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.ResultTakeTracker;
import io.github.leks2205.hangulquest.sense.StackView;
import io.github.leks2205.hangulquest.sense.Station;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTakeTrackerTest {
	private static final long SETTLE = ResultTakeTracker.SETTLE_MS;

	private static Map<String, Integer> tally(Object... pairs) {
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	private static StackView stack(String item, int count) {
		return new StackView(item, count);
	}

	@Test
	void aPlainClickCountsTheResultStack() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		boolean taken = tracker.click(Station.CRAFTING_TABLE, stack("minecraft:stick", 4), StackView.EMPTY,
				tally(), tally("minecraft:stick", 4), false, null, 1_000L);
		assertTrue(taken);
		assertTrue(tracker.poll(tally("minecraft:stick", 4), 1_100L, false).isEmpty());

		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:stick", 4), 1_000L + SETTLE, false);
		assertEquals(1, done.size());
		assertEquals("minecraft:stick", done.get(0).item());
		assertEquals(4, done.get(0).count());
		assertEquals(Station.CRAFTING_TABLE, done.get(0).station());
		assertTrue(tracker.poll(tally("minecraft:stick", 4), 5_000L, false).isEmpty(), "reported once");
	}

	@Test
	void aShiftClickCountsEverythingTheServerLaterDelivers() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		// The client predicts one craft of 4 planks right away.
		assertTrue(tracker.click(Station.INVENTORY_2X2, stack("minecraft:oak_planks", 4), StackView.EMPTY,
				tally("minecraft:oak_log", 6), tally("minecraft:oak_log", 6, "minecraft:oak_planks", 4),
				true, null, 1_000L));
		// ... then the server answers with all six logs crafted.
		assertTrue(tracker.poll(tally("minecraft:oak_planks", 24), 1_150L, false).isEmpty());
		assertTrue(tracker.poll(tally("minecraft:oak_planks", 24), 1_300L, false).isEmpty(),
				"the inventory changed at 1150, so it is not still yet");

		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:oak_planks", 24), 1_150L + SETTLE, false);
		assertEquals(1, done.size());
		assertEquals(24, done.get(0).count());
	}

	@Test
	void aClickThatDidNotTouchTheResultSlotIsNotATake() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		// Result slot unchanged: the click was somewhere else, even though the inventory changed.
		assertFalse(tracker.click(Station.CRAFTING_TABLE, stack("minecraft:stick", 4), stack("minecraft:stick", 4),
				tally(), tally("minecraft:stick", 4), false, null, 1_000L));
		// Result slot was empty before.
		assertFalse(tracker.click(Station.CRAFTING_TABLE, StackView.EMPTY, StackView.EMPTY,
				tally(), tally("minecraft:stick", 4), false, null, 1_000L));
		assertFalse(tracker.hasPending());
	}

	@Test
	void aTakeThatGaveThePlayerNothingIsDropped() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		// Thrown out with Q: the slot emptied but nothing arrived in the inventory or on the cursor.
		assertFalse(tracker.click(Station.CRAFTING_TABLE, stack("minecraft:stick", 4), StackView.EMPTY,
				tally(), tally(), false, null, 1_000L));
	}

	@Test
	void aPlainClickIsCappedAtTheResultSizeEvenIfMoreArrives() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		tracker.click(Station.FURNACE, stack("minecraft:iron_ingot", 3), StackView.EMPTY,
				tally(), tally("minecraft:iron_ingot", 3), false, null, 0L);
		// Picking up an unrelated iron ingot from the floor in the same moment.
		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:iron_ingot", 9), SETTLE + 1, true);
		assertEquals(3, done.get(0).count());
	}

	@Test
	void closingTheScreenFinishesOpenTakesImmediately() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		tracker.click(Station.SMOKER, stack("minecraft:cooked_beef", 2), StackView.EMPTY,
				tally(), tally("minecraft:cooked_beef", 2), false, null, 0L);
		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:cooked_beef", 2), 10L, true);
		assertEquals(1, done.size());
		assertEquals(Station.SMOKER, done.get(0).station());
		assertFalse(tracker.hasPending());
	}

	@Test
	void aTakeNeverStaysOpenForever() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		tracker.click(Station.LOOM, stack("minecraft:white_banner", 1), StackView.EMPTY,
				tally(), tally("minecraft:white_banner", 1), false, null, 0L);
		// The inventory keeps changing (something else is going on), so it never goes still.
		for (long t = 100; t < ResultTakeTracker.MAX_MS; t += 100) {
			assertTrue(tracker.poll(tally("minecraft:white_banner", 1, "minecraft:dirt", (int) t), t, false).isEmpty());
		}
		assertEquals(1, tracker.poll(tally("minecraft:white_banner", 1, "minecraft:dirt", 1), ResultTakeTracker.MAX_MS,
				false).size());
	}

	@Test
	void aSecondTakeClosesTheFirstAtTheStateItStartedFrom() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		tracker.click(Station.CRAFTING_TABLE, stack("minecraft:stick", 4), StackView.EMPTY,
				tally(), tally("minecraft:stick", 4), true, null, 0L);
		// 50 ms later the server has delivered 8 sticks, and the player clicks again for 4 more.
		tracker.click(Station.CRAFTING_TABLE, stack("minecraft:stick", 4), StackView.EMPTY,
				tally("minecraft:stick", 8), tally("minecraft:stick", 12), true, null, 50L);

		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:stick", 12), 50L + SETTLE, false);
		assertEquals(2, done.size());
		assertEquals(8, done.get(0).count());
		assertEquals(4, done.get(1).count());
	}

	@Test
	void aTradeReportsWhatWasPaid() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		// Tallies include the payment slots: 12 emeralds' worth of paper left the player's side.
		assertTrue(tracker.click(Station.MERCHANT, stack("minecraft:emerald", 1), StackView.EMPTY,
				tally("minecraft:paper", 24), tally("minecraft:paper", 12, "minecraft:emerald", 1),
				false, "minecraft:paper", 0L));
		List<ResultTakeTracker.Taken> done = tracker.poll(tally("minecraft:paper", 12, "minecraft:emerald", 1),
				SETTLE, false);
		assertEquals("minecraft:emerald", done.get(0).item());
		assertEquals(1, done.get(0).count());
		assertEquals("minecraft:paper", done.get(0).paidItem());
		assertEquals(12, done.get(0).paidCount());
	}

	@Test
	void anIdleTrackerHandsOutTheSameEmptyListEveryTick() {
		ResultTakeTracker tracker = new ResultTakeTracker();
		Map<String, Integer> inventory = tally("minecraft:dirt", 5);
		assertSame(tracker.poll(inventory, 1L, false), tracker.poll(inventory, 2L, false));
	}
}
