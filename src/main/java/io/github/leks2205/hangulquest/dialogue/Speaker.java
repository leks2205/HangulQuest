package io.github.leks2205.hangulquest.dialogue;

/**
 * Who speaks a line. The speech level is part of the persona: a child talks 반말, a librarian 합쇼체,
 * everyone else 해요체. The Korean names are the vanilla ones (names.json).
 */
public enum Speaker {
	VILLAGER("villager", "주민", SpeechLevel.HAEYO),
	FARMER("farmer", "농부", SpeechLevel.HAEYO),
	LIBRARIAN("librarian", "사서", SpeechLevel.HAPSYO),
	CHILD("child", "아이", SpeechLevel.BANMAL),
	TRADER("trader", "떠돌이 상인", SpeechLevel.HAEYO),
	/** The iron golem as a narrator. */
	GOLEM("golem", "철 골렘", SpeechLevel.HAEYO);

	private final String id;
	private final String ko;
	private final SpeechLevel level;

	Speaker(String id, String ko, SpeechLevel level) {
		this.id = id;
		this.ko = ko;
		this.level = level;
	}

	public String id() {
		return id;
	}

	public String ko() {
		return ko;
	}

	/** The only level this speaker uses. */
	public SpeechLevel level() {
		return level;
	}

	public static Speaker fromId(String id) {
		for (Speaker speaker : values()) {
			if (speaker.id.equals(id)) {
				return speaker;
			}
		}
		return null;
	}
}
