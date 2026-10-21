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
	/** Concept ids are lower-case ASCII; anything else cannot be a sound event name. */
	private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_]+");

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
