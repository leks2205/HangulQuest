package io.github.leks2205.hangulquest.dialogue;

/**
 * A conversation with a villager: the learning records go to the game's sink, and what changes the relationship
 * (switching to 반말, taking on or declining a request) to the callbacks.
 */
public final class VillagerDialogueSink implements DialogueSink {
	/** What a villager conversation can change besides the learning data. */
	public interface Villager {
		void acceptBanmal();

		void acceptRequest(String requestId);

		default void declineRequest(String requestId) {
		}
	}

	private final DialogueSink learning;
	private final Villager villager;

	public VillagerDialogueSink(DialogueSink learning, Villager villager) {
		this.learning = learning;
		this.villager = villager;
	}

	@Override
	public void exposeWord(String wordId) {
		learning.exposeWord(wordId);
	}

	@Override
	public void exposeGrammar(String point) {
		learning.exposeGrammar(point);
	}

	@Override
	public void practiceGrammar(String point, int options, boolean correct) {
		learning.practiceGrammar(point, options, correct);
	}

	@Override
	public void practiceWord(String wordId, boolean correct) {
		learning.practiceWord(wordId, correct);
	}

	@Override
	public boolean startTrail(String trailId) {
		return learning.startTrail(trailId);
	}

	@Override
	public void acceptBanmal() {
		villager.acceptBanmal();
	}

	@Override
	public void acceptRequest(String requestId) {
		villager.acceptRequest(requestId);
	}

	@Override
	public void declineRequest(String requestId) {
		villager.declineRequest(requestId);
	}
}
