package io.github.leks2205.hangulquest.ui.widget;

import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.hangul.JamoDiff;
import io.github.leks2205.hangulquest.util.hangul.TypedAnswer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * One typed question: the prompt (icon and gloss), a {@link HangulTextField}, and after Enter the
 * verdict with the jamo that differ. A host (a screen or a card) forwards the input events and
 * draws it inside its own panel. The second Enter hands the graded result to {@code onContinue}.
 */
public final class TypedAnswerPanel {
	private static final int ICON_ROW = 26;
	private static final int LINE = 12;
	private static final int CELL = 11;
	private static final int LABEL_WIDTH = 46;
	private static final int PAD = 8;
	/** A sentence longer than this many rows of jamo is not drawn as a grid. */
	private static final int MAX_CHUNKS = 3;
	private static final int KEY_ENTER = com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
	/** SDL scancode of the keypad Enter key (no InputConstants name for it in 26.3). */
	private static final int KEY_KP_ENTER = 88;

	private final String gloss;
	private final String icon;
	private final String expected;
	private final Consumer<TypedAnswer.Result> onContinue;
	private final HangulTextField field = new HangulTextField();
	private TypedAnswer.Result result;
	private List<Component> youCells = List.of();
	private List<Component> answerCells = List.of();

	/**
	 * @param gloss      the prompt, in the interface language (what the word means)
	 * @param icon       item id for the prompt icon, or null for the "?" tile
	 * @param expected   the Korean answer
	 * @param onContinue called with the graded result when the player moves on
	 */
	public TypedAnswerPanel(String gloss, String icon, String expected, Consumer<TypedAnswer.Result> onContinue) {
		this.gloss = gloss;
		this.icon = icon;
		this.expected = expected;
		this.onContinue = onContinue;
		field.setPlaceholder(I18n.t("hangulquest.ui.typed.placeholder"));
	}

	public HangulTextField field() {
		return field;
	}

	/** The graded result once the answer was submitted, else null. */
	public TypedAnswer.Result result() {
		return result;
	}

	public boolean keyPressed(KeyEvent event) {
		boolean enter = event.key() == KEY_ENTER || event.key() == KEY_KP_ENTER;
		if (result != null) {
			if (enter) {
				onContinue.accept(result);
			}
			return enter;
		}
		if (enter) {
			if (!field.isEmpty()) {
				submit();
			}
			return true;
		}
		return field.keyPressed(event);
	}

	public boolean keyReleased(KeyEvent event) {
		return field.keyReleased(event);
	}

	public boolean charTyped(CharacterEvent event) {
		return result == null && field.charTyped(event);
	}

	private void submit() {
		result = TypedAnswer.grade(expected, field.text());
		youCells = result.diff().ops().stream().map(TypedAnswerPanel::youCell).toList();
		answerCells = result.diff().ops().stream().map(TypedAnswerPanel::answerCell).toList();
	}

	private static Component youCell(JamoDiff.Op op) {
		return op.typed() == 0 ? Component.empty() : HangulTextField.styled(new String(Character.toChars(op.typed())));
	}

	private static Component answerCell(JamoDiff.Op op) {
		return op.expected() == 0 ? Component.empty() : HangulTextField.styled(new String(Character.toChars(op.expected())));
	}

	/** The footer line for the current state. */
	public String hint() {
		if (result != null) {
			return I18n.t("hangulquest.ui.typed.keys_done");
		}
		return I18n.t("hangulquest.ui.typed.keys_input", HangulTextField.keyName(field.input().toggleKey()));
	}

	// ----------------------------------------------------------------- drawing

	private int chunkSize(int width) {
		return Math.max(1, (width - 2 * PAD - LABEL_WIDTH) / CELL);
	}

	private int chunks(int width) {
		if (result == null || result.exact()) {
			return 0;
		}
		int size = chunkSize(width);
		return Math.min(MAX_CHUNKS, (result.diff().ops().size() + size - 1) / size);
	}

	/** Height of everything this panel draws for a width, so the host can size its own frame. */
	public int height(Font font, int width) {
		int height = ICON_ROW + field.height();
		if (result != null) {
			height += 6 + LINE + LINE + chunks(width) * (2 * LINE + 4) + (result.diff().oneOff() ? LINE : 0);
		}
		return height + 6 + Draw.wrappedHeight(font, Component.literal(hint()), width - 2 * PAD, 2);
	}

	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		int cursor = y;
		if (icon == null) {
			Draw.question(graphics, font, x + PAD, cursor, 16);
		} else {
			Draw.itemIcon(graphics, icon, x + PAD, cursor);
		}
		Draw.truncated(graphics, font, Component.literal(gloss), x + PAD + 22, cursor + 4, width - 2 * PAD - 22, Draw.WHITE);
		cursor += ICON_ROW;
		field.extractRenderState(graphics, font, x + PAD, cursor, width - 2 * PAD);
		cursor += field.height();
		if (result != null) {
			cursor += 6;
			cursor = extractVerdict(graphics, font, x, cursor, width);
		}
		Draw.wrapped(graphics, font, Component.literal(hint()), x + PAD, cursor + 6, width - 2 * PAD, Draw.DIM, 2);
	}

	private int extractVerdict(GuiGraphicsExtractor graphics, Font font, int x, int y, int width) {
		JamoDiff diff = result.diff();
		int cursor = y;
		if (diff.exact()) {
			Draw.korean(graphics, font, "정답!", x + PAD, cursor, Draw.GREEN);
		} else if (diff.oneOff()) {
			Draw.korean(graphics, font, "거의 다 됐어요!", x + PAD, cursor, Draw.GOLD);
		} else {
			Draw.korean(graphics, font, "다시 해 봐요!", x + PAD, cursor, Draw.RED);
		}
		cursor += LINE;
		if (diff.oneOff()) {
			Draw.label(graphics, font, I18n.t("hangulquest.ui.typed.one_off"), x + PAD, cursor, Draw.GRAY);
			cursor += LINE;
		}
		Draw.label(graphics, font, I18n.t("hangulquest.ui.typed.answer"), x + PAD, cursor, Draw.DIM);
		Draw.korean(graphics, font, expected, x + PAD + LABEL_WIDTH, cursor, Draw.GREEN);
		cursor += LINE;
		int size = chunkSize(width);
		for (int chunk = 0; chunk < chunks(width); chunk++) {
			int from = chunk * size;
			int to = Math.min(from + size, diff.ops().size());
			Draw.label(graphics, font, I18n.t("hangulquest.ui.typed.you"), x + PAD, cursor, Draw.DIM);
			Draw.label(graphics, font, I18n.t("hangulquest.ui.typed.answer"), x + PAD, cursor + LINE, Draw.DIM);
			for (int i = from; i < to; i++) {
				extractCell(graphics, font, diff.ops().get(i), i,
						x + PAD + LABEL_WIDTH + (i - from) * CELL, cursor);
			}
			cursor += 2 * LINE + 4;
		}
		return cursor;
	}

	/** Two rows of one cell each: what was typed above what was expected, the difference in colour. */
	private void extractCell(GuiGraphicsExtractor graphics, Font font, JamoDiff.Op op, int index, int cx, int cy) {
		int below = cy + LINE;
		switch (op.kind()) {
			case SAME -> {
				graphics.text(font, youCells.get(index), cx, cy, Draw.WHITE, false);
				graphics.text(font, answerCells.get(index), cx, below, Draw.GRAY, false);
			}
			case WRONG -> {
				graphics.text(font, youCells.get(index), cx, cy, Draw.RED, false);
				graphics.text(font, answerCells.get(index), cx, below, Draw.GOLD, false);
			}
			case MISSING -> {
				graphics.fill(cx + 1, cy + 8, cx + CELL - 3, cy + 9, Draw.GOLD);
				graphics.text(font, answerCells.get(index), cx, below, Draw.GOLD, false);
			}
			case EXTRA -> {
				graphics.text(font, youCells.get(index), cx, cy, Draw.RED, false);
				graphics.fill(cx + 1, below + 8, cx + CELL - 3, below + 9, Draw.RED);
			}
		}
	}
}
