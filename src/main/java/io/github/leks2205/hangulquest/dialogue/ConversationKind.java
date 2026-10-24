package io.github.leks2205.hangulquest.dialogue;

import java.util.Locale;

/**
 * What a conversation is for, which decides when the villager starts it (see {@code ConversationSelector}). A
 * dialogue file without {@code kind} is small talk.
 */
public enum ConversationKind {
	/** The first conversation with a villager: it says its name ({@code selfIntro} node). */
	INTRO("intro"),
	/** Everything else a villager may say, the profession conversations included. */
	SMALLTALK("smalltalk"),
	/** Once, when the friendship reaches the trigger's {@code friendship} (3, 6 or 10). */
	MILESTONE("milestone"),
	/** A close friend offers to switch to 반말 (LAZY, JOCK, PEPPY, SISTERLY only). */
	BANMAL_OFFER("banmal_offer"),
	/** Built by the game from a request file; never written as a dialogue file. */
	REQUEST("request");

	private final String id;

	ConversationKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** The kind of a data file name; null for an unknown one. */
	public static ConversationKind fromId(String id) {
		if (id == null) {
			return null;
		}
		String wanted = id.trim().toLowerCase(Locale.ROOT);
		for (ConversationKind kind : values()) {
			if (kind.id.equals(wanted)) {
				return kind;
			}
		}
		return null;
	}
}
