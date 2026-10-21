package io.github.leks2205.hangulquest.audio;

import java.util.function.BooleanSupplier;
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

	private final SoundBackend backend;
	private final BooleanSupplier enabled;

	public AudioService(SoundBackend backend, BooleanSupplier enabled) {
		this.backend = backend;
		this.enabled = enabled;
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
