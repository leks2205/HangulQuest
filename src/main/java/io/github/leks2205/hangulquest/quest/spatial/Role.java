package io.github.leks2205.hangulquest.quest.spatial;

/** What a place word does in the task; it decides the particle. */
public enum Role {
	/** Where the thing goes: 문 옆에 놓아요. */
	TARGET("에"),
	/** Where the activity happens: 횃불 아래에서 빵을 먹어요. */
	ACTIVITY_PLACE("에서");

	private final String particle;

	Role(String particle) {
		this.particle = particle;
	}

	public String particle() {
		return particle;
	}
}
