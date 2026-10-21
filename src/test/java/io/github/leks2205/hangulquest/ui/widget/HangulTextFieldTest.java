package io.github.leks2205.hangulquest.ui.widget;

import io.github.leks2205.hangulquest.util.hangul.DubeolsikLayout;
import io.github.leks2205.hangulquest.util.hangul.HangulInput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The field as a screen drives it: with the game's own event records. */
class HangulTextFieldTest {
	private static KeyEvent key(int code, int modifiers) {
		return new KeyEvent(code, 0, modifiers);
	}

	@Test
	void typesByKeyPositionFromKeyEvents() {
		HangulTextField field = new HangulTextField();
		for (char code : "GKSRMF".toCharArray()) {
			assertTrue(field.keyPressed(key(DubeolsikLayout.key(code), 0)));
		}
		assertEquals("한글", field.text());
	}

	@Test
	void shiftAndBackspaceAndClear() {
		HangulTextField field = new HangulTextField();
		field.keyPressed(key(DubeolsikLayout.key('Q'), HangulInput.MOD_SHIFT));
		field.keyPressed(key(DubeolsikLayout.key('K'), 0));
		assertEquals("빠", field.text());
		field.keyPressed(key(HangulInput.KEY_BACKSPACE, 0));
		assertEquals("ㅃ", field.text());
		field.clear();
		assertTrue(field.isEmpty());
	}

	@Test
	void rightAltTogglesAndTabShowsTheKeyMap() {
		HangulTextField field = new HangulTextField();
		assertTrue(field.keyPressed(key(HangulInput.KEY_RIGHT_ALT, 0)));
		assertFalse(field.input().korean());
		assertFalse(field.keyPressed(key(DubeolsikLayout.key('G'), 0)), "Latin letters arrive as characters");
		assertTrue(field.charTyped(new CharacterEvent('g')));
		assertEquals("g", field.text());
		assertFalse(field.keyMapVisible());
		field.keyPressed(key(com.mojang.blaze3d.platform.InputConstants.KEY_TAB, 0));
		assertTrue(field.keyMapVisible());
		assertTrue(field.height() > HangulTextField.HEIGHT);
	}

	@Test
	void precomposedTextFromTheOsIsAccepted() {
		HangulTextField field = new HangulTextField();
		assertTrue(field.charTyped(new CharacterEvent('한')));
		assertEquals("한", field.text());
	}
}
