package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The geometry of each {@link Relation}: which ones hold between a block that was just placed and an
 * {@link Anchor}, judged from the 7x7x7 cube around it (the nearest part of the captured cube). All offsets are relative to the placed block.
 *
 * <p>Facing: the sensor stores block ids, not block states, so the front of a door or a chest is not
 * known. Until it is, the front is the side the player stands on when placing (the dominant horizontal
 * axis from the anchor to the player); the back is the opposite side.
 */
public final class Relations {
	private static final int R = BlockNeighborhood.NEAR;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private Relations() {
	}

	/** Every relation that holds for this placement and anchor. */
	public static EnumSet<Relation> holding(Anchor anchor, PlacementFact fact) {
		return holding(anchor, fact, List.of());
	}

	/** Same, with the creatures around the player, for an {@link Anchor.Entity}. */
	public static EnumSet<Relation> holding(Anchor anchor, PlacementFact fact, List<EntityFact> entities) {
		return switch (anchor) {
			case Anchor.Entity entity -> holdingEntity(entity.type(), fact, entities);
			case Anchor.Block block -> holdingBlock(block.spec(), fact);
			case Anchor.Built built -> holdingBuilt(built, fact);
			case Anchor.Pair pair -> holdingPair(pair, fact);
			case Anchor.Placed placed -> holdingPlaced(placed, fact);
		};
	}

	public static boolean holds(Relation relation, Anchor anchor, PlacementFact fact) {
		return holding(anchor, fact).contains(relation);
	}

	/** Whether this relation can mean something for this anchor; the validator rejects the rest. */
	public static boolean supported(Relation relation, Anchor anchor) {
		if (anchor instanceof Anchor.Pair) {
			return relation == Relation.BETWEEN;
		}
		if (anchor instanceof Anchor.Entity) {
			return switch (relation) {
				case IN_FRONT_OF, BEHIND, NEXT_TO, NEAR -> true;
				default -> false;
			};
		}
		if (anchor instanceof Anchor.Placed) {
			return switch (relation) {
				case ON_TOP_OF, UNDER, NEXT_TO, NEAR -> true;
				default -> false;
			};
		}
		return switch (relation) {
			case ON_TOP_OF, UNDER, NEXT_TO, NEAR -> true;
			case IN_FRONT_OF, BEHIND -> anchor instanceof Anchor.Block block && BlockSpec.hasFacing(block.spec());
			case INSIDE, OUTSIDE -> switch (anchor) {
				case Anchor.Block block -> BlockSpec.encloses(block.spec());
				case Anchor.Built built -> built.pattern().encloses();
				default -> false;
			};
			case BETWEEN -> anchor instanceof Anchor.Block;
		};
	}

	/**
	 * The relation the player used instead of the wanted one, or null. 근처 is too loose to count as a "wrong"
	 * answer, and so is 밖 unless 안 was asked: the two contrast, so standing outside is the slip of a learner
	 * who did not read 안.
	 */
	public static Relation wrongRelation(Relation wanted, EnumSet<Relation> held) {
		for (Relation relation : Relation.values()) {
			boolean loose = relation == Relation.NEAR || relation == Relation.OUTSIDE && wanted != Relation.INSIDE;
			if (relation != wanted && !loose && held.contains(relation)) {
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

	// ------------------------------------------------------- two blocks, an earlier block

	/**
	 * 상자와 화로 사이: the placed block has one of the two to a side and the other directly opposite, 1 to 3
	 * blocks away on the same row. Every other relation holds when it holds for either block.
	 *
	 * <p>With water or lava as one of the two (모래와 물 사이에) a plant is placed on the shore: the block stands on
	 * the other one and the fluid lies next to it, in the row of the ground or of the plant.
	 */
	private static EnumSet<Relation> holdingPair(Anchor.Pair pair, PlacementFact fact) {
		BlockNeighborhood around = fact.around();
		EnumSet<Relation> held = holdingBlock(pair.first().spec(), fact);
		held.addAll(holdingBlock(pair.second().spec(), fact));
		held.remove(Relation.BETWEEN);
		for (int[] axis : new int[][]{{1, 0}, {0, 1}}) {
			int[] back = {-axis[0], -axis[1]};
			if (ray(around, pair.first().spec(), axis) && ray(around, pair.second().spec(), back)
					|| ray(around, pair.second().spec(), axis) && ray(around, pair.first().spec(), back)) {
				held.add(Relation.BETWEEN);
			}
		}
		if (!held.contains(Relation.BETWEEN) && isFluid(pair.first().spec()) != isFluid(pair.second().spec())) {
			boolean firstIsFluid = isFluid(pair.first().spec());
			String ground = firstIsFluid ? pair.second().spec() : pair.first().spec();
			String fluid = firstIsFluid ? pair.first().spec() : pair.second().spec();
			if (is(around, ground, 0, -1, 0) && fluidNear(around, fluid)) {
				held.add(Relation.BETWEEN);
			}
		}
		return held;
	}

	/** Water and lava lie in the world as blocks too, but nothing is built on them: they are only "between". */
	private static boolean isFluid(String spec) {
		return "minecraft:water".equals(spec) || "minecraft:lava".equals(spec);
	}

	/** The fluid next to the ground the block stands on, or next to the block itself: one or two blocks away. */
	private static boolean fluidNear(BlockNeighborhood around, String fluid) {
		for (int[] dir : DIRS) {
			for (int k = 1; k <= 2; k++) {
				if (is(around, fluid, k * dir[0], -1, k * dir[1]) || is(around, fluid, k * dir[0], 0, k * dir[1])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A creature as the anchor: the placed block lies in the direction it looks (앞), the opposite (뒤), within
	 * two and a half blocks and about level (옆), or within six blocks (근처). The nearest creature of the kind
	 * is the one measured.
	 */
	private static EnumSet<Relation> holdingEntity(String type, PlacementFact fact, List<EntityFact> entities) {
		EnumSet<Relation> held = EnumSet.noneOf(Relation.class);
		Vec3 block = new Vec3(fact.pos().getX() + 0.5, fact.pos().getY() + 0.5, fact.pos().getZ() + 0.5);
		EntityFact nearest = null;
		double best = Double.MAX_VALUE;
		for (EntityFact entity : entities) {
			double distance = entity.position().distanceToSqr(block);
			if (entity.type().equals(type) && distance < best) {
				best = distance;
				nearest = entity;
			}
		}
		if (nearest == null) {
			return held;
		}
		double dx = block.x - nearest.x();
		double dz = block.z - nearest.z();
		double flat = Math.hypot(dx, dz);
		double dy = Math.abs(block.y - (nearest.y() + 0.9));
		if (Math.sqrt(flat * flat + dy * dy) <= 6.0) {
			held.add(Relation.NEAR);
		}
		if (flat > 0.4 && flat <= 3.5 && dy <= 1.5) {
			Vec3 look = nearest.facing();
			double lookLength = Math.hypot(look.x, look.z);
			if (lookLength > 1.0E-6) {
				double alignment = (look.x * dx + look.z * dz) / (lookLength * flat);
				if (alignment >= 0.7) {
					held.add(Relation.IN_FRONT_OF);
				} else if (alignment <= -0.7) {
					held.add(Relation.BEHIND);
				}
			}
		}
		if (flat > 0.4 && flat <= 2.5 && dy <= 1.5) {
			held.add(Relation.NEXT_TO);
		}
		return held;
	}

	/** The block remembered from an earlier step is a point in the world; the placed block lies at an offset from it. */
	private static EnumSet<Relation> holdingPlaced(Anchor.Placed anchor, PlacementFact fact) {
		int dx = fact.pos().getX() - anchor.x();
		int dy = fact.pos().getY() - anchor.y();
		int dz = fact.pos().getZ() - anchor.z();
		EnumSet<Relation> held = EnumSet.noneOf(Relation.class);
		if (dx == 0 && dy == 0 && dz == 0) {
			return held;
		}
		if (dx == 0 && dz == 0 && dy == 1) {
			held.add(Relation.ON_TOP_OF);
		}
		if (dx == 0 && dz == 0 && dy == -1) {
			held.add(Relation.UNDER);
		}
		if (dy == 0 && Math.abs(dx) + Math.abs(dz) == 1) {
			held.add(Relation.NEXT_TO);
		}
		if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= R) {
			held.add(Relation.NEAR);
		}
		return held;
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
