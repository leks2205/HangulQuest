package io.github.leks2205.hangulquest.dialogue;

/** What a conversation does to the game: learning records and quest starts. {@link GameDialogueSink} is the real one. */
public interface DialogueSink {
	/** The word was seen in a line: an encounter, never a review. */
	void exposeWord(String wordId);

	/** The grammar point (a grammar.json id) was met. */
	void exposeGrammar(String point);

	/** A choice between {@code options} forms was answered. Logged, and never moves any schedule. */
	void practiceGrammar(String point, int options, boolean correct);

	/** A typed word that stood on screen was copied. Counts, is logged, never moves the schedule. */
	void practiceWord(String wordId, boolean correct);

	/** Starts the trail; false when it is unknown, finished, locked or the quest log is full. */
	boolean startTrail(String trailId);
}
