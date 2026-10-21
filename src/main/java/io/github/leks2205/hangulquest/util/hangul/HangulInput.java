package io.github.leks2205.hangulquest.util.hangul;

/**
 * What a typing field does with key and character events, without any drawing: the Korean/Latin
 * mode, the 두벌식 keys by position, and text an OS input method delivered already composed.
 * Key codes and modifier bits are the values Minecraft 26.3 reports (SDL scancodes and SDL modifier masks,
 * the same as {@code InputConstants}), kept here as plain ints so this can be tested without a window.
 *
 * <p>In Korean mode the letter keys type jamo from {@link DubeolsikLayout}, whatever the keyboard
 * layout. Printable characters that are not letters (space, digits, punctuation) come from the
 * character event, which is layout aware. In Latin mode every character comes from that event.
 */
public final class HangulInput {
	public static final int KEY_BACKSPACE = 42;
	public static final int KEY_RIGHT_ALT = 230;
	public static final int MOD_SHIFT = 3;
	public static final int MOD_CONTROL = 192;
	public static final int MOD_ALT = 768;
	public static final int MOD_SUPER = 3072;
	public static final int DEFAULT_MAX_LENGTH = 40;

	private final Dubeolsik automaton = new Dubeolsik();
	private boolean korean = true;
	private int toggleKey = KEY_RIGHT_ALT;
	private int maxLength = DEFAULT_MAX_LENGTH;

	public Dubeolsik automaton() {
		return automaton;
	}

	public String text() {
		return automaton.text();
	}

	public boolean korean() {
		return korean;
	}

	public void setKorean(boolean korean) {
		this.korean = korean;
	}

	/** The key that switches between Korean and Latin (right Alt by default). */
	public void setToggleKey(int key) {
		toggleKey = key;
	}

	public int toggleKey() {
		return toggleKey;
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = Math.max(1, maxLength);
	}

	public void clear() {
		automaton.clear();
	}

	/** A key press (or repeat). Returns true when the field used it. */
	public boolean key(int key, int modifiers) {
		if (key == toggleKey) {
			korean = !korean;
			return true;
		}
		if (key == KEY_BACKSPACE) {
			if ((modifiers & MOD_CONTROL) != 0) {
				automaton.clear();
			} else {
				automaton.backspace();
			}
			return true;
		}
		if (!korean || (modifiers & (MOD_CONTROL | MOD_ALT | MOD_SUPER)) != 0) {
			return false;
		}
		char jamo = DubeolsikLayout.jamoFor(key, (modifiers & MOD_SHIFT) != 0);
		if (jamo == 0) {
			return false;
		}
		String before = automaton.strokes();
		automaton.jamo(jamo);
		keepWithinLimit(before);
		return true;
	}

	/**
	 * A typed character. Hangul is taken as it is (an OS input method composed it); in Korean mode
	 * letters are refused, since the key press already typed them as jamo.
	 */
	public boolean character(int codePoint) {
		if (Character.isISOControl(codePoint)) {
			return false;
		}
		String before = automaton.strokes();
		if (HangulCompose.isHangul(codePoint)) {
			automaton.insert(new String(Character.toChars(codePoint)));
		} else if (korean && Character.isLetter(codePoint)) {
			return false;
		} else {
			automaton.literal(codePoint);
		}
		keepWithinLimit(before);
		return true;
	}

	private void keepWithinLimit(String before) {
		if (automaton.text().length() > maxLength) {
			automaton.restore(before);
		}
	}
}
