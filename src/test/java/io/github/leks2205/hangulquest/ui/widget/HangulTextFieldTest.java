package io.github.leks2205.hangulquest.ui.widget;

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
			assertTrue(field.keyPressed(key(code, 0)));
		}
		assertEquals("한글", field.text());
	}

	@Test
	void shiftAndBackspaceAndClear() {
		HangulTextField field = new HangulTextField();
		field.keyPressed(key('Q', 1));
		field.keyPressed(key('K', 0));
		assertEquals("빠", field.text());
		field.keyPressed(key(259, 0));
		assertEquals("ㅃ", field.text());
		field.clear();
		assertTrue(field.isEmpty());
	}

	@Test
	void rightAltTogglesAndTabShowsTheKeyMap() {
		HangulTextField field = new HangulTextField();
		assertTrue(field.keyPressed(key(346, 0)));
		assertFalse(field.input().korean());
		assertFalse(field.keyPressed(key('G', 0)), "Latin letters arrive as characters");
		assertTrue(field.charTyped(new CharacterEvent('g')));
		assertEquals("g", field.text());
		assertFalse(field.keyMapVisible());
		field.keyPressed(key(258, 0));
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
