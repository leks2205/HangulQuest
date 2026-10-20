package io.github.leks2205.hangulquest.quest;

/** Korean particle/allomorph rules driven by the final consonant (받침). */
public final class KoGrammar {
	private KoGrammar() {
	}

	public static boolean hasBatchim(String word) {
		if (word == null || word.isEmpty()) {
			return false;
		}
		char last = word.charAt(word.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return false;
		}
		return (last - 0xAC00) % 28 != 0;
	}

	public static String eul(String word) {
		return word + (hasBatchim(word) ? "을" : "를");
	}

	public static String iGa(String word) {
		return word + (hasBatchim(word) ? "이" : "가");
	}

	public static String eunNeun(String word) {
		return word + (hasBatchim(word) ? "은" : "는");
	}

	public static String waGwa(String word) {
		return word + (hasBatchim(word) ? "과" : "와");
	}

	public static String ieyo(String word) {
		return word + (hasBatchim(word) ? "이에요" : "예요");
	}

	/** 3 → "3개", with the object counter. */
	public static String counter(int count, String counter) {
		return Math.max(1, count) + counter;
	}
}
