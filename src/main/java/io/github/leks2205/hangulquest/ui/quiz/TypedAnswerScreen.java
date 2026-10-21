package io.github.leks2205.hangulquest.ui.quiz;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.widget.TypedAnswerPanel;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.hangul.TypedAnswer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A typed question on its own screen: the prompt, a Korean text field, Enter to check, the jamo that
 * differ when it was wrong. The cards framework can use it as it is or host a
 * {@link TypedAnswerPanel} itself. Esc closes the screen and reports nothing; the second Enter
 * reports the graded result and closes. The game does not pause.
 */
public final class TypedAnswerScreen extends Screen {
	private static final int WIDTH = 320;

	private final TypedAnswerPanel panel;

	/**
	 * @param gloss    what the word means, in the interface language
	 * @param icon     item id for the prompt icon, or null for the "?" tile
	 * @param expected the Korean answer
	 * @param onDone   gets the graded result when the player moves on
	 */
	public TypedAnswerScreen(String gloss, String icon, String expected, Consumer<TypedAnswer.Result> onDone) {
		super(Component.literal("한글 탐험"));
		this.panel = new TypedAnswerPanel(gloss, icon, expected, result -> {
			onClose(); // first, so the callback may open the next screen
			onDone.accept(result);
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return panel.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return panel.keyReleased(event) || super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return panel.charTyped(event) || super.charTyped(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int width = Math.min(WIDTH, this.width - 12);
		int inner = panel.height(this.font, width);
		int height = 22 + inner;
		int x = (this.width - width) / 2;
		int y = Math.max(4, (this.height - height) / 2);
		Draw.panel(graphics, x, y, width, height, 0);
		Draw.label(graphics, this.font, I18n.t("hangulquest.ui.typed.title"), x + 8, y + 6, Draw.GOLD);
		panel.extractRenderState(graphics, this.font, x, y + 20, width);
	}
}
