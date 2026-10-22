package io.github.leks2205.hangulquest.learn.hvpt;

/** Where a session reports to: every answer as it happens, and the score when the run is complete. */
public interface HvptSink {
	void answered(HvptPhase phase, HvptTrial trial, String picked, boolean correct, long latencyMs, long now);

	void finished(HvptResult result, long now);
}
