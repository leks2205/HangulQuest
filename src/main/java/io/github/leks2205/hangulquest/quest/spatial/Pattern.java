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
	RING("담", 3, 3, 3),
	ROW("줄", 2, 4, 3),
	/** Size 2 is 2 wide and 2 high, size 3 is 3x2, size 4 is 4x3; it can stand along x or z. */
	WALL("벽", 2, 4, 3),
	/** A plus of five blocks, flat on the ground or standing upright along x or z. */
	CROSS("십자 모양", 3, 3, 3),
	/** Columns that grow by one block per step, filled down to the ground; it rises toward any side. */
	STAIRS("계단", 3, 4, 3);

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

	/**
	 * The Korean noun: 기둥, 피라미드, 담 (a ring of blocks around an empty cell: the wall that closes off a
	 * yard), 벽, 십자 모양, 계단. A ring of fence blocks is a 울타리 instead, see {@link #ringOfFences}.
	 */
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

	/**
	 * How many ways the structure can stand: a row or a wall lies along x (0) or z (1), a cross is flat (0) or
	 * upright along x (1) or z (2), stairs rise toward +x, -x, +z or -z. Everything else has one.
	 */
	public int orientations() {
		return switch (this) {
			case ROW, WALL -> 2;
			case CROSS -> 3;
			case STAIRS -> 4;
			default -> 1;
		};
	}

	/** The height of a wall of this size: 2x2, 3x2, 4x3. */
	private static int wallHeight(int size) {
		return size >= 4 ? 3 : 2;
	}

	/** Only fence blocks make a 울타리; a ring of any other material is a 담. */
	public static boolean ringOfFences(String materialSpec) {
		String id = BlockSpec.representative(materialSpec);
		return id != null && io.github.leks2205.hangulquest.quest.goal.Matching.blockFamily(id).equals("fence");
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
			case WALL -> {
				for (int y = 0; y < wallHeight(size); y++) {
					for (int i = 0; i < size; i++) {
						cells.add(orientation == 0 ? new Cell(i, y, 0) : new Cell(0, y, i));
					}
				}
			}
			case CROSS -> {
				int arm = (size - 1) / 2;
				Cell middle = centre(size, orientation);
				for (int d = -arm; d <= arm; d++) {
					if (d == 0) {
						continue;
					}
					switch (orientation) {
						case 0 -> {
							cells.add(middle.plus(d, 0, 0));
							cells.add(middle.plus(0, 0, d));
						}
						case 1 -> {
							cells.add(middle.plus(d, 0, 0));
							cells.add(middle.plus(0, d, 0));
						}
						default -> {
							cells.add(middle.plus(0, 0, d));
							cells.add(middle.plus(0, d, 0));
						}
					}
				}
				cells.add(middle);
			}
			case STAIRS -> {
				for (int i = 0; i < size; i++) {
					for (int y = 0; y <= i; y++) {
						cells.add(switch (orientation) {
							case 0 -> new Cell(i, y, 0);
							case 1 -> new Cell(size - 1 - i, y, 0);
							case 2 -> new Cell(0, y, i);
							default -> new Cell(0, y, size - 1 - i);
						});
					}
				}
			}
		}
		return cells;
	}

	/** The cells directly above the highest blocks: where something sits "on top of" the structure. */
	public List<Cell> top(int size, int orientation) {
		List<Cell> cells = cells(size, orientation);
		if (this == STAIRS) {
			// Every step has its own tread: the cell above each column's highest block.
			List<Cell> treads = new ArrayList<>();
			for (Cell cell : cells) {
				if (!cells.contains(cell.plus(0, 1, 0))) {
					treads.add(cell.plus(0, 1, 0));
				}
			}
			return treads;
		}
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
			case ROW, WALL -> orientation == 0 ? new Cell((size - 1) / 2, 0, 0) : new Cell(0, 0, (size - 1) / 2);
			case CROSS -> {
				int arm = (size - 1) / 2;
				yield switch (orientation) {
					case 0 -> new Cell(arm, 0, arm);
					case 1 -> new Cell(arm, arm, 0);
					default -> new Cell(0, arm, arm);
				};
			}
			case STAIRS -> {
				int middle = (size - 1) / 2;
				yield switch (orientation) {
					case 0 -> new Cell(middle, 0, 0);
					case 1 -> new Cell(size - 1 - middle, 0, 0);
					case 2 -> new Cell(0, 0, middle);
					default -> new Cell(0, 0, size - 1 - middle);
				};
			}
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
