package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Builds a world in which a spatial goal is just satisfied, and the placement that satisfies it. */
public final class SpatialWorlds {
	private static final int X = 100;
	private static final int Y = 64;
	private static final int Z = 100;

	private SpatialWorlds() {
	}

	/** The placement that completes the goal (one placement; a count goal repeats it). */
	public static PlacementFact placementFor(Goal goal, boolean night) {
		return switch (goal) {
			case Goal.PlaceRel g -> placeRel(g, night);
			case Goal.BuildPattern g -> build(g, night);
			case Goal.PlaceOnPattern g -> onPattern(g, night);
			default -> throw new IllegalArgumentException("not a spatial goal: " + goal);
		};
	}

	/**
	 * The creatures a goal needs next to its placement: a creature anchor stands two blocks west of the block
	 * and looks along the way the relation asks (east for 앞, west for 뒤). Empty for every block anchor.
	 */
	public static List<EntityFact> entitiesFor(Goal goal) {
		if (!(goal instanceof Goal.PlaceRel g) || !BlockSpec.isEntityAnchor(g.anchor())) {
			return List.of();
		}
		// yaw 0 looks south (+z), -90 east (+x), 90 west (-x)
		float yaw = switch (g.relation()) {
			case IN_FRONT_OF -> -90.0F;
			case BEHIND -> 90.0F;
			default -> 0.0F; // 옆 and 근처: it looks at neither the block nor away from it
		};
		double distance = g.relation() == Relation.NEAR ? 4.0 : 2.0;
		return List.of(new EntityFact(g.anchor(), new UUID(8L, 1L), X + 0.5 - distance, Y, Z + 0.5, yaw, 0.0F));
	}

	private static PlacementFact placeRel(Goal.PlaceRel g, boolean night) {
		World world = new World();
		if (BlockSpec.isEntityAnchor(g.anchor())) {
			world.put(X, Y, Z, BlockSpec.representative(g.block()));
			PlacementFact fact = world.placed(new BlockPos(X, Y, Z), night, new Vec3(X + 0.5, Y, Z + 0.5));
			if (!Relations.holding(new Anchor.Entity(g.anchor()), fact, entitiesFor(g)).contains(g.relation())) {
				throw new AssertionError("fixture does not satisfy " + g.relation() + " for " + g.anchor());
			}
			return fact;
		}
		String anchor = BlockSpec.representative(g.anchor());
		Vec3 player = new Vec3(X + 0.5, Y, Z + 0.5);
		switch (g.relation()) {
			case ON_TOP_OF -> world.put(X, Y - 1, Z, anchor);
			case UNDER -> world.put(X, Y + 1, Z, anchor);
			case NEXT_TO -> world.put(X + 1, Y, Z, anchor);
			case IN_FRONT_OF -> {
				world.put(X + 1, Y, Z, anchor);
				player = new Vec3(X - 2.5, Y, Z + 0.5);
			}
			case BEHIND -> {
				world.put(X + 1, Y, Z, anchor);
				player = new Vec3(X + 4.5, Y, Z + 0.5);
			}
			case INSIDE -> world.put(X + 2, Y, Z, anchor).put(X - 2, Y, Z, anchor)
					.put(X, Y, Z + 2, anchor).put(X, Y, Z - 2, anchor);
			case OUTSIDE -> world.put(X + 3, Y, Z, anchor);
			case BETWEEN -> world.put(X - 1, Y, Z, anchor).put(X + 2, Y, Z,
					g.anchor2() == null ? anchor : BlockSpec.representative(g.anchor2()));
			case NEAR -> world.put(X + 2, Y + 1, Z + 2, anchor);
		}
		world.put(X, Y, Z, BlockSpec.representative(g.block()));
		Anchor.Block first = new Anchor.Block(g.anchor());
		return verified(world.placed(new BlockPos(X, Y, Z), night, player),
				g.anchor2() == null ? first : new Anchor.Pair(first, new Anchor.Block(g.anchor2())), g.relation());
	}

	private static PlacementFact build(Goal.BuildPattern g, boolean night) {
		World world = new World();
		List<Pattern.Cell> cells = g.pattern().cells(g.size(), 0);
		Pattern.Cell last = cells.get(cells.size() - 1);
		String material = BlockSpec.representative(g.material());
		for (Pattern.Cell cell : cells) {
			world.put(X + cell.x() - last.x(), Y + cell.y() - last.y(), Z + cell.z() - last.z(), material);
		}
		PlacementFact fact = world.placed(new BlockPos(X, Y, Z), night, new Vec3(X + 0.5, Y, Z + 0.5));
		if (PatternMatcher.builtWith(fact.around(), g.pattern(), g.size(), g.material()).isEmpty()) {
			throw new AssertionError("fixture does not build " + g);
		}
		return fact;
	}

	private static PlacementFact onPattern(Goal.PlaceOnPattern g, boolean night) {
		World world = new World();
		Pattern pattern = g.pattern();
		int size = g.size(); // the size the goal asks for, not the usual one
		List<Pattern.Cell> cells = pattern.cells(size, 0);
		Pattern.Cell at = switch (g.relation()) {
			case ON_TOP_OF -> pattern.top(size, 0).get(0);
			case UNDER -> cells.get(0).plus(0, -1, 0);
			case NEXT_TO -> cells.get(0).plus(-1, 0, 0);
			case INSIDE -> pattern.inside(size, 0).get(0);
			case OUTSIDE, NEAR -> cells.get(0).plus(-1, 0, -1);
			default -> throw new IllegalArgumentException(g.relation() + " does not fit a pattern");
		};
		String material = BlockSpec.representative(g.material());
		for (Pattern.Cell cell : cells) {
			world.put(X + cell.x() - at.x(), Y + cell.y() - at.y(), Z + cell.z() - at.z(), material);
		}
		world.put(X, Y, Z, BlockSpec.representative(g.block()));
		return verified(world.placed(new BlockPos(X, Y, Z), night, new Vec3(X + 0.5, Y, Z + 0.5)),
				new Anchor.Built(pattern, g.material(), size), g.relation());
	}

	private static PlacementFact verified(PlacementFact fact, Anchor anchor, Relation relation) {
		if (!Relations.holds(relation, anchor, fact)) {
			throw new AssertionError("fixture does not satisfy " + relation + " for " + anchor);
		}
		return fact;
	}
}
