package io.github.leks2205.hangulquest.ui.dialogue;

import java.util.ArrayList;
import java.util.List;

/** Places the words of a line left to right and wraps them at a width. Pure: the caller measures. */
public final class DialogueLayout {
	/** Where word {@code index} goes, relative to the top left of the line block. */
	public record Placed(int index, int x, int y, int width) {
	}

	public interface Measure {
		int width(String word);
	}

	/** The placed words and the height of the whole block. */
	public record Flow(List<Placed> words, int height) {
		/** The word under a point (relative to the block), or -1. */
		public int hit(int x, int y, int lineHeight) {
			for (Placed placed : words) {
				if (x >= placed.x() && x < placed.x() + placed.width() && y >= placed.y() && y < placed.y() + lineHeight) {
					return placed.index();
				}
			}
			return -1;
		}
	}

	private DialogueLayout() {
	}

	public static Flow flow(List<String> words, Measure measure, int maxWidth, int spaceWidth, int lineHeight) {
		List<Placed> placed = new ArrayList<>();
		int x = 0;
		int y = 0;
		for (int i = 0; i < words.size(); i++) {
			int width = measure.width(words.get(i));
			if (x > 0 && x + width > maxWidth) {
				x = 0;
				y += lineHeight;
			}
			placed.add(new Placed(i, x, y, width));
			x += width + spaceWidth;
		}
		return new Flow(placed, words.isEmpty() ? 0 : y + lineHeight);
	}
}
