package io.github.leks2205.hangulquest.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.ui.journal.AchievementsTab;
import io.github.leks2205.hangulquest.ui.journal.GrammarTab;
import io.github.leks2205.hangulquest.ui.journal.InfoTab;
import io.github.leks2205.hangulquest.ui.journal.Ink;
import io.github.leks2205.hangulquest.ui.journal.JournalHost;
import io.github.leks2205.hangulquest.ui.journal.JournalTab;
import io.github.leks2205.hangulquest.ui.journal.LibraryTab;
import io.github.leks2205.hangulquest.ui.journal.QuestTab;
import io.github.leks2205.hangulquest.ui.journal.SettingsTab;
import io.github.leks2205.hangulquest.ui.journal.StudyTab;
import io.github.leks2205.hangulquest.ui.journal.WordsTab;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The deliberately opened journal: overview, dictionary, review, achievements, quests, grammar, library, settings.
 * This class is the book itself (cover, tab bar, footer, shared widgets and click areas); each page
 * is a {@link JournalTab} in {@code ui.journal}.
 */
public final class JournalScreen extends Screen {
	private static final String[] TABS = {"hangulquest.ui.journal.tab.info", "hangulquest.ui.journal.tab.words",
			"hangulquest.ui.journal.tab.study", "hangulquest.ui.journal.tab.achievements", "hangulquest.ui.journal.tab.quests",
			"hangulquest.ui.journal.tab.grammar", "hangulquest.ui.journal.tab.library", "hangulquest.ui.journal.tab.settings"};
	private static final String[] PAGE_LABELS = {"— 1 —", "— 2 —", "— 3 —", "— 4 —", "— 5 —", "— 6 —", "— 7 —", "— 8 —"};
	private static final String[] SPINE = {"한", "글", "일", "지"};

	private final HangulQuestClient hq = HangulQuestClient.get();
	private final List<Hit> hits = new ArrayList<>();
	private final JournalTab[] tabs;
	private final Runnable[] tabClicks = new Runnable[TABS.length];
	private int tab;
	private String status = "";
	private Component statusText = Component.empty();
	private long epoch;
	private int mouseX;
	private int mouseY;
	private boolean showDe;

	/** A click area: a plain action, or one that gets the click position and may decline it. */
	private record Hit(int x, int y, int width, int height, Runnable action, JournalHost.PointClick point) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + width && my >= y && my < y + height;
		}

		/** True when the click was used. */
		boolean fire(double mx, double my) {
			if (action != null) {
				action.run();
				return true;
			}
			return point.click(mx, my);
		}
	}

	private final JournalHost host = new JournalHost() {
		@Override
		public HangulQuestClient hq() {
			return hq;
		}

		@Override
		public Font font() {
			return font;
		}

		@Override
		public boolean showDe() {
			return showDe;
		}

		@Override
		public int mouseX() {
			return mouseX;
		}

		@Override
		public int mouseY() {
			return mouseY;
		}

		@Override
		public long epoch() {
			return epoch;
		}

		@Override
		public void changed() {
			epoch++;
		}

		@Override
		public void status(String text) {
			status = text;
			statusText = Component.literal(text);
		}

		@Override
		public void openTab(int index) {
			tab = Math.max(0, Math.min(TABS.length - 1, index));
		}

		@Override
		public void close() {
			onClose();
		}

		@Override
		public void openWelcome() {
			WelcomeScreen.openFromJournal(JournalScreen.this);
		}

		@Override
		public int contentHeight() {
			return JournalScreen.this.contentHeight();
		}

		@Override
		public void hit(int x, int y, int width, int height, Runnable action) {
			hits.add(new Hit(x, y, width, height, action, null));
		}

		@Override
		public void hit(int x, int y, int width, int height, PointClick action) {
			hits.add(new Hit(x, y, width, height, null, action));
		}

		@Override
		public void button(GuiGraphicsExtractor g, int x, int y, int width, int height, String label,
						   boolean active, Runnable action) {
			JournalScreen.this.button(g, x, y, width, height, label, active, action);
		}

		@Override
		public void checkRow(GuiGraphicsExtractor g, int x, int y, int width, int height, String label,
							 boolean checked, Runnable action) {
			JournalScreen.this.checkRow(g, x, y, width, height, label, checked, action);
		}

		@Override
		public int toggle(GuiGraphicsExtractor g, int x, int y, int width, String label, boolean value,
						  Consumer<Boolean> setter) {
			return JournalScreen.this.toggle(g, x, y, width, label, value, setter);
		}
	};

	private final Runnable toggleTranslations = () -> {
		hq.config().journalTranslations = !showDe;
		hq.saveConfig();
	};

	public JournalScreen() {
		super(Component.literal("한글 탐험"));
		tabs = new JournalTab[] {
				new InfoTab(host), new WordsTab(host), new StudyTab(host),
				new AchievementsTab(host), new QuestTab(host), new GrammarTab(host), new LibraryTab(host), new SettingsTab(host)};
		for (int i = 0; i < tabs.length; i++) {
			int index = i;
			tabClicks[i] = () -> {
				tab = index;
				tabs[index].onOpen();
			};
		}
	}

	/** Dev-only: opens the journal on a given tab (0 = Info … 4 = Quests), used for screenshots. */
	public JournalScreen(int initialTab) {
		this();
		this.tab = Math.max(0, Math.min(TABS.length - 1, initialTab));
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_K) {
			onClose();
			return true;
		}
		if (tabs[tab].keyPressed(event.key())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (hq != null) {
			hq.saveConfig();
			if (hq.learn() != null) {
				hq.learn().store().flush();
			}
		}
		super.onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		HangulQuestClient.get().debugLog("journal click button={} x={} y={} hits={} gui={}x{} scale={}",
				event.button(), event.x(), event.y(), hits.size(), this.width, this.height,
				this.minecraft == null ? -1 : this.minecraft.getWindow().getGuiScale());
		if (tabs[tab].mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		if (event.button() != 1) {
			return super.mouseClicked(event, doubleClick);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y()) && hit.fire(event.x(), event.y())) {
				return true;
			}
		}
		if (tabs[tab].holdsPress()) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (tabs[tab].mouseDragged(dragX, dragY, event.button())) {
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (JournalTab page : tabs) {
			page.mouseReleased(event.button());
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (Math.signum(vertical) == 0) {
			return true;
		}
		if (tabs[tab].mouseScrolled(mouseX, mouseY, vertical)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();

		int panelWidth = Math.min(440, this.width - 12);
		int panelHeight = Math.min(300, this.height - 12);
		int px = (this.width - panelWidth) / 2;
		int py = (this.height - panelHeight) / 2;

		graphics.fill(0, 0, this.width, this.height, 0x99000000);
		drawBook(graphics, px, py, panelWidth, panelHeight);

		int tabWidth = (panelWidth - 10 - 24) / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			button(graphics, px + 5 + i * tabWidth, py + 5, tabWidth - 2, 16, I18n.t(TABS[i]), i == tab, tabClicks[i]);
		}

		showDe = hq != null && hq.config().journalTranslations;
		int eyeX = px + panelWidth - 28;
		int eyeY = py + 5;
		drawEye(graphics, eyeX, eyeY, showDe);
		hits.add(new Hit(eyeX - 2, eyeY - 2, 18, 18, toggleTranslations, null));

		int cx = px + 22;
		int cy = py + 26;
		int cw = panelWidth - 34;
		int ch = panelHeight - 40;
		tabs[tab].render(graphics, cx, cy, cw, ch);

		if (!status.isEmpty()) {
			Draw.truncated(graphics, this.font, statusText, cx, py + panelHeight - 13, cw - 30, Ink.ACCENT);
		}
		Draw.label(graphics, this.font, PAGE_LABELS[tab], px + panelWidth - 34, py + panelHeight - 13, Ink.INK_FAINT);
	}

	private int contentHeight() {
		int panelHeight = Math.min(300, this.height - 12);
		return panelHeight - 34;
	}

	/** Leather cover, sewn spine, page stack, margin rule, ribbon and ruling lines. */
	private void drawBook(GuiGraphicsExtractor g, int px, int py, int width, int height) {
		g.fill(px - 10, py - 10, px + width + 10, py + height + 12, 0x66000000);
		g.fill(px - 8, py - 8, px + width + 8, py + height + 8, Ink.LEATHER_DARK);
		g.fill(px - 6, py - 6, px + width + 6, py + height + 6, Ink.LEATHER);
		for (int x = px - 4; x < px + width + 4; x += 6) {
			g.fill(x, py - 5, x + 3, py - 4, Ink.STITCH);
			g.fill(x, py + height + 4, x + 3, py + height + 5, Ink.STITCH);
		}
		for (int y = py - 4; y < py + height + 4; y += 6) {
			g.fill(px - 5, y, px - 4, y + 3, Ink.STITCH);
			g.fill(px + width + 4, y, px + width + 5, y + 3, Ink.STITCH);
		}
		// stacked page edge then the top page
		g.fill(px + 3, py + 3, px + width + 3, py + height + 3, Ink.PAGE_EDGE);
		g.fill(px, py, px + width, py + height, Ink.PARCHMENT);
		g.fill(px, py, px + 14, py + height, Ink.PARCHMENT_DARK);
		for (int y = py + 10; y < py + height - 10; y += 14) {
			g.fill(px + 3, y, px + 11, y + 2, 0xFF7A5533);
		}
		// sewn binding shadow
		g.fill(px + 14, py, px + 15, py + height, 0x557A5533);
		// margin rule and faint ruling
		g.fill(px + 18, py + 26, px + 19, py + height - 16, 0x55A34A3A);
		for (int y = py + 30; y < py + height - 16; y += 18) {
			g.fill(px + 20, y, px + width - 10, y + 1, 0x18B49A6A);
		}
		// ribbon bookmark hanging from the page edge
		int ribbonX = px + width - 11;
		g.fill(ribbonX, py + 32, ribbonX + 7, py + 82, Ink.RIBBON);
		g.fill(ribbonX, py + 82, ribbonX + 3, py + 86, Ink.PARCHMENT);
		g.fill(ribbonX + 4, py + 82, ribbonX + 7, py + 86, Ink.PARCHMENT);
		// small title on the spine
		for (int i = 0; i < SPINE.length; i++) {
			Draw.korean(g, this.font, SPINE[i], px + 1, py + 40 + i * 12, 0xFFE0C894);
		}
	}

	private int toggle(GuiGraphicsExtractor g, int x, int y, int width, String label, boolean value,
					   Consumer<Boolean> setter) {
		Draw.label(g, this.font, value ? "✓ " + label : "· " + label, x, y, value ? Ink.GOOD : Ink.INK_FAINT);
		hits.add(new Hit(x, y - 1, width, 15, () -> {
			setter.accept(!value);
			hq.saveConfig();
		}, null));
		return y + 16;
	}

	// ------------------------------------------------------------------ widgets

	private void drawEye(GuiGraphicsExtractor g, int x, int y, boolean open) {
		boolean hover = mouseX >= x - 2 && mouseX < x + 16 && mouseY >= y - 2 && mouseY < y + 16;
		if (hover) {
			g.fill(x - 2, y - 2, x + 16, y + 16, Ink.PARCHMENT_DARK);
		}
		int color = open ? Ink.INK : Ink.INK_FAINT;
		g.fill(x + 1, y + 7, x + 15, y + 9, color);
		g.fill(x + 3, y + 5, x + 13, y + 7, color);
		g.fill(x + 3, y + 9, x + 13, y + 11, color);
		g.fill(x + 5, y + 3, x + 11, y + 5, color);
		g.fill(x + 5, y + 11, x + 11, y + 13, color);
		g.fill(x + 6, y + 6, x + 10, y + 10, open ? Ink.ACCENT : Ink.INK_FAINT);
		if (!open) {
			for (int i = 0; i < 12; i++) {
				g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFF8080);
			}
		}
	}

	/** A checkbox with a hand-drawn tick — safer than relying on a glyph in the font. */
	private void checkRow(GuiGraphicsExtractor g, int x, int y, int width, int height, String label,
						  boolean checked, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		int box = 9;
		int boxY = y + (height - box) / 2;
		g.fill(x, boxY, x + box, boxY + box, checked ? 0xFFFFF8E7 : hover ? 0xFFF2E3C2 : Ink.PARCHMENT_DARK);
		g.fill(x, boxY, x + box, boxY + 1, Ink.PAGE_EDGE);
		g.fill(x, boxY + box - 1, x + box, boxY + box, Ink.PAGE_EDGE);
		g.fill(x, boxY, x + 1, boxY + box, Ink.PAGE_EDGE);
		g.fill(x + box - 1, boxY, x + box, boxY + box, Ink.PAGE_EDGE);
		if (checked) {
			// Tick: two short strokes drawn as 2x2 blocks, readable at any GUI scale.
			g.fill(x + 2, boxY + 4, x + 4, boxY + 7, Ink.RIBBON);
			g.fill(x + 4, boxY + 6, x + 6, boxY + 8, Ink.RIBBON);
			g.fill(x + 6, boxY + 2, x + 8, boxY + 7, Ink.RIBBON);
			g.fill(x + 7, boxY + 2, x + 8, boxY + 5, Ink.RIBBON);
		}
		Draw.label(g, this.font, label, x + box + 4, y + (height - this.font.lineHeight) / 2,
				checked ? Ink.INK : Ink.INK_SOFT);
		hits.add(new Hit(x, y, width, height, action, null));
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int width, int height, String label,
						boolean active, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		int background = active ? Ink.RIBBON : hover ? 0xFFF2E3C2 : Ink.PARCHMENT_DARK;
		g.fill(x, y, x + width, y + height, background);
		g.fill(x, y + height - 1, x + width, y + height, active ? 0xFF5E1A1A : Ink.PAGE_EDGE);
		int textY = y + (height - this.font.lineHeight) / 2;
		int color = active ? Ink.CREAM : Ink.INK;
		if (this.font.width(label) > width - 8) {
			// Narrow windows: a long tab name ("Achievements") must not run into the next tab.
			Draw.truncated(g, this.font, net.minecraft.network.chat.Component.literal(label), x + 4, textY,
					width - 8, color, false);
		} else {
			Draw.label(g, this.font, label, x + 4, textY, color);
		}
		hits.add(new Hit(x, y, width, height, action, null));
	}
}
