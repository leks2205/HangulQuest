package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.widget.TypedAnswerPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;
import java.util.Optional;

/**
 * Productive recall with the keyboard: the picture and the gloss are shown and the player types the Korean
 * word. The answer is graded by {@code TypedAnswer.grade} (exact Good, one jamo off Hard, further off
 * Again). There is no retry: the jamo that differ are shown, and the next Enter closes the card.
 *
 * <p>The quiz screen forwards key and character events to the card and collects the first attempt with
 * {@link #takeAnswer()}; {@code QuizSession} records it like any other card's.
 */
public final class TypedCard implements Card {
	/** A word of more eojeol than this is not asked by typing. */
	public static final int MAX_EOJEOL = 2;
	private static final int WIDTH = 320;

	private final CardInfo info;
	private final TypedAnswerPanel panel;
	private Phase phase = Phase.ASKING;
	private boolean answered;
	private Answer pending;

	private TypedCard(CardInfo info) {
		this.info = info;
		this.panel = new TypedAnswerPanel(info.gloss(), info.icon(), info.word().ko, result -> phase = Phase.DONE);
	}

	/** True when the word can be typed: only Hangul syllables and single spaces, at most two eojeol. */
	public static boolean eligible(String ko) {
		if (ko == null || ko.isBlank()) {
			return false;
		}
		String[] parts = ko.split(" ", -1);
		if (parts.length > MAX_EOJEOL) {
			return false;
		}
		for (String part : parts) {
			if (!Syllables.onlySyllables(part)) {
				return false;
			}
		}
		return true;
	}

	/** A card for the word, or null when it cannot be typed. */
	public static TypedCard create(CardInfo info) {
		return eligible(info.word().ko) ? new TypedCard(info) : null;
	}

	@Override
	public CardInfo info() {
		return info;
	}

	@Override
	public ReviewMode mode() {
		return ReviewMode.TYPED;
	}

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public boolean answered() {
		return answered;
	}

	// ----------------------------------------------------------------- input

	public boolean keyPressed(KeyEvent event) {
		boolean handled = panel.keyPressed(event);
		collect();
		return handled;
	}

	public boolean keyReleased(KeyEvent event) {
		return panel.keyReleased(event);
	}

	public boolean charTyped(CharacterEvent event) {
		return panel.charTyped(event);
	}

	/** The first attempt, once, right after the player submitted it. */
	public Optional<Answer> takeAnswer() {
		Answer given = pending;
		pending = null;
		return Optional.ofNullable(given);
	}

	private void collect() {
		if (!answered && panel.result() != null) {
			answered = true;
			phase = Phase.FEEDBACK;
			pending = Answer.typed(panel.result());
		}
	}

	/** Typing has no number keys and no buttons. */
	@Override
	public Optional<Answer> choose(int index) {
		return Optional.empty();
	}

	@Override
	public Optional<Answer> confirm() {
		return Optional.empty();
	}

	// ----------------------------------------------------------------- drawing

	@Override
	public CardView view() {
		FeedbackCard feedback = answered
				? FeedbackCard.of(info, panel.result().graded().correct(), panel.field().text()) : null;
		return new CardView("단어를 써 보세요.", info.icon(), info.gloss(), false, "", "", CardView.Layout.ROWS,
				List.of(), 0, List.of(), "", panel.hint(), feedback);
	}

	/**
	 * Draws the card centred on the screen. With {@code listen} the answered card gets a "listen" button, whose
	 * click area goes into {@code hits}.
	 */
	public void paint(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, int mouseX,
					  int mouseY, boolean listen, List<CardPainter.Hit> hits) {
		hits.clear();
		boolean button = listen && answered;
		int width = Math.min(WIDTH, screenWidth - 12);
		int panelHeight = panel.height(font, width);
		int height = 22 + panelHeight + (button ? 18 : 0);
		int x = (screenWidth - width) / 2;
		int y = Math.max(4, (screenHeight - height) / 2);
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.korean(graphics, font, view().titleKo(), x + 8, y + 6, Draw.GOLD);
		panel.extractRenderState(graphics, font, x, y + 20, width);
		if (button) {
			hits.add(CardPainter.listenButton(graphics, font, x + width - 8, y + 20 + panelHeight, mouseX, mouseY));
		}
	}
}
