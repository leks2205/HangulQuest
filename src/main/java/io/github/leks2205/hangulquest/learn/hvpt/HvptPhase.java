package io.github.leks2205.hangulquest.learn.hvpt;

/** What a run of trials is for. Only {@link #TRAIN} uses the training voices; the tests use the held-out ones. */
public enum HvptPhase {
	PRE,
	TRAIN,
	POST;

	public boolean test() {
		return this != TRAIN;
	}
}
