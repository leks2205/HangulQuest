package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.util.hangul.HangulCompose;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Korean text always uses the bundled font; German stays on the default font. */
public final class KoText {
	/** The style never changes, so it is built once instead of for every Korean string drawn. */
	private static Style style;

	private KoText() {
	}

	public static Style style() {
		Style cached = style;
		if (cached == null) {
			cached = HangulQuestClient.hangul();
			style = cached;
		}
		return cached;
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

	/** A translation with Korean in it: each Hangul run uses the bundled font, the rest stays on the default one. */
	public static Component mixed(String text) {
		MutableComponent out = Component.empty();
		int start = 0;
		boolean hangul = false;
		for (int i = 0; i <= text.length(); i++) {
			boolean now = i < text.length() && HangulCompose.isHangul(text.charAt(i));
			if (i == text.length() || (i > start && now != hangul)) {
				String part = text.substring(start, i);
				out.append(hangul ? ko(part) : Component.literal(part));
				start = i;
			}
			hangul = now;
		}
		return out;
	}
}
