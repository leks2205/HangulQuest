package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.SpeechLevel;

import java.util.List;
import java.util.Locale;

/**
 * The eight personalities, inspired by life-sim games: four per gender, each with one adult voice and the speech
 * level it uses with the player. A villager's personality is drawn once and never changes.
 */
public enum Personality {
	LAZY(Gender.MALE, "느긋한", "v_lazy", SpeechLevel.HAEYO, true),
	JOCK(Gender.MALE, "활발한", "v_jock", SpeechLevel.HAEYO, true),
	CRANKY(Gender.MALE, "무뚝뚝한", "v_cranky", SpeechLevel.HAEYO, false),
	SMUG(Gender.MALE, "점잖은", "v_smug", SpeechLevel.HAEYO, false),
	NORMAL(Gender.FEMALE, "상냥한", "v_normal", SpeechLevel.HAEYO, false),
	PEPPY(Gender.FEMALE, "발랄한", "v_peppy", SpeechLevel.HAEYO, true),
	SNOOTY(Gender.FEMALE, "새침한", "v_snooty", SpeechLevel.HAPSYO, false),
	SISTERLY(Gender.FEMALE, "털털한", "v_sisterly", SpeechLevel.HAEYO, true);

	/** The friendship and learner level from which a villager who may switch offers 반말. */
	public static final int BANMAL_FRIENDSHIP = 6;
	public static final int BANMAL_PLAYER_LEVEL = 4;

	private final Gender gender;
	private final String ko;
	private final String voice;
	private final SpeechLevel register;
	private final boolean mayOfferBanmal;

	Personality(Gender gender, String ko, String voice, SpeechLevel register, boolean mayOfferBanmal) {
		this.gender = gender;
		this.ko = ko;
		this.voice = voice;
		this.register = register;
		this.mayOfferBanmal = mayOfferBanmal;
	}

	public Gender gender() {
		return gender;
	}

	/** The Korean label the journal shows (an adjective: 느긋한 주민). */
	public String ko() {
		return ko;
	}

	/** The adult voice id (voices.json). */
	public String voice() {
		return voice;
	}

	/**
	 * The voice this personality speaks with in a region: the standard adult voice, or the region's accent
	 * voice (see {@link Region#accent()}) in the taiga and savanna villages.
	 */
	public String voice(Region region) {
		String accent = region == null ? null : region.accent();
		return accent == null ? voice : voice + "_" + accent;
	}

	/** How this personality talks to the player: 합쇼체 for SNOOTY, 해요체 for everyone else. */
	public SpeechLevel register() {
		return register;
	}

	/** True for the four personalities that may offer to switch to 반말 once they are close friends. */
	public boolean mayOfferBanmal() {
		return mayOfferBanmal;
	}

	/** The lower-case id used in node ids, sound events and language keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The partner type that shares topics (CRANKY and SISTERLY, JOCK and PEPPY, LAZY and NORMAL, SMUG and SNOOTY). */
	public Personality counterpart() {
		return switch (this) {
			case CRANKY -> SISTERLY;
			case SISTERLY -> CRANKY;
			case JOCK -> PEPPY;
			case PEPPY -> JOCK;
			case LAZY -> NORMAL;
			case NORMAL -> LAZY;
			case SMUG -> SNOOTY;
			case SNOOTY -> SMUG;
		};
	}

	/** The four personalities of a gender, in declaration order. */
	public static List<Personality> of(Gender gender) {
		return java.util.Arrays.stream(values()).filter(p -> p.gender == gender).toList();
	}

	/** The personality of a data file name (any case), or null. */
	public static Personality fromId(String id) {
		if (id == null) {
			return null;
		}
		for (Personality personality : values()) {
			if (personality.name().equalsIgnoreCase(id.trim())) {
				return personality;
			}
		}
		return null;
	}
}
