package io.github.leks2205.hangulquest.ui.journal;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The quest tree with zoom, pan and a detail panel. The tree's shape is laid out once per trail
 * list ({@link QuestTreeLayout}), the per-trail state once per change of progress
 * ({@link NodeStates}) and the detail texts only when the selection or that state changes, so a
 * frame is a projection and a loop of fills.
 */
public final class QuestTab extends JournalTab {
	private static final int DETAIL_HEIGHT = 76;
	private static final int CONNECTOR = 0x667A5A38;
	private static final int HIGHLIGHT = 0xFFB98A2E;

	/** The selected trail's texts and flags, valid for one selection, one progress state and one second-ish. */
	private record Detail(int index, NodeStates states, boolean showDe, int language, long bucket,
						  Questline trail, boolean done, boolean unlocked, boolean tracked, boolean focused,
						  Component title, int titleColor, Component nav, String firstParent, String firstChild,
						  Component preview, Component progress, Component lock) {
	}

	private String selectedTrailId;
	private float treeZoom = 1.0F;
	private double treePanX;
	private double treePanY;
	private boolean treeDragging;
	private boolean treeHighlight;
	private int treeViewX;
	private int treeViewY;
	private int treeViewW;
	private int treeViewH;
	private int treeBottom;

	private QuestTreeLayout layout;
	private NodeStates states;
	private Detail detail;
	private String header = "";
	private int headerLanguage = -1;
	private int headerCount = -1;
	private String legend = "";
	private int legendPercent = -1;
	private int legendLanguage = -1;
	private int tipIndex = -1;
	private int tipLanguage = -1;
	private String tipText = "";
	private int tipWidth;

	private final JournalHost.PointClick nodeClick = this::clickNode;
	private final Runnable[] bandJumps = new Runnable[6];
	/** The second view, the trails by life situation, beside the tree. */
	private final ChapterView chapterView;
	private boolean chapterMode;
	private final Runnable showTree = () -> chapterMode = false;
	private final Runnable showChapters = () -> chapterMode = true;

	public QuestTab(JournalHost host) {
		super(host);
		chapterView = new ChapterView(host, this::showTrailInTree);
		for (int band = 0; band < bandJumps.length; band++) {
			int firstLevel = band * 3;
			bandJumps[band] = () -> {
				treeZoom = 0.9F;
				treePanX = 0;
				treePanY = firstLevel * QuestTreeLayout.ROW_GAP * treeZoom;
			};
		}
	}

	@Override
	public void onOpen() {
		treeZoom = 1.0F;
		treePanX = 0;
		treePanY = 0;
		chapterView.onOpen();
	}

	/** A trail picked in the chapter view: back to the tree with that trail selected and its neighbours lit. */
	private void showTrailInTree(String trailId) {
		selectedTrailId = trailId;
		treeHighlight = true;
		chapterMode = false;
	}

	// ------------------------------------------------------------------- input

	@Override
	public boolean keyPressed(int key) {
		if (key == InputConstants.KEY_F && !chapterMode) {
			fitTree();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (chapterMode) {
			return false;
		}
		// 26.3 uses SDL button numbers: 1 = left, 2 = right, 3 = middle.
		if (button == 2 && insideTree(x, y)) {
			// Right-click resets the highlight/selection; left-drag must not.
			treeHighlight = false;
			selectedTrailId = null;
			treeDragging = false;
			return true;
		}
		if (button == 1 && insideTree(x, y)) {
			// Empty tree space must be consumed too, otherwise Minecraft never sends drag events.
			treeDragging = true;
		}
		return false;
	}

	@Override
	public boolean holdsPress() {
		return treeDragging;
	}

	@Override
	public boolean mouseDragged(double dragX, double dragY, int button) {
		if (treeDragging && button == 1) {
			// Horizontal follows the mouse; vertical is inverted because originY = treeTop - treePanY.
			treePanX += dragX;
			treePanY -= dragY;
			return true;
		}
		return false;
	}

	@Override
	public void mouseReleased(int button) {
		if (button == 1) {
			treeDragging = false;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
		if (chapterMode) {
			return chapterView.mouseScrolled(vertical);
		}
		float oldZoom = treeZoom;
		treeZoom = (float) Math.max(0.25, Math.min(2.2, treeZoom * (vertical > 0 ? 1.12 : 0.89)));
		double factor = treeZoom / oldZoom;
		treePanX = mouseX - (mouseX - treePanX) * factor;
		treePanY = mouseY - (mouseY - treePanY) * factor;
		return true;
	}

	private boolean insideTree(double mouseX, double mouseY) {
		return mouseX >= treeViewX && mouseX < treeViewX + treeViewW
				&& mouseY >= treeViewY && mouseY < treeViewY + treeViewH;
	}

	private boolean clickNode(double mouseX, double mouseY) {
		if (layout == null) {
			return false;
		}
		int node = layout.nodeAt(mouseX, mouseY, treeViewY, treeBottom, treeViewX, treeViewX + treeViewW);
		if (node < 0) {
			return false;
		}
		selectedTrailId = layout.trail(node).id();
		treeHighlight = true;
		return true;
	}

	private void fitTree() {
		if (layout == null || layout.baseWidth() <= 0 || layout.baseHeight() <= 0) {
			return;
		}
		float fitX = (treeViewW - 18.0F) / layout.baseWidth();
		float fitY = (treeViewH - 6.0F) / layout.baseHeight();
		treeZoom = Math.max(0.18F, Math.min(1.0F, Math.min(fitX, fitY)));
		treePanX = 0;
		treePanY = 0;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		HangulQuestClient hq = host.hq();
		QuestEngine quests = hq.quests();
		if (quests == null) {
			Draw.label(g, host.font(), I18n.t("hangulquest.ui.quest.loading"), x, y, Ink.INK_SOFT);
			return;
		}
		host.button(g, x + width - 100, y - 2, 42, 14, I18n.t("hangulquest.ui.chapters.tree"), !chapterMode, showTree);
		host.button(g, x + width - 56, y - 2, 56, 14, I18n.t("hangulquest.ui.chapters.chapters"), chapterMode, showChapters);
		if (chapterMode) {
			chapterView.render(g, x, y + 16, width, height - 16);
			return;
		}
		if (layout == null || !layout.matches(quests.trails())) {
			layout = QuestTreeLayout.build(quests.trails(), quests.graph());
			states = null;
			detail = null;
		}
		NodeStates nodeStates = states(quests);
		int language = language();
		int highlighted = layout.indexOf(selectedTrailId);

		if (headerCount != layout.size() || headerLanguage != language) {
			header = I18n.t("hangulquest.ui.quest.tree_header", layout.size());
			headerCount = layout.size();
			headerLanguage = language;
		}
		int percent = Math.round(treeZoom * 100);
		if (legendPercent != percent || legendLanguage != language) {
			legend = I18n.t("hangulquest.ui.quest.tree_legend", percent);
			legendPercent = percent;
			legendLanguage = language;
		}
		Draw.label(g, host.font(), header, x, y, Ink.ACCENT);
		Draw.label(g, host.font(), legend, x + 110, y, Ink.INK_FAINT);

		int treeTop = y + 13;
		treeBottom = y + height - DETAIL_HEIGHT;
		treeViewX = x;
		treeViewY = treeTop;
		treeViewW = width;
		treeViewH = Math.max(20, treeBottom - treeTop);

		double originX = x + width / 2.0 - layout.baseWidth() * treeZoom / 2.0 + treePanX;
		double originY = treeTop + 4 - treePanY;
		layout.project(originX, originY, treeZoom);

		// TOPIK bands: faint separators at the band starts.
		int levels = layout.levels();
		for (int band = 0; band < 6; band++) {
			int firstLevel = band * 3;
			if (firstLevel >= levels) {
				break;
			}
			int bandY = (int) Math.round(originY + firstLevel * QuestTreeLayout.ROW_GAP * treeZoom);
			if (bandY > treeTop + 8 && bandY < treeBottom) {
				g.fill(x, bandY, x + width, bandY + 1, 0x227A5A38);
			}
		}
		// Fixed T1–T6 strip: always visible, independent of zoom/pan.
		for (int band = 0; band < 6; band++) {
			if (band * 3 >= levels) {
				break;
			}
			int labelY = treeTop + 6 + band * Math.max(15, (treeViewH - 18) / 6);
			Draw.label(g, host.font(), "T" + (band + 1), x + 2, labelY, Ink.INK_FAINT);
			host.hit(x, labelY - 2, 18, 12, bandJumps[band]);
		}
		host.hit(treeViewX, treeViewY, treeViewW, treeViewH, nodeClick);

		int selectedIndex = layout.indexOf(selectedTrailId);
		if (selectedIndex < 0) {
			Questline active = quests.activeTrail();
			selectedIndex = active == null ? -1 : layout.indexOf(active.id());
		}
		if (selectedIndex < 0 && layout.size() > 0) {
			selectedIndex = 0;
		}
		boolean[] connected = treeHighlight && selectedIndex >= 0 ? layout.connected(selectedIndex) : null;

		g.enableScissor(treeViewX, treeViewY, treeViewX + treeViewW, treeViewY + treeViewH);
		drawConnectors(g, connected);
		int hovered = drawNodes(g, nodeStates, x, width, treeTop, highlighted);
		if (connected != null) {
			dimUnconnected(g, connected, x, width, treeTop);
		}
		g.disableScissor();

		// Hide anything that was panned into the detail area.
		g.fill(x, treeBottom, x + width, y + height, Ink.PARCHMENT);

		if (selectedIndex >= 0) {
			Detail d = detail(quests, nodeStates, selectedIndex);
			selectedTrailId = d.trail().id();
			drawDetail(g, quests, d, x, width, treeBottom);
		}
		if (hovered >= 0) {
			drawTooltip(g, hovered, x, y, width, language);
		}
	}

	private NodeStates states(QuestEngine quests) {
		LearningService learn = host.hq().learn();
		ProgressData.QuestData progress = learn == null ? null : learn.data().quests;
		Map<String, Long> completed = progress == null ? null : progress.completed;
		int discovered = learn == null ? 0 : learn.discovered();
		int tracked = quests.trackedCount();
		String focused = quests.focusedId();
		Questline active = quests.activeTrail();
		NodeStates current = states;
		if (current != null && current.matches(layout, completed, completed == null ? 0 : completed.size(),
				discovered, tracked, focused, active)) {
			return current;
		}
		states = NodeStates.build(layout, completed, discovered, tracked, focused,
				quests::unlocked, quests::isTracked, active);
		return states;
	}

	private void drawConnectors(GuiGraphicsExtractor g, boolean[] connected) {
		int right = treeViewX + treeViewW;
		int bottom = treeViewY + treeViewH;
		int nodeHeight = layout.nodeHeight();
		for (int i = 0; i < layout.size(); i++) {
			int parent = layout.primaryParent(i);
			if (parent < 0) {
				continue;
			}
			if (connected != null && (!connected[i] || !connected[parent])) {
				continue;
			}
			int fromY = layout.top(parent) + nodeHeight;
			int toY = layout.top(i);
			int parentX = layout.cx(parent);
			int childX = layout.cx(i);
			// Connectors that lie wholly outside the view would be clipped anyway; skip their fills.
			if (Math.max(parentX, childX) + 1 < treeViewX || Math.min(parentX, childX) > right
					|| Math.max(fromY, toY) < treeViewY || Math.min(fromY, toY) > bottom) {
				continue;
			}
			int midY = (fromY + toY) / 2;
			g.fill(parentX, fromY, parentX + 1, midY, CONNECTOR);
			g.fill(Math.min(parentX, childX), midY, Math.max(parentX, childX) + 1, midY + 1, CONNECTOR);
			g.fill(childX, midY, childX + 1, toY, CONNECTOR);
		}
	}

	/** Draws the visible nodes; returns the node under the mouse, or -1. */
	private int drawNodes(GuiGraphicsExtractor g, NodeStates nodeStates, int x, int width, int treeTop,
						  int highlighted) {
		int hovered = -1;
		int nodeWidth = layout.nodeWidth();
		int nodeHeight = layout.nodeHeight();
		boolean zoomedOut = treeZoom < 0.7F;
		int current = nodeStates.current();
		for (int i = 0; i < layout.size(); i++) {
			int left = layout.left(i);
			int top = layout.top(i);
			if (left + nodeWidth < x || left > x + width || top + nodeHeight < treeTop || top > treeBottom) {
				continue;
			}
			boolean done = nodeStates.has(i, NodeStates.DONE);
			boolean unlocked = nodeStates.has(i, NodeStates.UNLOCKED);
			boolean active = nodeStates.has(i, NodeStates.TRACKED);
			int fill = active ? Ink.ACCENT : done ? Ink.GOOD : unlocked ? 0xFF8A6A44 : 0xFFD8C9A8;
			if (zoomedOut) {
				fill = layout.color(i);
			}
			g.fill(left, top, left + nodeWidth, top + nodeHeight, fill);
			if (zoomedOut && !active) {
				int border = done ? Ink.GOOD : unlocked ? 0xFF6B4E2E : 0xFFB9A886;
				g.fill(left, top, left + nodeWidth, top + 1, border);
				g.fill(left, top + nodeHeight - 1, left + nodeWidth, top + nodeHeight, border);
			}
			if (nodeWidth >= 16 && nodeHeight >= 16) {
				Draw.itemIcon(g, layout.icon(i), layout.cx(i) - 8, top + (nodeHeight - 16) / 2);
			} else {
				g.fill(layout.cx(i) - 2, top + nodeHeight / 2 - 1, layout.cx(i) + 2, top + nodeHeight / 2 + 1,
						Ink.CREAM);
			}
			if (nodeStates.has(i, NodeStates.RECOMMENDED) && current >= 0) {
				g.fill(left + nodeWidth - 4, top + 1, left + nodeWidth, top + 4, 0xFFE0B33A);
			}
			if (i == highlighted) {
				g.fill(left - 1, top - 1, left + nodeWidth + 1, top, HIGHLIGHT);
				g.fill(left - 1, top + nodeHeight, left + nodeWidth + 1, top + nodeHeight + 1, HIGHLIGHT);
				g.fill(left - 1, top, left, top + nodeHeight, HIGHLIGHT);
				g.fill(left + nodeWidth, top, left + nodeWidth + 1, top + nodeHeight, HIGHLIGHT);
			}
			if (layout.contains(i, host.mouseX(), host.mouseY())) {
				hovered = i;
			}
		}
		return hovered;
	}

	private void dimUnconnected(GuiGraphicsExtractor g, boolean[] connected, int x, int width, int treeTop) {
		int nodeWidth = layout.nodeWidth();
		int nodeHeight = layout.nodeHeight();
		for (int i = 0; i < layout.size(); i++) {
			if (connected[i]) {
				continue;
			}
			int left = layout.left(i);
			int top = layout.top(i);
			if (left + nodeWidth < x || left > x + width || top + nodeHeight < treeTop || top > treeBottom) {
				continue;
			}
			g.fill(left, top, left + nodeWidth, top + nodeHeight, 0xAAF3EAD8);
		}
	}

	private void drawTooltip(GuiGraphicsExtractor g, int index, int x, int y, int width, int language) {
		if (tipIndex != index || tipLanguage != language) {
			Questline trail = layout.trail(index);
			tipText = I18n.t("hangulquest.ui.quest.tip", trail.title(), layout.depth(index), trail.koreanLevel());
			tipWidth = host.font().width(tipText) + 8;
			tipIndex = index;
			tipLanguage = language;
		}
		int tx = Math.max(x, Math.min(host.mouseX() + 6, x + width - tipWidth));
		int ty = Math.max(y, host.mouseY() - 14);
		g.fill(tx - 2, ty - 2, tx + tipWidth, ty + 12, 0xFF3A2718);
		g.fill(tx - 1, ty - 1, tx + tipWidth - 1, ty + 11, Ink.PARCHMENT);
		Draw.label(g, host.font(), tipText, tx + 2, ty, Ink.INK);
	}

	// ----------------------------------------------------------------- detail

	private Detail detail(QuestEngine quests, NodeStates nodeStates, int index) {
		boolean showDe = host.showDe();
		int language = language();
		// The live status line moves with the game ticks, so the texts are refreshed a few times a second.
		long bucket = System.currentTimeMillis() / 250L;
		Detail cached = detail;
		if (cached != null && cached.index() == index && cached.states() == nodeStates
				&& cached.showDe() == showDe && cached.language() == language && cached.bucket() == bucket) {
			return cached;
		}
		Questline selected = layout.trail(index);
		boolean done = nodeStates.has(index, NodeStates.DONE);
		boolean unlocked = nodeStates.has(index, NodeStates.UNLOCKED);
		boolean tracked = nodeStates.has(index, NodeStates.TRACKED);
		boolean focused = selected.id().equals(quests.focusedId());
		String state = done ? I18n.t("hangulquest.ui.quest.state.done") : focused ? I18n.t("hangulquest.ui.quest.state.active") : tracked ? I18n.t("hangulquest.ui.quest.state.in_log")
				: unlocked ? I18n.t("hangulquest.ui.quest.state.open") : I18n.t("hangulquest.ui.quest.state.locked");
		String title = I18n.t("hangulquest.ui.quest.title_line", selected.title(), layout.depth(index),
				selected.koreanLevel(), state);

		int[] parents = layout.parents(index);
		int[] dependents = layout.dependents(index);
		List<String> parentTitles = new ArrayList<>();
		for (int parent : parents) {
			parentTitles.add(layout.trail(parent).title());
		}
		List<String> childTitles = new ArrayList<>();
		for (int child : dependents) {
			childTitles.add(layout.trail(child).title());
		}
		String nav = I18n.t("hangulquest.ui.quest.nav", parentTitles.isEmpty() ? "—" : String.join(", ", parentTitles),
				childTitles.isEmpty() ? "—" : String.join(", ", childTitles));

		List<QuestStep> steps = selected.allSteps();
		String preview = steps.isEmpty() ? "" : quests.clueKo(steps.get(0));
		String liveStatus = focused ? quests.statusText() : "";
		String progress = progressOf(selected, steps, done)
				+ (liveStatus.isBlank() || liveStatus.contains("/") ? "" : " · " + liveStatus);
		String reason = unlocked ? "" : lockReason(selected, quests);

		detail = new Detail(index, nodeStates, showDe, language, bucket, selected, done, unlocked, tracked, focused,
				Component.literal(title), done ? Ink.GOOD : tracked ? Ink.ACCENT : Ink.INK,
				Component.literal(nav),
				parents.length == 0 ? null : layout.trail(parents[0]).id(),
				dependents.length == 0 ? null : layout.trail(dependents[0]).id(),
				preview == null || preview.isBlank() ? null : KoText.ko(preview),
				Component.literal(progress),
				reason.isEmpty() ? null : Component.literal(I18n.t("hangulquest.ui.quest.locked_reason", reason)));
		return detail;
	}

	private void drawDetail(GuiGraphicsExtractor g, QuestEngine quests, Detail d, int x, int width,
							int detailTop) {
		g.fill(x, detailTop, x + width, detailTop + 1, 0x55CBB88F);
		Draw.truncated(g, host.font(), d.title(), x, detailTop + 3, width - 68, d.titleColor());
		Draw.truncated(g, host.font(), d.nav(), x, detailTop + 17,
				width - (d.firstParent() == null ? 0 : 22) - (d.firstChild() == null ? 0 : 22), Ink.INK_SOFT);
		if (d.firstParent() != null) {
			String firstParent = d.firstParent();
			host.button(g, x + width - 44, detailTop + 16, 20, 13, "◀", false, () -> selectedTrailId = firstParent);
		}
		if (d.firstChild() != null) {
			String firstChild = d.firstChild();
			host.button(g, x + width - 22, detailTop + 16, 20, 13, "▶", false, () -> selectedTrailId = firstChild);
		}
		if (d.preview() != null) {
			Draw.truncated(g, host.font(), d.preview(), x, detailTop + 31, width, Ink.INK);
		}
		Draw.truncated(g, host.font(), d.progress(), x, detailTop + 45, width - 68, Ink.INK_FAINT);
		Questline chosen = d.trail();
		if (!d.done()) {
			host.checkRow(g, x + width - 156, detailTop + 51, 88, 17, I18n.t("hangulquest.ui.quest.in_log"), d.tracked(), () -> {
				boolean on = !quests.isTracked(chosen.id());
				if (!quests.setTracked(chosen.id(), on)) {
					host.status(I18n.t("hangulquest.ui.quest.log_full"));
				} else {
					host.status(on ? I18n.t("hangulquest.ui.quest.added_to_log", chosen.title())
							: I18n.t("hangulquest.ui.quest.removed_from_log"));
				}
			});
		}
		if (d.tracked() && !d.focused() && !d.done()) {
			host.button(g, x + width - 62, detailTop + 52, 58, 16, I18n.t("hangulquest.ui.quest.focus"), false, () -> {
				quests.focus(chosen.id());
				host.status(I18n.t("hangulquest.ui.quest.focus_status", chosen.title()));
			});
		} else if (d.unlocked() && !d.tracked() && !d.done()) {
			host.button(g, x + width - 62, detailTop + 52, 58, 16, I18n.t("hangulquest.ui.quest.start"), false, () -> {
				Observation obs = new Observation();
				obs.now = System.currentTimeMillis();
				quests.startTrail(chosen.id(), obs, host.hq().hud());
				host.close();
			});
		} else if (!d.unlocked() && d.lock() != null) {
			Draw.truncated(g, host.font(), d.lock(), x + width - 150, detailTop + 45, 146, Ink.BAD);
		}
	}

	/** Completed steps within this trail, e.g. "2/4" — active or paused. */
	private String progressOf(Questline trail, List<QuestStep> steps, boolean done) {
		int total = steps.size();
		if (done) {
			return total + "/" + total;
		}
		LearningService learn = host.hq().learn();
		ProgressData.QuestData quests = learn == null ? null : learn.data().quests;
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
		LearningService learn = host.hq().learn();
		if (learn != null && learn.discovered() < trail.requires().minWordsKnown()) {
			return I18n.t("hangulquest.ui.quest.requires_words", trail.requires().minWordsKnown());
		}
		List<String> missing = new ArrayList<>();
		for (String parentId : trail.requires().completed()) {
			boolean done = learn != null && learn.data().quests.completed.containsKey(parentId);
			if (!done) {
				Questline parent = quests.byId(parentId);
				missing.add(parent == null ? parentId : (host.showDe() ? parent.title() : parent.titleKo()));
			}
		}
		return missing.isEmpty() ? "" : I18n.t("hangulquest.ui.quest.requires_after", String.join(", ", missing));
	}
}
