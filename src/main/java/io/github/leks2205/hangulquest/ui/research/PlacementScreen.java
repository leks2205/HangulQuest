package io.github.leks2205.hangulquest.ui.research;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.learn.research.Placement;
import io.github.leks2205.hangulquest.learn.research.Pseudowords;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Random;

/**
 * The optional vocabulary check: one Korean word at a time, Yes or No (keys 1 and 2). It never gates anything.
 * Esc stops it at any time and records nothing; only a finished test seeds words into the scheduler.
 */
public final class PlacementScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int BUTTON_H = 22;

	private enum Stage { INTRO, TEST, RESULT }

	private final Screen parent;
	private final Placement placement;
	private Stage stage = Stage.INTRO;
	private int seeded;
	/** Hit boxes of the current frame: x, y, width, height. */
	private final int[][] boxes = new int[2][4];
	private int boxCount;

	public PlacementScreen(Screen parent) {
		super(Component.literal("한글 탐험"));
		this.parent = parent;
		HangulQuestClient hq = HangulQuestClient.get();
		List<String> nonwords = Pseudowords.load().stream().map(Pseudowords.Item::ko).toList();
		this.placement = Placement.build(hq.catalog().all(), LexiconTags.shared(), nonwords, new Random());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (stage == Stage.TEST) {
			if (key == InputConstants.KEY_1 || key == InputConstants.KEY_NUMPAD1) {
				answer(true);
				return true;
			}
			if (key == InputConstants.KEY_2 || key == InputConstants.KEY_NUMPAD2) {
				answer(false);
				return true;
			}
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
				|| key == InputConstants.KEY_SPACE) {
			advance();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = 0; i < boxCount; i++) {
				int[] box = boxes[i];
				if (event.x() >= box[0] && event.x() < box[0] + box[2] && event.y() >= box[1] && event.y() < box[1] + box[3]) {
					if (stage == Stage.TEST) {
						answer(i == 0);
					} else {
						advance();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void advance() {
		if (stage == Stage.INTRO) {
			stage = Stage.TEST;
		} else if (stage == Stage.RESULT) {
			onClose();
		}
	}

	private void answer(boolean knows) {
		placement.answer(knows);
		if (placement.finished()) {
			stage = Stage.RESULT;
			var learn = HangulQuestClient.get().learn();
			if (learn != null) {
				seeded = learn.seedFromPlacement(placement.claimedWordIds(), System.currentTimeMillis());
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boxCount = 0;
		int width = Math.min(WIDTH, this.width - 12);
		int x = (this.width - width) / 2;
		switch (stage) {
			case INTRO -> {
				int textHeight = Draw.wrappedHeight(this.font, Component.literal(I18n.t("hangulquest.ui.research.placement.intro")), width - 16, 12);
				int height = 22 + textHeight + 10 + BUTTON_H + 22;
				int y = (this.height - height) / 2;
				Draw.panel(graphics, x, y, width, height, 0);
				Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.title"), x + 8, y + 6, Draw.GOLD);
				Draw.wrapped(graphics, this.font, Component.literal(I18n.t("hangulquest.ui.research.placement.intro")),
						x + 8, y + 22, width - 16, Draw.WHITE, 12);
				int by = y + 22 + textHeight + 10;
				button(graphics, I18n.t("hangulquest.ui.research.placement.start"), x + width / 2 - 50, by, 100, mouseX, mouseY);
				Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.skip"), x + 8, by + BUTTON_H + 6, Draw.DIM);
			}
			case TEST -> {
				Placement.Item item = placement.current();
				int height = 110;
				int y = (this.height - height) / 2;
				Draw.panel(graphics, x, y, width, height, 0);
				Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.title"), x + 8, y + 6, Draw.GOLD);
				String progress = I18n.t("hangulquest.ui.research.placement.progress", placement.answered() + 1, placement.size());
				Draw.label(graphics, this.font, progress, x + width - 8 - this.font.width(progress), y + 6, Draw.DIM);
				if (item != null) {
					Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.question"), x + 8, y + 24, Draw.GRAY);
					String ko = item.ko();
					Draw.korean(graphics, this.font, ko, x + (width - this.font.width(KoText.ko(ko))) / 2, y + 42, Draw.WHITE);
				}
				int bw = (width - 24) / 2;
				button(graphics, "1  " + I18n.t("hangulquest.ui.research.placement.yes"), x + 8, y + 66, bw, mouseX, mouseY);
				button(graphics, "2  " + I18n.t("hangulquest.ui.research.placement.no"), x + 16 + bw, y + 66, bw, mouseX, mouseY);
				Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.keys"), x + 8, y + height - 12, Draw.DIM);
			}
			case RESULT -> {
				Placement.Result result = placement.result();
				String score = I18n.t("hangulquest.ui.research.placement.score", Math.round(result.score() * 100),
						result.hits(), result.realShown(), result.falseAlarms(), result.pseudoShown());
				String note = result.reliable()
						? I18n.t("hangulquest.ui.research.placement.seeded", seeded)
						: I18n.t("hangulquest.ui.research.placement.unreliable");
				int height = 22 + Draw.wrappedHeight(this.font, Component.literal(score), width - 16, 4)
						+ Draw.wrappedHeight(this.font, Component.literal(note), width - 16, 4) + 18 + BUTTON_H + 12;
				int y = (this.height - height) / 2;
				Draw.panel(graphics, x, y, width, height, 0);
				Draw.label(graphics, this.font, I18n.t("hangulquest.ui.research.placement.result"), x + 8, y + 6, Draw.GOLD);
				int cursor = y + 22;
				Draw.wrapped(graphics, this.font, Component.literal(score), x + 8, cursor, width - 16, Draw.WHITE, 4);
				cursor += Draw.wrappedHeight(this.font, Component.literal(score), width - 16, 4) + 6;
				Draw.wrapped(graphics, this.font, Component.literal(note), x + 8, cursor, width - 16, Draw.GRAY, 4);
				cursor += Draw.wrappedHeight(this.font, Component.literal(note), width - 16, 4) + 12;
				button(graphics, I18n.t("hangulquest.ui.research.placement.done"), x + width / 2 - 50, cursor, 100, mouseX, mouseY);
			}
		}
	}

	private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int mx, int my) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + BUTTON_H;
		g.fill(x, y, x + w, y + BUTTON_H, hover ? 0x55FFFFFF : 0x33FFFFFF);
		g.fill(x, y, x + w, y + 1, Draw.BORDER);
		g.fill(x, y + BUTTON_H - 1, x + w, y + BUTTON_H, Draw.BORDER);
		g.fill(x, y, x + 1, y + BUTTON_H, Draw.BORDER);
		g.fill(x + w - 1, y, x + w, y + BUTTON_H, Draw.BORDER);
		Draw.label(g, this.font, label, x + (w - this.font.width(label)) / 2, y + (BUTTON_H - 8) / 2, Draw.WHITE);
		if (boxCount < boxes.length) {
			boxes[boxCount++] = new int[] {x, y, w, BUTTON_H};
		}
	}
}
