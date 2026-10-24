package io.github.leks2205.hangulquest.audio;

/** The little part of the sound system that {@link AudioService} needs, so it can be tested without a client. */
public interface SoundBackend {
	/** True when a sound event of this name is registered, i.e. a loaded pack defines it. */
	boolean has(String event);

	/** True when any event starts with this prefix. Only for rare checks, not for every frame. */
	boolean hasAny(String prefix);

	void play(String event, float volume);

	/** Stops the sound {@link #play} started last, if it still plays (a spoken line that was cut short). */
	default void stopLast() {
	}
}
