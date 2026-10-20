package io.github.leks2205.hangulquest.quest;

/**
 * Closed verb set. Two stems are stored so the difficulty ladder can build
 * grammatical endings without irregular-verb guesswork:
 * {@code eoyo} takes 아/어 (and 해), {@code stem} takes 지/고서는.
 */
public enum Verb {
	MINE("캐다", "캐", "캐", "abbauen"),
	CHOP("베다", "베", "베어", "fällen"),
	HARVEST("수확하다", "수확하", "수확해", "ernten"),
	PLACE("놓다", "놓", "놓아", "platzieren"),
	COLLECT("모으다", "모으", "모아", "sammeln"),
	EAT("먹다", "먹", "먹어", "essen"),
	DRINK("마시다", "마시", "마셔", "trinken"),
	LOOK("보다", "보", "봐", "anschauen"),
	WATCH("바라보다", "바라보", "바라봐", "betrachten"),
	DEFEAT("물리치다", "물리치", "물리쳐", "besiegen"),
	GO("가다", "가", "가", "gehen"),
	CLIMB("올라가다", "올라가", "올라가", "hinaufsteigen"),
	DESCEND("내려가다", "내려가", "내려가", "hinabsteigen"),
	OPEN("열다", "열", "열어", "öffnen"),
	TALK("이야기하다", "이야기하", "이야기해", "sprechen"),
	SLEEP("자다", "자", "자", "schlafen"),
	CRAFT("만들다", "만들", "만들어", "herstellen"),
	USE("쓰다", "쓰", "써", "benutzen"),
	RING("울리다", "울리", "울려", "läuten");

	private final String dictionary;
	private final String stem;
	private final String eoyo;
	private final String german;

	Verb(String dictionary, String stem, String eoyo, String german) {
		this.dictionary = dictionary;
		this.stem = stem;
		this.eoyo = eoyo;
		this.german = german;
	}

	public String dictionary() {
		return dictionary;
	}

	/** Stem for 지 / 지 않고서는. */
	public String stem() {
		return stem;
	}

	/** Stem before 아/어야 / 아/어야만. */
	public String eoyo() {
		return eoyo;
	}

	public String polite() {
		return eoyo + "요";
	}

	public String german() {
		return german;
	}

	public static Verb byId(String id, Verb fallback) {
		if (id == null) {
			return fallback;
		}
		try {
			return Verb.valueOf(id.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			return fallback;
		}
	}
}
