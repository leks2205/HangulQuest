package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.quest.grammar.Conjugator;

/**
 * Closed verb set. A verb is its dictionary form, its object frame and its German gloss; every form
 * the difficulty ladder needs comes from {@link Conjugator}: {@code eoyo} takes 아/어 (and 해),
 * {@code stem} takes 지/고서는, and {@code future} is the -(으)ㄹ form (줍다 → 주울, 걸다 → 걸).
 * Verbs that always come with a fixed object (먹이를 주다, 말을 걸다) carry it as {@code prefix}.
 */
public enum Verb {
	MINE("캐다", "abbauen"),
	CHOP("베다", "fällen"),
	HARVEST("수확하다", "ernten"),
	PLACE("놓다", "platzieren"),
	COLLECT("모으다", "sammeln"),
	EAT("먹다", "essen"),
	DRINK("마시다", "trinken"),
	LOOK("보다", "anschauen"),
	WATCH("바라보다", "betrachten"),
	DEFEAT("물리치다", "besiegen"),
	/** Not to be done to a friendly creature: 주민을 해치지 마세요 (물리치다 is for an enemy). */
	HARM("해치다", "verletzen"),
	GO("가다", "gehen"),
	CLIMB("올라가다", "hinaufsteigen"),
	DESCEND("내려가다", "hinabsteigen"),
	OPEN("열다", "öffnen"),
	TALK("이야기하다", "sprechen", Frame.COM),
	SLEEP("자다", "schlafen", Frame.LOC),
	CRAFT("만들다", "herstellen"),
	USE("쓰다", "benutzen"),
	RING("울리다", "läuten"),

	// Added for the verb table (verbs.json): the right verb for the right object.
	BREAK("부수다", "zerstören"),
	DIG("파다", "graben"),
	PULL("뽑다", "ausreißen"),
	SNAP("꺾다", "pflücken"),
	PICK("따다", "pflücken"),
	SHATTER("깨다", "zerbrechen"),
	PLANT("심다", "pflanzen"),
	SPREAD("깔다", "auslegen"),
	HANG("걸다", "aufhängen"),
	STAND("세우다", "aufstellen"),
	HANG_DOOR("달다", "anbringen"),
	STACK("쌓다", "stapeln"),
	LIGHT("밝히다", "entzünden"),
	KINDLE("피우다", "anzünden"),
	OBTAIN("구하다", "beschaffen"),
	PICK_UP("줍다", "aufheben"),
	WEAVE("치다", "spannen"),
	PRESS("누르다", "drücken"),
	FLIP("당기다", "ziehen"),
	SWITCH_ON("켜다", "einschalten"),
	ARRIVE("도착하다", "ankommen"),
	FEED("주다", "füttern", "먹이를 ", Frame.DAT),
	THROW("던지다", "werfen"),
	PET("쓰다듬다", "streicheln"),
	RIDE("타다", "reiten"),
	ADDRESS("걸다", "ansprechen", "말을 ", Frame.DAT),
	TOUCH("만지다", "berühren"),
	GIVE_ITEM("주다", "geben", "아이템을 ", Frame.DAT),

	// Action variety: give, put in, hold, enter, drop, close, melt, bake, walk.
	GIVE("주다", "geben", Frame.DAT),
	PUT_IN("넣다", "hineinlegen"),
	HOLD("들다", "halten"),
	ENTER("들어가다", "hineingehen"),
	DROP_AT("떨어뜨리다", "fallen lassen"),
	CLOSE("닫다", "schließen"),
	MELT("녹이다", "schmelzen"),
	BAKE("굽다", "braten"),
	WALK("걷다", "laufen"),

	// The note block is struck, not rung.
	STRIKE("치다", "anschlagen"),

	// Wave 3: coming and going, resting, waiting, cooking, swimming, taking out, listening, raising.
	LEAVE("나가다", "hinausgehen"),
	COME_OUT("나오다", "herauskommen"),
	LIE("눕다", "sich hinlegen", Frame.IN),
	SIT("앉다", "sich setzen", Frame.IN),
	WAIT("기다리다", "warten"),
	COOK("요리하다", "kochen"),
	SWIM("수영하다", "schwimmen", Frame.LOC),
	TAKE_OUT("꺼내다", "herausnehmen"),
	HEAR("듣다", "hören"),
	RAISE("키우다", "aufziehen"),
	/** A ring of fence blocks is fenced: 울타리를 쳐요. */
	FENCE("치다", "umzäunen"),
	BUY("사다", "kaufen"),
	SELL("팔다", "verkaufen"),

	// Wave 4: the verbs of the blocks that were all "used" (쓰다) before: operate, play, charge, fill, till.
	OPERATE("사용하다", "benutzen"),
	PLAY("틀다", "abspielen"),
	CHARGE("충전하다", "aufladen"),
	FILL("채우다", "füllen"),
	TILL("갈다", "pflügen"),

	// Wave 6: wearing armour. The verb follows the body part: a helmet is put on the head (쓰다), a chest piece and
	// trousers are put on (입다), shoes are put on the feet (신다); taking any of them off is 벗다.
	WEAR_HEAD("쓰다", "aufsetzen"),
	PUT_ON("입다", "anziehen"),
	PUT_ON_SHOES("신다", "anziehen"),
	TAKE_OFF("벗다", "ausziehen"),

	// Wave 8: writing on a sign.
	WRITE("쓰다", "schreiben"),

	// Wave 9: jumping down from a height.
	JUMP_DOWN("뛰어내리다", "hinunterspringen"),

	// Wave 9: the tool in the hand decides the verb on a creature, and armour is put on a creature (입히다).
	SHEAR("깎다", "scheren"),
	LEASH("묶다", "anleinen"),
	BRUSH("닦다", "bürsten"),
	UNARMOUR("벗기다", "die Rüstung abnehmen"),
	DRESS("입히다", "anlegen", Frame.DAT),
	DYE("염색하다", "färben"),

	// Wave 10: shooting and hitting what is shot at (맞히다, not 맞추다).
	SHOOT("쏘다", "schießen"),
	HIT("맞히다", "treffen"),

	// Water and lava from a bucket are poured (ㅅ-irregular: 부어요, 부으세요).
	POUR("붓다", "gießen"),

	// Wave 13: an effect is received.
	RECEIVE("받다", "erhalten");


	/** How the target noun is marked in front of the verb. */
	public enum Frame {
		/** 돌을 캐요. */
		ACC,
		/** 주민과 이야기해요. */
		COM,
		/** 소에게 먹이를 줘요. */
		DAT,
		/** 침대에서 자요. */
		LOC,
		/** 침대에 누워요, 보트에 앉아요: the place the body goes to. */
		IN
	}

	private final String dictionary;
	private final String german;
	private final String prefix;
	private final Frame frame;
	private final Conjugator.Word word;

	Verb(String dictionary, String german) {
		this(dictionary, german, "", Frame.ACC);
	}

	Verb(String dictionary, String german, Frame frame) {
		this(dictionary, german, "", frame);
	}

	Verb(String dictionary, String german, String prefix, Frame frame) {
		this.dictionary = dictionary;
		this.german = german;
		this.prefix = prefix;
		this.frame = frame;
		this.word = Conjugator.of(dictionary);
	}

	public String dictionary() {
		return dictionary;
	}

	/** Stem for 지 / 지 않고서는. */
	public String stem() {
		return word.stem();
	}

	/** Stem before 아/어야 / 아/어야만. */
	public String eoyo() {
		return word.eo();
	}

	/** The -(으)ㄹ form (without the following word): 캐 → 캘, 먹 → 먹을, 걸 → 걸, 줍 → 주울. */
	public String future() {
		return word.future();
	}

	/** Fixed object that always precedes the verb ("먹이를 ", "말을 "), or the empty string. */
	public String prefix() {
		return prefix;
	}

	public Frame frame() {
		return frame;
	}

	/** The past attributive stem before 후에 and in -(으)ㄴ: 먹 → 먹은, 자 → 잔, 만들 → 만든, 굽 → 구운. */
	public String attributive() {
		return word.eun();
	}

	/** Honorific -(으)세요 stem: 들 → 드세요, 굽 → 구우세요, 걷 → 걸으세요. */
	public String honorific() {
		return word.seyo();
	}

	public String polite() {
		return word.eoyo();
	}

	/** Past polite: 캐 → 캤어요, 놓아 → 놓았어요, 부숴 → 부쉈어요, 주워 → 주웠어요. */
	public String past() {
		return word.past();
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
