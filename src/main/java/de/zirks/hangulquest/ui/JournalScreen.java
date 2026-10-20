package de.zirks.hangulquest.ui;

import de.zirks.hangulquest.HangulQuestClient;
import de.zirks.hangulquest.data.Category;
import de.zirks.hangulquest.data.Concept;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.learn.Achievements;
import de.zirks.hangulquest.learn.LearningService;
import de.zirks.hangulquest.learn.ProgressData;
import de.zirks.hangulquest.learn.Streak;
import de.zirks.hangulquest.learn.WordProgress;
import de.zirks.hangulquest.quest.QuestEngine;
import de.zirks.hangulquest.quest.QuestStep;
import de.zirks.hangulquest.quest.Questline;
import de.zirks.hangulquest.quest.TopicResolver;
import de.zirks.hangulquest.sense.Observation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The deliberately opened journal: overview, dictionary, review, achievements, quests, settings. */
public final class JournalScreen extends Screen {
	private static final String[] TABS = {"Info", "Wörter", "Lernen", "Erfolge", "Quests", "Optionen"};

	// A worn notebook: parchment page on a stitched leather cover, ink-brown text.
	private static final int PARCHMENT = 0xFFF3EAD6;
	private static final int PARCHMENT_DARK = 0xFFE7DBBF;
	private static final int PAGE_EDGE = 0xFFCBB88F;
	private static final int LEATHER = 0xFF4A2E1C;
	private static final int LEATHER_DARK = 0xFF2C1A0D;
	private static final int STITCH = 0xFFB08A5A;
	private static final int INK = 0xFF2A2017;
	private static final int INK_SOFT = 0xFF54422F;
	private static final int INK_FAINT = 0xFF6F5A3C;
	private static final int ACCENT = 0xFF7A3616;
	private static final int GOOD = 0xFF3F6B3A;
	private static final int BAD = 0xFF8E2B2B;
	private static final int RIBBON = 0xFF8E2B2B;
	private static final int CREAM = 0xFFF7EBD0;

	private final HangulQuestClient hq = HangulQuestClient.get();
	private final List<Hit> hits = new ArrayList<>();
	private int tab;
	private int scroll;
	private String selectedWordId;
	private long confirmResetUntil;
	private String status = "";
	private List<Word> dictionaryCache;
	private int mouseX;
	private int mouseY;
	private boolean showDe;
	private String selectedTrailId;
	private float treeZoom = 1.0F;
	private double treePanX;
	private double treePanY;
	private boolean treeDragged;
	private boolean treeDragging;
	private boolean treeHighlight;
	private int treeViewX;
	private int treeViewY;
	private int treeViewW;
	private int treeViewH;
	private int treeBaseW;
	private int treeBaseH;

	private record Hit(int x, int y, int width, int height, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + width && my >= y && my < y + height;
		}
	}

	public JournalScreen() {
		super(Component.literal("한글 탐험"));
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
		if (tab == 4 && event.key() == InputConstants.KEY_F) {
			fitTree();
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
		// 26.3 uses SDL button numbers: 1 = left, 2 = right, 3 = middle.
		if (tab == 4 && event.button() == 2 && insideTree(event.x(), event.y())) {
			// Right-click resets the highlight/selection; left-drag must not.
			treeHighlight = false;
			selectedTrailId = null;
			treeDragging = false;
			return true;
		}
		if (event.button() != 1) {
			return super.mouseClicked(event, doubleClick);
		}
		if (tab == 4 && insideTree(event.x(), event.y())) {
			// Empty tree space must be consumed too, otherwise Minecraft never sends drag events.
			treeDragging = true;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.action().run();
				return true;
			}
		}
		if (tab == 4 && treeDragging) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean insideTree(double mouseX, double mouseY) {
		return mouseX >= treeViewX && mouseX < treeViewX + treeViewW
				&& mouseY >= treeViewY && mouseY < treeViewY + treeViewH;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (tab == 4 && treeDragging && event.button() == 1) {
			// Horizontal follows the mouse; vertical is inverted because originY = treeTop - treePanY.
			treePanX += dragX;
			treePanY -= dragY;
			treeDragged = true;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 1) {
			treeDragging = false;
			treeDragged = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		double direction = Math.signum(vertical);
		if (direction == 0) {
			return true;
		}
		int maxScroll;
		switch (tab) {
			case 1 -> {
				int rows = Math.max(1, (contentHeight() - 16) / 18);
				maxScroll = Math.max(0, visibleWords().size() - rows);
			}
			case 3 -> {
				int rows = Math.max(1, (contentHeight() - 12) / 16);
				maxScroll = Math.max(0, Achievements.ALL.size() - rows);
			}
			case 4 -> {
				float oldZoom = treeZoom;
				treeZoom = (float) Math.max(0.25, Math.min(2.2,
						treeZoom * (vertical > 0 ? 1.12 : 0.89)));
				double factor = treeZoom / oldZoom;
				treePanX = mouseX - (mouseX - treePanX) * factor;
				treePanY = mouseY - (mouseY - treePanY) * factor;
				return true;
			}
			default -> {
				return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
			}
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) direction * 2));
		return true;
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
			int x = px + 5 + i * tabWidth;
			boolean active = i == tab;
			final int tabIndex = i;
			button(graphics, x, py + 5, tabWidth - 2, 16, TABS[i], active, () -> {
				tab = tabIndex;
				scroll = 0;
				selectedWordId = null;
				if (tabIndex == 4) {
					treeZoom = 1.0F;
					treePanX = 0;
					treePanY = 0;
				}
			});
		}

		showDe = hq != null && hq.config().journalTranslations;
		int eyeX = px + panelWidth - 28;
		int eyeY = py + 5;
		drawEye(graphics, eyeX, eyeY, showDe);
		hits.add(new Hit(eyeX - 2, eyeY - 2, 18, 18, () -> {
			hq.config().journalTranslations = !showDe;
			hq.saveConfig();
		}));

		int cx = px + 22;
		int cy = py + 26;
		int cw = panelWidth - 34;
		int ch = panelHeight - 40;

		switch (tab) {
			case 0 -> overview(graphics, cx, cy, cw, ch);
			case 1 -> dictionary(graphics, cx, cy, cw, ch);
			case 2 -> review(graphics, cx, cy, cw, ch);
			case 3 -> achievements(graphics, cx, cy, cw, ch);
			case 4 -> quests(graphics, cx, cy, cw, ch);
			case 5 -> settings(graphics, cx, cy, cw, ch);
			default -> {
			}
		}

		if (!status.isEmpty()) {
			Draw.truncated(graphics, this.font, Component.literal(status), cx,
					py + panelHeight - 13, cw - 30, ACCENT);
		}
		Draw.label(graphics, this.font, "— " + (tab + 1) + " —",
				px + panelWidth - 34, py + panelHeight - 13, INK_FAINT);
	}

	private int contentHeight() {
		int panelHeight = Math.min(300, this.height - 12);
		return panelHeight - 34;
	}

	// ---------------------------------------------------------------- overview

	private void overview(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		LearningService learn = hq.learn();
		if (learn == null) {
			Draw.label(g, this.font, "Lernsystem lädt…", x, y, INK_SOFT);
			return;
		}
		Streak streak = learn.streak();
		int due = learn.dueCount(System.currentTimeMillis());
		int level = Math.min(10, 1 + learn.discovered() / 25);
		int yy = y;
		Draw.label(g, this.font, "Entdeckt " + learn.discovered() + "   Beherrscht " + learn.mastered(),
				x, yy, INK);
		yy += 16;
		Draw.label(g, this.font, "Fällig " + due + "   Serie " + streak.current() + " Tage (best " + streak.best() + ")",
				x, yy, INK);
		yy += 16;
		Draw.label(g, this.font, "Heute " + learn.todaySeen() + " gesehen, " + learn.todayCorrect() + " richtig",
				x, yy, INK_SOFT);
		yy += 16;
		Draw.label(g, this.font, "Stufe " + level + " von 10", x, yy, INK_SOFT);
		yy += 22;

		Word word = wordOfTheDay();
		if (word != null) {
			Draw.label(g, this.font, "Wort des Tages", x, yy, ACCENT);
			yy += 14;
			Draw.itemIcon(g, word.iconItem, x, yy);
			Draw.korean(g, this.font, word.ko, x + 20, yy + 3, INK);
			if (showDe) {
				Draw.truncated(g, this.font, Component.literal(word.deShort), x + 60, yy + 3,
						width - 60, INK_SOFT);
			}
			yy += 22;
		}

		button(g, x, yy, 110, 18, "Wiederholen", false, () -> {
			hq.hud().startFromDue();
			if (hq.hud() != null && hq.learn().dueCount(System.currentTimeMillis()) > 0) {
				onClose();
			}
		});
		button(g, x + 118, yy, 110, 18, "Quests öffnen", false, () -> {
			tab = 4;
			scroll = 0;
		});
	}

	private Word wordOfTheDay() {
		LearningService learn = hq.learn();
		if (learn == null || learn.discovered() == 0) {
			return null;
		}
		List<String> ids = new ArrayList<>(learn.data().words.keySet());
		ids.sort(String::compareTo);
		int index = Math.floorMod(LocalDate.now().getDayOfYear() * 31 + LocalDate.now().getYear(), ids.size());
		return hq.catalog().byId(ids.get(index)).orElse(null);
	}

	// -------------------------------------------------------------- dictionary

	private void dictionary(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		if (selectedWordId != null) {
			wordDetail(g, x, y, width, height);
			return;
		}
		List<Word> words = visibleWords();
		int rowHeight = 18;
		int maxRows = Math.max(1, (height - 16) / rowHeight);
		int maxScroll = Math.max(0, words.size() - maxRows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int end = Math.min(words.size(), scroll + maxRows);
		for (int i = scroll; i < end; i++) {
			Word word = words.get(i);
			int rowY = y + (i - scroll) * rowHeight;
			boolean discovered = hq.learn() != null && hq.learn().introduced(word.id);
			Draw.itemIcon(g, word.iconItem, x, rowY + 1);
			Draw.korean(g, this.font, word.ko, x + 20, rowY + 3, discovered ? INK : INK_FAINT);
			if (showDe) {
				Draw.truncated(g, this.font, Component.literal(word.deShort), x + 72, rowY + 3,
						width - 100, discovered ? INK_SOFT : INK_FAINT);
			}
			String level = levelDots(word.id);
			Draw.label(g, this.font, level, x + width - 30, rowY + 3, ACCENT);
			hits.add(new Hit(x, rowY, width, rowHeight, () -> selectedWordId = word.id));
		}
		Draw.label(g, this.font, words.size() + " Wörter · Mausrad scrollt", x, y + height - 10, INK_FAINT);
	}

	private String levelDots(String wordId) {
		if (hq.learn() == null) {
			return "";
		}
		WordProgress progress = hq.learn().data().words.get(wordId);
		if (progress == null) {
			return "·";
		}
		if (progress.known) {
			return "★";
		}
		return "●".repeat(Math.max(1, Math.min(6, progress.level + 1)));
	}

	private List<Word> visibleWords() {
		if (dictionaryCache == null) {
			List<Word> words = new ArrayList<>(hq.catalog().all());
			words.sort(Comparator
					.comparing((Word word) -> hq.learn() == null || !hq.learn().introduced(word.id))
					.thenComparing(word -> word.category.ordinal())
					.thenComparing(word -> word.ko));
			dictionaryCache = words;
		}
		return dictionaryCache;
	}

	private void wordDetail(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		Word word = hq.catalog().byId(selectedWordId).orElse(null);
		if (word == null) {
			selectedWordId = null;
			return;
		}
		Draw.itemIcon(g, word.iconItem, x, y);
		Draw.korean(g, this.font, word.ko, x + 22, y + 3, ACCENT);
		int yy;
		if (showDe) {
			Draw.wrapped(g, this.font, Component.literal(word.de), x + 22, y + 17, width - 24, INK, 2);
			yy = y + 34;
		} else {
			yy = y + 20;
		}
		Concept concept = hq.catalog().concept(word.conceptId == null ? "" : word.conceptId).orElse(null);
		if (concept != null && concept.sentenceKo != null) {
			Component sentence = showDe
					? KoText.line(concept.sentenceKo, concept.sentenceDe == null ? "" : concept.sentenceDe)
					: KoText.ko(concept.sentenceKo);
			Draw.wrapped(g, this.font, sentence, x, yy, width, INK_SOFT, 2);
			yy += 22;
		}
		if (word.hasDescKo()) {
			Draw.wrapped(g, this.font, KoText.ko(word.descKo), x, yy, width, ACCENT, 2);
			yy += 22;
		}
		WordProgress progress = hq.learn() == null ? null : hq.learn().data().words.get(word.id);
		String metrics = progress == null ? "noch nicht entdeckt"
				: "gesehen " + progress.seen + " · richtig " + progress.correct + " · falsch " + progress.wrong
				+ (progress.known ? " · bekannt" : "");
		Draw.label(g, this.font, metrics, x, yy, INK_FAINT);
		yy += 20;
		boolean known = progress != null && progress.known;
		button(g, x, yy, 110, 18, known ? "Wieder lernen" : "Als bekannt", false, () -> {
			hq.learn().markKnown(word.id, !known, System.currentTimeMillis());
			dictionaryCache = null;
			status = known ? "Wird wieder gelernt." : "Als bekannt markiert.";
		});
		button(g, x + 118, yy, 90, 18, "Zurück", false, () -> selectedWordId = null);
	}

	// ------------------------------------------------------------------ review

	private void review(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		LearningService learn = hq.learn();
		if (learn == null) {
			return;
		}
		long now = System.currentTimeMillis();
		List<Word> due = learn.dueWords(now, 8);
		Draw.label(g, this.font, due.size() == 0 ? "Nichts fällig. Alles im grünen Bereich."
				: due.size() + " Karten fällig:", x, y, INK);
		int yy = y + 16;
		for (Word word : due) {
			Draw.korean(g, this.font, word.ko, x, yy, INK);
			Draw.truncated(g, this.font, Component.literal(word.deShort), x + 70, yy, width - 80, INK_SOFT);
			yy += 14;
		}
		if (!due.isEmpty()) {
			button(g, x, y + height - 30, 150, 18, "Jetzt wiederholen", false, () -> {
				hq.hud().startFromDue();
				onClose();
			});
		}
		Draw.label(g, this.font, "Wiederholungen erscheinen auch automatisch im Spiel.", x,
				y + height - 10, INK_FAINT);
	}

	// ------------------------------------------------------------ achievements

	private void achievements(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		int rowHeight = 16;
		int maxRows = Math.max(1, (height - 12) / rowHeight);
		int maxScroll = Math.max(0, Achievements.ALL.size() - maxRows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int end = Math.min(Achievements.ALL.size(), scroll + maxRows);
		for (int i = scroll; i < end; i++) {
			Achievements.Achievement achievement = Achievements.ALL.get(i);
			boolean unlocked = hq.learn() != null
					&& hq.learn().data().achievements.containsKey(achievement.id());
			int rowY = y + (i - scroll) * rowHeight;
			Draw.label(g, this.font, unlocked ? "✓" : "·", x, rowY, unlocked ? GOOD : INK_FAINT);
			Draw.label(g, this.font, achievement.de(), x + 14, rowY, unlocked ? INK : INK_FAINT);
			Draw.label(g, this.font, achievement.ko(), x + width - 80, rowY, unlocked ? ACCENT : INK_FAINT);
		}
	}

	// ------------------------------------------------------------------- quests

	private record NodePos(int cx, int top, int width, int height) {
		boolean contains(double mx, double my) {
			return mx >= cx - width / 2.0 && mx < cx + width / 2.0
					&& my >= top && my < top + height;
		}
	}

	private void quests(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		QuestEngine quests = hq.quests();
		if (quests == null) {
			Draw.label(g, this.font, "Quests laden…", x, y, INK_SOFT);
			return;
		}
		List<Questline> trails = new ArrayList<>(quests.trails());
		trails.sort(Comparator.comparingInt(Questline::order).thenComparing(Questline::id));
		Map<String, Questline> byId = new HashMap<>();
		for (Questline trail : trails) {
			byId.put(trail.id(), trail);
		}
		Map<String, Integer> depths = new HashMap<>();
		for (Questline trail : trails) {
			depthOf(trail, byId, depths);
		}
		int maxLevel = 1;
		for (int depth : depths.values()) {
			maxLevel = Math.max(maxLevel, depth);
		}
		List<List<Questline>> levels = new ArrayList<>();
		for (int level = 0; level < maxLevel; level++) {
			levels.add(new ArrayList<>());
		}
		for (Questline trail : trails) {
			levels.get(depths.getOrDefault(trail.id(), 1) - 1).add(trail);
		}

		// One primary parent per quest gives the tree a real shape; additional prerequisites stay in the detail.
		Map<String, String> primaryParent = new HashMap<>();
		Map<String, List<Questline>> childrenByParent = new HashMap<>();
		for (Questline trail : trails) {
			String topic = TopicResolver.keyFor(trail, byId);
			String chosen = null;
			for (String parentId : trail.requires().completed()) {
				Questline parent = byId.get(parentId);
				if (parent == null) {
					continue;
				}
				if (chosen == null) {
					chosen = parentId;
				}
				if (TopicResolver.keyFor(parent, byId).equals(topic)) {
					chosen = parentId;
					break;
				}
			}
			if (chosen != null) {
				primaryParent.put(trail.id(), chosen);
				childrenByParent.computeIfAbsent(chosen, key -> new ArrayList<>()).add(trail);
			}
		}
		Comparator<Questline> byOrder = Comparator.comparingInt(Questline::order)
				.thenComparing(Questline::id);
		Map<String, Integer> xIndex = new HashMap<>();
		for (int level = 0; level < levels.size(); level++) {
			List<Questline> row = levels.get(level);
			if (level == 0) {
				row.sort(byOrder);
			} else {
				row.sort(Comparator.comparingInt((Questline trail) -> {
					String parentId = primaryParent.get(trail.id());
					return parentId == null ? Integer.MAX_VALUE : xIndex.getOrDefault(parentId, Integer.MAX_VALUE);
				}).thenComparing(byOrder));
			}
			for (int i = 0; i < row.size(); i++) {
				xIndex.put(row.get(i).id(), i);
			}
		}

		Questline current = quests.activeTrail();
		if (current == null && hq.learn() != null) {
			String lastId = null;
			long lastAt = Long.MIN_VALUE;
			for (var entry : hq.learn().data().quests.completed.entrySet()) {
				if (entry.getValue() > lastAt) {
					lastAt = entry.getValue();
					lastId = entry.getKey();
				}
			}
			current = lastId == null ? null : byId.get(lastId);
		}
		Draw.label(g, this.font, "Questbaum · " + trails.size() + " Linien", x, y, ACCENT);
		Draw.label(g, this.font, "✓ ▶ ○ ★  Rad:Zoom  Ziehen  F:Fit  "
						+ Math.round(treeZoom * 100) + "%",
				x + 110, y, INK_FAINT);

		int detailHeight = 76;
		int treeTop = y + 13;
		int treeBottom = y + height - detailHeight;
		treeViewX = x;
		treeViewY = treeTop;
		treeViewW = width;
		treeViewH = Math.max(20, treeBottom - treeTop);

		int slot = 30;
		int maxRow = 1;
		for (List<Questline> row : levels) {
			maxRow = Math.max(maxRow, row.size());
		}
		int baseWidth = slot * maxRow;
		treeBaseW = baseWidth;
		int nodeWidth = 22;
		int nodeHeight = 20;
		int rowGap = 28;
		treeBaseH = (maxLevel - 1) * rowGap + nodeHeight;
		double originX = x + width / 2.0 - baseWidth * treeZoom / 2.0 + treePanX;
		double originY = treeTop + 4 - treePanY;

		Map<String, NodePos> positions = new HashMap<>();
		for (int level = 0; level < levels.size(); level++) {
			List<Questline> row = levels.get(level);
			if (row.isEmpty()) {
				continue;
			}
			int rowWidth = slot * row.size();
			int startX = (baseWidth - rowWidth) / 2;
			int baseTop = level * rowGap;
			for (int i = 0; i < row.size(); i++) {
				int baseCx = startX + slot * i + slot / 2;
				int cx = (int) Math.round(originX + baseCx * treeZoom);
				int top = (int) Math.round(originY + baseTop * treeZoom);
				int w = Math.max(10, (int) Math.round(nodeWidth * treeZoom));
				int h = Math.max(8, (int) Math.round(nodeHeight * treeZoom));
				positions.put(row.get(i).id(), new NodePos(cx, top, w, h));
			}
		}

		int line = 0x667A5A38;
		// TOPIK bands: faint separators at the band starts.
		for (int band = 0; band < 6; band++) {
			int firstLevel = band * 3;
			if (firstLevel >= levels.size()) {
				break;
			}
			int bandY = (int) Math.round(originY + firstLevel * rowGap * treeZoom);
			if (bandY > treeTop + 8 && bandY < treeBottom) {
				g.fill(x, bandY, x + width, bandY + 1, 0x227A5A38);
			}
		}
		// Fixed T1–T6 strip: always visible, independent of zoom/pan.
		for (int band = 0; band < 6; band++) {
			int firstLevel = band * 3;
			if (firstLevel >= levels.size()) {
				break;
			}
			int labelY = treeTop + 6 + band * Math.max(15, (treeViewH - 18) / 6);
			Draw.label(g, this.font, "T" + (band + 1), x + 2, labelY, INK_FAINT);
			hits.add(new Hit(x, labelY - 2, 18, 12, () -> {
				treeZoom = 0.9F;
				treePanX = 0;
				treePanY = jumpLevel(firstLevel, rowGap, treeZoom);
			}));
		}
		Questline selected = byId.get(selectedTrailId);
		if (selected == null) {
			selected = quests.activeTrail();
		}
		if (selected == null && !trails.isEmpty()) {
			selected = trails.get(0);
		}
		java.util.Set<String> connected = null;
		if (treeHighlight && selected != null) {
			connected = new java.util.HashSet<>();
			connected.add(selected.id());
			String parentId = primaryParent.get(selected.id());
			while (parentId != null && connected.add(parentId)) {
				parentId = primaryParent.get(parentId);
			}
			java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
			queue.add(selected.id());
			while (!queue.isEmpty()) {
				String id = queue.poll();
				for (Questline child : childrenByParent.getOrDefault(id, List.of())) {
					if (connected.add(child.id())) {
						queue.add(child.id());
					}
				}
			}
		}
		g.enableScissor(treeViewX, treeViewY, treeViewX + treeViewW, treeViewY + treeViewH);
		for (Questline trail : trails) {
			NodePos child = positions.get(trail.id());
			if (child == null) {
				continue;
			}
			String parentId = primaryParent.get(trail.id());
			if (parentId == null) {
				continue;
			}
			if (connected != null && (!connected.contains(trail.id()) || !connected.contains(parentId))) {
				continue;
			}
			NodePos parent = positions.get(parentId);
			if (parent != null) {
				int fromY = parent.top() + parent.height();
				int midY = (fromY + child.top()) / 2;
				g.fill(parent.cx(), fromY, parent.cx() + 1, midY, line);
				g.fill(Math.min(parent.cx(), child.cx()), midY,
						Math.max(parent.cx(), child.cx()) + 1, midY + 1, line);
				g.fill(child.cx(), midY, child.cx() + 1, child.top(), line);
			}
		}

		NodePos hovered = null;
		Questline hoveredTrail = null;
		for (Questline trail : trails) {
			NodePos pos = positions.get(trail.id());
			if (pos == null) {
				continue;
			}
			int left = pos.cx() - pos.width() / 2;
			if (left + pos.width() < x || left > x + width
					|| pos.top() + pos.height() < treeTop || pos.top() > treeBottom) {
				continue;
			}
			boolean done = hq.learn() != null
					&& hq.learn().data().quests.completed.containsKey(trail.id());
			boolean unlocked = quests.unlocked(trail);
			boolean active = quests.isTracked(trail.id());
			int fill = active ? ACCENT : done ? GOOD : unlocked ? 0xFF8A6A44 : 0xFFD8C9A8;
			if (treeZoom < 0.7F) {
				fill = QuestTheme.color(trail.topicKey());
			}
			g.fill(left, pos.top(), left + pos.width(), pos.top() + pos.height(), fill);
			if (treeZoom < 0.7F && !active) {
				int border = done ? GOOD : unlocked ? 0xFF6B4E2E : 0xFFB9A886;
				g.fill(left, pos.top(), left + pos.width(), pos.top() + 1, border);
				g.fill(left, pos.top() + pos.height() - 1, left + pos.width(),
						pos.top() + pos.height(), border);
			}
			if (pos.width() >= 16 && pos.height() >= 16) {
				Draw.itemIcon(g, QuestTheme.icon(TopicResolver.keyFor(trail, byId)), pos.cx() - 8,
						pos.top() + (pos.height() - 16) / 2);
			} else {
				g.fill(pos.cx() - 2, pos.top() + pos.height() / 2 - 1,
						pos.cx() + 2, pos.top() + pos.height() / 2 + 1, CREAM);
			}
			boolean recommended = current != null && !done && unlocked
					&& !trail.id().equals(current.id())
					&& trail.koreanLevel() == current.koreanLevel()
					&& TopicResolver.keyFor(trail, byId).equals(TopicResolver.keyFor(current, byId));
			if (recommended) {
				g.fill(left + pos.width() - 4, pos.top() + 1, left + pos.width(),
						pos.top() + 4, 0xFFE0B33A);
			}
			if (trail.id().equals(selectedTrailId)) {
				g.fill(left - 1, pos.top() - 1, left + pos.width() + 1, pos.top(), 0xFFB98A2E);
				g.fill(left - 1, pos.top() + pos.height(), left + pos.width() + 1,
						pos.top() + pos.height() + 1, 0xFFB98A2E);
				g.fill(left - 1, pos.top(), left, pos.top() + pos.height(), 0xFFB98A2E);
				g.fill(left + pos.width(), pos.top(), left + pos.width() + 1,
						pos.top() + pos.height(), 0xFFB98A2E);
			}
			if (pos.contains(this.mouseX, this.mouseY)) {
				hovered = pos;
				hoveredTrail = trail;
			}
			final String id = trail.id();
			hits.add(new Hit(left, pos.top(), pos.width(), pos.height(), () -> {
				selectedTrailId = id;
				treeHighlight = true;
			}));
		}
		if (connected != null) {
			for (Questline trail : trails) {
				if (connected.contains(trail.id())) {
					continue;
				}
				NodePos pos = positions.get(trail.id());
				if (pos == null) {
					continue;
				}
				int left = pos.cx() - pos.width() / 2;
				g.fill(left, pos.top(), left + pos.width(), pos.top() + pos.height(), 0xAAF3EAD8);
			}
		}
		g.disableScissor();

		// Hide anything that was panned into the detail area.
		g.fill(x, treeBottom, x + width, y + height, PARCHMENT);

		if (selected != null) {
			selectedTrailId = selected.id();
			boolean done = hq.learn() != null
					&& hq.learn().data().quests.completed.containsKey(selected.id());
			boolean unlocked = quests.unlocked(selected);
			boolean active = quests.isTracked(selected.id());
			boolean focused = selected.id().equals(quests.focusedId());
			String state = done ? "✓ fertig" : focused ? "▶ aktiv" : active ? "▸ im Log"
					: unlocked ? "○ offen" : "· gesperrt";
			int detailTop = treeBottom;
			g.fill(x, detailTop, x + width, detailTop + 1, 0x55CBB88F);
			String title = selected.titleDe();
			Draw.truncated(g, this.font, Component.literal(title + " · Stufe " + depths.getOrDefault(selected.id(), 1)
							+ " · TOPIK " + selected.koreanLevel() + " · " + state),
					x, detailTop + 3, width - 68, done ? GOOD : active ? ACCENT : INK);
			List<String> parentIds = new ArrayList<>();
			List<String> parents = new ArrayList<>();
			for (String parentId : selected.requires().completed()) {
				Questline parent = byId.get(parentId);
				if (parent != null) {
					parentIds.add(parentId);
					parents.add(parent.titleDe());
				}
			}
			List<String> childIds = new ArrayList<>();
			List<String> children = new ArrayList<>();
			for (Questline other : trails) {
				if (other.requires().completed().contains(selected.id())) {
					childIds.add(other.id());
					children.add(other.titleDe());
				}
			}
			String nav = "Vorher: " + (parents.isEmpty() ? "—" : String.join(", ", parents))
					+ "   Danach: " + (children.isEmpty() ? "—" : String.join(", ", children));
			Draw.truncated(g, this.font, Component.literal(nav), x, detailTop + 17,
					width - (parentIds.isEmpty() ? 0 : 22) - (childIds.isEmpty() ? 0 : 22), INK_SOFT);
			if (!parentIds.isEmpty()) {
				String firstParent = parentIds.get(0);
				button(g, x + width - 44, detailTop + 16, 20, 13, "◀", false,
						() -> selectedTrailId = firstParent);
			}
			if (!childIds.isEmpty()) {
				String firstChild = childIds.get(0);
				button(g, x + width - 22, detailTop + 16, 20, 13, "▶", false,
						() -> selectedTrailId = firstChild);
			}
			String preview = selected.allSteps().isEmpty() ? ""
					: quests.clueKo(selected.allSteps().get(0));
			if (preview != null && !preview.isBlank()) {
				Draw.truncated(g, this.font, KoText.ko(preview), x, detailTop + 31,
						width, INK);
			}
			Draw.truncated(g, this.font, Component.literal(progressOf(selected, done)
							+ (focused ? " · " + quests.statusText() : "")),
					x, detailTop + 45, width - 68, INK_FAINT);
			if (active && !focused && !done) {
				Questline chosen = selected;
				button(g, x + width - 62, detailTop + 52, 58, 16, "Fokus", false, () -> {
					quests.focus(chosen.id());
					status = "Fokus: " + chosen.titleDe();
				});
			} else if (unlocked && !active && !done) {
				Questline chosen = selected;
				button(g, x + width - 62, detailTop + 52, 58, 16, "Starten", false, () -> {
					Observation obs = new Observation();
					obs.now = System.currentTimeMillis();
					quests.startTrail(chosen.id(), obs, hq.hud());
					onClose();
				});
			} else if (!unlocked) {
				String reason = lockReason(selected, quests);
				if (!reason.isEmpty()) {
					Draw.truncated(g, this.font, Component.literal("Gesperrt: " + reason),
							x + width - 150, detailTop + 45, 146, BAD);
				}
			}
		}

		if (hoveredTrail != null && hovered != null) {
			String label = hoveredTrail.titleDe() + " · Stufe "
					+ depths.getOrDefault(hoveredTrail.id(), 1) + " · TOPIK "
					+ hoveredTrail.koreanLevel();
			int tw = this.font.width(label) + 8;
			int tx = Math.max(x, Math.min(this.mouseX + 6, x + width - tw));
			int ty = Math.max(y, this.mouseY - 14);
			g.fill(tx - 2, ty - 2, tx + tw, ty + 12, 0xFF3A2718);
			g.fill(tx - 1, ty - 1, tx + tw - 1, ty + 11, PARCHMENT);
			Draw.label(g, this.font, label, tx + 2, ty, INK);
		}
	}

	private double jumpLevel(int firstLevel, int rowGap, float zoom) {
		return firstLevel * rowGap * zoom;
	}

	private void fitTree() {
		if (treeBaseW <= 0 || treeBaseH <= 0) {
			return;
		}
		float fitX = (treeViewW - 18.0F) / treeBaseW;
		float fitY = (treeViewH - 6.0F) / treeBaseH;
		treeZoom = Math.max(0.18F, Math.min(1.0F, Math.min(fitX, fitY)));
		treePanX = 0;
		treePanY = 0;
	}

	private int depthOf(Questline trail, Map<String, Questline> byId, Map<String, Integer> memo) {
		Integer known = memo.get(trail.id());
		if (known != null) {
			return known;
		}
		int depth = 1;
		for (String parentId : trail.requires().completed()) {
			Questline parent = byId.get(parentId);
			if (parent != null) {
				depth = Math.max(depth, depthOf(parent, byId, memo) + 1);
			}
		}
		memo.put(trail.id(), depth);
		return depth;
	}

	// ----------------------------------------------------------------- settings

	private void settings(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		int yy = y;
		yy = toggle(g, x, yy, width, "Quests", hq.config().quests, value -> hq.config().quests = value);
		yy = toggle(g, x, yy, width, "Quest-Tracker", hq.config().questTracker,
				value -> hq.config().questTracker = value);
		yy = toggle(g, x, yy, width, "HUD-Karten", hq.config().hudCards, value -> hq.config().hudCards = value);
		yy = toggle(g, x, yy, width, "Tooltips", hq.config().tooltips, value -> hq.config().tooltips = value);
		yy = toggle(g, x, yy, width, "Auto-Quiz", hq.config().autoQuiz, value -> hq.config().autoQuiz = value);
		yy = toggle(g, x, yy, width, "Ton", hq.config().sound, value -> hq.config().sound = value);
		yy = toggle(g, x, yy, width, "Deutsch in Quests", hq.config().questGerman,
				value -> hq.config().questGerman = value);
		yy = toggle(g, x, yy, width, "Lute-Import", hq.config().luteImport,
				value -> hq.config().luteImport = value);
		yy += 6;
		button(g, x, yy, 150, 18, "Fortschritt zurücksetzen", false, () -> {
			long now = System.currentTimeMillis();
			if (now < confirmResetUntil) {
				hq.learn().store().backup("pre-reset");
				hq.quests().abandon();
				hq.learn().data().words.clear();
				hq.learn().data().days.clear();
				hq.learn().data().achievements.clear();
				hq.learn().data().totals = new de.zirks.hangulquest.learn.ProgressData.Totals();
				hq.learn().data().quests = new de.zirks.hangulquest.learn.ProgressData.QuestData();
				hq.learn().markDirty();
				hq.learn().store().flush();
				dictionaryCache = null;
				confirmResetUntil = 0;
				status = "Fortschritt zurückgesetzt (Backup angelegt).";
			} else {
				confirmResetUntil = now + 3000;
				status = "Noch einmal klicken, um wirklich zu löschen.";
			}
		});
		Draw.label(g, this.font, "Lute: " + luteInfo(), x, y + height - 10, INK_FAINT);
	}

	private String luteInfo() {
		var info = hq.learn() == null ? null : hq.learn().data().vocab.luteImport;
		if (info == null) {
			return "keine Datei";
		}
		return info.imported + " Wörter importiert, " + info.skipped + " übersprungen";
	}

	/** Completed steps within this trail, e.g. "2/4" — active or paused. */
	private String progressOf(Questline trail, boolean done) {
		int total = trail.allSteps().size();
		if (done) {
			return total + "/" + total;
		}
		var quests = hq.learn() == null ? null : hq.learn().data().quests;
		if (quests == null) {
			return "0/" + total;
		}
		ProgressData.Active state = null;
		if (quests.active != null && trail.id().equals(quests.active.trailId)) {
			state = quests.active;
		} else if (quests.saved != null) {
			state = quests.saved.get(trail.id());
		}
		int index = 0;
		if (state != null && state.stepId != null) {
			List<QuestStep> steps = trail.allSteps();
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(state.stepId)) {
					index = i;
					break;
				}
			}
		}
		return index + "/" + total;
	}

	private String lockReason(Questline trail, QuestEngine quests) {
		if (hq.learn() != null && hq.learn().discovered() < trail.requires().minWordsKnown()) {
			return "ab " + trail.requires().minWordsKnown() + " Wörtern";
		}
		List<String> missing = new ArrayList<>();
		for (String parentId : trail.requires().completed()) {
			boolean done = hq.learn() != null
					&& hq.learn().data().quests.completed.containsKey(parentId);
			if (!done) {
				Questline parent = quests.byId(parentId);
				missing.add(parent == null ? parentId : (showDe ? parent.titleDe() : parent.titleKo()));
			}
		}
		return missing.isEmpty() ? "" : "nach " + String.join(", ", missing);
	}

	/** Leather cover, sewn spine, page stack, margin rule, ribbon and ruling lines. */
	private void drawBook(GuiGraphicsExtractor g, int px, int py, int width, int height) {
		g.fill(px - 10, py - 10, px + width + 10, py + height + 12, 0x66000000);
		g.fill(px - 8, py - 8, px + width + 8, py + height + 8, LEATHER_DARK);
		g.fill(px - 6, py - 6, px + width + 6, py + height + 6, LEATHER);
		for (int x = px - 4; x < px + width + 4; x += 6) {
			g.fill(x, py - 5, x + 3, py - 4, STITCH);
			g.fill(x, py + height + 4, x + 3, py + height + 5, STITCH);
		}
		for (int y = py - 4; y < py + height + 4; y += 6) {
			g.fill(px - 5, y, px - 4, y + 3, STITCH);
			g.fill(px + width + 4, y, px + width + 5, y + 3, STITCH);
		}
		// stacked page edge then the top page
		g.fill(px + 3, py + 3, px + width + 3, py + height + 3, PAGE_EDGE);
		g.fill(px, py, px + width, py + height, PARCHMENT);
		g.fill(px, py, px + 14, py + height, PARCHMENT_DARK);
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
		g.fill(ribbonX, py + 32, ribbonX + 7, py + 82, RIBBON);
		g.fill(ribbonX, py + 82, ribbonX + 3, py + 86, PARCHMENT);
		g.fill(ribbonX + 4, py + 82, ribbonX + 7, py + 86, PARCHMENT);
		// small title on the spine
		String spine = "한글일지";
		for (int i = 0; i < spine.length(); i++) {
			Draw.korean(g, this.font, spine.substring(i, i + 1), px + 1, py + 40 + i * 12, 0xFFE0C894);
		}
	}

	private int toggle(GuiGraphicsExtractor g, int x, int y, int width, String label, boolean value,
					   java.util.function.Consumer<Boolean> setter) {
		Draw.label(g, this.font, value ? "✓ " + label : "· " + label, x, y, value ? GOOD : INK_FAINT);
		hits.add(new Hit(x, y - 1, width, 15, () -> {
			setter.accept(!value);
			hq.saveConfig();
		}));
		return y + 16;
	}

	// ------------------------------------------------------------------ widgets

	private void drawEye(GuiGraphicsExtractor g, int x, int y, boolean open) {
		boolean hover = mouseX >= x - 2 && mouseX < x + 16 && mouseY >= y - 2 && mouseY < y + 16;
		if (hover) {
			g.fill(x - 2, y - 2, x + 16, y + 16, PARCHMENT_DARK);
		}
		int color = open ? INK : INK_FAINT;
		g.fill(x + 1, y + 7, x + 15, y + 9, color);
		g.fill(x + 3, y + 5, x + 13, y + 7, color);
		g.fill(x + 3, y + 9, x + 13, y + 11, color);
		g.fill(x + 5, y + 3, x + 11, y + 5, color);
		g.fill(x + 5, y + 11, x + 11, y + 13, color);
		g.fill(x + 6, y + 6, x + 10, y + 10, open ? ACCENT : INK_FAINT);
		if (!open) {
			for (int i = 0; i < 12; i++) {
				g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFF8080);
			}
		}
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int width, int height, String label,
						boolean active, Runnable action) {
		boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		int background = active ? RIBBON : hover ? 0xFFF2E3C2 : PARCHMENT_DARK;
		g.fill(x, y, x + width, y + height, background);
		g.fill(x, y + height - 1, x + width, y + height, active ? 0xFF5E1A1A : PAGE_EDGE);
		Draw.label(g, this.font, label, x + 4, y + (height - this.font.lineHeight) / 2,
				active ? CREAM : INK);
		hits.add(new Hit(x, y, width, height, action));
	}
}
