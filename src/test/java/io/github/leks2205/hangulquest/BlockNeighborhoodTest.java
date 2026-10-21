package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.PlacementDiff;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockNeighborhoodTest {
	/** A fake world keyed by block position; counts every lookup. */
	private static final class FakeBlocks implements BlockNeighborhood.Lookup {
		final Map<BlockPos, String> blocks = new HashMap<>();
		int lookups;

		void put(int x, int y, int z, String id) {
			blocks.put(new BlockPos(x, y, z), id);
		}

		@Override
		public String blockAt(int x, int y, int z) {
			lookups++;
			return blocks.get(new BlockPos(x, y, z));
		}
	}

	@Test
	void capturesTheCubeWithOffsetsRelativeToTheCentre() {
		FakeBlocks world = new FakeBlocks();
		world.put(10, 64, 10, "minecraft:torch");
		world.put(10, 63, 10, "minecraft:stone");
		world.put(13, 64, 7, "minecraft:oak_log");
		world.put(7, 67, 13, "minecraft:dirt");

		BlockNeighborhood around = BlockNeighborhood.capture(10, 64, 10, world);

		assertEquals("minecraft:torch", around.at(0, 0, 0));
		assertEquals("minecraft:stone", around.at(0, -1, 0));
		assertEquals("minecraft:oak_log", around.at(3, 0, -3));
		assertEquals("minecraft:dirt", around.at(-3, 3, 3));
		assertEquals("minecraft:air", around.at(1, 1, 1));
		assertTrue(around.isAir(2, 0, 0));
		assertFalse(around.isAir(0, -1, 0));
	}

	@Test
	void readsExactlySevenCubedBlocksAndNothingMore() {
		FakeBlocks world = new FakeBlocks();
		BlockNeighborhood.capture(0, 0, 0, world);
		assertEquals(7 * 7 * 7, world.lookups);
	}

	@Test
	void offsetsOutsideTheCubeAreUnknown() {
		BlockNeighborhood around = BlockNeighborhood.capture(0, 0, 0, new FakeBlocks());
		assertNull(around.at(4, 0, 0));
		assertNull(around.at(0, -4, 0));
		assertNull(around.at(0, 0, 9));
	}

	@Test
	void everyKindOfAirIsOnePaletteEntryAtIndexZero() {
		FakeBlocks world = new FakeBlocks();
		world.put(1, 0, 0, "minecraft:cave_air");
		world.put(0, 1, 0, "minecraft:void_air");
		world.put(0, 0, 1, "minecraft:stone");
		world.put(0, 0, 2, "minecraft:stone");
		world.put(0, 0, 3, "minecraft:glass");

		BlockNeighborhood around = BlockNeighborhood.capture(0, 0, 0, world);

		assertEquals(List.of("minecraft:air", "minecraft:stone", "minecraft:glass"), around.palette());
		assertEquals("minecraft:air", around.at(1, 0, 0));
		assertEquals(2, around.count("minecraft:stone"));
		assertEquals(0, around.count("minecraft:diamond_block"));
	}

	@Test
	void aPlacedDoorIsOnePlacementNotTwo() {
		// A door fills two stacked positions; the one next to the clicked face is the placement.
		BlockPos clicked = new BlockPos(5, 64, 5);
		BlockPos face = new BlockPos(5, 65, 5);
		Map<BlockPos, String> before = new HashMap<>();
		before.put(new BlockPos(5, 65, 5), "minecraft:air");
		before.put(new BlockPos(5, 66, 5), "minecraft:air");
		before.put(clicked, "minecraft:stone");
		Map<BlockPos, String> after = new HashMap<>(before);
		after.put(new BlockPos(5, 65, 5), "minecraft:oak_door");
		after.put(new BlockPos(5, 66, 5), "minecraft:oak_door");

		List<PlacementDiff.Placed> changed = PlacementDiff.find(before, pos -> after.get(pos));
		List<PlacementDiff.Placed> primary = PlacementDiff.primary(changed, face, clicked);

		assertEquals(2, changed.size());
		assertEquals(1, primary.size());
		assertEquals(face, primary.get(0).pos());
	}

	@Test
	void unchangedAndAirPositionsAreNotPlacements() {
		Map<BlockPos, String> before = new HashMap<>();
		before.put(new BlockPos(0, 0, 0), "minecraft:stone");
		before.put(new BlockPos(1, 0, 0), "minecraft:dirt");
		before.put(new BlockPos(2, 0, 0), "minecraft:air");
		Map<BlockPos, String> after = new HashMap<>(before);
		after.put(new BlockPos(1, 0, 0), "minecraft:cave_air"); // broken, not placed
		after.put(new BlockPos(2, 0, 0), "minecraft:torch");

		List<PlacementDiff.Placed> changed = PlacementDiff.find(before, after::get);

		assertEquals(1, changed.size());
		assertEquals("minecraft:torch", changed.get(0).block());
	}

	@Test
	void replacingABlockWithAnotherCountsButTheSameBlockDoesNot() {
		Map<BlockPos, String> before = Map.of(new BlockPos(0, 0, 0), "minecraft:grass_block",
				new BlockPos(1, 0, 0), "minecraft:stone");
		Map<BlockPos, String> after = Map.of(new BlockPos(0, 0, 0), "minecraft:oak_planks",
				new BlockPos(1, 0, 0), "minecraft:stone");
		List<PlacementDiff.Placed> changed = PlacementDiff.find(before, after::get);
		assertEquals(List.of(new PlacementDiff.Placed(new BlockPos(0, 0, 0), "minecraft:oak_planks")), changed);
	}

	@Test
	void differentBlocksPlacedTogetherStaySeparate() {
		BlockPos clicked = new BlockPos(0, 0, 0);
		List<PlacementDiff.Placed> changed = List.of(
				new PlacementDiff.Placed(new BlockPos(0, 1, 0), "minecraft:oak_door"),
				new PlacementDiff.Placed(new BlockPos(0, 2, 0), "minecraft:oak_door"),
				new PlacementDiff.Placed(new BlockPos(1, 1, 0), "minecraft:torch"));
		List<PlacementDiff.Placed> primary = PlacementDiff.primary(changed, new BlockPos(0, 1, 0), clicked);
		assertEquals(2, primary.size());
	}
}
