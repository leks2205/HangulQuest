package de.zirks.hangulquest.sense;

/** Keeps quizzes out of combat, menus and other unsafe moments. */
public final class SafetyGate {
	private SafetyGate() {
	}

	public static boolean safeForQuiz(Observation obs) {
		return obs != null
				&& !obs.dead
				&& !obs.screenOpen
				&& !obs.spectator
				&& obs.health >= 8.0F
				&& obs.hostilesNear == 0
				&& !obs.damageRecently
				&& obs.idle;
	}
}
