package io.github.leks2205.hangulquest.villager;

import java.util.ArrayList;
import java.util.List;

/**
 * What the player knows about one villager. Name, gender, personality, region and voice are given once and
 * never change; the rest is the relationship. Lives in {@code villagers.json} of the profile folder.
 */
public final class VillagerProfile {
	public static final int MAX_FRIENDSHIP = 10;
	/** Friendship from which a villager is never forgotten to make room. */
	public static final int KEEP_FRIENDSHIP = 3;
	/** Friendship levels that unlock a conversation. */
	public static final List<Integer> MILESTONES = List.of(3, 6, 10);
	/** Conversations remembered per villager; the least recent one is forgotten beyond this. */
	static final int MAX_SEEN = 64;

	public final String uuid;
	public final String name;
	public final Gender gender;
	public final Personality personality;
	/** Null until the villager is met in a village of a known type (a migrated 3.0 villager starts without). */
	public Region region;
	/** The adult voice id, stored so a later change of the voice table never swaps a known villager's voice. */
	public final String voice;
	public int friendship;
	/** When the player first talked to it (epoch ms). */
	public long firstMet;
	/** The in-game day of the last conversation; -1 before the first. */
	public long lastTalkDay = -1;
	/** Conversation ids in the order they were last seen, the least recent first. */
	public final List<String> seen = new ArrayList<>();
	/** True once the player accepted the villager's offer to talk 반말. */
	public boolean banmal;
	/** The in-game day the villager last offered or was handed in a request; -1 before the first. */
	public long requestDay = -1;
	/** The in-game day the villager last reminded the player of its request; -1 before the first. */
	public long reminderDay = -1;
	/** The profession seen last (registry id), for the journal; null when unknown. */
	public String profession;
	/** Whether the villager was a child when last seen. */
	public boolean baby;

	public VillagerProfile(String uuid, String name, Gender gender, Personality personality, String voice) {
		this.uuid = uuid;
		this.name = name;
		this.gender = gender;
		this.personality = personality;
		this.voice = voice;
	}

	/** An independent copy, to try out a conversation without changing the profile. */
	public VillagerProfile copy() {
		VillagerProfile copy = new VillagerProfile(uuid, name, gender, personality, voice);
		copy.region = region;
		copy.friendship = friendship;
		copy.firstMet = firstMet;
		copy.lastTalkDay = lastTalkDay;
		copy.seen.addAll(seen);
		copy.banmal = banmal;
		copy.requestDay = requestDay;
		copy.reminderDay = reminderDay;
		copy.profession = profession;
		copy.baby = baby;
		return copy;
	}

	/** The voice it speaks with now: a child voice of its gender while it is a baby, its own adult voice after. */
	public String currentVoice() {
		return baby ? gender.childVoice() : voice;
	}

	public boolean hasSeen(String conversationId) {
		return seen.contains(conversationId);
	}

	/** Moves the conversation to the most recent end of {@link #seen}. */
	public void markSeen(String conversationId) {
		seen.remove(conversationId);
		seen.add(conversationId);
		while (seen.size() > MAX_SEEN) {
			seen.remove(0);
		}
	}

	/** How recently a conversation was seen: -1 never, higher is more recent. */
	public int seenRank(String conversationId) {
		return seen.indexOf(conversationId);
	}

	/**
	 * The first conversation of an in-game day: friendship +1 (up to the maximum). True when it was the first
	 * one that day.
	 */
	public boolean talkedOn(long day) {
		if (day <= lastTalkDay) {
			return false;
		}
		lastTalkDay = day;
		friendship = Math.min(MAX_FRIENDSHIP, friendship + 1);
		return true;
	}

	/** A finished request: friendship +2 (up to the maximum). */
	public void requestFinished() {
		friendship = Math.min(MAX_FRIENDSHIP, friendship + 2);
	}
}
