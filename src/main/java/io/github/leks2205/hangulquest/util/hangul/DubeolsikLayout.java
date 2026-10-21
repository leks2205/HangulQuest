package io.github.leks2205.hangulquest.util.hangul;

/**
 * The standard 두벌식 layout by key position. Keys are GLFW key codes, which name the positions of a
 * US keyboard whatever layout is installed: on a German QWERTZ keyboard the key printed Z reports
 * {@link #KEY_Y}, so the layout needs no Z/Y swap. Pure data, no Minecraft types.
 */
public final class DubeolsikLayout {
	public static final int KEY_COMMA = 44;
	public static final int KEY_PERIOD = 46;
	public static final int KEY_SLASH = 47;
	public static final int KEY_SEMICOLON = 59;
	public static final int KEY_Y = 89;
	public static final int KEY_Z = 90;

	/** The three letter rows as US key positions, with the punctuation keys that end each row. */
	public static final int[][] ROWS = {
			{'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'},
			{'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', KEY_SEMICOLON},
			{'Z', 'X', 'C', 'V', 'B', 'N', 'M', KEY_COMMA, KEY_PERIOD, KEY_SLASH}};

	private static final String LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM";
	private static final String PLAIN = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";
	/** Shift changes only ㅂ ㅈ ㄷ ㄱ ㅅ ㅐ ㅔ. */
	private static final String SHIFTED = "ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";

	private DubeolsikLayout() {
	}

	/** The jamo this key types (with or without Shift), or 0 when the key is not a letter key. */
	public static char jamoFor(int key, boolean shift) {
		int index = key >= 'A' && key <= 'Z' ? LETTERS.indexOf((char) key) : -1;
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
		return index < 0 ? -1 : LETTERS.charAt(index);
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
			default -> key >= 'A' && key <= 'Z' ? String.valueOf((char) key) : "";
		};
	}
}
