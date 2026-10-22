package io.github.leks2205.hangulquest.learn.hvpt;

/** The little part of the audio service the sound forge needs, so the model runs without a client. */
public interface HvptSounds {
	/** True when the voices pack has this event (and the sound setting is on). */
	boolean has(String event);

	/** Starts the sound; does nothing when it is missing. */
	void play(String event);
}
