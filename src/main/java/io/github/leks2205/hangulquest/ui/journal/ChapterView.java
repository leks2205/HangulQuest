package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailVisibility;
import io.github.leks2205.hangulquest.story.ChapterSet;
import io.github.leks2205.hangulquest.story.LibraryHook;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.StampMemo;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The quest tab's second view: the trails by life situation. One list of chapters with their progress; a
 * chapter opens to its trails, and a trail can be shown in the tree. Rows are built once a second (or when
 * progress changes), not per frame.
 */
final class ChapterView {
	private static final int ROW_HEIGHT = 22;
	private static final int TRAIL_ROW = 15;
	private static final int BAR_WIDTH = 64;

	/** One chapter as listed. */
	record Entry(ChapterSet.Progress progress, String gloss, Component glossText, String levels, String count) {
	}

	/** One trail of an opened chapter. */
	record TrailRow(String id, String titleKo, Component gloss, String level, State state) {
	}

	enum State {
		DONE, OPEN, LOCKED
	}

	private record Page(List<Entry> entries, String header, int version) {
	}

	private final JournalHost host;
	private final Consumer<String> showTrail;
	private final StampMemo<Page> memo = new StampMemo<>();
	private final StampMemo<List<TrailRow>> trailMemo = new StampMemo<>();
	private final JournalHost.PointClick listClick = this::clickList;
	private final JournalHost.PointClick trailClick = this::clickTrail;

	private int scroll;
	private String openChapter;
	private int listX;
	private int listY;
	private int listWidth;
	private int listFirst;
	private int listEnd;
	private List<TrailRow> trailRows = List.of();
	private List<Entry> entries = List.of();
	private String header = "";

	ChapterView(JournalHost host, Consumer<String> showTrail) {
		this.host = host;
		this.showTrail = showTrail;
	}

	void onOpen() {
		scroll = 0;
		openChapter = null;
	}

	boolean mouseScrolled(double vertical) {
		int rows = openChapter == null ? entries.size() : trailRows.size();
		scroll = Math.max(0, Math.min(Math.max(0, rows - 1), scroll - (int) Math.signum(vertical) * 2));
		return true;
	}

	void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		QuestEngine quests = host.hq().quests();
		LearningService learn = host.hq().learn();
		if (quests == null || learn == null) {
			return;
		}
		ChapterSet chapters = LibraryHook.content().chapters();
		long stamp = StampMemo.perSecond(System.currentTimeMillis(), host.epoch(), language());
		Page page = memo.value(stamp);
		boolean showAll = host.hq().config().showAllTrails;
		int version = learn.data().quests.completed.size() + 31 * quests.trails().size()
				+ 977 * System.identityHashCode(quests.visibility()) + (showAll ? 1 : 0);
		if (page == null || page.version() != version) {
			List<Entry> built = build(chapters, quests, learn, showAll);
			page = memo.store(stamp, new Page(built, I18n.t("hangulquest.ui.chapters.header", built.size()), version));
			trailMemo.clear();
		}
		entries = page.entries();
		header = page.header();
		if (entries.isEmpty()) {
			Draw.label(g, host.font(), I18n.t("hangulquest.ui.chapters.empty"), x, y + 4, Ink.INK_SOFT);
			return;
		}
		if (openChapter != null) {
			Entry entry = entries.stream().filter(e -> e.progress().chapter().id().equals(openChapter)).findFirst().orElse(null);
			if (entry == null) {
				openChapter = null;
			} else {
				renderTrails(g, x, y, width, height, entry, quests, learn, stamp);
				return;
			}
		}
		renderList(g, x, y, width, height);
	}

	/** The chapters with something to show: a trail that is not hidden (all of them with "show all trails"). */
	private List<Entry> build(ChapterSet chapters, QuestEngine quests, LearningService learn, boolean showAll) {
		List<Entry> out = new ArrayList<>();
		Map<String, TrailVisibility.State> visibility = quests.visibility();
		for (ChapterSet.Progress progress : chapters.progress(trail -> quests.byId(trail) != null,
				trail -> learn.data().quests.completed.containsKey(trail))) {
			ChapterSet.Chapter chapter = progress.chapter();
			if (!showAll && chapter.trails().stream().noneMatch(id -> {
				TrailVisibility.State state = visibility.get(id);
				return state != null && state != TrailVisibility.State.HIDDEN;
			})) {
				continue;
			}
			String levels = chapter.minLevel() == chapter.maxLevel() ? "L" + chapter.minLevel()
					: I18n.t("hangulquest.ui.chapters.levels", chapter.minLevel(), chapter.maxLevel());
			String gloss = I18n.t(chapter.titleKey());
			out.add(new Entry(progress, gloss, Component.literal(gloss), levels, progress.done() + "/" + progress.total()));
		}
		return List.copyOf(out);
	}

	// ------------------------------------------------------------------- the chapter list

	private void renderList(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		Draw.label(g, host.font(), header, x, y, Ink.INK_FAINT);
		int top = y + 14;
		int visible = Math.max(1, (height - 14) / ROW_HEIGHT);
		scroll = Math.max(0, Math.min(Math.max(0, entries.size() - visible), scroll));
		int end = Math.min(entries.size(), scroll + visible);
		for (int i = scroll; i < end; i++) {
			Entry entry = entries.get(i);
			ChapterSet.Progress progress = entry.progress();
			int rowY = top + (i - scroll) * ROW_HEIGHT;
			boolean hover = host.mouseX() >= x && host.mouseX() < x + width && host.mouseY() >= rowY && host.mouseY() < rowY + ROW_HEIGHT - 1;
			if (hover) {
				g.fill(x - 2, rowY, x + width, rowY + ROW_HEIGHT - 1, Ink.PARCHMENT_DARK);
			}
			Draw.itemIcon(g, progress.chapter().icon(), x, rowY + 2);
			Draw.korean(g, host.font(), progress.chapter().titleKo(), x + 22, rowY + 3, progress.complete() ? Ink.GOOD : Ink.INK);
			Draw.truncated(g, host.font(), entry.glossText(), x + 22, rowY + 12, width - BAR_WIDTH - 60, Ink.INK_FAINT);
			Draw.label(g, host.font(), entry.levels(), x + width - BAR_WIDTH - 76, rowY + 7, Ink.INK_FAINT);
			drawBar(g, x + width - BAR_WIDTH - 36, rowY + 8, progress);
			Draw.label(g, host.font(), entry.count(), x + width - 32, rowY + 7,
					progress.complete() ? Ink.GOOD : Ink.INK_SOFT);
		}
		listX = x;
		listY = top;
		listWidth = width;
		listFirst = scroll;
		listEnd = end;
		host.hit(x, top, width, (end - scroll) * ROW_HEIGHT, listClick);
	}

	private void drawBar(GuiGraphicsExtractor g, int x, int y, ChapterSet.Progress progress) {
		g.fill(x, y, x + BAR_WIDTH, y + 5, Ink.PARCHMENT_DARK);
		g.fill(x, y + 5, x + BAR_WIDTH, y + 6, Ink.PAGE_EDGE);
		if (progress.total() > 0 && progress.done() > 0) {
			g.fill(x, y, x + BAR_WIDTH * progress.done() / progress.total(), y + 5, progress.complete() ? Ink.GOOD : Ink.ACCENT);
		}
	}

	private boolean clickList(double mx, double my) {
		int index = listFirst + (int) ((my - listY) / ROW_HEIGHT);
		if (index < listFirst || index >= listEnd || mx < listX || mx >= listX + listWidth) {
			return false;
		}
		openChapter = entries.get(index).progress().chapter().id();
		scroll = 0;
		trailMemo.clear();
		return true;
	}

	// ---------------------------------------------------------------- the trails of one chapter

	private void renderTrails(GuiGraphicsExtractor g, int x, int y, int width, int height, Entry entry,
							  QuestEngine quests, LearningService learn, long stamp) {
		ChapterSet.Progress progress = entry.progress();
		Draw.itemIcon(g, progress.chapter().icon(), x, y);
		Draw.korean(g, host.font(), progress.chapter().titleKo(), x + 22, y + 1, Ink.ACCENT);
		Draw.truncated(g, host.font(), entry.glossText(), x + 22, y + 11, width - 140, Ink.INK_SOFT);
		Draw.label(g, host.font(), entry.count(), x + width - 100, y + 4, progress.complete() ? Ink.GOOD : Ink.INK_SOFT);
		host.button(g, x + width - 62, y, 58, 16, I18n.t("hangulquest.ui.words.back"), false, () -> {
			openChapter = null;
			scroll = 0;
		});
		List<TrailRow> rows = trailMemo.value(stamp);
		if (rows == null) {
			rows = trailMemo.store(stamp, trailRows(progress.chapter(), quests, learn));
		}
		trailRows = rows;
		int top = y + 26;
		int visible = Math.max(1, (height - 26 - 12) / TRAIL_ROW);
		scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), scroll));
		int end = Math.min(rows.size(), scroll + visible);
		for (int i = scroll; i < end; i++) {
			TrailRow row = rows.get(i);
			int rowY = top + (i - scroll) * TRAIL_ROW;
			boolean hover = host.mouseX() >= x && host.mouseX() < x + width && host.mouseY() >= rowY && host.mouseY() < rowY + TRAIL_ROW - 1;
			if (hover) {
				g.fill(x - 2, rowY, x + width, rowY + TRAIL_ROW - 1, Ink.PARCHMENT_DARK);
			}
			int color = row.state() == State.DONE ? Ink.GOOD : row.state() == State.OPEN ? Ink.INK : Ink.INK_FAINT;
			Draw.label(g, host.font(), row.state() == State.DONE ? "✓" : row.state() == State.OPEN ? "●" : "·", x, rowY + 3, color);
			Draw.korean(g, host.font(), row.titleKo(), x + 14, rowY + 3, color);
			Draw.truncated(g, host.font(), row.gloss(), x + 130, rowY + 3, width - 190, Ink.INK_FAINT);
			Draw.label(g, host.font(), row.level(), x + width - 40, rowY + 3, Ink.INK_FAINT);
		}
		listX = x;
		listY = top;
		listWidth = width;
		listFirst = scroll;
		listEnd = end;
		host.hit(x, top, width, (end - scroll) * TRAIL_ROW, trailClick);
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.chapters.show_in_tree"), x, y + height - 10, Ink.INK_FAINT);
	}

	private List<TrailRow> trailRows(ChapterSet.Chapter chapter, QuestEngine quests, LearningService learn) {
		List<TrailRow> rows = new ArrayList<>();
		Lang lang = Lang.current();
		boolean showAll = host.hq().config().showAllTrails;
		Map<String, TrailVisibility.State> visibility = quests.visibility();
		for (String id : chapter.trails()) {
			Questline trail = quests.byId(id);
			if (trail == null || (!showAll && visibility.get(id) == TrailVisibility.State.HIDDEN)) {
				continue;
			}
			State state = learn.data().quests.completed.containsKey(id) ? State.DONE
					: quests.unlocked(trail) ? State.OPEN : State.LOCKED;
			rows.add(new TrailRow(id, trail.titleKo(), Component.literal(trail.title(lang)), "L" + trail.koreanLevel(), state));
		}
		return List.copyOf(rows);
	}

	private boolean clickTrail(double mx, double my) {
		int index = listFirst + (int) ((my - listY) / TRAIL_ROW);
		if (index < listFirst || index >= listEnd || mx < listX || mx >= listX + listWidth) {
			return false;
		}
		showTrail.accept(trailRows.get(index).id());
		return true;
	}

	private static int language() {
		return Lang.current().id();
	}
}
