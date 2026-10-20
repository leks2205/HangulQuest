package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.util.TextClean;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Small drawing helpers shared by the HUD cards and the journal screen. */
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

	private Draw() {
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
		graphics.fill(x, y, x + width, y + height, BG);
		graphics.fill(x, y, x + width, y + 1, BORDER);
		graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
		graphics.fill(x, y, x + 1, y + height, BORDER);
		graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
	}

	public static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, Component.literal(text), x, y, color, SHADOW);
	}

	public static void korean(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, KoText.ko(text), x, y, color, SHADOW);
	}

	public static void wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
							  int width, int color, int maxLines) {
		List<FormattedCharSequenceWrapper> ignored = null;
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
		int drawn = 0;
		for (net.minecraft.util.FormattedCharSequence line : lines) {
			if (maxLines > 0 && drawn >= maxLines) {
				break;
			}
			graphics.text(font, line, x, y + drawn * (font.lineHeight + 1), color, SHADOW);
			drawn++;
		}
	}

	public static int wrappedHeight(Font font, Component text, int width, int maxLines) {
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
		int count = Math.min(lines.size(), maxLines);
		return count <= 0 ? 0 : count * (font.lineHeight + 1);
	}

	public static void truncated(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
								 int maxWidth, int color) {
		truncated(graphics, font, text, x, y, maxWidth, color, SHADOW);
	}

	/** Same, but with an explicit shadow flag — HUD text without a panel needs the vanilla shadow. */
	public static void truncated(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
								 int maxWidth, int color, boolean shadow) {
		if (font.width(text) <= maxWidth) {
			graphics.text(font, text, x, y, color, shadow);
			return;
		}
		String plain = text.getString();
		String cut = TextClean.truncate(plain, Math.max(3, plain.length() * maxWidth / Math.max(1, font.width(text))));
		graphics.text(font, Component.literal(cut), x, y, color, shadow);
	}

	/** A neutral "?" tile shown instead of an item icon until the answer is revealed. */
	public static void question(GuiGraphicsExtractor graphics, Font font, int x, int y, int size) {
		graphics.fill(x, y, x + size, y + size, 0xFF23232B);
		graphics.fill(x, y, x + size, y + 1, BORDER);
		graphics.fill(x, y + size - 1, x + size, y + size, BORDER);
		graphics.fill(x, y, x + 1, y + size, BORDER);
		graphics.fill(x + size - 1, y, x + size, y + size, BORDER);
		String mark = "?";
		int width = font.width(mark);
		graphics.text(font, Component.literal(mark), x + (size - width) / 2,
				y + (size - font.lineHeight) / 2 + 1, GOLD, SHADOW);
	}

	public static void itemIcon(GuiGraphicsExtractor graphics, String itemId, int x, int y) {
		if (itemId == null || itemId.isBlank()) {
			return;
		}
		try {
			Identifier id = Identifier.parse(itemId);
			BuiltInRegistries.ITEM.get(id).ifPresent(holder ->
					graphics.item(new ItemStack(holder.value()), x, y));
		} catch (RuntimeException ignored) {
			// unknown item: no icon
		}
	}

	private record FormattedCharSequenceWrapper(Object value) {
	}

	public static Component koreanLine(String korean, String german) {
		return KoText.line(korean, german);
	}
}
