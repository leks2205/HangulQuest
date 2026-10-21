package io.github.leks2205.hangulquest.sense;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Works out which blocks appeared in the region snapshotted when the player right-clicked a block.
 * The placed block usually shows up on the clicked face rather than at the clicked position, so
 * the whole region is compared.
 */
public final class PlacementDiff {
	private PlacementDiff() {
	}

	public record Placed(BlockPos pos, String block) {
	}

	/** Every position that now holds a non-air block it did not hold before. */
	public static List<Placed> find(Map<BlockPos, String> before, Function<BlockPos, String> after) {
		List<Placed> changed = new ArrayList<>(2);
		for (Map.Entry<BlockPos, String> entry : before.entrySet()) {
			String now = after.apply(entry.getKey());
			if (!BlockNeighborhood.isAirId(now) && !now.equals(entry.getValue())) {
				changed.add(new Placed(entry.getKey(), now));
			}
		}
		return changed;
	}

	/**
	 * One placement per block id: a door or bed fills two positions with the same block, but the
	 * player placed one thing. The position nearest the clicked face wins, then the clicked block.
	 */
	public static List<Placed> primary(List<Placed> changed, BlockPos faceTarget, BlockPos clicked) {
		List<Placed> best = new ArrayList<>(2);
		for (Placed candidate : changed) {
			int at = -1;
			for (int i = 0; i < best.size(); i++) {
				if (best.get(i).block().equals(candidate.block())) {
					at = i;
					break;
				}
			}
			if (at < 0) {
				best.add(candidate);
			} else if (closer(candidate.pos(), best.get(at).pos(), faceTarget, clicked)) {
				best.set(at, candidate);
			}
		}
		return best;
	}

	private static boolean closer(BlockPos a, BlockPos b, BlockPos faceTarget, BlockPos clicked) {
		double faceA = a.distSqr(faceTarget);
		double faceB = b.distSqr(faceTarget);
		if (faceA != faceB) {
			return faceA < faceB;
		}
		double clickA = a.distSqr(clicked);
		double clickB = b.distSqr(clicked);
		if (clickA != clickB) {
			return clickA < clickB;
		}
		// Stable whatever order the map was iterated in.
		return a.asLong() < b.asLong();
	}
}
