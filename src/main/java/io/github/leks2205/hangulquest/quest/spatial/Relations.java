package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.PlacementFact;

import java.util.EnumSet;
import java.util.List;

/**
 * The geometry of each {@link Relation}: which ones hold between a block that was just placed and an
 * {@link Anchor}, judged from the 7x7x7 cube around it. All offsets are relative to the placed block.
 *
 * <p>Facing: the sensor stores block ids, not block states, so the front of a door or a chest is not
 * known. Until it is, the front is the side the player stands on when placing (the dominant horizontal
 * axis from the anchor to the player); the back is the opposite side.
 */
public final class Relations {
	private static final int R = BlockNeighborhood.RADIUS;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private Relations() {
	}

	/** Every relation that holds for this placement and anchor. */
	public static EnumSet<Relation> holding(Anchor anchor, PlacementFact fact) {
		return switch (anchor) {
			case Anchor.Block block -> holdingBlock(block.spec(), fact);
			case Anchor.Built built -> holdingBuilt(built, fact);
		};
	}

	public static boolean holds(Relation relation, Anchor anchor, PlacementFact fact) {
		return holding(anchor, fact).contains(relation);
	}

	/** Whether this relation can mean something for this anchor; the validator rejects the rest. */
	public static boolean supported(Relation relation, Anchor anchor) {
		return switch (relation) {
			case ON_TOP_OF, UNDER, NEXT_TO, NEAR -> true;
			case IN_FRONT_OF, BEHIND -> anchor instanceof Anchor.Block block && BlockSpec.hasFacing(block.spec());
			case INSIDE, OUTSIDE -> switch (anchor) {
				case Anchor.Block block -> BlockSpec.encloses(block.spec());
				case Anchor.Built built -> built.pattern().encloses();
			};
			case BETWEEN -> anchor instanceof Anchor.Block;
		};
	}

	/**
	 * The relation the player used instead of the wanted one, or null. 근처 and 밖 are too loose to
	 * count as a "wrong" answer; everything more specific is.
	 */
	public static Relation wrongRelation(Relation wanted, EnumSet<Relation> held) {
		for (Relation relation : Relation.values()) {
			if (relation != wanted && relation != Relation.NEAR && relation != Relation.OUTSIDE
					&& held.contains(relation)) {
				return relation;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ blocks

	private static EnumSet<Relation> holdingBlock(String spec, PlacementFact fact) {
		BlockNeighborhood around = fact.around();
		EnumSet<Relation> held = EnumSet.noneOf(Relation.class);
		if (is(around, spec, 0, -1, 0)) {
			held.add(Relation.ON_TOP_OF);
		}
		if (is(around, spec, 0, 1, 0)) {
			held.add(Relation.UNDER);
		}
		for (int[] dir : DIRS) {
			if (is(around, spec, dir[0], 0, dir[1])) {
				held.add(Relation.NEXT_TO);
			}
		}
		if (BlockSpec.hasFacing(spec)) {
			facing(around, spec, fact, held);
		}
		if (BlockSpec.encloses(spec)) {
			boolean enclosed = true;
			for (int[] dir : DIRS) {
				enclosed &= ray(around, spec, dir);
			}
			if (enclosed) {
				held.add(Relation.INSIDE);
			} else if (anyNearby(around, spec)) {
				held.add(Relation.OUTSIDE);
			}
		}
		for (int[] axis : new int[][]{{1, 0}, {0, 1}}) {
			if (ray(around, spec, axis) && ray(around, spec, new int[]{-axis[0], -axis[1]})) {
				held.add(Relation.BETWEEN);
			}
		}
		if (anyBlock(around, spec)) {
			held.add(Relation.NEAR);
		}
		return held;
	}

	private static boolean is(BlockNeighborhood around, String spec, int dx, int dy, int dz) {
		return BlockSpec.matches(spec, around.at(dx, dy, dz));
	}

	/** A matching block 1 to 3 steps away in this horizontal direction, at the level of the placed block. */
	private static boolean ray(BlockNeighborhood around, String spec, int[] dir) {
		for (int k = 1; k <= R; k++) {
			if (is(around, spec, k * dir[0], 0, k * dir[1])) {
				return true;
			}
		}
		return false;
	}

	private static boolean anyNearby(BlockNeighborhood around, String spec) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int dx = -R; dx <= R; dx++) {
					if ((dx != 0 || dy != 0 || dz != 0) && is(around, spec, dx, dy, dz)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean anyBlock(BlockNeighborhood around, String spec) {
		for (int dy = -R; dy <= R; dy++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int dx = -R; dx <= R; dx++) {
					if ((dx != 0 || dy != 0 || dz != 0) && is(around, spec, dx, dy, dz)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void facing(BlockNeighborhood around, String spec, PlacementFact fact,
							   EnumSet<Relation> held) {
		for (int[] dir : DIRS) {
			for (int k = 1; k <= 2; k++) {
				if (!is(around, spec, k * dir[0], 0, k * dir[1])) {
					continue;
				}
				double towardX = fact.playerPos().x - (fact.pos().getX() + k * dir[0] + 0.5);
				double towardZ = fact.playerPos().z - (fact.pos().getZ() + k * dir[1] + 0.5);
				int frontX = 0;
				int frontZ = 0;
				if (Math.abs(towardX) >= Math.abs(towardZ)) {
					frontX = towardX >= 0 ? 1 : -1;
				} else {
					frontZ = towardZ >= 0 ? 1 : -1;
				}
				// The placed block lies in the direction -dir from the anchor.
				if (-dir[0] == frontX && -dir[1] == frontZ) {
					held.add(Relation.IN_FRONT_OF);
				} else if (dir[0] == frontX && dir[1] == frontZ) {
					held.add(Relation.BEHIND);
				}
			}
		}
	}

	// ---------------------------------------------------------------- structures

	private static EnumSet<Relation> holdingBuilt(Anchor.Built built, PlacementFact fact) {
		EnumSet<Relation> held = EnumSet.noneOf(Relation.class);
		for (PatternMatcher.Match match : PatternMatcher.find(fact.around(), built.pattern(), built.size(),
				built.material())) {
			List<Pattern.Cell> cells = match.cells();
			Pattern.Cell here = new Pattern.Cell(0, 0, 0);
			if (cells.contains(here)) {
				continue;
			}
			boolean inside = match.inside().contains(here);
			if (match.top().contains(here)) {
				held.add(Relation.ON_TOP_OF);
			}
			if (cells.contains(here.plus(0, 1, 0))) {
				held.add(Relation.UNDER);
			}
			if (inside) {
				held.add(Relation.INSIDE);
			} else {
				held.add(Relation.OUTSIDE);
				for (int[] dir : DIRS) {
					if (cells.contains(here.plus(dir[0], 0, dir[1]))) {
						held.add(Relation.NEXT_TO);
					}
				}
			}
			held.add(Relation.NEAR);
		}
		return held;
	}
}
