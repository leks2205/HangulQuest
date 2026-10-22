package io.github.leks2205.hangulquest.ui.quiz;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.HudController;
import io.github.leks2205.hangulquest.ui.quiz.cards.Card;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardAudio;
import io.github.leks2205.hangulquest.ui.quiz.cards.CardPainter;
import io.github.leks2205.hangulquest.ui.quiz.cards.TypedCard;
import io.github.leks2205.hangulquest.util.I18n;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where quizzes, sidequest questions and the next-quest choice are answered. A screen captures the
 * keyboard, so the number keys answer here and never switch the hotbar. It does not pause the game.
 * Esc (or the answer key again) closes it and records nothing; what was pending stays pending.
 */
public final class QuizScreen extends Screen {
	private static final int[] NUMBER_KEYS = {
			InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
			InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7,
			InputConstants.KEY_8};
	private static final int[] NUMPAD_KEYS = {
			InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
			InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6,
			InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8};
	private static final int ROW_HEIGHT = 18;

	private final HudController hud;
	private final HangulQuestClient hq = HangulQuestClient.get();
	private final List<int[]> rows = new ArrayList<>();
	private final List<CardPainter.Hit> cardHits = new ArrayList<>();
	/** The card that plays a review; it stays on screen for its retry and feedback after the answer is recorded. */
	private Card card;
	/** Speaks the card's word when the answer shows, and again on the listen key or button. */
	private final CardAudio cardAudio;
	private QuizOutcome lastOutcome = QuizOutcome.NONE;
	/** What is shown; read from the controller once per tick and after an answer, not once per frame. */
	private QuizView view;

	public QuizScreen(HudController hud) {
		super(Component.literal("한글 탐험"));
		this.hud = hud;
		this.view = hud.quizView();
		this.card = view == null ? null : view.card();
		var word = card == null || card.info() == null ? null : card.info().word();
		this.cardAudio = new CardAudio(hq == null ? null : hq.audio(), word == null ? null : word.conceptId);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		view = hud.quizView();
		if (card != null) {
			// Once answered the session is idle but the card still has its retry and feedback to show.
			if (card.finished() || (!card.answered() && (view == null || view.card() != card))) {
				onClose();
			}
		} else if (view == null) {
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
		if (card instanceof TypedCard typed) {
			// A typed card owns the keyboard: letters, digits and Enter are its input, only Esc closes.
			if (event.key() == InputConstants.KEY_ESCAPE) {
				onClose();
				return true;
			}
			if (typed.answered() && event.key() == InputConstants.KEY_L) {
				cardAudio.replay(); // the field is closed by now, so L is not a letter
				return true;
			}
			boolean handled = typed.keyPressed(event);
			typed.takeAnswer().ifPresent(this::record);
			cardAudio.update(card.phase());
			if (card.finished()) {
				onClose();
			}
			return handled || super.keyPressed(event);
		}
		if (event.key() == InputConstants.KEY_ESCAPE
				|| (hq != null && hq.answerKey() != null && hq.answerKey().matches(event))) {
			onClose();
			return true;
		}
		if (card != null && event.key() == InputConstants.KEY_L && CardAudio.showsAnswer(card.phase())) {
			cardAudio.replay();
			return true;
		}
		for (int i = 0; i < NUMBER_KEYS.length; i++) {
			if (event.key() == NUMBER_KEYS[i] || event.key() == NUMPAD_KEYS[i]) {
				choose(i);
				return true;
			}
		}
		if (card != null) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
					|| event.key() == InputConstants.KEY_SPACE) {
				cardAction(CardPainter.CONFIRM);
				return true;
			}
			if (event.key() == InputConstants.KEY_BACKSPACE) {
				cardAction(CardPainter.UNDO);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return card instanceof TypedCard typed && typed.keyReleased(event) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return card instanceof TypedCard typed && typed.charTyped(event) || super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && card != null) {
			for (CardPainter.Hit hit : cardHits) {
				if (hit.contains(event.x(), event.y())) {
					cardAction(hit.action());
					return true;
				}
			}
		} else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
		if (card != null) {
			cardAction(index);
			return;
		}
		QuizOutcome outcome = hud.answerInScreen(index);
		if (outcome != QuizOutcome.NONE) {
			lastOutcome = outcome;
		}
		view = hud.quizView();
	}

	/** Passes an input to the card; its first attempt, if this was one, is recorded through the HUD. */
	private void cardAction(int action) {
		if (action == CardPainter.LISTEN) {
			cardAudio.replay();
			return;
		}
		Optional<Card.Answer> given;
		if (action == CardPainter.CONFIRM) {
			given = card.confirm();
		} else if (action == CardPainter.UNDO) {
			card.undo();
			given = Optional.empty();
		} else {
			given = card.choose(action);
		}
		given.ifPresent(this::record);
		cardAudio.update(card.phase());
		if (card.finished()) {
			onClose();
		}
	}

	private void record(Card.Answer answer) {
		QuizOutcome outcome = hud.recordCardAnswer(answer);
		if (outcome != QuizOutcome.NONE) {
			lastOutcome = outcome;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (card instanceof TypedCard typed) {
			typed.paint(graphics, this.font, this.width, this.height, mouseX, mouseY, cardAudio.listenable(), cardHits);
			return;
		}
		if (card != null) {
			CardPainter.paint(graphics, this.font, card.view(), this.width, this.height, mouseX, mouseY, cardHits,
					cardAudio.listenable());
			return;
		}
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
		return I18n.t("hangulquest.ui.quiz.keys", keys);
	}
}
