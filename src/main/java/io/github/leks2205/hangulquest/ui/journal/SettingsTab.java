package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.learn.research.Export;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/** The switches, the interface language and the (double-click) progress reset. */
public final class SettingsTab extends JournalTab {
	private long confirmResetUntil;

	/** Steps through Auto and every language the mod ships text for. */
	private final Runnable switchLanguage = () -> {
		String next = Lang.next(host.hq().config().language);
		host.hq().config().language = next;
		Lang.set(next);
		host.hq().saveConfig();
	};
	private static final double[] RETENTION_STEPS = {0.80, 0.85, 0.88, 0.90, 0.92, 0.95};

	/** Steps through the retention choices, wrapping round at the end. */
	private final Runnable switchRetention = () -> {
		double current = host.hq().config().retention;
		double next = RETENTION_STEPS[0];
		for (double step : RETENTION_STEPS) {
			if (step > current + 1e-9) {
				next = step;
				break;
			}
		}
		host.hq().config().retention = next;
		applyLearning();
	};
	private final Runnable switchNewWords = () -> {
		int[] choices = Config.NEW_WORD_CHOICES;
		int at = 0;
		for (int i = 0; i < choices.length; i++) {
			if (choices[i] == host.hq().config().newWordsPerDay) {
				at = i;
			}
		}
		host.hq().config().newWordsPerDay = choices[(at + 1) % choices.length];
		applyLearning();
	};
	private final Runnable resetProgress = this::resetProgress;
	private final Runnable exportResearch = this::exportResearch;

	public SettingsTab(JournalHost host) {
		super(host);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		HangulQuestClient hq = host.hq();
		int yy = y;
		// The two quest-picker switches share the rows of the quest switches, so the page keeps its height.
		int half = width / 2;
		host.toggle(g, x + half, yy, width - half, I18n.t("hangulquest.ui.settings.auto_pick_quests"),
				hq.config().autoPickQuests, value -> hq.config().autoPickQuests = value);
		yy = host.toggle(g, x, yy, half, I18n.t("hangulquest.ui.settings.quests"), hq.config().quests, value -> hq.config().quests = value);
		host.toggle(g, x + half, yy, width - half, I18n.t("hangulquest.ui.settings.show_all_trails"),
				hq.config().showAllTrails, value -> hq.config().showAllTrails = value);
		yy = host.toggle(g, x, yy, half, I18n.t("hangulquest.ui.settings.quest_tracker"), hq.config().questTracker,
				value -> hq.config().questTracker = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.hud_cards"), hq.config().hudCards, value -> hq.config().hudCards = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.tooltips"), hq.config().tooltips, value -> hq.config().tooltips = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.auto_quiz"), hq.config().autoQuiz, value -> hq.config().autoQuiz = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.sound"), hq.config().sound, value -> hq.config().sound = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.quest_translations"), hq.config().questTranslations,
				value -> hq.config().questTranslations = value);
		String language = Lang.isAuto(hq.config().language)
				? I18n.t("hangulquest.ui.settings.language_auto", Lang.current().label()) : Lang.current().label();
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.settings.language", Lang.current().label()), x, yy, Ink.INK_SOFT);
		host.button(g, x + width - 100, yy - 2, 100, 16, language, false, switchLanguage);
		yy += 18;
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.settings.new_words"), x, yy, Ink.INK_SOFT);
		host.button(g, x + width - 100, yy - 2, 100, 16, String.valueOf(hq.config().newWordsPerDay), false, switchNewWords);
		yy += 18;
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.settings.retention"), x, yy, Ink.INK_SOFT);
		host.button(g, x + width - 100, yy - 2, 100, 16, Math.round(hq.config().retention * 100) + "%", false,
				switchRetention);
		yy += 18;
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.typed_cards"), hq.config().typedCards,
				value -> hq.config().typedCards = value);
		yy = host.toggle(g, x, yy, width, I18n.t("hangulquest.ui.settings.lute_import"), hq.config().luteImport,
				value -> hq.config().luteImport = value);
		yy += 6;
		host.button(g, x, yy, 150, 18, I18n.t("hangulquest.ui.settings.reset"), false, resetProgress);
		yy += 22;
		host.button(g, x, yy, 150, 18, I18n.t("hangulquest.ui.settings.export"), false, exportResearch);
		Draw.truncated(g, host.font(), Component.literal(I18n.t("hangulquest.ui.settings.export_hint")), x + 156, yy + 5,
				Math.max(20, width - 156), Ink.INK_FAINT);
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.settings.lute_info", luteInfo()), x, y + height - 10, Ink.INK_FAINT);
	}

	/** Saves the learning settings and hands them to the running scheduler. */
	private void applyLearning() {
		HangulQuestClient hq = host.hq();
		if (hq.learn() != null) {
			hq.learn().configure(hq.config().retention, hq.config().newWordsPerDay);
		}
		hq.saveConfig();
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
			host.status(I18n.t("hangulquest.ui.settings.reset_done"));
		} else {
			confirmResetUntil = now + 3000;
			host.status(I18n.t("hangulquest.ui.settings.reset_confirm"));
		}
	}

	/** Writes the opt-in research zip into the config folder; nothing is sent anywhere. */
	private void exportResearch() {
		HangulQuestClient hq = host.hq();
		if (hq.learn() == null) {
			return;
		}
		try {
			hq.learn().reviewLog().flush();
			Path configFolder = FabricLoader.getInstance().getConfigDir().resolve(HangulQuestClient.MOD_ID);
			LexiconTags tags = LexiconTags.shared();
			Path zip = Export.write(hq.learn().reviewLog().dir(), configFolder, LocalDate.now(),
					id -> hq.catalog().byId(id).map(word -> tags.isCore(word.conceptId)).orElse(false));
			host.status(I18n.t("hangulquest.ui.settings.export_done", zip.getFileName()));
		} catch (IOException | RuntimeException failed) {
			host.status(I18n.t("hangulquest.ui.settings.export_failed"));
		}
	}

	private String luteInfo() {
		var info = host.hq().learn() == null ? null : host.hq().learn().data().vocab.luteImport;
		if (info == null) {
			return I18n.t("hangulquest.ui.settings.lute_none");
		}
		return I18n.t("hangulquest.ui.settings.lute_summary", info.imported, info.skipped);
	}
}
