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

	/** True when the last syllable ends in ㄹ (종성 8). */
	public static boolean endsInRieul(String word) {
		if (word == null || word.isEmpty()) {
			return false;
		}
		char last = word.charAt(word.length() - 1);
		return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8;
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

	/** Direction/instrument particle: 숲으로, 바다로, 강으로, 물로 (ㄹ takes 로). */
	public static String euro(String word) {
		return word + (hasBatchim(word) && !endsInRieul(word) ? "으로" : "로");
	}

	/** Quotation-style attributive: 횃불이라는 / 나무라는. */
	public static String ira(String word) {
		return word + (hasBatchim(word) ? "이라는" : "라는");
	}

	/** Quotation particle: 횃불이라고 / 나무라고. */
	public static String iraGo(String word) {
		return word + (hasBatchim(word) ? "이라고" : "라고");
	}

	/** Topic-like "called": 횃불이란 / 나무란. */
	public static String iran(String word) {
		return word + (hasBatchim(word) ? "이란" : "란");
	}

	public static String ieyo(String word) {
		return word + (hasBatchim(word) ? "이에요" : "예요");
	}

	/** 3 → "세 개", 12 → "열두 개", 21 → "21개": native numerals up to 20, digits beyond. */
	public static String counter(int count, String counter) {
		int n = Math.max(1, count);
		if (n <= 20 && isNativeCounter(counter)) {
			return nativeNumeral(n) + " " + counter;
		}
		return n + counter;
	}

	/** Counters that take native Korean numerals (한 개, 두 마리). */
	public static boolean isNativeCounter(String counter) {
		return switch (counter) {
			case "개", "마리", "명", "그루", "송이", "병", "자루", "장", "권" -> true;
			default -> false;
		};
	}

	private static final String[] ONES = {"", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"};

	/** Native numeral in its determiner form before a counter: 1→한, 2→두, 20→스무. Valid for 1..20. */
	public static String nativeNumeral(int n) {
		if (n < 1 || n > 20) {
			throw new IllegalArgumentException("native numerals are only built for 1..20, got " + n);
		}
		if (n == 20) {
			return "스무";
		}
		if (n < 10) {
			return ONES[n];
		}
		return n == 10 ? "열" : "열" + ONES[n - 10];
	}
}
