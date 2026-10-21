package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden voxel fixtures for every pattern: layers from the bottom up (blank line between layers),
 * one line per z row, one character per x column. '#' is the material, 's' another stone, 'T' a torch.
 * A shape counts as built when it includes the block that was just placed, so every material block
 * of a positive fixture is tried as the last one placed, in all four rotations.
 */
class PatternTest {
	private static final String MATERIAL = "minecraft:cobblestone";
	private static final Map<Character, String> LEGEND = Map.of('#', MATERIAL, 's', "minecraft:stone",
			'T', "minecraft:torch");

	private static World world(String fixture) {
		return World.parse(LEGEND, 10, 10, 10, fixture);
	}

	/** True when finishing the fixture with this block would complete the pattern. */
	private static boolean builtAt(World world, BlockPos pos, Pattern pattern, int size, String material) {
		PlacementFact fact = world.placed(pos);
		return !PatternMatcher.builtWith(fact.around(), pattern, size, material).isEmpty();
	}

	private static void assertBuilt(String name, String fixture, Pattern pattern, int size) {
		World world = world(fixture);
		for (int turn = 0; turn < 4; turn++) {
			List<BlockPos> cells = world.find(MATERIAL);
			assertFalse(cells.isEmpty(), name);
			for (BlockPos cell : cells) {
				assertTrue(builtAt(world, cell, pattern, size, MATERIAL),
						name + " turn " + turn + ": not built when " + cell + " was last");
			}
			world = world.rotated();
		}
	}

	private static void assertNotBuilt(String name, String fixture, Pattern pattern, int size) {
		World world = world(fixture);
		for (int turn = 0; turn < 4; turn++) {
			for (BlockPos cell : world.find(MATERIAL)) {
				assertFalse(builtAt(world, cell, pattern, size, MATERIAL),
						name + " turn " + turn + ": counted as built with " + cell + " last");
			}
			world = world.rotated();
		}
	}

	// ------------------------------------------------------------------ pillar

	@Test
	void pillarOfThree() {
		assertBuilt("pillar 3", "#\n\n#\n\n#", Pattern.PILLAR, 3);
		assertBuilt("pillar 4 holds a pillar of 3", "#\n\n#\n\n#\n\n#", Pattern.PILLAR, 3);
		assertBuilt("pillar 2", "#\n\n#", Pattern.PILLAR, 2);
		assertBuilt("pillar 4", "#\n\n#\n\n#\n\n#", Pattern.PILLAR, 4);
	}

	@Test
	void pillarNearMisses() {
		assertNotBuilt("too low", "#\n\n#", Pattern.PILLAR, 3);
		assertNotBuilt("gap", "#\n\n.\n\n#", Pattern.PILLAR, 3);
		assertNotBuilt("staircase", "#.\n\n.#\n\n#.", Pattern.PILLAR, 3);
		assertNotBuilt("other stone in the middle", "#\n\ns\n\n#", Pattern.PILLAR, 3);
		assertNotBuilt("lying down", "###", Pattern.PILLAR, 3);
		assertNotBuilt("three high is not four", "#\n\n#\n\n#", Pattern.PILLAR, 4);
		assertNotBuilt("single block", "#", Pattern.PILLAR, 2);
	}

	@Test
	void pillarOnlyCountsWhenTheNewBlockIsPartOfIt() {
		// a finished pillar next to a block that was just placed elsewhere
		World world = world("#.#\n\n#.#\n\n#.s");
		assertTrue(builtAt(world, new BlockPos(10, 12, 10), Pattern.PILLAR, 3, MATERIAL));
		assertFalse(builtAt(world, new BlockPos(12, 12, 10), Pattern.PILLAR, 3, MATERIAL),
				"the stone is not the material");
		World lone = world("#.#\n\n#.#\n\n#.#").put(11, 10, 10, MATERIAL);
		assertFalse(builtAt(lone, new BlockPos(11, 10, 10), Pattern.PILLAR, 3, MATERIAL),
				"a block beside a finished pillar did not build one");
	}

	// ------------------------------------------------------------------ pyramid

	private static final String PYRAMID = """
			###
			###
			###

			...
			.#.
			...""";

	@Test
	void pyramidOfBase3() {
		assertBuilt("pyramid", PYRAMID, Pattern.PYRAMID, 3);
	}

	@Test
	void pyramidNearMisses() {
		assertNotBuilt("no top", "###\n###\n###", Pattern.PYRAMID, 3);
		assertNotBuilt("top off centre", "###\n###\n###\n\n#..\n...\n...", Pattern.PYRAMID, 3);
		assertNotBuilt("missing a corner", "##.\n###\n###\n\n...\n.#.\n...", Pattern.PYRAMID, 3);
		assertNotBuilt("base only 2 wide", "##.\n##.\n##.\n\n...\n.#.\n...", Pattern.PYRAMID, 3);
		assertNotBuilt("floating top", "###\n###\n###\n\n...\n...\n...\n\n...\n.#.\n...", Pattern.PYRAMID, 3);
		assertNotBuilt("wrong stone in the base", "###\n#s#\n###\n\n...\n.#.\n...", Pattern.PYRAMID, 3);
		assertNotBuilt("a plus", ".#.\n###\n.#.\n\n...\n.#.\n...", Pattern.PYRAMID, 3);
	}

	// --------------------------------------------------------------------- ring

	private static final String RING = "###\n#.#\n###";

	@Test
	void ringOfEight() {
		assertBuilt("ring", RING, Pattern.RING, 3);
		assertBuilt("ring with a torch inside", "###\n#T#\n###", Pattern.RING, 3);
		assertBuilt("ring with stone inside", "###\n#s#\n###", Pattern.RING, 3);
	}

	@Test
	void ringNearMisses() {
		assertNotBuilt("one cell missing", "##.\n#.#\n###", Pattern.RING, 3);
		assertNotBuilt("filled", "###\n###\n###", Pattern.RING, 3);
		assertNotBuilt("a plus", ".#.\n#.#\n.#.", Pattern.RING, 3);
		assertNotBuilt("standing up", "###\n\n#.#\n\n###", Pattern.RING, 3);
		assertNotBuilt("two by three", "##\n#.\n##", Pattern.RING, 3);
		assertNotBuilt("wrong stone in the wall", "###\ns.#\n###", Pattern.RING, 3);
	}

	// ----------------------------------------------------------------------- row

	@Test
	void rowAlongEitherAxis() {
		assertBuilt("row along x", "###", Pattern.ROW, 3);
		assertBuilt("row along z", "#\n#\n#", Pattern.ROW, 3);
		assertBuilt("row of 4 holds a row of 3", "####", Pattern.ROW, 3);
		assertBuilt("row of 2", "##", Pattern.ROW, 2);
		assertBuilt("row of 4", "####", Pattern.ROW, 4);
	}

	@Test
	void rowNearMisses() {
		assertNotBuilt("gap", "#.#", Pattern.ROW, 3);
		assertNotBuilt("diagonal", "#..\n.#.\n..#", Pattern.ROW, 3);
		assertNotBuilt("an L", "##\n#.", Pattern.ROW, 3);
		assertNotBuilt("too short", "##", Pattern.ROW, 3);
		assertNotBuilt("other stone in the row", "#s#", Pattern.ROW, 3);
		assertNotBuilt("standing up", "#\n\n#\n\n#", Pattern.ROW, 3);
		assertNotBuilt("three is not four", "###", Pattern.ROW, 4);
	}

	// ------------------------------------------------------------- material families

	@Test
	void materialCanBeAFamilyOrATag() {
		World planks = World.parse(Map.of('#', "minecraft:oak_planks", 'b', "minecraft:birch_planks"), 10, 10, 10,
				"#b#");
		assertTrue(builtAt(planks, new BlockPos(11, 10, 10), Pattern.ROW, 3, "minecraft:oak_planks"),
				"planks are one family");
		assertTrue(builtAt(planks, new BlockPos(11, 10, 10), Pattern.ROW, 3, "#minecraft:planks"));
		assertFalse(builtAt(planks, new BlockPos(11, 10, 10), Pattern.ROW, 3, "minecraft:stone"));
	}

	// ----------------------------------------------------------------- anchor points

	@Test
	void anchorPoints() {
		assertEquals(List.of(new Pattern.Cell(0, 3, 0)), Pattern.PILLAR.top(3, 0));
		assertEquals(new Pattern.Cell(0, 0, 0), Pattern.PILLAR.centre(3, 0));
		assertTrue(Pattern.PILLAR.inside(3, 0).isEmpty());

		assertEquals(List.of(new Pattern.Cell(1, 2, 1)), Pattern.PYRAMID.top(3, 0), "above the apex");
		assertEquals(new Pattern.Cell(1, 0, 1), Pattern.PYRAMID.centre(3, 0));
		assertEquals(10, Pattern.PYRAMID.cells(3, 0).size());
		assertTrue(Pattern.PYRAMID.cells(3, 0).contains(new Pattern.Cell(1, 1, 1)));

		assertEquals(8, Pattern.RING.cells(3, 0).size());
		assertEquals(List.of(new Pattern.Cell(1, 0, 1)), Pattern.RING.inside(3, 0));
		assertEquals(new Pattern.Cell(1, 0, 1), Pattern.RING.centre(3, 0));
		assertEquals(8, Pattern.RING.top(3, 0).size());
		assertTrue(Pattern.RING.encloses());

		assertEquals(3, Pattern.ROW.top(3, 0).size());
		assertEquals(new Pattern.Cell(1, 0, 0), Pattern.ROW.centre(3, 0));
		assertEquals(new Pattern.Cell(0, 0, 1), Pattern.ROW.centre(3, 1));
		assertEquals(List.of(new Pattern.Cell(0, 0, 0), new Pattern.Cell(0, 0, 1), new Pattern.Cell(0, 0, 2)),
				Pattern.ROW.cells(3, 1));
	}

	@Test
	void anchorPointsAreMovedWithTheMatch() {
		World world = world(PYRAMID);
		PlacementFact fact = world.placed(new BlockPos(11, 11, 11));
		PatternMatcher.Match match = PatternMatcher.builtWith(fact.around(), Pattern.PYRAMID, 3, MATERIAL).get(0);
		assertEquals(new Pattern.Cell(0, 1, 0), match.top().get(0), "just above the block that was placed");
		assertEquals(new Pattern.Cell(0, -1, 0), match.centre());
	}

	@Test
	void sizeLimitsKeepThePatternInsideTheSevenCube() {
		assertTrue(Pattern.PILLAR.validSize(4));
		assertFalse(Pattern.PILLAR.validSize(5), "5 would not fit around the last block placed");
		assertFalse(Pattern.PILLAR.validSize(1));
		assertTrue(Pattern.PYRAMID.validSize(3));
		assertFalse(Pattern.PYRAMID.validSize(5));
		assertTrue(Pattern.ROW.validSize(2));
		assertFalse(Pattern.RING.validSize(4));
	}

	// -------------------------------------------------------------------- cost

	@Test
	void matchingReadsOnlyTheCapturedCube() {
		AtomicInteger lookups = new AtomicInteger();
		World world = world(PYRAMID);
		BlockNeighborhood around = BlockNeighborhood.capture(11, 11, 11, (x, y, z) -> {
			lookups.incrementAndGet();
			return world.at(x, y, z);
		});
		assertEquals(7 * 7 * 7, lookups.get(), "one cube per placement");
		int before = lookups.get();
		assertFalse(PatternMatcher.builtWith(around, Pattern.PYRAMID, 3, MATERIAL).isEmpty());
		assertTrue(PatternMatcher.find(around, Pattern.RING, 3, MATERIAL).isEmpty());
		assertEquals(before, lookups.get(), "matching never goes back to the world");
	}
}
