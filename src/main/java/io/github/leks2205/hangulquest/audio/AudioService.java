package io.github.leks2205.hangulquest.audio;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Plays the spoken form of a word from the optional "Hangul Quest Voices" resource pack.
 * <p>
 * The mod jar carries no sounds. The pack defines one event per word, {@code hangulquest:word.<conceptId>}, with
 * one entry per voice; the sound engine picks one of them at random. Without the pack the event does not exist,
 * and every call here quietly does nothing (asking first avoids the engine's "unknown soundEvent" warning).
 */
public final class AudioService {
	public static final String WORD_PREFIX = "word.";
	/** The sound forge's stimuli: one event per item, form and voice, because the game picks the voice there. */
	public static final String HVPT_PREFIX = "hvpt.";
	/**
	 * A spoken conversation line in one voice: {@code line.<dialogueId>.<nodeId>.<voiceId>}, its 반말 version
	 * {@code line.<dialogueId>.<nodeId>_banmal.<voiceId>}, a villager's self-introduction
	 * {@code line.name.<nameId>.<voiceId>}. One sound per event: the game picks the voice.
	 */
	public static final String LINE_PREFIX = "line.";
	/** Concept ids are lower-case ASCII; anything else cannot be a sound event name. */
	private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_]+");
	private static final Pattern SAFE_EVENT = Pattern.compile("[a-z0-9_.]+");

	/** The HUD speaks a word on its own at most this often, so a run of cards never turns into chatter. */
	public static final long AMBIENT_GAP_MS = 10_000L;

	private final SoundBackend backend;
	private final BooleanSupplier enabled;
	private final LongSupplier clock;
	private long lastAmbient;
	private boolean ambientPlayed;

	public AudioService(SoundBackend backend, BooleanSupplier enabled) {
		this(backend, enabled, System::currentTimeMillis);
	}

	public AudioService(SoundBackend backend, BooleanSupplier enabled, LongSupplier clock) {
		this.backend = backend;
		this.enabled = enabled;
		this.clock = clock;
	}

	/** True when the setting is on and a voice pack with word sounds is loaded. */
	public boolean available() {
		return enabled.getAsBoolean() && packLoaded();
	}

	public boolean packLoaded() {
		try {
			return backend.hasAny(WORD_PREFIX);
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/** True when the sound setting is on. */
	public boolean settingOn() {
		return enabled.getAsBoolean();
	}

	/** True when a voice pack with the sound forge stimuli is loaded. */
	public boolean hvptLoaded() {
		try {
			return backend.hasAny(HVPT_PREFIX);
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/** True when the setting is on and the pack defines this exact event (a sound forge stimulus, for example). */
	public boolean hasSound(String event) {
		if (event == null || !SAFE_EVENT.matcher(event).matches() || !enabled.getAsBoolean()) {
			return false;
		}
		try {
			return backend.has(event);
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/** Plays one exact event; true when it was started. */
	public boolean playSound(String event) {
		if (!hasSound(event)) {
			return false;
		}
		try {
			backend.play(event, 1.0F);
			return true;
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/** The event of a conversation line in a voice; its 반말 version when {@code banmal}. */
	public static String lineEvent(String dialogueId, String nodeId, boolean banmal, String voiceId) {
		return LINE_PREFIX + dialogueId + "." + nodeId + (banmal ? "_banmal" : "") + "." + voiceId;
	}

	/** The event of a villager saying its name ("저는 민준이에요."), by the romanised id of the name. */
	public static String selfIntroEvent(String nameId, String voiceId) {
		return LINE_PREFIX + "name." + nameId + "." + voiceId;
	}

	/**
	 * Speaks a conversation line, cutting off the line before; false (and silent) when the setting is off, no voice
	 * pack is loaded or the pack has no recording of this line in this voice.
	 */
	public boolean playLine(String event) {
		if (!hasSound(event)) {
			return false;
		}
		try {
			backend.stopLast();
			backend.play(event, 1.0F);
			return true;
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/** Stops the line that is being spoken (the conversation was closed). */
	public void stopLine() {
		try {
			backend.stopLast();
		} catch (RuntimeException failed) {
			// nothing to stop
		}
	}

	/** True when the setting is on and this word has a recording. Cheap enough to ask when a card opens. */
	public boolean hasWord(String conceptId) {
		if (conceptId == null || !SAFE_ID.matcher(conceptId).matches() || !enabled.getAsBoolean()) {
			return false;
		}
		try {
			return backend.has(WORD_PREFIX + conceptId);
		} catch (RuntimeException failed) {
			return false;
		}
	}

	/**
	 * For the HUD: plays the word unless one was played less than {@link #AMBIENT_GAP_MS} ago. A word without
	 * a recording, or a silent setting, does not use up the gap.
	 */
	public synchronized boolean playAmbient(String conceptId) {
		long now = clock.getAsLong();
		if (ambientPlayed && now - lastAmbient < AMBIENT_GAP_MS) {
			return false;
		}
		if (!playWord(conceptId)) {
			return false;
		}
		ambientPlayed = true;
		lastAmbient = now;
		return true;
	}

	/** True when this word has a recording and it was started. */
	public boolean playWord(String conceptId) {
		if (conceptId == null || !SAFE_ID.matcher(conceptId).matches() || !enabled.getAsBoolean()) {
			return false;
		}
		String event = WORD_PREFIX + conceptId;
		try {
			if (!backend.has(event)) {
				return false;
			}
			backend.play(event, 1.0F);
			return true;
		} catch (RuntimeException failed) {
			// audio is a bonus; a broken sound system must not disturb a lesson
			return false;
		}
	}
}
