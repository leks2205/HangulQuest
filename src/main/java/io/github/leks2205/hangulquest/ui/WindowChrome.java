package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** What every HUD window shares: where it sits on the screen, the "[H] close" footer and the "+N" badge. */
public final class WindowChrome {
	/** What the footer needs: the player's hint-key label (such as "[H]") and how many windows wait. */
	public record Info(String keyLabel, int waiting) {
	}

	/** Narrowest a window gets, so the footer always fits. */
	public static final int MIN_WIDTH = 130;

	private static String memoKey = "";
	private static Component memo = Component.empty();

	private WindowChrome() {
	}

	/** Room a window leaves at its bottom for the footer. */
	public static int footerHeight(Font font) {
		return font.lineHeight + 5;
	}

	/** The window's top-left corner: the wanted spot, moved off the crosshair and the hotbar. */
	public static WindowPlacement.Point place(int guiWidth, int guiHeight, int x, int y, int width, int height) {
		return WindowPlacement.resolve(guiWidth, guiHeight, x, y, width, height);
	}

	/** The same, also keeping clear of {@code soft} areas such as the quest log when there is room. */
	public static WindowPlacement.Point place(int guiWidth, int guiHeight, int x, int y, int width, int height,
											  java.util.List<WindowPlacement.Rect> soft) {
		return WindowPlacement.resolve(guiWidth, guiHeight, x, y, width, height, soft);
	}

	/** "[H] 닫기 · close" at the bottom left, "+N" at the bottom right when more windows wait. */
	public static void footer(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, Info info) {
		int line = y + height - font.lineHeight - 3;
		int badgeWidth = 0;
		if (info.waiting() > 0) {
			String badge = "+" + info.waiting();
			badgeWidth = font.width(badge) + 6;
			Draw.label(graphics, font, badge, x + width - badgeWidth + 1, line, Draw.GOLD);
		}
		Draw.truncated(graphics, font, label(info.keyLabel()), x + 7, line, width - 14 - badgeWidth, Draw.DIM);
	}

	private static Component label(String keyLabel) {
		String key = keyLabel + "|" + Lang.current().code();
		if (!key.equals(memoKey)) {
			memo = Component.empty()
					.append(Component.literal(keyLabel + " "))
					.append(KoText.ko("닫기"))
					.append(Component.literal(" · " + I18n.t("hangulquest.ui.hud.window_close")));
			memoKey = key;
		}
		return memo;
	}
}
