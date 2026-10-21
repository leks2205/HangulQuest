package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.learn.fsrs.CardState;

/** Per-word scheduling state persisted in progress.json. */
public final class WordProgress {
	public long introducedAt;
	/** For display only (journal dots) and for downgrades; the schedule lives in {@link #fsrs}. */
	public int level;
	/** When the word is due; mirrors {@code fsrs.due} (the old field, still read by older builds). */
	public long dueAt;
	public int seen;
	public int correct;
	public int wrong;
	public long lastSeenAt;
	/** Marked known by hand (or imported as known). Seeds the stability to 30 days. */
	public boolean known;
	public String origin = "CURATED";
	public Integer luteStatus;
	/** The FSRS memory state, schema v4. */
	public CardState fsrs;
	/** Encounters, contexts and recall days, schema v4. */
	public ExposureLedger.Entry ledger;
}
