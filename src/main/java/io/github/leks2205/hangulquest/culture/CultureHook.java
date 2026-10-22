package io.github.leks2205.hangulquest.culture;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.story.MarkFile;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.HudController;
import io.github.leks2205.hangulquest.ui.HudWindows;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.ui.WindowChrome;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Shows the culture card of a note when its anchor first turns up (or its holiday is near) and keeps the
 * queue behind it. The card is a HUD window ({@link HudWindows}): it stays until the hint key closes it, and
 * the next note comes after. The check runs once a second on the last observation (at once when something
 * was crafted); nothing here touches the game.
 */
public final class CultureHook {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	/** The profile file that remembers which notes were shown. */
	public static final String FILE = "culture-seen.json";
	/** The old timeout, used when windows close automatically. */
	private static final long CARD_MS = 16_000L;
	private static final long ONE_DAY_MS = 86_400_000L;
	/** Culture notes are never urgent: they wait for a free place in the window queue. */
	private static final int MAX_WAITING_BEHIND = 3;
	private static final long CHECK_MS = 1000L;

	private static volatile CultureSet content = CultureSet.EMPTY;
	private static CultureCards cards;
	private static Path cardsDir;
	private static long lastCheck;

	private CultureHook() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CultureHook::onTick);
	}

	/** Called with every content load. */
	public static void publish(CultureSet loaded) {
		content = loaded == null ? CultureSet.EMPTY : loaded;
	}

	public static CultureSet content() {
		return content;
	}

	private static CultureCards cardsFor(LearningService learn) {
		Path dir = learn.store().file().toAbsolutePath().getParent();
		if (cards == null || !dir.equals(cardsDir)) {
			cards = new CultureCards(MarkFile.open(dir, FILE, LOG));
			cardsDir = dir;
		}
		return cards;
	}

	private static void onTick(Minecraft mc) {
		HangulQuestClient hq = HangulQuestClient.get();
		if (hq == null || hq.learn() == null || mc.player == null) {
			return;
		}
		boolean wanted = hq.config().enabled && hq.config().hudCards;
		CultureCards queue = cardsFor(hq.learn());
		long now = System.currentTimeMillis();
		// A craft is a fact of one tick only, so it is checked at once; what the player holds, once a second.
		if (wanted && (!hq.lastObservation().crafted.isEmpty() || now - lastCheck >= CHECK_MS)) {
			lastCheck = now;
			queue.offer(CultureTrigger.due(content, CultureTrigger.itemsOf(hq.lastObservation()), LocalDate.now(),
					queue.shown()));
		}
		HudController hud = hq.hud();
		if (hud == null) {
			return;
		}
		boolean canShow = wanted && mc.gui.screen() == null && hud.windows().waiting() < MAX_WAITING_BEHIND;
		CultureCards.Card before = queue.current();
		// the window decides when the card goes away, so the queue's own timer never runs out
		CultureCards.Card card = queue.tick(now, canShow, ONE_DAY_MS);
		if (card != null && card != before) {
			boolean taken = hud.windows().show(HudWindows.Window.of(HudWindows.Kind.CULTURE,
							"culture:" + card.note().id() + "@" + card.key(), card)
					.priority(-1).autoCloseMs(CARD_MS).onClose(reason -> queue.dismiss()));
			if (!taken) {
				queue.dismiss();
			}
		}
	}

	/** Draws a culture window at its place on the screen; called by the HUD for the window on top. */
	public static void draw(GuiGraphicsExtractor graphics, Minecraft mc, CultureCards.Card card, WindowChrome.Info chrome,
							 java.util.List<io.github.leks2205.hangulquest.ui.WindowPlacement.Rect> soft) {
		Font font = mc.font;
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int width = WindowChrome.widthFor(screenWidth, Math.min(250, screenWidth - 12), soft);
		int textWidth = width - 14;
		CultureNote note = card.note();
		Component title = Component.empty().append(KoText.ko(note.titleKo())).append(Component.literal("  " + I18n.t(note.titleKey())));
		Component summary = KoText.ko(note.ko());
		Component text = Component.literal(I18n.t(note.textKey()));
		String date = note.isHoliday() ? dateLine(card) : "";
		int height = 6 + 2 * (font.lineHeight + 3) + Draw.wrappedHeight(font, summary, textWidth, 3) + 4
				+ Draw.wrappedHeight(font, text, textWidth, 8) + 4 + (date.isEmpty() ? 0 : font.lineHeight + 3)
				+ font.lineHeight + 3 + WindowChrome.footerHeight(font);
		var at = WindowChrome.place(screenWidth, mc.getWindow().getGuiScaledHeight(), screenWidth - width - 6, 6, width, height, soft);
		int x = at.x();
		int y = at.y();
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.itemIcon(graphics, note.icon(), x + width - 22, y + 4);
		int cursor = y + 6;
		Draw.label(graphics, font, I18n.t("hangulquest.ui.culture.card_title"), x + 7, cursor, Draw.GOLD);
		cursor += font.lineHeight + 3;
		Draw.truncated(graphics, font, title, x + 7, cursor, textWidth - 20, Draw.WHITE);
		cursor += font.lineHeight + 3;
		Draw.wrapped(graphics, font, summary, x + 7, cursor, textWidth, Draw.BLUE, 3);
		cursor += Draw.wrappedHeight(font, summary, textWidth, 3) + 4;
		Draw.wrapped(graphics, font, text, x + 7, cursor, textWidth, Draw.GRAY, 8);
		cursor += Draw.wrappedHeight(font, text, textWidth, 8) + 4;
		if (!date.isEmpty()) {
			Draw.label(graphics, font, date, x + 7, cursor, Draw.GOLD);
			cursor += font.lineHeight + 3;
		}
		Draw.label(graphics, font, I18n.t("hangulquest.ui.culture.card_hint"), x + 7, cursor, Draw.DIM);
		WindowChrome.footer(graphics, font, x, y, width, height, chrome);
	}

	private static String dateLine(CultureCards.Card card) {
		try {
			return I18n.t("hangulquest.ui.culture.date", LunarHolidays.display(LocalDate.parse(card.key()), Lang.current()));
		} catch (RuntimeException notADate) {
			return "";
		}
	}
}
