package io.github.leks2205.hangulquest.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.leks2205.hangulquest.util.hangul.DubeolsikLayout;
import io.github.leks2205.hangulquest.util.hangul.HangulInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The typing code keeps key codes as plain ints so it can be tested without a window. They must be the
 * values the game reports: 26.3 uses SDL scancodes, not GLFW codes (typing and Enter broke once over this).
 */
class KeyCodeParityTest {
	@Test
	void letterKeysAreTheGamesScancodes() {
		assertEquals(InputConstants.KEY_A, DubeolsikLayout.KEY_A);
		assertEquals(InputConstants.KEY_Y, DubeolsikLayout.KEY_Y);
		assertEquals(InputConstants.KEY_Z, DubeolsikLayout.KEY_Z);
		assertEquals(InputConstants.KEY_Q, DubeolsikLayout.key('Q'));
		assertEquals(InputConstants.KEY_M, DubeolsikLayout.key('M'));
		assertEquals(InputConstants.KEY_COMMA, DubeolsikLayout.KEY_COMMA);
		assertEquals(InputConstants.KEY_PERIOD, DubeolsikLayout.KEY_PERIOD);
		assertEquals(InputConstants.KEY_SLASH, DubeolsikLayout.KEY_SLASH);
		assertEquals(InputConstants.KEY_SEMICOLON, DubeolsikLayout.KEY_SEMICOLON);
	}

	@Test
	void controlKeysAndModifiersAreTheGames() {
		assertEquals(InputConstants.KEY_BACKSPACE, HangulInput.KEY_BACKSPACE);
		assertEquals(InputConstants.KEY_RALT, HangulInput.KEY_RIGHT_ALT);
		assertEquals(InputConstants.MOD_SHIFT, HangulInput.MOD_SHIFT);
		assertEquals(InputConstants.MOD_CONTROL, HangulInput.MOD_CONTROL);
		assertEquals(InputConstants.MOD_ALT, HangulInput.MOD_ALT);
		assertEquals(InputConstants.MOD_SUPER, HangulInput.MOD_SUPER);
	}
}
