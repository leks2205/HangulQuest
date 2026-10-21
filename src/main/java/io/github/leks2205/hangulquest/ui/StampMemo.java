package io.github.leks2205.hangulquest.ui;

/**
 * Remembers one value together with the stamp it was built for. A frame asks {@link #value} first and
 * only builds (and {@link #store}s) a new value when the stamp moved on, so the common frame neither
 * allocates a key nor runs a lambda.
 */
public final class StampMemo<V> {
	private long stamp;
	private V value;

	/** The remembered value, or null when it was built for another stamp (or never). */
	public V value(long wanted) {
		return value != null && stamp == wanted ? value : null;
	}

	public V store(long wanted, V built) {
		stamp = wanted;
		value = built;
		return built;
	}

	public void clear() {
		value = null;
	}

	/** One stamp per second of wall time, per change counter and per variant (language, toggle). */
	public static long perSecond(long nowMs, long changes, int variant) {
		return of(Math.floorDiv(nowMs, 1000L), changes, variant);
	}

	/** A stamp for a bucket of any size, e.g. a tick number: the bucket, a change counter and a variant. */
	public static long of(long bucket, long changes, int variant) {
		return (bucket << 24) | ((changes & 0xFFFFF) << 4) | (variant & 0xF);
	}
}
