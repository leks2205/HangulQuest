package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Draws a {@link CardView} in the quiz screen and reports where the player can click. The layout is
 * measured first (nothing drawn) and then drawn with the same code, so the panel always fits.
 */
public final class CardPainter {
	/** Hit action for the main button. */
	public static final int CONFIRM = -1;
	/** Hit action for the row of placed tiles (takes the last one back). */
	public static final int UNDO = -2;
	/** Hit action for the "listen" button on the feedback. */
	public static final int LISTEN = -3;

	private static final int ROW = 18;
	private static final int TILE_W = 26;
	private static final int TILE_H = 22;
	private static final int BUTTON_H = 16;

	/** A clickable rectangle; {@code action} is an item index or {@link #CONFIRM} or {@link #UNDO}. */
	public record Hit(int x, int y, int w, int h, int action) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private CardPainter() {
	}

	/**
	 * Draws the card centred on the screen. {@code hits} is cleared and refilled. {@code listen}: the word has a
	 * recording, so the feedback offers the "listen" button and key.
	 */
	public static void paint(GuiGraphicsExtractor g, Font font, CardView view, int screenWidth, int screenHeight,
							 int mouseX, int mouseY, List<Hit> hits, boolean listen) {
		hits.clear();
		int width = Math.min(340, screenWidth - 12);
		int height = run(null, font, view, 0, 0, width, 0, 0, null, listen);
		int x = (screenWidth - width) / 2;
		int y = Math.max(4, (screenHeight - height) / 2);
		Draw.panel(g, x, y, width, height, 0);
		run(g, font, view, x, y, width, mouseX, mouseY, hits, listen);
	}

	/** Lays the card out from the top; draws when {@code g} is not null. Returns the panel height. */
	private static int run(GuiGraphicsExtractor g, Font font, CardView view, int x, int y, int width, int mx, int my,
						   List<Hit> hits, boolean listen) {
		int inner = width - 16;
		boolean draw = g != null;
		if (draw) {
			Draw.korean(g, font, view.titleKo(), x + 8, y + 6, Draw.GOLD);
			FeedbackCard feedback = view.feedback();
			if (feedback != null && view.layout() != CardView.Layout.BUTTONS) {
				Draw.korean(g, font, feedback.correct() ? "정답!" : "다시!", x + width - 44, y + 6,
						feedback.correct() ? Draw.GREEN : Draw.RED);
			}
		}
		int cursor = y + 20;

		if (draw) {
			if (view.icon() == null) {
				Draw.question(g, font, x + 8, cursor, 16);
			} else {
				Draw.itemIcon(g, view.icon(), x + 8, cursor);
			}
			if (view.mainKorean()) {
				Draw.korean(g, font, view.main(), x + 30, cursor + 2, Draw.WHITE);
			} else {
				Draw.truncated(g, font, Component.literal(view.main()), x + 30, cursor + 2, width - 40, Draw.WHITE);
			}
		}
		cursor += 24;

		if (!view.message().isEmpty()) {
			Component message = Component.literal(view.message());
			if (draw) {
				Draw.wrapped(g, font, message, x + 8, cursor, inner, Draw.GRAY, 2);
			}
			cursor += Draw.wrappedHeight(font, message, inner, 2) + 4;
		}

		switch (view.layout()) {
			case ROWS -> cursor = rows(g, font, view, x, cursor, width, mx, my, hits);
			case BUTTONS -> cursor = buttons(g, font, view, x, cursor, width, mx, my, hits);
			case TILES -> cursor = tiles(g, font, view, x, cursor, width, mx, my, hits);
		}

		if (!view.button().isEmpty()) {
			int buttonW = Math.min(inner, Math.max(90, font.width(view.button()) + 24));
			int buttonX = x + (width - buttonW) / 2;
			if (draw) {
				button(g, font, view.button(), buttonX, cursor, buttonW, BUTTON_H, mx, my);
				hits.add(new Hit(buttonX, cursor, buttonW, BUTTON_H, CONFIRM));
			}
			cursor += BUTTON_H + 6;
		}

		if (view.feedback() != null) {
			cursor = feedback(g, font, view.feedback(), x, cursor, width, listen, mx, my, hits);
		}

		if (draw) {
			String keys = listen && view.feedback() != null
					? view.keys() + "  " + I18n.t("hangulquest.ui.audio.keys", "L") : view.keys();
			Draw.truncated(g, font, Component.literal(keys), x + 8, cursor + 2, inner, Draw.DIM);
		}
		return cursor + 14 - y;
	}

	private static int rows(GuiGraphicsExtractor g, Font font, CardView view, int x, int cursor, int width, int mx,
							int my, List<Hit> hits) {
		for (int i = 0; i < view.items().size(); i++) {
			CardView.Item item = view.items().get(i);
			int rowY = cursor + i * ROW;
			if (g != null) {
				boolean usable = item.state() == CardView.State.NORMAL;
				boolean hover = usable && mx >= x + 4 && mx < x + width - 4 && my >= rowY && my < rowY + ROW;
				if (hover) {
					g.fill(x + 4, rowY - 2, x + width - 4, rowY + ROW - 4, 0x33FFFFFF);
				}
				if (usable) {
					hits.add(new Hit(x + 4, rowY - 2, width - 8, ROW, i));
				}
				int color = color(item.state());
				Draw.label(g, font, (i + 1) + ".", x + 10, rowY, Draw.DIM);
				if (item.korean()) {
					Draw.korean(g, font, item.text(), x + 28, rowY, color);
				} else {
					Draw.truncated(g, font, Component.literal(item.text()), x + 28, rowY, width - 44, color);
				}
			}
		}
		return cursor + view.items().size() * ROW + 4;
	}

	private static int buttons(GuiGraphicsExtractor g, Font font, CardView view, int x, int cursor, int width,
							   int mx, int my, List<Hit> hits) {
		int count = Math.max(1, view.items().size());
		int gap = 4;
		int each = (width - 16 - gap * (count - 1)) / count;
		for (int i = 0; i < view.items().size(); i++) {
			int bx = x + 8 + i * (each + gap);
			if (g != null) {
				button(g, font, (i + 1) + " " + view.items().get(i).text(), bx, cursor, each, BUTTON_H + 2, mx, my);
				hits.add(new Hit(bx, cursor, each, BUTTON_H + 2, i));
			}
		}
		return cursor + (view.items().isEmpty() ? 0 : BUTTON_H + 8);
	}

	private static int tiles(GuiGraphicsExtractor g, Font font, CardView view, int x, int cursor, int width, int mx,
							 int my, List<Hit> hits) {
		// The answer slots, centred.
		int slots = Math.max(1, view.slots());
		int slotsW = slots * (TILE_W + 2) - 2;
		int slotX = x + (width - slotsW) / 2;
		if (g != null) {
			for (int i = 0; i < slots; i++) {
				int sx = slotX + i * (TILE_W + 2);
				g.fill(sx, cursor, sx + TILE_W, cursor + TILE_H, 0x33000000);
				g.fill(sx, cursor + TILE_H - 1, sx + TILE_W, cursor + TILE_H, Draw.BORDER);
				if (i < view.assembled().size()) {
					int textW = font.width(view.assembled().get(i));
					Draw.korean(g, font, view.assembled().get(i), sx + (TILE_W - textW) / 2, cursor + 7, Draw.WHITE);
				}
			}
			if (!view.assembled().isEmpty() && view.feedback() == null) {
				hits.add(new Hit(slotX, cursor, slotsW, TILE_H, UNDO));
			}
		}
		cursor += TILE_H + 8;

		// The tiles, wrapped to the panel width.
		int perRow = Math.max(1, (width - 16) / (TILE_W + 4));
		int rowCount = (view.items().size() + perRow - 1) / perRow;
		for (int i = 0; i < view.items().size(); i++) {
			CardView.Item item = view.items().get(i);
			int col = i % perRow;
			int inRow = Math.min(perRow, view.items().size() - (i / perRow) * perRow);
			int rowW = inRow * (TILE_W + 4) - 4;
			int tx = x + (width - rowW) / 2 + col * (TILE_W + 4);
			int ty = cursor + (i / perRow) * (TILE_H + 6);
			if (g != null) {
				boolean usable = item.state() == CardView.State.NORMAL;
				boolean hover = usable && mx >= tx && mx < tx + TILE_W && my >= ty && my < ty + TILE_H;
				int fill = item.state() == CardView.State.USED ? 0x22FFFFFF : hover ? 0x55FFFFFF : 0x33FFFFFF;
				g.fill(tx, ty, tx + TILE_W, ty + TILE_H, fill);
				g.fill(tx, ty, tx + TILE_W, ty + 1, Draw.BORDER);
				g.fill(tx, ty + TILE_H - 1, tx + TILE_W, ty + TILE_H, Draw.BORDER);
				g.fill(tx, ty, tx + 1, ty + TILE_H, Draw.BORDER);
				g.fill(tx + TILE_W - 1, ty, tx + TILE_W, ty + TILE_H, Draw.BORDER);
				int textW = font.width(item.text());
				Draw.korean(g, font, item.text(), tx + (TILE_W - textW) / 2, ty + 7, color(item.state()));
				if (i < 9) {
					Draw.label(g, font, String.valueOf(i + 1), tx + 2, ty + 1, Draw.DIM);
				}
				if (item.state() != CardView.State.DISABLED) {
					hits.add(new Hit(tx, ty, TILE_W, TILE_H, i));
				}
			}
		}
		return cursor + rowCount * (TILE_H + 6) + 2;
	}

	private static int feedback(GuiGraphicsExtractor g, Font font, FeedbackCard card, int x, int cursor, int width,
								boolean listen, int mx, int my, List<Hit> hits) {
		int inner = width - 16;
		if (g != null) {
			g.fill(x + 8, cursor, x + width - 8, cursor + 1, Draw.BORDER);
		}
		cursor += 5;
		int line = card.pron() == null ? 11 : 22;
		if (g != null) {
			// the word, its pronunciation under it when that differs, then the gloss
			int textWidth = inner - 22 - (listen ? listenWidth(font) + 6 : 0);
			Draw.itemIcon(g, card.icon(), x + 8, cursor);
			Draw.korean(g, font, card.ko(), x + 30, cursor, Draw.WHITE);
			if (card.pron() != null) {
				Draw.korean(g, font, card.pron(), x + 30, cursor + 11, Draw.DIM);
			}
			Draw.truncated(g, font, Component.literal(card.gloss()), x + 30, cursor + line, textWidth, Draw.GRAY);
			if (listen) {
				hits.add(listenButton(g, font, x + width - 8, cursor, mx, my));
			}
		}
		cursor += line + 13;
		if (card.yours() != null) {
			if (g != null) {
				Draw.truncated(g, font, Component.literal(I18n.t("hangulquest.ui.card.yours", card.yours())), x + 8,
						cursor, inner, Draw.RED);
			}
			cursor += 12;
		}
		if (card.sentenceKo() != null) {
			Component sentence = KoText.ko(card.sentenceKo());
			if (g != null) {
				Draw.wrapped(g, font, sentence, x + 8, cursor, inner, Draw.WHITE, 2);
			}
			cursor += Draw.wrappedHeight(font, sentence, inner, 2) + 2;
			if (card.sentenceGloss() != null) {
				Component gloss = Component.literal(card.sentenceGloss());
				if (g != null) {
					Draw.wrapped(g, font, gloss, x + 8, cursor, inner, Draw.GRAY, 2);
				}
				cursor += Draw.wrappedHeight(font, gloss, inner, 2) + 2;
			}
		}
		return cursor + 4;
	}

	/** The "listen" button with its right edge at {@code right}; returns where it can be clicked. */
	static Hit listenButton(GuiGraphicsExtractor g, Font font, int right, int y, int mx, int my) {
		int w = listenWidth(font);
		button(g, font, I18n.t("hangulquest.ui.audio.listen"), right - w, y, w, 14, mx, my);
		return new Hit(right - w, y, w, 14, LISTEN);
	}

	private static int listenWidth(Font font) {
		return Math.max(40, font.width(I18n.t("hangulquest.ui.audio.listen")) + 12);
	}

	private static void button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, int h, int mx,
							   int my) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
		g.fill(x, y, x + w, y + h, hover ? 0x55FFFFFF : 0x33FFFFFF);
		g.fill(x, y, x + w, y + 1, Draw.BORDER);
		g.fill(x, y + h - 1, x + w, y + h, Draw.BORDER);
		g.fill(x, y, x + 1, y + h, Draw.BORDER);
		g.fill(x + w - 1, y, x + w, y + h, Draw.BORDER);
		Draw.truncated(g, font, Component.literal(label), x + Math.max(3, (w - font.width(label)) / 2), y + (h - 8) / 2,
				w - 6, Draw.WHITE);
	}

	private static int color(CardView.State state) {
		return switch (state) {
			case CORRECT -> Draw.GREEN;
			case WRONG -> Draw.RED;
			case DISABLED, USED -> Draw.DIM;
			default -> Draw.WHITE;
		};
	}
}
