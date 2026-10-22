package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.PlacementFact;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every relation, with the cases where it holds and the near cases where it does not. */
class RelationTest {
	private static final String TORCH = "minecraft:torch";
	private static final String CHEST = "minecraft:chest";
	private static final String DOOR = "minecraft:spruce_door";
	private static final String FENCE = "minecraft:birch_fence";
	private static final String COBBLE = "minecraft:cobblestone";
	private static final BlockPos P = new BlockPos(10, 10, 10);

	private static final Anchor CHESTS = new Anchor.Block("minecraft:chest");
	private static final Anchor DOORS = new Anchor.Block("#minecraft:doors");
	private static final Anchor FENCES = new Anchor.Block("minecraft:oak_fence");
	private static final Anchor TORCHES = new Anchor.Block("minecraft:torch");

	/** A torch at P with the given blocks around it; the player stands right at P unless said otherwise. */
	private static PlacementFact torch(Object... blocks) {
		return torchSeenFrom(new Vec3(10.5, 10, 10.5), blocks);
	}

	private static PlacementFact torchSeenFrom(Vec3 player, Object... blocks) {
		World world = new World().put(10, 10, 10, TORCH);
		for (int i = 0; i < blocks.length; i += 2) {
			BlockPos at = (BlockPos) blocks[i];
			world.put(at.getX(), at.getY(), at.getZ(), (String) blocks[i + 1]);
		}
		return world.placed(P, false, player);
	}

	private static BlockPos at(int dx, int dy, int dz) {
		return P.offset(dx, dy, dz);
	}

	private static void assertHolds(Relation relation, Anchor anchor, PlacementFact fact) {
		assertTrue(Relations.holds(relation, anchor, fact), relation + " should hold, holding " + Relations.holding(anchor, fact));
	}

	private static void assertNotHolds(Relation relation, Anchor anchor, PlacementFact fact) {
		assertFalse(Relations.holds(relation, anchor, fact), relation + " should not hold, holding " + Relations.holding(anchor, fact));
	}

	// ----------------------------------------------------------------- simple ones

	@Test
	void onTopOf() {
		assertHolds(Relation.ON_TOP_OF, CHESTS, torch(at(0, -1, 0), CHEST));
		assertNotHolds(Relation.ON_TOP_OF, CHESTS, torch(at(0, -2, 0), CHEST));
		assertNotHolds(Relation.ON_TOP_OF, CHESTS, torch(at(1, -1, 0), CHEST));
		assertNotHolds(Relation.ON_TOP_OF, CHESTS, torch(at(0, 1, 0), CHEST));
		assertNotHolds(Relation.ON_TOP_OF, CHESTS, torch(at(0, -1, 0), COBBLE));
	}

	@Test
	void under() {
		assertHolds(Relation.UNDER, CHESTS, torch(at(0, 1, 0), CHEST));
		assertNotHolds(Relation.UNDER, CHESTS, torch(at(0, 2, 0), CHEST));
		assertNotHolds(Relation.UNDER, CHESTS, torch(at(0, -1, 0), CHEST));
		assertNotHolds(Relation.UNDER, CHESTS, torch(at(1, 1, 0), CHEST));
	}

	@Test
	void nextTo() {
		for (BlockPos beside : new BlockPos[]{at(1, 0, 0), at(-1, 0, 0), at(0, 0, 1), at(0, 0, -1)}) {
			assertHolds(Relation.NEXT_TO, CHESTS, torch(beside, CHEST));
		}
		assertNotHolds(Relation.NEXT_TO, CHESTS, torch(at(1, 0, 1), CHEST));
		assertNotHolds(Relation.NEXT_TO, CHESTS, torch(at(2, 0, 0), CHEST));
		assertNotHolds(Relation.NEXT_TO, CHESTS, torch(at(1, 1, 0), CHEST));
		assertNotHolds(Relation.NEXT_TO, CHESTS, torch(at(0, -1, 0), CHEST));
	}

	@Test
	void nextToAFamilyAndATag() {
		assertHolds(Relation.NEXT_TO, DOORS, torch(at(1, 0, 0), DOOR));
		assertHolds(Relation.NEXT_TO, new Anchor.Block("minecraft:oak_door"), torch(at(1, 0, 0), DOOR));
		assertNotHolds(Relation.NEXT_TO, DOORS, torch(at(1, 0, 0), "minecraft:iron_door"));
		assertNotHolds(Relation.NEXT_TO, DOORS, torch(at(1, 0, 0), "minecraft:oak_trapdoor"));
	}

	@Test
	void near() {
		assertHolds(Relation.NEAR, CHESTS, torch(at(3, 3, -3), CHEST));
		assertHolds(Relation.NEAR, CHESTS, torch(at(1, 0, 0), CHEST));
		assertNotHolds(Relation.NEAR, CHESTS, torch(at(4, 0, 0), CHEST));
		assertNotHolds(Relation.NEAR, CHESTS, torch());
		assertNotHolds(Relation.NEAR, TORCHES, torch());
	}

	@Test
	void between() {
		assertHolds(Relation.BETWEEN, TORCHES, torch(at(-1, 0, 0), TORCH, at(2, 0, 0), TORCH));
		assertHolds(Relation.BETWEEN, TORCHES, torch(at(0, 0, -3), TORCH, at(0, 0, 1), TORCH));
		assertNotHolds(Relation.BETWEEN, TORCHES, torch(at(-1, 0, 0), TORCH));
		assertNotHolds(Relation.BETWEEN, TORCHES, torch(at(-1, 0, 0), TORCH, at(0, 0, 2), TORCH),
				"two torches at a right angle do not enclose it");
		assertNotHolds(Relation.BETWEEN, TORCHES, torch(at(-1, 0, 0), TORCH, at(2, 1, 0), TORCH));
		assertNotHolds(Relation.BETWEEN, TORCHES, torch(at(-1, 0, 0), TORCH, at(4, 0, 0), TORCH),
				"4 away is out of the cube");
	}

	private static void assertNotHolds(Relation relation, Anchor anchor, PlacementFact fact, String why) {
		assertFalse(Relations.holds(relation, anchor, fact), why);
	}

	// ----------------------------------------------------------------- facing ones

	@Test
	void inFrontOfAndBehindFollowTheSideThePlayerIsOn() {
		// chest east of the torch; the player stands west of both: the torch is in front of the chest
		PlacementFact fromWest = torchSeenFrom(new Vec3(5.5, 10, 10.5), at(1, 0, 0), CHEST);
		assertHolds(Relation.IN_FRONT_OF, CHESTS, fromWest);
		assertNotHolds(Relation.BEHIND, CHESTS, fromWest);
		// the player stands east of the chest: the torch is behind it
		PlacementFact fromEast = torchSeenFrom(new Vec3(15.5, 10, 10.5), at(1, 0, 0), CHEST);
		assertHolds(Relation.BEHIND, CHESTS, fromEast);
		assertNotHolds(Relation.IN_FRONT_OF, CHESTS, fromEast);
		// the chest two blocks away still counts, three do not
		assertHolds(Relation.IN_FRONT_OF, CHESTS, torchSeenFrom(new Vec3(5.5, 10, 10.5), at(2, 0, 0), CHEST));
		assertNotHolds(Relation.IN_FRONT_OF, CHESTS, torchSeenFrom(new Vec3(5.5, 10, 10.5), at(3, 0, 0), CHEST));
		// the north-south axis works the same way
		PlacementFact fromNorth = torchSeenFrom(new Vec3(10.5, 10, 4.5), at(0, 0, 1), CHEST);
		assertHolds(Relation.IN_FRONT_OF, CHESTS, fromNorth);
		// a torch beside the chest, not on its axis, is neither
		PlacementFact aside = torchSeenFrom(new Vec3(10.5, 10, 4.5), at(1, 0, 0), CHEST);
		assertNotHolds(Relation.IN_FRONT_OF, CHESTS, aside);
		assertNotHolds(Relation.BEHIND, CHESTS, aside);
	}

	@Test
	void anchorsWithoutAFrontNeverGetOne() {
		PlacementFact fact = torchSeenFrom(new Vec3(5.5, 10, 10.5), at(1, 0, 0), COBBLE);
		Anchor cobble = new Anchor.Block(COBBLE);
		assertNotHolds(Relation.IN_FRONT_OF, cobble, fact);
		assertNotHolds(Relation.BEHIND, cobble, fact);
		assertFalse(Relations.supported(Relation.IN_FRONT_OF, cobble));
		assertFalse(Relations.supported(Relation.BEHIND, cobble));
	}

	@Test
	void whichAnchorsHaveAFront() {
		for (String front : new String[]{"minecraft:oak_door", "#minecraft:doors", "minecraft:chest", "#minecraft:chests",
				"minecraft:furnace", "minecraft:red_bed", "#minecraft:beds", "minecraft:smoker", "minecraft:iron_door", "minecraft:crafting_table", "minecraft:oak_stairs",
				"minecraft:barrel", "minecraft:oak_sign", "minecraft:white_banner", "minecraft:carved_pumpkin"}) {
			assertTrue(BlockSpec.hasFacing(front), front);
			assertTrue(Relations.supported(Relation.BEHIND, new Anchor.Block(front)), front);
		}
		for (String plain : new String[]{"minecraft:stone", "minecraft:torch", "minecraft:oak_fence", "#minecraft:planks",
				"minecraft:cobblestone_wall"}) {
			assertFalse(BlockSpec.hasFacing(plain), plain);
			assertFalse(Relations.supported(Relation.IN_FRONT_OF, new Anchor.Block(plain)), plain);
		}
	}

	// ------------------------------------------------------------- inside / outside

	@Test
	void insideMeansWalledInOnAllSides() {
		PlacementFact penned = torch(at(2, 0, 0), FENCE, at(-1, 0, 0), FENCE, at(0, 0, 1), FENCE, at(0, 0, -3), FENCE);
		assertHolds(Relation.INSIDE, FENCES, penned);
		assertNotHolds(Relation.OUTSIDE, FENCES, penned);
		PlacementFact open = torch(at(2, 0, 0), FENCE, at(-1, 0, 0), FENCE, at(0, 0, 1), FENCE);
		assertNotHolds(Relation.INSIDE, FENCES, open);
		assertHolds(Relation.OUTSIDE, FENCES, open);
		assertNotHolds(Relation.INSIDE, FENCES, torch());
		assertNotHolds(Relation.OUTSIDE, FENCES, torch());
	}

	@Test
	void outsideNeedsTheWallToBeNearby() {
		assertHolds(Relation.OUTSIDE, FENCES, torch(at(3, 0, 0), FENCE));
		assertNotHolds(Relation.OUTSIDE, FENCES, torch(at(0, 3, 0), FENCE), "three above is not a wall of the pen");
		assertNotHolds(Relation.INSIDE, FENCES, torch(at(3, 0, 0), FENCE));
	}

	@Test
	void onlyWallsMakeSomethingInsideOrOutside() {
		assertFalse(Relations.supported(Relation.INSIDE, CHESTS));
		assertFalse(Relations.supported(Relation.OUTSIDE, DOORS));
		assertTrue(Relations.supported(Relation.INSIDE, FENCES));
		assertTrue(Relations.supported(Relation.INSIDE, new Anchor.Block("#minecraft:planks")));
		assertTrue(Relations.supported(Relation.OUTSIDE, new Anchor.Block("minecraft:stone_bricks")));
		assertTrue(Relations.supported(Relation.INSIDE, new Anchor.Block("minecraft:cobblestone")));
	}

	// ----------------------------------------------------------------- structures

	private static final Anchor PILLAR = new Anchor.Built(Pattern.PILLAR, COBBLE);
	private static final Anchor PYRAMID = new Anchor.Built(Pattern.PYRAMID, COBBLE);
	private static final Anchor RING = new Anchor.Built(Pattern.RING, COBBLE);
	private static final Anchor ROW = new Anchor.Built(Pattern.ROW, COBBLE);

	/** A torch at P and a structure laid out so that {@code cell} of it is at {@code (dx, dy, dz)} from the torch. */
	private static PlacementFact torchBeside(Pattern pattern, Pattern.Cell cell, int dx, int dy, int dz) {
		World world = new World().put(10, 10, 10, TORCH);
		for (Pattern.Cell c : pattern.cells(pattern.defaultSize(), 0)) {
			world.put(10 + dx + c.x() - cell.x(), 10 + dy + c.y() - cell.y(), 10 + dz + c.z() - cell.z(), COBBLE);
		}
		return world.placed(P);
	}

	@Test
	void onTopOfAStructure() {
		Pattern.Cell top = Pattern.PILLAR.top(3, 0).get(0);
		assertHolds(Relation.ON_TOP_OF, PILLAR, torchBeside(Pattern.PILLAR, top, 0, 0, 0));
		assertNotHolds(Relation.ON_TOP_OF, PILLAR, torchBeside(Pattern.PILLAR, top, 1, 0, 0));
		assertNotHolds(Relation.ON_TOP_OF, PILLAR, torchBeside(Pattern.PILLAR, top, 0, -1, 0), "one block too low");

		Pattern.Cell apex = Pattern.PYRAMID.top(3, 0).get(0);
		assertHolds(Relation.ON_TOP_OF, PYRAMID, torchBeside(Pattern.PYRAMID, apex, 0, 0, 0));
		// on the base layer next to the apex is on the steps, not on the top
		assertNotHolds(Relation.ON_TOP_OF, PYRAMID, torchBeside(Pattern.PYRAMID, new Pattern.Cell(0, 1, 0), 0, 0, 0));
		assertHolds(Relation.ON_TOP_OF, RING, torchBeside(Pattern.RING, new Pattern.Cell(0, 1, 0), 0, 0, 0));
		assertHolds(Relation.ON_TOP_OF, ROW, torchBeside(Pattern.ROW, new Pattern.Cell(1, 1, 0), 0, 0, 0));
	}

	@Test
	void underNextToAndNearAStructure() {
		assertHolds(Relation.UNDER, PILLAR, torchBeside(Pattern.PILLAR, new Pattern.Cell(0, 0, 0), 0, 1, 0));
		assertNotHolds(Relation.UNDER, PILLAR, torchBeside(Pattern.PILLAR, new Pattern.Cell(0, 0, 0), 0, 2, 0));
		assertHolds(Relation.NEXT_TO, PILLAR, torchBeside(Pattern.PILLAR, new Pattern.Cell(0, 1, 0), 1, 0, 0));
		assertNotHolds(Relation.NEXT_TO, PILLAR, torchBeside(Pattern.PILLAR, new Pattern.Cell(0, 1, 0), 2, 0, 0));
		assertHolds(Relation.NEAR, PILLAR, torchBeside(Pattern.PILLAR, new Pattern.Cell(0, 1, 0), 2, 0, 0));
		assertNotHolds(Relation.NEAR, PILLAR, torch());
		assertNotHolds(Relation.NEXT_TO, PILLAR, torch());
	}

	@Test
	void insideAndOutsideARing() {
		Pattern.Cell centre = Pattern.RING.inside(3, 0).get(0);
		PlacementFact inside = torchBeside(Pattern.RING, centre, 0, 0, 0);
		assertHolds(Relation.INSIDE, RING, inside);
		assertNotHolds(Relation.OUTSIDE, RING, inside);
		assertNotHolds(Relation.NEXT_TO, RING, inside, "inside the ring is not 'next to' it");
		PlacementFact outside = torchBeside(Pattern.RING, new Pattern.Cell(2, 0, 1), -1, 0, 0);
		assertHolds(Relation.OUTSIDE, RING, outside);
		assertHolds(Relation.NEXT_TO, RING, outside);
		assertNotHolds(Relation.INSIDE, RING, outside);
	}

	@Test
	void aTorchThatIsAPartOfTheStructureIsNotNextToIt() {
		World world = new World();
		for (Pattern.Cell c : Pattern.ROW.cells(3, 0)) {
			world.put(10 + c.x(), 10, 10, TORCH);
		}
		PlacementFact fact = world.placed(P);
		assertEquals(EnumSet.noneOf(Relation.class), Relations.holding(new Anchor.Built(Pattern.ROW, TORCH), fact));
	}

	@Test
	void structureRelationsOnlyWhereTheyMakeSense() {
		assertFalse(Relations.supported(Relation.BETWEEN, PILLAR));
		assertFalse(Relations.supported(Relation.IN_FRONT_OF, PILLAR));
		assertFalse(Relations.supported(Relation.BEHIND, RING));
		assertFalse(Relations.supported(Relation.INSIDE, PYRAMID));
		assertFalse(Relations.supported(Relation.OUTSIDE, PILLAR));
		assertTrue(Relations.supported(Relation.INSIDE, RING));
		assertTrue(Relations.supported(Relation.OUTSIDE, RING));
		for (Relation relation : new Relation[]{Relation.ON_TOP_OF, Relation.UNDER, Relation.NEXT_TO, Relation.NEAR}) {
			assertTrue(Relations.supported(relation, PYRAMID), relation.name());
		}
		assertTrue(Relations.supported(Relation.BETWEEN, TORCHES));
	}

	// -------------------------------------------------------------------- wrong relation

	@Test
	void aWrongRelationIsRecognised() {
		// under the chest instead of on top of it
		EnumSet<Relation> held = Relations.holding(CHESTS, torch(at(0, 1, 0), CHEST));
		assertEquals(Relation.UNDER, Relations.wrongRelation(Relation.ON_TOP_OF, held));
		assertNull(Relations.wrongRelation(Relation.UNDER, held), "the right one is not wrong");
		// merely near is not a wrong relation, and neither is nothing
		assertNull(Relations.wrongRelation(Relation.ON_TOP_OF, Relations.holding(CHESTS, torch(at(3, 0, 0), CHEST))));
		assertNull(Relations.wrongRelation(Relation.ON_TOP_OF, Relations.holding(CHESTS, torch())));
		// next to a fence is a wrong answer for 'inside'; being outside it is not worth a remark
		held = Relations.holding(FENCES, torch(at(1, 0, 0), FENCE));
		assertEquals(Relation.NEXT_TO, Relations.wrongRelation(Relation.INSIDE, held));
	}

	// ------------------------------------------------------------- walls, plus, stairs

	private static final Anchor WALL = new Anchor.Built(Pattern.WALL, COBBLE);
	private static final Anchor CROSS = new Anchor.Built(Pattern.CROSS, COBBLE);
	private static final Anchor STAIRS = new Anchor.Built(Pattern.STAIRS, COBBLE);

	@Test
	void relationsToAWall() {
		for (Pattern.Cell top : Pattern.WALL.top(3, 0)) {
			assertHolds(Relation.ON_TOP_OF, WALL, torchBeside(Pattern.WALL, top, 0, 0, 0));
		}
		assertNotHolds(Relation.ON_TOP_OF, WALL, torchBeside(Pattern.WALL, new Pattern.Cell(1, 3, 0), 0, 0, 0),
				"a block higher than the top is in the air");
		assertHolds(Relation.NEXT_TO, WALL, torchBeside(Pattern.WALL, new Pattern.Cell(-1, 0, 0), 0, 0, 0));
		// the wall is one block thick: its other side counts too
		assertHolds(Relation.NEXT_TO, WALL, torchBeside(Pattern.WALL, new Pattern.Cell(1, 1, 1), 0, 0, 0));
		assertHolds(Relation.UNDER, WALL, torchBeside(Pattern.WALL, new Pattern.Cell(0, 0, 0), 0, 1, 0));
		assertNotHolds(Relation.NEXT_TO, WALL, torchBeside(Pattern.WALL, new Pattern.Cell(0, 0, 2), 0, 0, 0));
	}

	@Test
	void relationsToAPlusAndToStairs() {
		assertHolds(Relation.ON_TOP_OF, CROSS, torchBeside(Pattern.CROSS, new Pattern.Cell(1, 1, 1), 0, 0, 0));
		assertHolds(Relation.NEXT_TO, CROSS, torchBeside(Pattern.CROSS, new Pattern.Cell(0, 0, 0), 0, 0, 0));
		assertNotHolds(Relation.NEXT_TO, CROSS, torch(), "no plus at all");

		// a torch on every tread is on the stairs; one beside the lowest step is next to them
		for (Pattern.Cell tread : Pattern.STAIRS.top(3, 0)) {
			assertHolds(Relation.ON_TOP_OF, STAIRS, torchBeside(Pattern.STAIRS, tread, 0, 0, 0));
		}
		assertHolds(Relation.NEXT_TO, STAIRS, torchBeside(Pattern.STAIRS, new Pattern.Cell(-1, 0, 0), 0, 0, 0));
		assertNotHolds(Relation.ON_TOP_OF, STAIRS, torchBeside(Pattern.STAIRS, new Pattern.Cell(0, 2, 0), 0, 0, 0),
				"two above the first step is in the air");
		assertFalse(Relations.supported(Relation.INSIDE, STAIRS) || Relations.supported(Relation.BETWEEN, WALL));
	}

	// ------------------------------------------------- between two different blocks

	private static final String FURNACE = "minecraft:furnace";
	private static final Anchor CHEST_AND_FURNACE = new Anchor.Pair(new Anchor.Block(CHEST), new Anchor.Block(FURNACE));

	@Test
	void betweenTwoDifferentBlocks() {
		PlacementFact alongX = torch(at(-1, 0, 0), CHEST, at(2, 0, 0), FURNACE);
		assertHolds(Relation.BETWEEN, CHEST_AND_FURNACE, alongX);
		// whichever block is on which side
		assertHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), FURNACE, at(2, 0, 0), CHEST));
		assertHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(0, 0, -3), CHEST, at(0, 0, 1), FURNACE));
		// two chests enclose it, but that is 'between two chests', not between a chest and a furnace
		PlacementFact twoChests = torch(at(-1, 0, 0), CHEST, at(2, 0, 0), CHEST);
		assertHolds(Relation.BETWEEN, CHESTS, twoChests);
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, twoChests);
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), CHEST));
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), CHEST, at(0, 0, 2), FURNACE),
				"a right angle does not enclose it");
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), CHEST, at(2, 1, 0), FURNACE),
				"the furnace is a level higher");
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), CHEST, at(4, 0, 0), FURNACE),
				"4 away is out of the cube");
		assertNotHolds(Relation.BETWEEN, CHEST_AND_FURNACE, torch(at(-1, 0, 0), CHEST, at(2, 0, 0), COBBLE));
	}

	@Test
	void onlyBetweenFitsTwoBlocksAndTheOtherRelationsShowAsSlips() {
		assertTrue(Relations.supported(Relation.BETWEEN, CHEST_AND_FURNACE));
		for (Relation relation : Relation.values()) {
			assertEquals(relation == Relation.BETWEEN, Relations.supported(relation, CHEST_AND_FURNACE), relation.name());
		}
		// next to one of the two is a wrong answer for 'between'
		EnumSet<Relation> held = Relations.holding(CHEST_AND_FURNACE, torch(at(1, 0, 0), FURNACE));
		assertEquals(Relation.NEXT_TO, Relations.wrongRelation(Relation.BETWEEN, held));
		held = Relations.holding(CHEST_AND_FURNACE, torch(at(0, -1, 0), CHEST));
		assertEquals(Relation.ON_TOP_OF, Relations.wrongRelation(Relation.BETWEEN, held));
	}

	// ----------------------------------------------------- the block of an earlier step

	@Test
	void aBlockPlacedInAnEarlierStepIsAPoint() {
		PlacementFact fact = torch();
		Anchor.Placed below = new Anchor.Placed("s2", 10, 9, 10);
		assertHolds(Relation.ON_TOP_OF, below, fact);
		assertHolds(Relation.UNDER, new Anchor.Placed("s2", 10, 11, 10), fact);
		for (int[] beside : new int[][]{{11, 10, 10}, {9, 10, 10}, {10, 10, 11}, {10, 10, 9}}) {
			assertHolds(Relation.NEXT_TO, new Anchor.Placed("s2", beside[0], beside[1], beside[2]), fact);
		}
		Anchor.Placed diagonal = new Anchor.Placed("s2", 11, 10, 11);
		assertNotHolds(Relation.NEXT_TO, diagonal, fact);
		assertHolds(Relation.NEAR, diagonal, fact);
		assertNotHolds(Relation.NEAR, new Anchor.Placed("s2", 14, 10, 10), fact, "4 away is not near");
		assertNotHolds(Relation.ON_TOP_OF, new Anchor.Placed("s2", 10, 8, 10), fact);
		assertEquals(EnumSet.noneOf(Relation.class), Relations.holding(new Anchor.Placed("s2", 10, 10, 10), fact),
				"the block itself is not beside itself");
		assertEquals("@s2", below.key());
	}

	@Test
	void anEarlierBlockHasNoFrontAndNoInside() {
		Anchor.Placed placed = new Anchor.Placed("s2", 0, 0, 0);
		for (Relation relation : Relation.values()) {
			boolean fits = relation == Relation.ON_TOP_OF || relation == Relation.UNDER || relation == Relation.NEXT_TO
					|| relation == Relation.NEAR;
			assertEquals(fits, Relations.supported(relation, placed), relation.name());
		}
	}

	// ------------------------------------------------------------------- the words

	@Test
	void everyRelationHasAWordAndAGrammarPoint() {
		String[] expected = {"위", "아래", "옆", "앞", "뒤", "안", "밖", "사이", "근처"};
		Relation[] all = Relation.values();
		assertEquals(expected.length, all.length);
		for (int i = 0; i < all.length; i++) {
			assertEquals(expected[i], all[i].ko());
			assertFalse(all[i].gloss(Lang.EN).isBlank());
			assertFalse(all[i].gloss(Lang.DE).isBlank());
			assertTrue(all[i] == Relation.NEAR || all[i].grammarId().matches("L[12]\\.loc_[a-z]+_e"), all[i].grammarId());
		}
		assertEquals("L1.loc_wi_e", Relation.ON_TOP_OF.grammarId());
		assertEquals("L1.loc_yeop_e", Relation.NEXT_TO.grammarId());
		assertEquals("L2.loc_sai_e", Relation.BETWEEN.grammarId());
		assertTrue(Relation.IN_FRONT_OF.needsFacing() && Relation.BEHIND.needsFacing());
		assertFalse(Relation.NEXT_TO.needsFacing());
		assertEquals(Relation.UNDER, Relation.byId("under", null));
		assertNull(Relation.byId("sideways", null));
	}
}
