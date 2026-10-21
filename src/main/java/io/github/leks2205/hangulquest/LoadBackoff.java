package io.github.leks2205.hangulquest;

/**
 * Retry schedule for the content load: after a failure the next attempt waits 5 s, then 30 s,
 * 2 min and finally 10 min, so a broken install is retried instead of re-parsed 20 times a second.
 * Pure state, driven by the caller's clock.
 */
final class LoadBackoff {
	static final long[] DELAYS_MS = {5_000L, 30_000L, 120_000L, 600_000L};

	private int failures;
	private long retryAt;

	/** True when no failure is pending or the wait since the last one is over. */
	boolean mayAttempt(long now) {
		return failures == 0 || now >= retryAt;
	}

	/** Records a failed attempt; true only for the first failure since the last success. */
	boolean failed(long now) {
		boolean first = failures == 0;
		retryAt = now + DELAYS_MS[Math.min(failures, DELAYS_MS.length - 1)];
		failures++;
		return first;
	}

	void succeeded() {
		failures = 0;
		retryAt = 0;
	}

	int failures() {
		return failures;
	}

	/** Milliseconds until the next attempt, 0 when one may start now. */
	long waitMs(long now) {
		return failures == 0 ? 0 : Math.max(0, retryAt - now);
	}
}
