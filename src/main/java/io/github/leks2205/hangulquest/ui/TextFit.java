package io.github.leks2205.hangulquest.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts styled text to a pixel width, keeping the style of every kept piece. Pure: the caller says
 * how wide a piece is, so the logic runs without a font. The style type is opaque here (in the game
 * it is a {@code Style}, so Korean keeps its font after the cut).
 */
public final class TextFit {
	private TextFit() {
	}

	/** One run of text in one style. */
	public record Piece<S>(String text, S style) {
	}

	public interface Measure<S> {
		int width(String text, S style);
	}

	/**
	 * @param pieces the kept runs, the last one possibly shortened; the caller appends the ellipsis
	 * @param cut true when text was dropped, so an ellipsis belongs behind {@code pieces}
	 */
	public record Fitted<S>(List<Piece<S>> pieces, boolean cut) {
	}

	public static <S> Fitted<S> fit(List<Piece<S>> text, int maxWidth, int ellipsisWidth, Measure<S> measure) {
		int total = 0;
		for (Piece<S> piece : text) {
			total += measure.width(piece.text(), piece.style());
		}
		if (total <= maxWidth) {
			return new Fitted<>(text, false);
		}
		int budget = Math.max(0, maxWidth - ellipsisWidth);
		List<Piece<S>> kept = new ArrayList<>();
		int used = 0;
		for (Piece<S> piece : text) {
			int width = measure.width(piece.text(), piece.style());
			if (used + width <= budget) {
				kept.add(piece);
				used += width;
				continue;
			}
			String head = longestHead(piece.text(), budget - used, piece.style(), measure);
			if (!head.isEmpty()) {
				kept.add(new Piece<>(head, piece.style()));
			}
			break;
		}
		return new Fitted<>(kept, true);
	}

	/** The longest prefix that fits, never splitting a surrogate pair. */
	static <S> String longestHead(String text, int room, S style, Measure<S> measure) {
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			int end = safeEnd(text, mid);
			if (measure.width(text.substring(0, end), style) <= room) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}
		return text.substring(0, safeEnd(text, low));
	}

	private static int safeEnd(String text, int end) {
		if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
				&& Character.isLowSurrogate(text.charAt(end))) {
			return end - 1;
		}
		return end;
	}
}
