package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.List;

/**
 * The 13x13x13 cube of blocks around a placed block, as palette indices. Captured once per placement,
 * never per tick. Offsets run from -{@value #RADIUS} to {@value #RADIUS} on every axis; the three
 * air blocks are one palette entry, and index 0 is always air. The cube is wide enough for a pillar or a row of
 * seven with the new block at its end; relations ({@code Relations}) still look only {@value #NEAR} blocks around.
 */
public final class BlockNeighborhood {
	public static final int RADIUS = 6;
	/** How far a relation (next to, near, in front of) looks: the 7x7x7 cube the first versions captured. */
	public static final int NEAR = 3;
	public static final int SIZE = RADIUS * 2 + 1;
	public static final String AIR = "minecraft:air";

	/** Reads a block id at absolute coordinates; null or an air id both mean air. */
	@FunctionalInterface
	public interface Lookup {
		String blockAt(int x, int y, int z);
	}

	private final short[] cells = new short[SIZE * SIZE * SIZE];
	private final List<String> palette = new ArrayList<>(8);

	private BlockNeighborhood() {
		palette.add(AIR);
	}

	/** Captures the cube centred on the given block with exactly {@code SIZE^3} lookups. */
	public static BlockNeighborhood capture(int cx, int cy, int cz, Lookup lookup) {
		BlockNeighborhood around = new BlockNeighborhood();
		int i = 0;
		for (int dy = -RADIUS; dy <= RADIUS; dy++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				for (int dx = -RADIUS; dx <= RADIUS; dx++) {
					around.cells[i++] = around.indexOf(lookup.blockAt(cx + dx, cy + dy, cz + dz));
				}
			}
		}
		return around;
	}

	private short indexOf(String id) {
		if (isAirId(id)) {
			return 0;
		}
		for (int i = 1; i < palette.size(); i++) {
			if (palette.get(i).equals(id)) {
				return (short) i;
			}
		}
		palette.add(id);
		return (short) (palette.size() - 1);
	}

	/** Null counts as air: an unloaded chunk reads as air on the client too. */
	public static boolean isAirId(String id) {
		return id == null || id.equals(AIR) || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
	}

	/** The block at an offset from the centre; null when the offset is outside the cube. */
	public String at(int dx, int dy, int dz) {
		if (Math.abs(dx) > RADIUS || Math.abs(dy) > RADIUS || Math.abs(dz) > RADIUS) {
			return null;
		}
		return palette.get(cells[((dy + RADIUS) * SIZE + (dz + RADIUS)) * SIZE + dx + RADIUS]);
	}

	public boolean isAir(int dx, int dy, int dz) {
		String id = at(dx, dy, dz);
		return id == null || id.equals(AIR);
	}

	/** How many cells hold this block. */
	public int count(String block) {
		int index = palette.indexOf(block);
		if (index < 0) {
			return 0;
		}
		int n = 0;
		for (short cell : cells) {
			if (cell == index) {
				n++;
			}
		}
		return n;
	}

	/** Distinct blocks in the cube, air first. */
	public List<String> palette() {
		return List.copyOf(palette);
	}
}
