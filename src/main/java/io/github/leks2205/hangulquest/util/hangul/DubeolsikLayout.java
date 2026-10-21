package io.github.leks2205.hangulquest.util.hangul;

/**
 * The standard 두벌식 layout by key position. Keys are the scancodes Minecraft 26.3 reports in
 * {@code KeyEvent.key()} (SDL scancodes, the same values as {@code InputConstants.KEY_*}): they name the
 * physical positions of a US keyboard whatever layout is installed, so on a German QWERTZ keyboard the key
 * printed Z reports {@link #KEY_Y} and the layout needs no Z/Y swap. Pure data, no Minecraft types.
 */
public final class DubeolsikLayout {
	/** Scancode of the A key; A to Z follow in alphabetical order. */
	public static final int KEY_A = 4;
	public static final int KEY_COMMA = 54;
	public static final int KEY_PERIOD = 55;
	public static final int KEY_SLASH = 56;
	public static final int KEY_SEMICOLON = 51;
	public static final int KEY_Y = KEY_A + 24;
	public static final int KEY_Z = KEY_A + 25;

	/** The three letter rows as US key positions, with the punctuation keys that end each row. */
	public static final int[][] ROWS = {
			{key('Q'), key('W'), key('E'), key('R'), key('T'), key('Y'), key('U'), key('I'), key('O'), key('P')},
			{key('A'), key('S'), key('D'), key('F'), key('G'), key('H'), key('J'), key('K'), key('L'), KEY_SEMICOLON},
			{key('Z'), key('X'), key('C'), key('V'), key('B'), key('N'), key('M'), KEY_COMMA, KEY_PERIOD, KEY_SLASH}};

	private static final String LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM";
	private static final String PLAIN = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";
	/** Shift changes only ㅂ ㅈ ㄷ ㄱ ㅅ ㅐ ㅔ. */
	private static final String SHIFTED = "ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";

	private DubeolsikLayout() {
	}

	/** The jamo this key types (with or without Shift), or 0 when the key is not a letter key. */
	public static char jamoFor(int key, boolean shift) {
		int index = isLetter(key) ? LETTERS.indexOf(letter(key)) : -1;
		if (index < 0) {
			return 0;
		}
		return (shift ? SHIFTED : PLAIN).charAt(index);
	}

	/** The key that types this jamo, or -1 (compound vowels and finals have no key of their own). */
	public static int keyFor(char jamo) {
		int shifted = SHIFTED.indexOf(jamo);
		int plain = PLAIN.indexOf(jamo);
		int index = plain >= 0 ? plain : shifted;
		return index < 0 ? -1 : key(LETTERS.charAt(index));
	}

	/** The scancode of a US letter key, 'A' to 'Z'. */
	public static int key(char usLetter) {
		return KEY_A + (usLetter - 'A');
	}

	private static boolean isLetter(int key) {
		return key >= KEY_A && key <= KEY_Z;
	}

	private static char letter(int key) {
		return (char) ('A' + (key - KEY_A));
	}

	/** True when typing this jamo needs Shift held. */
	public static boolean needsShift(char jamo) {
		return PLAIN.indexOf(jamo) < 0 && SHIFTED.indexOf(jamo) >= 0;
	}

	/** The character printed on the key of a US keyboard, for the key-map overlay. */
	public static String usLabel(int key) {
		return switch (key) {
			case KEY_COMMA -> ",";
			case KEY_PERIOD -> ".";
			case KEY_SLASH -> "/";
			case KEY_SEMICOLON -> ";";
			default -> isLetter(key) ? String.valueOf(letter(key)) : "";
		};
	}
}
