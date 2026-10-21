package io.github.leks2205.hangulquest.quest.spatial;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * A tiny sparse world for tests, with a voxel parser for golden fixtures. A fixture is layers from
 * the bottom up, separated by a blank line; inside a layer each line is one z row and each character
 * one x column. The legend maps characters to block ids; everything else is air.
 */
public final class World {
	private final Map<Long, String> blocks = new HashMap<>();

	private static long key(int x, int y, int z) {
		return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
	}

	public World put(int x, int y, int z, String id) {
		blocks.put(key(x, y, z), id);
		return this;
	}

	public String at(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	/** Parses a fixture; the corner of the first layer is (originX, originY, originZ). */
	public static World parse(Map<Character, String> legend, int originX, int originY, int originZ,
							  String fixture) {
		World world = new World();
		int y = originY;
		for (String layer : fixture.strip().split("\n\\s*\n")) {
			int z = originZ;
			for (String row : layer.strip().split("\n")) {
				String cells = row.strip();
				for (int x = 0; x < cells.length(); x++) {
					String id = legend.get(cells.charAt(x));
					if (id != null) {
						world.put(originX + x, y, z, id);
					}
				}
				z++;
			}
			y++;
		}
		return world;
	}

	/** The world turned a quarter turn about the vertical axis through (0, 0). */
	public World rotated() {
		World turned = new World();
		for (Map.Entry<Long, String> entry : blocks.entrySet()) {
			long k = entry.getKey();
			int x = (int) (k >> 40) - 4096;
			int y = (int) ((k >> 20) & 0xFFFFF) - 4096;
			int z = (int) (k & 0xFFFFF) - 4096;
			turned.put(-z, y, x, entry.getValue());
		}
		return turned;
	}

	/** Every position that holds a block with this id. */
	public java.util.List<BlockPos> find(String id) {
		java.util.List<BlockPos> found = new java.util.ArrayList<>();
		for (Map.Entry<Long, String> entry : blocks.entrySet()) {
			if (entry.getValue().equals(id)) {
				long k = entry.getKey();
				found.add(new BlockPos((int) (k >> 40) - 4096, (int) ((k >> 20) & 0xFFFFF) - 4096,
						(int) (k & 0xFFFFF) - 4096));
			}
		}
		return found;
	}

	/** The block at {@code pos} was just placed: the fact the sensor would report for it. */
	public PlacementFact placed(BlockPos pos, boolean night, Vec3 player) {
		String block = at(pos.getX(), pos.getY(), pos.getZ());
		BlockNeighborhood around = BlockNeighborhood.capture(pos.getX(), pos.getY(), pos.getZ(), this::at);
		return new PlacementFact(block, pos, 1000L, night, player, around);
	}

	public PlacementFact placed(BlockPos pos) {
		return placed(pos, false, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}
}
