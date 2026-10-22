package io.github.leks2205.hangulquest.sense;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * How far the nearest block of a kind is from the player: the pure part of the NEAR_BLOCK check, so the game
 * side only supplies block lookups. The scan covers a cube around the player that reaches {@link #MAX_RADIUS}
 * blocks sideways and {@link #MAX_VERTICAL} up and down; a block beyond that is simply not seen, which is
 * what "near" means for a crop in a field or a nest in a tree.
 */
public final class BlockProximity {
	public static final int MAX_RADIUS = 16;
	public static final int MAX_VERTICAL = 6;

	private BlockProximity() {
	}

	/**
	 * The distance (in blocks, to the middle of the block) of the nearest block for each wanted spec, only for
	 * the specs that have one within their radius.
	 *
	 * @param wanted  block spec to radius
	 * @param lookup  block id at absolute coordinates; null or air is nothing
	 * @param matches whether the spec stands for the block id (a family, a tag or the id itself)
	 */
	public static Map<String, Double> nearest(double px, double py, double pz, Map<String, Integer> wanted,
											  BlockNeighborhood.Lookup lookup, BiPredicate<String, String> matches) {
		Map<String, Double> found = new HashMap<>();
		if (wanted.isEmpty()) {
			return found;
		}
		int reach = 0;
		for (int radius : wanted.values()) {
			reach = Math.max(reach, Math.min(radius, MAX_RADIUS));
		}
		int vertical = Math.min(reach, MAX_VERTICAL);
		int cx = (int) Math.floor(px);
		int cy = (int) Math.floor(py);
		int cz = (int) Math.floor(pz);
		for (int dy = -vertical; dy <= vertical + 1; dy++) { // one more above: the player is two blocks tall
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dx = -reach; dx <= reach; dx++) {
					String id = lookup.blockAt(cx + dx, cy + dy, cz + dz);
					if (BlockNeighborhood.isAirId(id)) {
						continue;
					}
					double distance = Math.sqrt(square(cx + dx + 0.5 - px) + square(cy + dy + 0.5 - py)
							+ square(cz + dz + 0.5 - pz));
					for (Map.Entry<String, Integer> want : wanted.entrySet()) {
						Double best = found.get(want.getKey());
						if (distance <= want.getValue() && (best == null || distance < best)
								&& matches.test(want.getKey(), id)) {
							found.put(want.getKey(), distance);
						}
					}
				}
			}
		}
		return found;
	}

	private static double square(double value) {
		return value * value;
	}
}
