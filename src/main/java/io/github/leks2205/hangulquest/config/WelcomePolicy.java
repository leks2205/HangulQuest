package io.github.leks2205.hangulquest.config;

import io.github.leks2205.hangulquest.learn.ProgressData;

/**
 * When the Hangul welcome screen appears. Pure decision logic on {@link Config} so it can be tested
 * without the game: Hangul is a prerequisite and is not taught, the screen only points at resources.
 *
 * <ul>
 *   <li>Fresh install: shown on the first world join.</li>
 *   <li>"Remind me later": shown once more on the first world join of a later start, then never.</li>
 *   <li>Existing player (progress already has words or finished quests): never shown.</li>
 *   <li>"I can read it" and "Don't show this again": never shown.</li>
 * </ul>
 */
public final class WelcomePolicy {
	/** First showing plus one reminder. */
	public static final int MAX_SHOWINGS = 2;

	private WelcomePolicy() {
	}

	/** Anyone with learned words or finished quests has clearly got past the alphabet. */
	public static boolean isExistingPlayer(ProgressData progress) {
		if (progress == null) {
			return false;
		}
		return !progress.words.isEmpty()
				|| !progress.quests.completed.isEmpty()
				|| !progress.quests.history.isEmpty();
	}

	/**
	 * Decides on the first world join of a game start and records the outcome in {@code config}.
	 * Returns true when the screen should open now; the caller saves the config afterwards.
	 */
	public static boolean onFirstJoin(Config config, ProgressData progress) {
		if (config.welcomeDone) {
			return false;
		}
		if (config.welcomeShown == 0 && isExistingPlayer(progress)) {
			// Someone who updated from an older version: never greet them.
			config.welcomeDone = true;
			return false;
		}
		config.welcomeShown++;
		if (config.welcomeShown >= MAX_SHOWINGS) {
			// The reminder is used up; opening the screen from the journal still works.
			config.welcomeDone = true;
		}
		return true;
	}

	/** The "Remind me later" button only makes sense while a reminder is left. */
	public static boolean canRemindLater(Config config) {
		return !config.welcomeDone && config.welcomeShown < MAX_SHOWINGS;
	}

	/** "I can read it, let's go". */
	public static void confirmed(Config config) {
		config.welcomeDone = true;
	}

	/** "Don't show this again". */
	public static void dismissedForGood(Config config) {
		config.welcomeDone = true;
	}
}
