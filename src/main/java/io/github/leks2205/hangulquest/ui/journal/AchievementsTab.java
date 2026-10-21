package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.ui.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The achievement list, unlocked ones in ink. */
public final class AchievementsTab extends JournalTab {
	private static final int ROW_HEIGHT = 16;

	private int scroll;

	public AchievementsTab(JournalHost host) {
		super(host);
	}

	@Override
	public void onOpen() {
		scroll = 0;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double vertical) {
		int rows = Math.max(1, (host.contentHeight() - 12) / ROW_HEIGHT);
		int maxScroll = Math.max(0, Achievements.ALL.size() - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical) * 2));
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		int maxRows = Math.max(1, (height - 12) / ROW_HEIGHT);
		int maxScroll = Math.max(0, Achievements.ALL.size() - maxRows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int end = Math.min(Achievements.ALL.size(), scroll + maxRows);
		var learn = host.hq().learn();
		for (int i = scroll; i < end; i++) {
			Achievements.Achievement achievement = Achievements.ALL.get(i);
			boolean unlocked = learn != null && learn.data().achievements.containsKey(achievement.id());
			int rowY = y + (i - scroll) * ROW_HEIGHT;
			Draw.label(g, host.font(), unlocked ? "✓" : "·", x, rowY, unlocked ? Ink.GOOD : Ink.INK_FAINT);
			Draw.label(g, host.font(), achievement.de(), x + 14, rowY, unlocked ? Ink.INK : Ink.INK_FAINT);
			Draw.label(g, host.font(), achievement.ko(), x + width - 80, rowY, unlocked ? Ink.ACCENT : Ink.INK_FAINT);
		}
	}
}
