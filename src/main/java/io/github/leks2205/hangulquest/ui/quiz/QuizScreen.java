package io.github.leks2205.hangulquest.ui.quiz;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.HudController;
import io.github.leks2205.hangulquest.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Where quizzes, sidequest questions and the next-quest choice are answered. A screen captures the
 * keyboard, so the number keys answer here and never switch the hotbar. It does not pause the game.
 * Esc (or the answer key again) closes it and records nothing; what was pending stays pending.
 */
public final class QuizScreen extends Screen {
	private static final int[] NUMBER_KEYS = {
			InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
			InputConstants.KEY_4, InputConstants.KEY_5};
	private static final int[] NUMPAD_KEYS = {
			InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
			InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5};
	private static final int ROW_HEIGHT = 18;

	private final HudController hud;
	private final HangulQuestClient hq = HangulQuestClient.get();
	private final List<int[]> rows = new ArrayList<>();
	private QuizOutcome lastOutcome = QuizOutcome.NONE;
	/** What is shown; read from the controller once per tick and after an answer, not once per frame. */
	private QuizView view;

	public QuizScreen(HudController hud) {
		super(Component.literal("한글 탐험"));
		this.hud = hud;
		this.view = hud.quizView();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		view = hud.quizView();
		if (view == null) {
			onClose(); // answered, expired elsewhere or finished
		}
	}

	@Override
	public void removed() {
		// However the screen goes away, an unanswered review is dropped without a record.
		hud.dismissQuiz();
		super.removed();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE
				|| (hq != null && hq.answerKey() != null && hq.answerKey().matches(event))) {
			onClose();
			return true;
		}
		for (int i = 0; i < NUMBER_KEYS.length; i++) {
			if (event.key() == NUMBER_KEYS[i] || event.key() == NUMPAD_KEYS[i]) {
				choose(i);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = 0; i < rows.size(); i++) {
				int[] row = rows.get(i);
				if (event.x() >= row[0] && event.x() < row[0] + row[2]
						&& event.y() >= row[1] && event.y() < row[1] + ROW_HEIGHT) {
					choose(i);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void choose(int index) {
		QuizOutcome outcome = hud.answerInScreen(index);
		if (outcome != QuizOutcome.NONE) {
			lastOutcome = outcome;
		}
		view = hud.quizView();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (view == null) {
			return;
		}
		rows.clear();

		int shown = view.options().size();
		boolean hasPrompt = !view.prompt().isEmpty();
		int width = Math.min(320, this.width - 12);
		int height = 22 + (hasPrompt ? 26 : 0) + shown * ROW_HEIGHT + 24;
		int x = (this.width - width) / 2;
		int y = (this.height - height) / 2;
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, this.font, view.titleKo(), x + 8, y + 6, Draw.GOLD);
		if (lastOutcome != QuizOutcome.NONE) {
			boolean right = lastOutcome == QuizOutcome.CORRECT;
			String verdict = right ? "정답!" : "다시!";
			Draw.korean(graphics, this.font, verdict, x + width - 44, y + 6, right ? Draw.GREEN : Draw.RED);
		}

		int cursor = y + 20;
		if (hasPrompt) {
			if (view.promptIcon() == null) {
				Draw.question(graphics, this.font, x + 8, cursor, 16);
			} else {
				Draw.itemIcon(graphics, view.promptIcon(), x + 8, cursor);
			}
			if (view.promptKorean()) {
				Draw.korean(graphics, this.font, view.prompt(), x + 30, cursor + 2, Draw.WHITE);
			} else {
				Draw.truncated(graphics, this.font, Component.literal(view.prompt()), x + 30, cursor + 2,
						width - 40, Draw.WHITE);
			}
			cursor += 26;
		}
		for (int i = 0; i < shown; i++) {
			QuizView.Option option = view.options().get(i);
			int rowY = cursor + i * ROW_HEIGHT;
			boolean hover = mouseX >= x + 4 && mouseX < x + width - 4 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			if (hover) {
				graphics.fill(x + 4, rowY - 2, x + width - 4, rowY + ROW_HEIGHT - 4, 0x33FFFFFF);
			}
			rows.add(new int[] {x + 4, rowY - 2, width - 8});
			Draw.label(graphics, this.font, (i + 1) + ".", x + 10, rowY, Draw.DIM);
			if (view.optionsKorean()) {
				Draw.korean(graphics, this.font, option.text(), x + 28, rowY, Draw.WHITE);
			} else {
				Draw.truncated(graphics, this.font, Component.literal(option.text()), x + 28, rowY,
						width - 44, Draw.WHITE);
			}
			if (!option.note().isEmpty()) {
				Draw.truncated(graphics, this.font, Component.literal(option.note()), x + 150, rowY,
						width - 158, Draw.GRAY);
			}
		}
		Draw.label(graphics, this.font, footer(shown), x + 8, y + height - 12, Draw.DIM);
	}

	private static String footer(int shown) {
		String keys = shown <= 1 ? "1" : "1–" + shown;
		return Tr.t("%s: antworten · Esc: schließen").formatted(keys);
	}
}
