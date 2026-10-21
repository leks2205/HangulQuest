package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * The grammar book: every construction of the ladder in order. The ones the player has met open to their
 * rule card and examples; the others stay locked lines. What is listed comes from {@link GrammarShelf}.
 */
public final class GrammarTab extends JournalTab {
	private static final int ROW_HEIGHT = 16;

	private int scroll;
	private String selectedId;

	private List<GrammarShelf.Row> rows = List.of();
	private int rowsVersion = -1;
	private long rowsEpoch = -1;
	private int rowsLanguage = -1;
	// Where the rows are on screen this frame, for the one click area that covers them.
	private int listX;
	private int listY;
	private int listWidth;
	private int listFirst;
	private int listEnd;
	private final JournalHost.PointClick rowClick = this::clickRow;

	private Detail detail;

	/** The texts of the detail page for one construction. */
	private record Detail(String id, int version, int language, boolean showDe, GrammarBook.Construction c,
						  Component examples, List<Component> rule, String metrics) {
	}

	public GrammarTab(JournalHost host) {
		super(host);
	}

	@Override
	public void onOpen() {
		scroll = 0;
		selectedId = null;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double vertical) {
		int visible = Math.max(1, (host.contentHeight() - 16) / ROW_HEIGHT);
		int maxScroll = Math.max(0, rows.size() - visible);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical) * 2));
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		LearningService learn = host.hq().learn();
		if (learn == null) {
			return;
		}
		GrammarProgress progress = learn.grammar();
		refresh(progress);
		if (selectedId != null) {
			detail(g, progress, x, y, width, height);
			return;
		}
		int visible = Math.max(1, (height - 16) / ROW_HEIGHT);
		int maxScroll = Math.max(0, rows.size() - visible);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int end = Math.min(rows.size(), scroll + visible);
		for (int i = scroll; i < end; i++) {
			GrammarShelf.Row row = rows.get(i);
			int rowY = y + (i - scroll) * ROW_HEIGHT;
			Draw.label(g, host.font(), "L" + row.level(), x, rowY + 3, row.met() ? Ink.ACCENT : Ink.INK_FAINT);
			if (row.met()) {
				Draw.korean(g, host.font(), row.form(), x + 24, rowY + 3, Ink.INK);
				Draw.label(g, host.font(), row.mastery() >= 6 ? "★" : "●".repeat(row.mastery()), x + width - 40, rowY + 3,
						Ink.ACCENT);
			} else {
				Draw.label(g, host.font(), I18n.t("hangulquest.ui.grammar.locked"), x + 24, rowY + 3, Ink.INK_FAINT);
			}
		}
		listX = x;
		listY = y;
		listWidth = width;
		listFirst = scroll;
		listEnd = end;
		host.hit(x, y, width, (end - scroll) * ROW_HEIGHT, rowClick);
		String hint = GrammarShelf.metCount(rows) == 0
				? I18n.t("hangulquest.ui.grammar.empty")
				: I18n.t("hangulquest.ui.grammar.count", GrammarShelf.metCount(rows), rows.size());
		Draw.truncated(g, host.font(), Component.literal(hint), x, y + height - 10, width, Ink.INK_FAINT);
	}

	private boolean clickRow(double mx, double my) {
		int index = listFirst + (int) ((my - listY) / ROW_HEIGHT);
		if (index < listFirst || index >= listEnd || mx < listX || mx >= listX + listWidth) {
			return false;
		}
		GrammarShelf.Row row = rows.get(index);
		if (!row.met()) {
			return true; // a locked line opens nothing, but the click is used
		}
		selectedId = row.id();
		return true;
	}

	private void refresh(GrammarProgress progress) {
		if (rowsVersion != progress.version() || rowsEpoch != host.epoch() || rowsLanguage != language()) {
			rows = GrammarShelf.rows(GrammarBook.shared(), progress);
			rowsVersion = progress.version();
			rowsEpoch = host.epoch();
			rowsLanguage = language();
		}
	}

	private void detail(GuiGraphicsExtractor g, GrammarProgress progress, int x, int y, int width, int height) {
		Detail d = detailFor(progress);
		if (d == null) {
			selectedId = null;
			return;
		}
		Draw.korean(g, host.font(), d.c().form(), x, y + 2, Ink.ACCENT);
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.grammar.level", d.c().level()), x + width - 50, y + 2,
				Ink.INK_FAINT);
		int cursor = y + 18;
		for (Component line : d.rule()) {
			Draw.wrapped(g, host.font(), line, x, cursor, width, Ink.INK, 3);
			cursor += Draw.wrappedHeight(host.font(), line, width, 3) + 3;
		}
		cursor += 2;
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.grammar.examples"), x, cursor, Ink.INK_FAINT);
		cursor += 12;
		Draw.wrapped(g, host.font(), d.examples(), x, cursor, width, Ink.INK_SOFT, 6);
		cursor += Draw.wrappedHeight(host.font(), d.examples(), width, 6) + 4;
		Draw.label(g, host.font(), d.metrics(), x, cursor, Ink.INK_FAINT);
		cursor += 16;
		host.button(g, x, cursor, 90, 18, I18n.t("hangulquest.ui.words.back"), false, () -> selectedId = null);
	}

	/** The detail texts for the selected construction, rebuilt only when what they show may have changed. */
	private Detail detailFor(GrammarProgress progress) {
		Detail cached = detail;
		boolean showDe = host.showDe();
		if (cached != null && cached.id().equals(selectedId) && cached.version() == progress.version()
				&& cached.language() == language() && cached.showDe() == showDe) {
			return cached;
		}
		GrammarBook.Construction c = GrammarShelf.open(GrammarBook.shared(), progress, selectedId);
		if (c == null) {
			return null;
		}
		List<Component> rule = c.ruleCard().lines(Lang.current()).stream().map(KoText::mixed).toList();
		MutableComponent exampleText = Component.empty();
		List<GrammarBook.Example> shown = c.examples().subList(0, Math.min(3, c.examples().size()));
		for (int i = 0; i < shown.size(); i++) {
			exampleText.append(KoText.line(shown.get(i).ko(), showDe ? shown.get(i).en() : null));
			if (i < shown.size() - 1) {
				exampleText.append("\n");
			}
		}
		String metrics = I18n.t("hangulquest.ui.grammar.metrics", progress.steps(c.id()), progress.answers(c.id()),
				Math.round(progress.pL(c.id()) * 100));
		detail = new Detail(c.id(), progress.version(), language(), showDe, c, exampleText, rule, metrics);
		return detail;
	}
}
