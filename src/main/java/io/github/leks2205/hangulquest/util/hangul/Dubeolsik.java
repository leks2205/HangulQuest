package io.github.leks2205.hangulquest.util.hangul;

/**
 * The 두벌식 keystroke state machine: the keys typed so far and the text they compose into, the way
 * a Korean IME assembles it. Ported from a 두벌식 typing trainer.
 *
 * <ul>
 * <li>A final consonant moves to the next syllable when a vowel follows (값 + ㅏ gives 갑사).</li>
 * <li>Compound finals and vowels form from two keys and split again the same way.</li>
 * <li>Backspace removes one key at a time (갑사 back to 값 back to 갑 back to 가).</li>
 * </ul>
 *
 * <p>The keys are the source of truth and the text is composed from them, so backspace can never
 * disagree with typing. Answers are short, so composing the whole thing again per key is cheap.
 */
public final class Dubeolsik {
	/** The keys typed, jamo and literal characters mixed. */
	private final StringBuilder strokes = new StringBuilder();
	private String text = "";

	/** Types one jamo key. A compound ("ㅘ", "ㄳ") counts as the two keys that make it. */
	public void jamo(char jamo) {
		HangulCompose.appendJamo(strokes, jamo);
		text = compose(strokes);
	}

	/** Types a literal character (space, digit, punctuation, Latin). Hangul goes through {@link #insert}. */
	public void literal(int codePoint) {
		strokes.appendCodePoint(codePoint);
		text = compose(strokes);
	}

	/** Types already composed text, e.g. what an OS input method committed; syllables become keys again. */
	public void insert(CharSequence composed) {
		composed.codePoints().forEach(cp -> HangulCompose.appendJamo(strokes, cp));
		text = compose(strokes);
	}

	/** Removes the last key. Returns false when there was nothing to remove. */
	public boolean backspace() {
		int length = strokes.length();
		if (length == 0) {
			return false;
		}
		strokes.setLength(length - Character.charCount(strokes.codePointBefore(length)));
		text = compose(strokes);
		return true;
	}

	/** Goes back to an earlier {@link #strokes()}. */
	public void restore(String earlier) {
		strokes.setLength(0);
		strokes.append(earlier);
		text = compose(strokes);
	}

	public void clear() {
		strokes.setLength(0);
		text = "";
	}

	/** The composed text, including the syllable still being composed. */
	public String text() {
		return text;
	}

	/** The keys typed so far, one jamo per key. */
	public String strokes() {
		return strokes.toString();
	}

	public boolean isEmpty() {
		return strokes.length() == 0;
	}

	/**
	 * True while the last character can still change: the last key was a jamo, so the next key may
	 * extend or replace it. A space or other literal commits.
	 */
	public boolean composing() {
		int length = strokes.length();
		if (length == 0) {
			return false;
		}
		int last = strokes.codePointBefore(length);
		return last <= 0xFFFF && HangulCompose.isKeyJamo((char) last);
	}

	/** The text that keys compose into. */
	public static String compose(CharSequence strokes) {
		Composer composer = new Composer(strokes.length());
		strokes.codePoints().forEach(composer::feed);
		return composer.finish();
	}

	/** One pass over the keys: finished syllables go to {@code out}, the open one is in cho/jung/jong. */
	private static final class Composer {
		private final StringBuilder out;
		private char cho;
		private char jung;
		private char jong;

		Composer(int capacity) {
			out = new StringBuilder(capacity);
		}

		void feed(int codePoint) {
			if (codePoint > 0xFFFF || !HangulCompose.isKeyJamo((char) codePoint)) {
				flush();
				out.appendCodePoint(codePoint);
			} else if (HangulCompose.isVowel((char) codePoint)) {
				vowel((char) codePoint);
			} else {
				consonant((char) codePoint);
			}
		}

		String finish() {
			flush();
			return out.toString();
		}

		private void consonant(char key) {
			if (cho != 0 && jung != 0) {
				if (jong == 0 && HangulCompose.canBeFinal(key)) {
					jong = key;
					return;
				}
				char joined = jong == 0 ? 0 : HangulCompose.joinFinal(jong, key);
				if (joined != 0) {
					jong = joined;
					return;
				}
			}
			flush();
			cho = key;
		}

		private void vowel(char key) {
			if (cho != 0 && jung == 0) {
				jung = key;
				return;
			}
			char joined = jung != 0 && jong == 0 ? HangulCompose.joinVowel(jung, key) : 0;
			if (joined != 0) {
				jung = joined;
				return;
			}
			if (cho != 0 && jung != 0 && jong != 0) {
				// The final leaves with the vowel: 값 + ㅏ is 갑사, 갑 + ㅏ is 가바.
				String parts = HangulCompose.splitFinal(jong);
				char moved;
				if (parts == null) {
					moved = jong;
					jong = 0;
				} else {
					jong = parts.charAt(0);
					moved = parts.charAt(1);
				}
				flush();
				cho = moved;
				jung = key;
				return;
			}
			flush();
			jung = key;
		}

		private void flush() {
			if (cho != 0 && jung != 0) {
				out.append(HangulCompose.compose(cho, jung, jong));
			} else {
				if (cho != 0) {
					out.append(cho);
				}
				if (jung != 0) {
					out.append(jung);
				}
			}
			cho = 0;
			jung = 0;
			jong = 0;
		}
	}
}
