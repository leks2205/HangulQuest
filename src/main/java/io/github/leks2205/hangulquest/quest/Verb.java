package io.github.leks2205.hangulquest.quest;

/**
 * Closed verb set. Stems are stored so the difficulty ladder can build
 * grammatical endings without irregular-verb guesswork:
 * {@code eoyo} takes 아/어 (and 해), {@code stem} takes 지/고서는, and the optional
 * {@code future} form is the -(으)ㄹ stem where it cannot be derived (ㅂ-irregular 줍다 → 주울).
 * Verbs that always come with a fixed object (먹이를 주다, 말을 걸다) carry it as {@code prefix}.
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
	TALK("이야기하다", "이야기하", "이야기해", "sprechen", Frame.COM),
	SLEEP("자다", "자", "자", "schlafen", Frame.LOC),
	CRAFT("만들다", "만들", "만들어", "herstellen"),
	USE("쓰다", "쓰", "써", "benutzen"),
	RING("울리다", "울리", "울려", "läuten"),

	// Added for the verb table (verbs.json): the right verb for the right object.
	BREAK("부수다", "부수", "부숴", "zerstören"),
	DIG("파다", "파", "파", "graben"),
	PULL("뽑다", "뽑", "뽑아", "ausreißen"),
	SNAP("꺾다", "꺾", "꺾어", "pflücken"),
	PICK("따다", "따", "따", "pflücken"),
	SHATTER("깨다", "깨", "깨", "zerbrechen"),
	PLANT("심다", "심", "심어", "pflanzen"),
	SPREAD("깔다", "깔", "깔아", "auslegen"),
	HANG("걸다", "걸", "걸어", "aufhängen"),
	STAND("세우다", "세우", "세워", "aufstellen"),
	HANG_DOOR("달다", "달", "달아", "anbringen"),
	STACK("쌓다", "쌓", "쌓아", "stapeln"),
	LIGHT("밝히다", "밝히", "밝혀", "entzünden"),
	KINDLE("피우다", "피우", "피워", "anzünden"),
	OBTAIN("구하다", "구하", "구해", "beschaffen"),
	PICK_UP("줍다", "줍", "주워", "aufheben", "주울"),
	WEAVE("치다", "치", "쳐", "spannen"),
	PRESS("누르다", "누르", "눌러", "drücken"),
	FLIP("당기다", "당기", "당겨", "ziehen"),
	SWITCH_ON("켜다", "켜", "켜", "einschalten"),
	ARRIVE("도착하다", "도착하", "도착해", "ankommen"),
	FEED("주다", "주", "줘", "füttern", "먹이를 ", Frame.DAT),
	THROW("던지다", "던지", "던져", "werfen"),
	PET("쓰다듬다", "쓰다듬", "쓰다듬어", "streicheln"),
	RIDE("타다", "타", "타", "reiten"),
	ADDRESS("걸다", "걸", "걸어", "ansprechen", "말을 ", Frame.DAT);

	/** How the target noun is marked in front of the verb. */
	public enum Frame {
		/** 돌을 캐요. */
		ACC,
		/** 주민과 이야기해요. */
		COM,
		/** 소에게 먹이를 줘요. */
		DAT,
		/** 침대에서 자요. */
		LOC
	}

	private final String dictionary;
	private final String stem;
	private final String eoyo;
	private final String german;
	private final String future;
	private final String prefix;
	private final Frame frame;

	Verb(String dictionary, String stem, String eoyo, String german) {
		this(dictionary, stem, eoyo, german, null, "", Frame.ACC);
	}

	Verb(String dictionary, String stem, String eoyo, String german, Frame frame) {
		this(dictionary, stem, eoyo, german, null, "", frame);
	}

	Verb(String dictionary, String stem, String eoyo, String german, String future) {
		this(dictionary, stem, eoyo, german, future, "", Frame.ACC);
	}

	Verb(String dictionary, String stem, String eoyo, String german, String prefix, Frame frame) {
		this(dictionary, stem, eoyo, german, null, prefix, frame);
	}

	Verb(String dictionary, String stem, String eoyo, String german, String future, String prefix,
		 Frame frame) {
		this.dictionary = dictionary;
		this.stem = stem;
		this.eoyo = eoyo;
		this.german = german;
		this.future = future;
		this.prefix = prefix;
		this.frame = frame;
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

	/** The -(으)ㄹ form (without the following word): 캐 → 캘, 먹 → 먹을, 걸 → 걸, 줍 → 주울. */
	public String future() {
		return future != null ? future : eulR(stem);
	}

	/** Fixed object that always precedes the verb ("먹이를 ", "말을 "), or the empty string. */
	public String prefix() {
		return prefix;
	}

	public Frame frame() {
		return frame;
	}

	public String polite() {
		return eoyo + "요";
	}

	/** Past polite: 캐 → 캤어요, 놓아 → 놓았어요, 부숴 → 부쉈어요, 주워 → 주웠어요. */
	public String past() {
		return pastStem(eoyo) + "어요";
	}

	public String german() {
		return german;
	}

	/** Adds the past marker ㅆ to an 아/어 form; every stored form ends in an open syllable (놓아, 캐, 봐). */
	private static String pastStem(String form) {
		char last = form.charAt(form.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3 || (last - 0xAC00) % 28 != 0) {
			throw new IllegalStateException("아/어 form must end in an open syllable: " + form);
		}
		// ㅆ is final consonant number 20 (0 = none).
		return form.substring(0, form.length() - 1) + (char) (last + 20);
	}

	/** Appends the object/obligation marker: 먹 → 먹을, 가 → 갈 (ㄹ becomes the final consonant). */
	static String eulR(String stem) {
		if (stem == null || stem.isEmpty()) {
			return "";
		}
		char last = stem.charAt(stem.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return stem + "ㄹ";
		}
		int jong = (last - 0xAC00) % 28;
		if (jong == 8) {
			// ㄹ-final stems already end in ㄹ: 열다 → 열 수밖에, 만들다 → 만들 수밖에.
			return stem;
		}
		if (jong != 0) {
			return stem + "을";
		}
		return stem.substring(0, stem.length() - 1) + (char) (last + 8);
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
