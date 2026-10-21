package io.github.leks2205.hangulquest.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small drawing helpers shared by the HUD cards and the journal screen. Wrapped, truncated and
 * Korean text and item stacks are cached: they are the same every frame, and building them
 * (splitting, measuring, registry lookups) used to be most of what the UI allocated.
 */
public final class Draw {
	/** Flat text: the journal's dark ink on parchment is unreadable with MC's drop shadow. */
	private static final boolean SHADOW = false;
	public static final int BG = 0xE6151518;
	public static final int BG_SOFT = 0xB0151518;
	public static final int BORDER = 0xFF3B3B45;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFB8B8C2;
	public static final int DIM = 0xFF7C7C86;
	public static final int GOLD = 0xFFFFC857;
	public static final int GREEN = 0xFF8CE99A;
	public static final int RED = 0xFFFF6B6B;
	public static final int BLUE = 0xFF8AC6FF;

	/** Default-font ellipsis: the bundled Hangul font is not promised to carry it. */
	private static final FormattedText ELLIPSIS = FormattedText.of("…");
	/** Cached widths go stale after a resource reload; this bounds how long. */
	private static final long CACHE_TTL_NANOS = 20_000_000_000L;

	private record TextKey(Component text, int width) {
	}

	private static final LruCache<TextKey, FormattedCharSequence> FITTED = new LruCache<>(256);
	private static final LruCache<TextKey, List<FormattedCharSequence>> WRAPPED = new LruCache<>(128);
	private static final LruCache<String, FormattedCharSequence> KOREAN = new LruCache<>(512);
	private static final LruCache<String, ItemStack> STACKS = new LruCache<>(1024);

	private static Font cacheFont;
	private static long cacheBorn;

	private Draw() {
	}

	/** Drops the width-dependent caches when the font changed or they are old enough to doubt. */
	private static void sync(Font font) {
		long now = System.nanoTime();
		if (font != cacheFont || now - cacheBorn > CACHE_TTL_NANOS) {
			FITTED.clear();
			WRAPPED.clear();
			cacheFont = font;
			cacheBorn = now;
		}
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
		graphics.fill(x, y, x + width, y + height, BG);
		graphics.fill(x, y, x + width, y + 1, BORDER);
		graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
		graphics.fill(x, y, x + 1, y + height, BORDER);
		graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
	}

	public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, SHADOW);
	}

	public static void korean(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, KOREAN.get(text, Draw::koreanSequence), x, y, color, SHADOW);
	}

	private static FormattedCharSequence koreanSequence(String text) {
		return KoText.ko(text).getVisualOrderText();
	}

	/** Draws text that is already in visual order, e.g. a cached {@link #fit} or a line of {@link #lines}. */
	public static void text(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
							int color, boolean shadow) {
		graphics.text(font, text, x, y, color, shadow);
	}

	// ------------------------------------------------------------------ wrapping

	/** The text wrapped to the width. Cached per text and width; do not modify the list. */
	public static List<FormattedCharSequence> lines(Font font, Component text, int width) {
		sync(font);
		return WRAPPED.get(new TextKey(text, width), Draw::split);
	}

	private static List<FormattedCharSequence> split(TextKey key) {
		return cacheFont.split(key.text(), key.width());
	}

	public static void wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
							  int width, int color, int maxLines) {
		drawLines(graphics, font, lines(font, text, width), x, y, color, maxLines);
	}

	public static int wrappedHeight(Font font, Component text, int width, int maxLines) {
		return linesHeight(font, lines(font, text, width), maxLines);
	}

	/** Draws already wrapped lines, at most {@code maxLines} of them (0 or less means all). */
	public static void drawLines(GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines,
								 int x, int y, int color, int maxLines) {
		int drawn = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (maxLines > 0 && drawn >= maxLines) {
				break;
			}
			graphics.text(font, lines.get(i), x, y + drawn * (font.lineHeight + 1), color, SHADOW);
			drawn++;
		}
	}

	public static int linesHeight(Font font, List<FormattedCharSequence> lines, int maxLines) {
		int count = Math.min(lines.size(), maxLines);
		return count <= 0 ? 0 : count * (font.lineHeight + 1);
	}

	// ---------------------------------------------------------------- truncation

	public static void truncated(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
								 int maxWidth, int color) {
		truncated(graphics, font, text, x, y, maxWidth, color, SHADOW);
	}

	/** Same, but with an explicit shadow flag — HUD text without a panel needs the vanilla shadow. */
	public static void truncated(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
								 int maxWidth, int color, boolean shadow) {
		graphics.text(font, fit(font, text, maxWidth), x, y, color, shadow);
	}

	/**
	 * The text in visual order, cut with an ellipsis when it is wider than {@code maxWidth}. Every kept
	 * piece keeps its style, so Korean stays in the Hangul font. Cached per text and width.
	 */
	public static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
		sync(font);
		return FITTED.get(new TextKey(text, maxWidth), Draw::buildFit);
	}

	private static FormattedCharSequence buildFit(TextKey key) {
		Font font = cacheFont;
		Component text = key.text();
		if (font.width(text) <= key.width()) {
			return text.getVisualOrderText();
		}
		List<TextFit.Piece<Style>> pieces = new ArrayList<>();
		text.visit((style, part) -> {
			if (!part.isEmpty()) {
				pieces.add(new TextFit.Piece<>(part, style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		TextFit.Fitted<Style> fitted = TextFit.fit(pieces, key.width(), font.width(ELLIPSIS),
				(part, style) -> font.width(FormattedText.of(part, style)));
		List<FormattedText> parts = new ArrayList<>();
		for (TextFit.Piece<Style> piece : fitted.pieces()) {
			parts.add(FormattedText.of(piece.text(), piece.style()));
		}
		if (fitted.cut()) {
			parts.add(ELLIPSIS);
		}
		return Language.getInstance().getVisualOrder(FormattedText.composite(parts));
	}

	// ------------------------------------------------------------------- widgets

	/** A neutral "?" tile shown instead of an item icon until the answer is revealed. */
	public static void question(GuiGraphicsExtractor graphics, Font font, int x, int y, int size) {
		graphics.fill(x, y, x + size, y + size, 0xFF23232B);
		graphics.fill(x, y, x + size, y + 1, BORDER);
		graphics.fill(x, y + size - 1, x + size, y + size, BORDER);
		graphics.fill(x, y, x + 1, y + size, BORDER);
		graphics.fill(x + size - 1, y, x + size, y + size, BORDER);
		String mark = "?";
		int width = font.width(mark);
		graphics.text(font, mark, x + (size - width) / 2,
				y + (size - font.lineHeight) / 2 + 1, GOLD, SHADOW);
	}

	public static void itemIcon(GuiGraphicsExtractor graphics, String itemId, int x, int y) {
		if (itemId == null || itemId.isBlank()) {
			return;
		}
		ItemStack stack = STACKS.get(itemId, Draw::stackFor);
		if (stack != null) {
			graphics.item(stack, x, y);
		}
	}

	/** One stack per item id, built once; null for an unknown id so it is not looked up again. */
	private static ItemStack stackFor(String itemId) {
		try {
			Identifier id = Identifier.parse(itemId);
			return BuiltInRegistries.ITEM.get(id).map(holder -> new ItemStack(holder.value())).orElse(null);
		} catch (RuntimeException unknown) {
			return null;
		}
	}

	public static Component koreanLine(String korean, String german) {
		return KoText.line(korean, german);
	}
}
