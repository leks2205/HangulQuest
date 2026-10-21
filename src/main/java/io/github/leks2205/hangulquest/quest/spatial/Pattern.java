package io.github.leks2205.hangulquest.quest.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small structures a player builds, as lists of cells. Cell offsets start at the structure's own
 * corner (0,0,0), so a matcher can lay them over a world at any position.
 *
 * <p>The matcher only sees the 7x7x7 cube around a new block, and a structure has to fit inside it
 * with the new block as one of its cells. A pillar or a row is therefore capped at 4 (the last block
 * is at an end, with 3 more on one side); a pyramid and a ring are 3 wide.
 */
public enum Pattern {
	PILLAR("기둥", 2, 4, 3),
	PYRAMID("피라미드", 3, 3, 3),
	RING("울타리", 3, 3, 3),
	ROW("줄", 2, 4, 3);

	/** One cell of a structure, relative to its corner. */
	public record Cell(int x, int y, int z) {
		public Cell plus(int dx, int dy, int dz) {
			return new Cell(x + dx, y + dy, z + dz);
		}
	}

	private final String ko;
	private final int minSize;
	private final int maxSize;
	private final int defaultSize;

	Pattern(String ko, int minSize, int maxSize, int defaultSize) {
		this.ko = ko;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.defaultSize = defaultSize;
	}

	/** The Korean noun: 기둥, 피라미드, 울타리 (a ring of blocks around an empty cell). */
	public String ko() {
		return ko;
	}

	public int minSize() {
		return minSize;
	}

	public int maxSize() {
		return maxSize;
	}

	/** The size when a goal does not give one (a torch "on the pillar"). */
	public int defaultSize() {
		return defaultSize;
	}

	public boolean validSize(int size) {
		return size >= minSize && size <= maxSize;
	}

	/** A row can lie along x (0) or along z (1); everything else has one orientation. */
	public int orientations() {
		return this == ROW ? 2 : 1;
	}

	/** True when something can stand inside it. */
	public boolean encloses() {
		return this == RING;
	}

	/** The blocks of the structure. */
	public List<Cell> cells(int size, int orientation) {
		List<Cell> cells = new ArrayList<>();
		switch (this) {
			case PILLAR -> {
				for (int y = 0; y < size; y++) {
					cells.add(new Cell(0, y, 0));
				}
			}
			case ROW -> {
				for (int i = 0; i < size; i++) {
					cells.add(orientation == 0 ? new Cell(i, 0, 0) : new Cell(0, 0, i));
				}
			}
			case RING -> {
				for (int z = 0; z < 3; z++) {
					for (int x = 0; x < 3; x++) {
						if (x != 1 || z != 1) {
							cells.add(new Cell(x, 0, z));
						}
					}
				}
			}
			case PYRAMID -> {
				// Layers of size, size-2, ... 1, each centred on the layer below.
				for (int layer = 0; size - 2 * layer >= 1; layer++) {
					int width = size - 2 * layer;
					for (int z = 0; z < width; z++) {
						for (int x = 0; x < width; x++) {
							cells.add(new Cell(layer + x, layer, layer + z));
						}
					}
				}
			}
		}
		return cells;
	}

	/** The cells directly above the highest blocks: where something sits "on top of" the structure. */
	public List<Cell> top(int size, int orientation) {
		List<Cell> cells = cells(size, orientation);
		int highest = 0;
		for (Cell cell : cells) {
			highest = Math.max(highest, cell.y());
		}
		List<Cell> top = new ArrayList<>();
		for (Cell cell : cells) {
			if (cell.y() == highest) {
				top.add(cell.plus(0, 1, 0));
			}
		}
		return top;
	}

	/** The middle of the footprint at the bottom layer. */
	public Cell centre(int size, int orientation) {
		return switch (this) {
			case PILLAR -> new Cell(0, 0, 0);
			case PYRAMID -> new Cell(size / 2, 0, size / 2);
			case RING -> new Cell(1, 0, 1);
			case ROW -> orientation == 0 ? new Cell((size - 1) / 2, 0, 0) : new Cell(0, 0, (size - 1) / 2);
		};
	}

	/** The enclosed cells, empty for structures that enclose nothing. */
	public List<Cell> inside(int size, int orientation) {
		return this == RING ? List.of(new Cell(1, 0, 1)) : List.of();
	}

	public static Pattern byId(String id, Pattern fallback) {
		if (id == null) {
			return fallback;
		}
		try {
			return valueOf(id.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			return fallback;
		}
	}
}
