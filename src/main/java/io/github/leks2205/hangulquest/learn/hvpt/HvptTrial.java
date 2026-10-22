package io.github.leks2205.hangulquest.learn.hvpt;

import java.util.List;

/**
 * One question: the sound of form {@code target} of an item, said by {@code voice}. {@code options} are the
 * form keys the player chooses from, in display order (two or three).
 */
public record HvptTrial(String contrast, String itemId, String target, String voice, List<String> options) {
	public HvptTrial {
		options = List.copyOf(options);
	}

	public String targetEvent() {
		return HvptSet.event(itemId, target, voice);
	}

	public String event(String form) {
		return HvptSet.event(itemId, form, voice);
	}

	/** The review log's name for what was heard. */
	public String stimulus() {
		return HvptSet.stimulusId(itemId, target);
	}
}
