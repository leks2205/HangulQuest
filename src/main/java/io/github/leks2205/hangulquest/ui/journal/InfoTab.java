package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.DayCounts;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.Streak;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.StampMemo;
import io.github.leks2205.hangulquest.ui.research.PlacementScreen;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The overview: counters, streak and the word of the day. Its numbers are read once a second, not every frame. */
public final class InfoTab extends JournalTab {
	/** What the page shows, read from the learning state once per second. */
	private record Snapshot(String learned, String due, String today, String level, Word word,
							String wordLabel, Component gloss) {
	}

	private final StampMemo<Snapshot> memo = new StampMemo<>();
	private final Runnable startReview = this::startReview;
	private final Runnable openQuests = () -> host.openTab(JournalHost.QUEST_TAB);
	private final Runnable openWelcome = host::openWelcome;
	private final Runnable openPlacement = () -> {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreenAndShow(new PlacementScreen(mc.gui.screen()));
	};

	public InfoTab(JournalHost host) {
		super(host);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		LearningService learn = host.hq().learn();
		if (learn == null) {
			Draw.label(g, host.font(), I18n.t("hangulquest.ui.info.loading"), x, y, Ink.INK_SOFT);
			return;
		}
		long now = System.currentTimeMillis();
		long stamp = StampMemo.perSecond(now, host.epoch(), language());
		Snapshot shot = memo.value(stamp);
		if (shot == null) {
			shot = memo.store(stamp, snapshot(learn, now));
		}
		int yy = y;
		Draw.label(g, host.font(), shot.learned(), x, yy, Ink.INK);
		yy += 16;
		Draw.label(g, host.font(), shot.due(), x, yy, Ink.INK);
		yy += 16;
		Draw.label(g, host.font(), shot.today(), x, yy, Ink.INK_SOFT);
		yy += 16;
		Draw.label(g, host.font(), shot.level(), x, yy, Ink.INK_SOFT);
		yy += 22;

		Word word = shot.word();
		if (word != null) {
			Draw.label(g, host.font(), shot.wordLabel(), x, yy, Ink.ACCENT);
			yy += 14;
			Draw.itemIcon(g, word.iconItem, x, yy);
			Draw.korean(g, host.font(), word.ko, x + 20, yy + 3, Ink.INK);
			if (host.showDe()) {
				Draw.truncated(g, host.font(), shot.gloss(), x + 60, yy + 3, width - 60, Ink.INK_SOFT);
			}
			yy += 22;
		}

		host.button(g, x, yy, 110, 18, I18n.t("hangulquest.ui.info.review"), false, startReview);
		host.button(g, x + 118, yy, 110, 18, I18n.t("hangulquest.ui.info.open_quests"), false, openQuests);
		yy += 26;
		host.button(g, x, yy, 228, 18, I18n.t("hangulquest.ui.info.about_hangul"), false, openWelcome);
		yy += 24;
		host.button(g, x, yy, 228, 18, I18n.t("hangulquest.ui.info.placement"), false, openPlacement);
	}

	private void startReview() {
		host.hq().hud().startFromDue();
		if (host.hq().hud() != null && host.hq().learn().dueCount(System.currentTimeMillis()) > 0) {
			host.close();
		}
	}

	private Snapshot snapshot(LearningService learn, long now) {
		Streak streak = learn.streak();
		int discovered = learn.discovered();
		int level = Math.min(10, 1 + discovered / 25);
		// Read today's counts without creating the day: opening the journal must not count as a study day.
		DayCounts today = learn.data().days.get(LocalDate.now().toString());
		int seen = today == null ? 0 : today.seen;
		int correct = today == null ? 0 : today.correct;
		Word word = wordOfTheDay(learn);
		return new Snapshot(
				I18n.t("hangulquest.ui.info.discovered_mastered", discovered, learn.mastered()),
				I18n.t("hangulquest.ui.info.due_week", learn.dueCount(now), streak.weekDays(), Streak.WEEKLY_GOAL,
						streak.freezesLeft()),
				I18n.t("hangulquest.ui.info.today", seen, correct),
				I18n.t("hangulquest.ui.info.level", level),
				word, I18n.t("hangulquest.ui.info.word_of_day"),
				word == null ? Component.empty() : Component.literal(word.gloss(Lang.current())));
	}

	private Word wordOfTheDay(LearningService learn) {
		if (learn.discovered() == 0) {
			return null;
		}
		List<String> ids = new ArrayList<>(learn.data().words.keySet());
		ids.sort(String::compareTo);
		LocalDate day = LocalDate.now();
		int index = Math.floorMod(day.getDayOfYear() * 31 + day.getYear(), ids.size());
		return host.hq().catalog().byId(ids.get(index)).orElse(null);
	}
}
