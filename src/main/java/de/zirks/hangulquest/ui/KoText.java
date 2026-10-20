package de.zirks.hangulquest.ui;

import de.zirks.hangulquest.HangulQuestClient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Korean text always uses the bundled font; German stays on the default font. */
public final class KoText {
	private KoText() {
	}

	public static Style style() {
		return HangulQuestClient.hangul();
	}

	public static MutableComponent ko(String text) {
		return Component.literal(text).setStyle(style());
	}

	public static Component line(String korean, String german) {
		if (german == null || german.isBlank()) {
			return ko(korean);
		}
		return Component.empty().append(ko(korean)).append(Component.literal("  " + german));
	}
}
