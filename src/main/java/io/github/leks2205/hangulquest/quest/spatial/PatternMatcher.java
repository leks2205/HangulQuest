package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a {@link Pattern} inside the 7x7x7 cube around a new block. Nothing here reads the world: the
 * cube was captured once when the block was placed, so a match costs array reads only.
 * Offsets are relative to the placed block, as in {@link BlockNeighborhood#at}.
 */
public final class PatternMatcher {
	private static final int R = BlockNeighborhood.RADIUS;
	private static final int N = BlockNeighborhood.SIZE;

	private PatternMatcher() {
	}

	/** One structure found in the cube, with all its points in placed-block-relative offsets. */
	public record Match(Pattern pattern, int size, int orientation, int ox, int oy, int oz) {
		public List<Pattern.Cell> cells() {
			return shift(pattern.cells(size, orientation));
		}

		/** Where something would sit on top of the structure. */
		public List<Pattern.Cell> top() {
			return shift(pattern.top(size, orientation));
		}

		public Pattern.Cell centre() {
			return shift(List.of(pattern.centre(size, orientation))).get(0);
		}

		public List<Pattern.Cell> inside() {
			return shift(pattern.inside(size, orientation));
		}

		public boolean contains(int dx, int dy, int dz) {
			return cells().contains(new Pattern.Cell(dx, dy, dz));
		}

		private List<Pattern.Cell> shift(List<Pattern.Cell> cells) {
			List<Pattern.Cell> moved = new ArrayList<>(cells.size());
			for (Pattern.Cell cell : cells) {
				moved.add(cell.plus(ox, oy, oz));
			}
			return moved;
		}
	}

	/** Every copy of the structure made of {@code material} inside the cube, wherever it stands. */
	public static List<Match> find(BlockNeighborhood around, Pattern pattern, int size, String material) {
		boolean[] solid = mask(around, material);
		List<Match> found = new ArrayList<>(2);
		for (int orientation = 0; orientation < pattern.orientations(); orientation++) {
			List<Pattern.Cell> cells = pattern.cells(size, orientation);
			List<Pattern.Cell> inside = pattern.inside(size, orientation);
			for (int oy = -R; oy <= R; oy++) {
				for (int oz = -R; oz <= R; oz++) {
					for (int ox = -R; ox <= R; ox++) {
						if (fits(solid, cells, inside, ox, oy, oz)) {
							found.add(new Match(pattern, size, orientation, ox, oy, oz));
						}
					}
				}
			}
		}
		return found;
	}

	/** The copies that include the block just placed: what the player just finished building. */
	public static List<Match> builtWith(BlockNeighborhood around, Pattern pattern, int size, String material) {
		List<Match> through = new ArrayList<>(1);
		for (Match match : find(around, pattern, size, material)) {
			if (match.contains(0, 0, 0)) {
				through.add(match);
			}
		}
		return through;
	}

	private static boolean fits(boolean[] solid, List<Pattern.Cell> cells, List<Pattern.Cell> inside,
								int ox, int oy, int oz) {
		for (Pattern.Cell cell : cells) {
			int x = cell.x() + ox;
			int y = cell.y() + oy;
			int z = cell.z() + oz;
			if (Math.abs(x) > R || Math.abs(y) > R || Math.abs(z) > R || !solid[index(x, y, z)]) {
				return false;
			}
		}
		// A hollow centre may hold anything but the material itself (a torch inside the ring is fine).
		for (Pattern.Cell cell : inside) {
			int x = cell.x() + ox;
			int y = cell.y() + oy;
			int z = cell.z() + oz;
			if (Math.abs(x) > R || Math.abs(y) > R || Math.abs(z) > R || solid[index(x, y, z)]) {
				return false;
			}
		}
		return true;
	}

	private static int index(int dx, int dy, int dz) {
		return ((dy + R) * N + (dz + R)) * N + dx + R;
	}

	/** Which cells of the cube hold the material; each distinct block id is judged once. */
	static boolean[] mask(BlockNeighborhood around, String material) {
		boolean[] solid = new boolean[N * N * N];
		Map<String, Boolean> judged = new HashMap<>();
		for (int dy = -R; dy <= R; dy++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int dx = -R; dx <= R; dx++) {
					String id = around.at(dx, dy, dz);
					solid[index(dx, dy, dz)] = id != null
							&& judged.computeIfAbsent(id, key -> BlockSpec.matches(material, key));
				}
			}
		}
		return solid;
	}
}
