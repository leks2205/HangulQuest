package io.github.leks2205.hangulquest.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.hangul.DubeolsikLayout;
import io.github.leks2205.hangulquest.util.hangul.HangulCompose;
import io.github.leks2205.hangulquest.util.hangul.HangulInput;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A one-line text field that types Korean on any keyboard. The letter keys are read by position
 * (scancodes name US positions), so a German QWERTZ keyboard needs no Z/Y swap; a Korean
 * syllable an OS input method already composed is accepted as it is. A screen forwards
 * {@link #keyPressed}, {@link #keyReleased} and {@link #charTyped} and calls {@link #extractRenderState}.
 *
 * <p>The right Alt key (or whatever {@link #setToggleKey} sets) switches between Korean and Latin;
 * Tab shows a map of which Korean letter sits on each key.
 */
public final class HangulTextField {
	public static final int HEIGHT = 20;
	private static final int KEY_TAB = com.mojang.blaze3d.platform.InputConstants.KEY_TAB;
	private static final int KEY_LEFT_SHIFT = com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT;
	private static final int KEY_RIGHT_SHIFT = com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT;
	private static final int KEY_W = 22;
	private static final int KEY_H = 20;
	private static final int[] ROW_OFFSET = {0, 6, 16};

	private final HangulInput input = new HangulInput();
	private String placeholder = "";
	private boolean keyMapVisible;
	private boolean shiftDown;
	/** The caption printed on each key of this keyboard, filled the first time the map is drawn. */
	private String[][] captions;
	private String shownText;
	private Component shown = Component.empty();
	private int shownOffset;

	public HangulInput input() {
		return input;
	}

	public String text() {
		return input.text();
	}

	public boolean isEmpty() {
		return input.text().isEmpty();
	}

	public void clear() {
		input.clear();
	}

	/** Grey text shown while the field is empty. */
	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder == null ? "" : placeholder;
	}

	public void setToggleKey(int key) {
		input.setToggleKey(key);
	}

	public void setMaxLength(int length) {
		input.setMaxLength(length);
	}

	public void setKeyMapVisible(boolean visible) {
		keyMapVisible = visible;
	}

	public boolean keyMapVisible() {
		return keyMapVisible;
	}

	// ------------------------------------------------------------------ input

	/** Returns true when the field used the key; Enter, Esc and the rest are for the screen. */
	public boolean keyPressed(KeyEvent event) {
		trackShift(event.key(), true);
		if (event.key() == KEY_TAB) {
			keyMapVisible = !keyMapVisible;
			return true;
		}
		return input.key(event.key(), event.modifiers());
	}

	public boolean keyReleased(KeyEvent event) {
		trackShift(event.key(), false);
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return input.character(event.codepoint());
	}

	private void trackShift(int key, boolean down) {
		if (key == KEY_LEFT_SHIFT || key == KEY_RIGHT_SHIFT) {
			shiftDown = down;
		}
	}

	// ----------------------------------------------------------------- drawing

	/** Draws the field at {@code x, y} with the given width and {@link #HEIGHT}; returns the height used. */
	public int extractRenderState(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		graphics.fill(x, y, x + width, y + HEIGHT, 0xFF0E0E12);
		int border = input.korean() ? Draw.GOLD : Draw.BLUE;
		graphics.fill(x, y, x + width, y + 1, border);
		graphics.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, border);
		graphics.fill(x, y, x + 1, y + HEIGHT, border);
		graphics.fill(x + width - 1, y, x + width, y + HEIGHT, border);

		int badge = 20;
		Draw.korean(graphics, font, input.korean() ? "한" : "A", x + width - badge + 4, y + 6, border);
		int textX = x + 6;
		int room = width - 12 - badge;
		int textY = y + (HEIGHT - font.lineHeight) / 2 + 1;
		String text = input.text();
		if (text.isEmpty()) {
			Draw.truncated(graphics, font, Component.literal(placeholder), textX, textY, room, Draw.DIM);
		} else {
			fit(font, text, room);
			graphics.text(font, shown, textX, textY, Draw.WHITE, false);
			int end = textX + font.width(shown);
			if (input.automaton().composing()) {
				int lastStart = textX + font.width(styled(text.substring(shownOffset, text.length() - 1)));
				graphics.fill(lastStart, textY + font.lineHeight, end, textY + font.lineHeight + 1, Draw.GOLD);
			} else if ((System.currentTimeMillis() / 500) % 2 == 0) {
				graphics.fill(end + 1, textY - 1, end + 2, textY + font.lineHeight, Draw.WHITE);
			}
		}
		int used = HEIGHT;
		if (keyMapVisible) {
			used += 4 + extractKeyMap(graphics, font, x, y + HEIGHT + 4);
		}
		return used;
	}

	/** Height the field takes with or without the key map. */
	public int height() {
		return keyMapVisible ? HEIGHT + 4 + keyMapHeight() : HEIGHT;
	}

	/** Keeps the end of a long text in view: the start is dropped until the rest fits. */
	private void fit(Font font, String text, int room) {
		if (text.equals(shownText)) {
			return;
		}
		int offset = 0;
		Component component = styled(text);
		while (offset < text.length() - 1 && font.width(component) > room) {
			offset++;
			component = styled(text.substring(offset));
		}
		shownText = text;
		shownOffset = offset;
		shown = component;
	}

	/** The text with Hangul in the bundled font and everything else in the default one. */
	public static Component styled(String text) {
		MutableComponent out = Component.empty();
		StringBuilder run = new StringBuilder();
		boolean hangul = false;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			boolean isHangul = HangulCompose.isHangul(cp);
			if (run.length() > 0 && isHangul != hangul) {
				out.append(run(run.toString(), hangul));
				run.setLength(0);
			}
			hangul = isHangul;
			run.appendCodePoint(cp);
			i += Character.charCount(cp);
		}
		if (run.length() > 0) {
			out.append(run(run.toString(), hangul));
		}
		return out;
	}

	private static Component run(String text, boolean hangul) {
		return hangul ? KoText.ko(text) : Component.literal(text);
	}

	/** One piece of text in the font that has its glyphs. */
	public static void drawMixed(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, styled(text), x, y, color, false);
	}

	// ---------------------------------------------------------------- key map

	private int keyMapHeight() {
		return 12 + DubeolsikLayout.ROWS.length * (KEY_H + 2);
	}

	/** Which Korean letter sits on which key; Shift shows the doubled consonants. Returns its height. */
	private int extractKeyMap(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		Draw.label(graphics, font, I18n.t("hangulquest.ui.typed.keymap"), x, y, Draw.GRAY);
		int top = y + 12;
		String[][] labels = captions(DubeolsikLayout.ROWS);
		for (int row = 0; row < DubeolsikLayout.ROWS.length; row++) {
			for (int col = 0; col < DubeolsikLayout.ROWS[row].length; col++) {
				int key = DubeolsikLayout.ROWS[row][col];
				char jamo = DubeolsikLayout.jamoFor(key, shiftDown);
				boolean changed = jamo != 0 && jamo != DubeolsikLayout.jamoFor(key, false);
				int kx = x + ROW_OFFSET[row] + col * (KEY_W + 2);
				int ky = top + row * (KEY_H + 2);
				graphics.fill(kx, ky, kx + KEY_W, ky + KEY_H, jamo == 0 ? 0xFF17171C : 0xFF23232B);
				Draw.label(graphics, font, labels[row][col], kx + 2, ky + 1, Draw.DIM);
				if (jamo != 0) {
					String letter = String.valueOf(jamo);
					Draw.korean(graphics, font, letter, kx + (KEY_W - font.width(KoText.ko(letter))) / 2 + 2, ky + 9,
							changed ? Draw.GOLD : Draw.WHITE);
				}
			}
		}
		return keyMapHeight();
	}

	/** Captions come from the keyboard in use, so a QWERTZ player sees the letters printed on the keys. */
	private String[][] captions(int[][] rows) {
		if (captions == null) {
			captions = new String[rows.length][];
			for (int row = 0; row < rows.length; row++) {
				captions[row] = new String[rows[row].length];
				for (int col = 0; col < rows[row].length; col++) {
					captions[row][col] = caption(rows[row][col]);
				}
			}
		}
		return captions;
	}

	private static String caption(int key) {
		try {
			String name = keyName(key);
			if (name.codePointCount(0, name.length()) == 1) {
				return name.toUpperCase(java.util.Locale.ROOT);
			}
		} catch (RuntimeException unavailable) {
			// The US label is right for most keyboards anyway.
		}
		return DubeolsikLayout.usLabel(key);
	}

	/** The name of a key for the footer hint, e.g. "Right Alt". */
	public static String keyName(int key) {
		return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
	}
}
