package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.ContainerDiff;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerDiffTest {
	private static Map<String, Integer> tally(Object... pairs) {
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	@Test
	void itemsMovedFromThePlayerIntoTheContainerArePuts() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally(), tally("minecraft:cobblestone", 64, "minecraft:apple", 3));

		List<ContainerDiff.Put> puts = diff.update(tally("minecraft:cobblestone", 20),
				tally("minecraft:cobblestone", 44, "minecraft:apple", 3));

		assertEquals(List.of(new ContainerDiff.Put("minecraft:cobblestone", 20)), puts);
	}

	@Test
	void aTickWithNoChangeAllocatesNothing() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally("minecraft:bread", 2), tally("minecraft:dirt", 1));
		List<ContainerDiff.Put> first = diff.update(tally("minecraft:bread", 2), tally("minecraft:dirt", 1));
		List<ContainerDiff.Put> second = diff.update(tally("minecraft:bread", 2), tally("minecraft:dirt", 1));
		assertTrue(first.isEmpty());
		assertSame(first, second);
	}

	@Test
	void itemsAppearingInTheContainerOnTheirOwnAreNotThePlayersDoing() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally(), tally("minecraft:dirt", 5));
		// A hopper fills the chest; the player's inventory is unchanged.
		assertTrue(diff.update(tally("minecraft:iron_ingot", 3), tally("minecraft:dirt", 5)).isEmpty());
		// The server's first content packet after opening: the chest fills, the player side is the same.
		assertTrue(diff.update(tally("minecraft:iron_ingot", 3, "minecraft:coal", 9), tally("minecraft:dirt", 5))
				.isEmpty());
	}

	@Test
	void takingSomethingOutAndPuttingItBackIsNotAPut() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally("minecraft:diamond", 5), tally());
		assertTrue(diff.update(tally("minecraft:diamond", 2), tally("minecraft:diamond", 3)).isEmpty());
		assertTrue(diff.update(tally("minecraft:diamond", 5), tally()).isEmpty(), "returned what was taken");

		// Only the part beyond what was taken out is new: 2 taken, then 5 put in.
		diff.clear();
		diff.open(tally("minecraft:diamond", 5), tally("minecraft:diamond", 4));
		diff.update(tally("minecraft:diamond", 3), tally("minecraft:diamond", 6));
		List<ContainerDiff.Put> puts = diff.update(tally("minecraft:diamond", 8), tally("minecraft:diamond", 1));
		assertEquals(List.of(new ContainerDiff.Put("minecraft:diamond", 3)), puts);
	}

	@Test
	void severalItemsInOneTickAreReportedSeparately() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally(), tally("minecraft:bread", 4, "minecraft:apple", 2));
		List<ContainerDiff.Put> puts = diff.update(tally("minecraft:bread", 4, "minecraft:apple", 1),
				tally("minecraft:apple", 1));
		assertEquals(2, puts.size());
		assertTrue(puts.contains(new ContainerDiff.Put("minecraft:bread", 4)));
		assertTrue(puts.contains(new ContainerDiff.Put("minecraft:apple", 1)));
	}

	@Test
	void aPutIsLimitedToWhatThePlayerSideActuallyLost() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally(), tally("minecraft:coal", 2));
		// 2 coal left the player, but 5 arrived (a hopper added 3).
		List<ContainerDiff.Put> puts = diff.update(tally("minecraft:coal", 5), tally());
		assertEquals(List.of(new ContainerDiff.Put("minecraft:coal", 2)), puts);
	}
}
