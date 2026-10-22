package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.config.WelcomePolicy;
import io.github.leks2205.hangulquest.data.HangulResources;
import io.github.leks2205.hangulquest.util.Lang;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.List;

/**
 * Shown once on the first world join of a fresh install: Hangul Quest expects the player to read Hangul
 * and does not teach it. Nothing here blocks quests or records a grade; the self-check is just a line
 * of Korean. Links go through Minecraft's own confirmation screen, the mod makes no network request.
 */
public final class WelcomeScreen extends Screen {
	private static final String CHECK_SENTENCE = "고양이가 나무 위에 있어요.";
	private static final int PAD = 10;
	private static final int ROW = 18;
	private static final int BUTTON = 20;

	private final Screen parent;
	private final boolean manual;
	private final List<HangulResources.Entry> resources;
	private boolean expanded;
	private int scroll;

	// layout, computed in init()
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int titleY;
	private int bodyY;
	private int checkY;
	private int listHeaderY;
	private List<FormattedCharSequence> bodyLines = List.of();

	private WelcomeScreen(Screen parent, boolean manual) {
		super(Component.literal("한글 탐험"));
		this.parent = parent;
		this.manual = manual;
		this.resources = HangulResources.orderedFor(HangulResources.load(), Lang.current());
	}

	/** Opens the screen by itself on the first world join (a reminder may still be left). */
	public static void openOnJoin() {
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new WelcomeScreen(null, false));
	}

	/** Opens the screen from the journal; closing returns to {@code parent}. */
	public static void openFromJournal(Screen parent) {
		net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new WelcomeScreen(parent, true));
	}

	private Config config() {
		HangulQuestClient hq = HangulQuestClient.get();
		return hq == null ? null : hq.config();
	}

	@Override
	protected void init() {
		panelWidth = Math.min(380, this.width - 16);
		int inner = panelWidth - 2 * PAD;
		bodyLines = this.font.split(Component.literal(I18n.t("hangulquest.ui.welcome.body")), inner);

		int fixed = PAD + 12 + 6 + bodyLines.size() * (this.font.lineHeight + 1) + 8 + 30 + 8
				+ BUTTON + 4 + BUTTON + 8 + BUTTON + PAD;
		int listChrome = 6 + 12;
		int visible = 0;
		if (expanded && !resources.isEmpty()) {
			int room = this.height - 4 - fixed - listChrome;
			visible = Math.max(1, Math.min(resources.size(), room / ROW));
			scroll = Math.max(0, Math.min(resources.size() - visible, scroll));
		}
		panelHeight = fixed + (expanded && visible > 0 ? listChrome + visible * ROW : 0);
		left = (this.width - panelWidth) / 2;
		top = Math.max(2, (this.height - panelHeight) / 2);

		int y = top + PAD;
		titleY = y;
		y += 12 + 6;
		bodyY = y;
		y += bodyLines.size() * (this.font.lineHeight + 1) + 8;
		checkY = y;
		y += 30 + 8;

		int x = left + PAD;
		addRenderableWidget(Button.builder(Component.literal(I18n.t("hangulquest.ui.welcome.yes")), button -> {
			WelcomePolicy.confirmed(config());
			onClose();
		}).bounds(x, y, inner, BUTTON).build());
		y += BUTTON + 4;
		addRenderableWidget(Button.builder(Component.literal(I18n.t("hangulquest.ui.welcome.not_yet")), button -> {
			expanded = !expanded;
			scroll = 0;
			rebuildWidgets();
		}).bounds(x, y, inner, BUTTON).build());
		y += BUTTON;

		if (expanded && visible > 0) {
			y += 6;
			listHeaderY = y;
			y += 12;
			for (int i = scroll; i < scroll + visible; i++) {
				HangulResources.Entry entry = resources.get(i);
				addRenderableWidget(Button.builder(Component.literal(label(entry)), button -> open(entry))
						.bounds(x, y, inner, ROW - 2).build());
				y += ROW;
			}
		}

		y += 8;
		if (!manual && config() != null && WelcomePolicy.canRemindLater(config())) {
			int half = (inner - 4) / 2;
			addRenderableWidget(Button.builder(Component.literal(I18n.t("hangulquest.ui.welcome.later")), button -> onClose())
					.bounds(x, y, half, BUTTON).build());
			addRenderableWidget(Button.builder(Component.literal(I18n.t("hangulquest.ui.welcome.never")), button -> {
				WelcomePolicy.dismissedForGood(config());
				onClose();
			}).bounds(x + half + 4, y, inner - half - 4, BUTTON).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal(I18n.t("hangulquest.ui.welcome.never")), button -> {
				WelcomePolicy.dismissedForGood(config());
				onClose();
			}).bounds(x, y, inner, BUTTON).build());
		}
	}

	private static String label(HangulResources.Entry entry) {
		return entry.title() + "  [" + entry.lang().toUpperCase(java.util.Locale.ROOT) + "]";
	}

	/** Vanilla confirmation screen first, then the system browser. */
	private void open(HangulResources.Entry entry) {
		try {
			ConfirmLinkScreen.confirmLinkNow(this, URI.create(entry.url()));
		} catch (IllegalArgumentException badUrl) {
			HangulQuestClient.LOG.warn("skipping malformed resource link {}", entry.url());
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (expanded && resources.size() > 1 && vertical != 0) {
			int next = Math.max(0, scroll - (int) Math.signum(vertical));
			if (next != scroll) {
				scroll = next;
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void onClose() {
		HangulQuestClient hq = HangulQuestClient.get();
		if (hq != null) {
			hq.saveConfig();
		}
		if (parent != null) {
			this.minecraft.setScreenAndShow(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Draw.panel(graphics, left, top, panelWidth, panelHeight, 0);
		Draw.label(graphics, this.font, I18n.t("hangulquest.ui.welcome.title"), left + PAD, titleY, Draw.GOLD);
		int y = bodyY;
		for (FormattedCharSequence line : bodyLines) {
			graphics.text(this.font, line, left + PAD, y, Draw.WHITE, false);
			y += this.font.lineHeight + 1;
		}

		// Self-check: a plain line of Korean in the mod's font, not a test.
		int inner = panelWidth - 2 * PAD;
		graphics.fill(left + PAD, checkY, left + PAD + inner, checkY + 30, 0xFF23232B);
		Component sentence = KoText.ko(CHECK_SENTENCE);
		graphics.text(this.font, sentence, left + (panelWidth - this.font.width(sentence)) / 2, checkY + 5,
				Draw.WHITE, false);
		String caption = I18n.t("hangulquest.ui.welcome.caption");
		graphics.text(this.font, Component.literal(caption),
				left + (panelWidth - this.font.width(caption)) / 2, checkY + 18, Draw.GRAY, false);

		if (expanded) {
			Draw.truncated(graphics, this.font,
					Component.literal(I18n.t("hangulquest.ui.welcome.free_resources")),
					left + PAD, listHeaderY, inner, Draw.GRAY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
}
