package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.audio.AudioService;

/**
 * The sound of one card: the word is spoken once, the first time the card shows the correct form, and again
 * whenever the player asks. Silent without the voice pack or with the setting off (the service decides).
 */
public final class CardAudio {
	private final AudioService audio;
	private final String conceptId;
	private final boolean listenable;
	private boolean spoken;

	/** {@code audio} may be null (no client); the card is then simply silent. */
	public CardAudio(AudioService audio, String conceptId) {
		this.audio = audio;
		this.conceptId = conceptId;
		this.listenable = audio != null && audio.hasWord(conceptId);
	}

	/** True once the correct form is on screen: a self-grade card after the reveal, the others at their feedback. */
	public static boolean showsAnswer(Card.Phase phase) {
		return phase == Card.Phase.REVEALED || phase == Card.Phase.FEEDBACK || phase == Card.Phase.DONE;
	}

	/** Call after every input: speaks the word the first time the answer shows. Returns whether it started a clip. */
	public boolean update(Card.Phase phase) {
		if (spoken || !showsAnswer(phase)) {
			return false;
		}
		spoken = true;
		return replay();
	}

	/** The "listen" key or button. */
	public boolean replay() {
		return audio != null && audio.playWord(conceptId);
	}

	/** True when the word has a recording, so the card may offer the button and the key. */
	public boolean listenable() {
		return listenable;
	}
}
