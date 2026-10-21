package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The switches, the interface language and the (double-click) progress reset. */
public final class SettingsTab extends JournalTab {
	private long confirmResetUntil;

	private final Runnable switchLanguage = () -> {
		Lang next = Lang.current() == Lang.DE ? Lang.EN : Lang.DE;
		host.hq().config().language = next.code();
		Lang.set(next.code());
		host.hq().saveConfig();
	};
	private final Runnable resetProgress = this::resetProgress;

	public SettingsTab(JournalHost host) {
		super(host);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		HangulQuestClient hq = host.hq();
		int yy = y;
		yy = host.toggle(g, x, yy, width, Tr.t("Quests"), hq.config().quests, value -> hq.config().quests = value);
		yy = host.toggle(g, x, yy, width, Tr.t("Quest-Tracker"), hq.config().questTracker,
				value -> hq.config().questTracker = value);
		yy = host.toggle(g, x, yy, width, Tr.t("HUD-Karten"), hq.config().hudCards, value -> hq.config().hudCards = value);
		yy = host.toggle(g, x, yy, width, Tr.t("Tooltips"), hq.config().tooltips, value -> hq.config().tooltips = value);
		yy = host.toggle(g, x, yy, width, Tr.t("Auto-Quiz"), hq.config().autoQuiz, value -> hq.config().autoQuiz = value);
		yy = host.toggle(g, x, yy, width, Tr.t("Ton"), hq.config().sound, value -> hq.config().sound = value);
		yy = host.toggle(g, x, yy, width, Tr.t("Übersetzungen in Quests"), hq.config().questTranslations,
				value -> hq.config().questTranslations = value);
		Draw.label(g, host.font(), Tr.t("Sprache") + ": " + Lang.current().label(), x, yy, Ink.INK_SOFT);
		host.button(g, x + width - 70, yy - 2, 70, 16, Lang.current().label(), false, switchLanguage);
		yy += 18;
		yy = host.toggle(g, x, yy, width, Tr.t("Lute-Import"), hq.config().luteImport,
				value -> hq.config().luteImport = value);
		yy += 6;
		host.button(g, x, yy, 150, 18, Tr.t("Fortschritt zurücksetzen"), false, resetProgress);
		Draw.label(g, host.font(), Tr.t("Lute: ") + luteInfo(), x, y + height - 10, Ink.INK_FAINT);
	}

	private void resetProgress() {
		HangulQuestClient hq = host.hq();
		long now = System.currentTimeMillis();
		if (now < confirmResetUntil) {
			hq.learn().store().backup("pre-reset");
			hq.quests().abandon();
			hq.learn().data().words.clear();
			hq.learn().data().days.clear();
			hq.learn().data().achievements.clear();
			hq.learn().data().totals = new ProgressData.Totals();
			hq.learn().data().quests = new ProgressData.QuestData();
			hq.learn().markDirty();
			hq.learn().store().flush();
			host.changed();
			confirmResetUntil = 0;
			host.status(Tr.t("Fortschritt zurückgesetzt (Backup angelegt)."));
		} else {
			confirmResetUntil = now + 3000;
			host.status(Tr.t("Noch einmal klicken, um wirklich zu löschen."));
		}
	}

	private String luteInfo() {
		var info = host.hq().learn() == null ? null : host.hq().learn().data().vocab.luteImport;
		if (info == null) {
			return Tr.t("keine Datei");
		}
		return info.imported + Tr.t(" Wörter importiert, ") + info.skipped + Tr.t(" übersprungen");
	}
}
