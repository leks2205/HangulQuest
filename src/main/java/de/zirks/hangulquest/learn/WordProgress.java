package de.zirks.hangulquest.learn;

/** Per-word scheduling state persisted in progress.json. */
public final class WordProgress {
	public long introducedAt;
	public int level;
	public long dueAt;
	public int seen;
	public int correct;
	public int wrong;
	public long lastSeenAt;
	public boolean known;
	public String origin = "CURATED";
	public Integer luteStatus;
}
