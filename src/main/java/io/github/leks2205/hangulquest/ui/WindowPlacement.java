package io.github.leks2205.hangulquest.ui;

import java.util.List;

/**
 * Keeps the HUD windows off the crosshair and the hotbar. A window stays where it wants to be unless it
 * would cover one of them; then it moves to the nearest free spot. Windows now stay until closed, so a
 * window on the crosshair would be in the way for as long as the player reads it.
 */
public final class WindowPlacement {
	/** Half the side of the box kept free around the crosshair, in GUI pixels. */
	static final int CROSSHAIR_HALF = 9;
	/** The hotbar, the off-hand slot, and the health, hunger and XP rows above it. */
	static final int HOTBAR_HALF_WIDTH = 122;
	static final int HOTBAR_ZONE_HEIGHT = 60;
	/** Narrowest a window gets when it is squeezed in beside the quest log. */
	public static final int MIN_BESIDE_LOG = 160;
	/** Space kept between the log and a window beside it, and between that window and the screen edge. */
	static final int BESIDE_GAP = 6;

	public record Rect(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		int overlap(Rect other) {
			int w = Math.min(right(), other.right()) - Math.max(x, other.x);
			int h = Math.min(bottom(), other.bottom()) - Math.max(y, other.y);
			return w > 0 && h > 0 ? w * h : 0;
		}
	}

	public record Point(int x, int y) {
	}

	private WindowPlacement() {
	}

	/** The areas windows must not cover. */
	public static List<Rect> keepOut(int guiWidth, int guiHeight) {
		int cx = guiWidth / 2;
		int cy = guiHeight / 2;
		return List.of(
				new Rect(cx - CROSSHAIR_HALF, cy - CROSSHAIR_HALF, 2 * CROSSHAIR_HALF, 2 * CROSSHAIR_HALF),
				new Rect(cx - HOTBAR_HALF_WIDTH, guiHeight - HOTBAR_ZONE_HEIGHT, 2 * HOTBAR_HALF_WIDTH,
						HOTBAR_ZONE_HEIGHT));
	}

	/**
	 * The width a window of {@code wanted} width gets next to the {@code soft} areas (the quest log): the wanted
	 * width when it fits in the free space to the right of them, otherwise that free space, but never under
	 * {@link #MIN_BESIDE_LOG} (and never over {@code wanted}). The window wraps its text to what it gets.
	 */
	public static int widthBeside(int guiWidth, int wanted, List<Rect> soft) {
		int right = 0;
		for (Rect area : soft) {
			right = Math.max(right, area.right());
		}
		if (right == 0) {
			return wanted;
		}
		int free = guiWidth - right - 2 * BESIDE_GAP;
		return wanted <= free ? wanted : Math.min(wanted, Math.max(MIN_BESIDE_LOG, free));
	}

	/** {@link #resolve(int, int, int, int, int, int, List)} with nothing else to avoid. */
	public static Point resolve(int guiWidth, int guiHeight, int x, int y, int width, int height) {
		return resolve(guiWidth, guiHeight, x, y, width, height, List.of());
	}

	/**
	 * Where a window of {@code width} x {@code height} that would like to sit at ({@code x}, {@code y}) goes:
	 * there when it is free, otherwise the nearest spot beside, above or below the areas to keep free, always
	 * inside the screen. If no spot is free (a tiny screen), the one that covers the least. The crosshair and
	 * the hotbar come first; {@code soft} areas (the quest log) are avoided only when that costs nothing there.
	 */
	public static Point resolve(int guiWidth, int guiHeight, int x, int y, int width, int height, List<Rect> soft) {
		List<Rect> hard = keepOut(guiWidth, guiHeight);
		List<Rect> avoid = new java.util.ArrayList<>(hard);
		avoid.addAll(soft);
		int[] xs = new int[1 + 2 * avoid.size()];
		int[] ys = new int[xs.length];
		xs[0] = clamp(x, 0, guiWidth - width);
		ys[0] = clamp(y, 0, guiHeight - height);
		for (int i = 0; i < avoid.size(); i++) {
			Rect area = avoid.get(i);
			xs[1 + 2 * i] = clamp(area.x() - width - 1, 0, guiWidth - width);
			xs[2 + 2 * i] = clamp(area.right() + 1, 0, guiWidth - width);
			ys[1 + 2 * i] = clamp(area.y() - height - 1, 0, guiHeight - height);
			ys[2 + 2 * i] = clamp(area.bottom() + 1, 0, guiHeight - height);
		}
		Point best = new Point(xs[0], ys[0]);
		long bestCovered = covered(hard, soft, best, width, height);
		long bestDistance = (long) (best.x() - x) * (best.x() - x) + (long) (best.y() - y) * (best.y() - y);
		for (int cx : xs) {
			for (int cy : ys) {
				Point candidate = new Point(cx, cy);
				long covered = covered(hard, soft, candidate, width, height);
				long distance = (long) (cx - x) * (cx - x) + (long) (cy - y) * (cy - y);
				if (covered < bestCovered || (covered == bestCovered && distance < bestDistance)) {
					best = candidate;
					bestCovered = covered;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** Covered area, the crosshair and hotbar weighing far more than the soft areas. */
	private static long covered(List<Rect> hard, List<Rect> soft, Point at, int width, int height) {
		Rect window = new Rect(at.x(), at.y(), width, height);
		long total = 0;
		for (Rect area : hard) {
			total += 1_000L * window.overlap(area);
		}
		for (Rect area : soft) {
			total += window.overlap(area);
		}
		return total;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}
