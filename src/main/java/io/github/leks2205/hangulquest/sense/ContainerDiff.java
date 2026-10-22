package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds what the player put into an open container (chest, barrel, shulker box ...) by comparing
 * item tallies of its slots between ticks. A put only counts when the player's own side lost the
 * same items, so a hopper emptying into the chest is not credited. Items taken out and put back
 * cancel out and do not count as a put. The other way round, an item counts as taken out when the
 * container lost it and the player's side gained it, unless it was put in a moment before
 * (see {@link #takes()}).
 */
public final class ContainerDiff {
	public record Put(String item, int count) {
	}

	private static final List<Put> NONE = List.of();

	private Map<String, Integer> container = new HashMap<>();
	private Map<String, Integer> player = new HashMap<>();
	private final Map<String, Integer> withdrawn = new HashMap<>();
	private final Map<String, Integer> deposited = new HashMap<>();
	private List<Put> takes = NONE;

	/** Starts watching a container that was just opened. */
	public void open(Map<String, Integer> containerTally, Map<String, Integer> playerTally) {
		container = new HashMap<>(containerTally);
		player = new HashMap<>(playerTally);
		withdrawn.clear();
		deposited.clear();
		takes = NONE;
	}

	/** What the player took out in the last {@link #update}; the shared empty list while nothing was taken. */
	public List<Put> takes() {
		return takes;
	}

	/** Compares against the previous tick. Allocates only when something changed. */
	public List<Put> update(Map<String, Integer> containerNow, Map<String, Integer> playerNow) {
		takes = NONE;
		boolean containerSame = containerNow.equals(container);
		boolean playerSame = playerNow.equals(player);
		if (containerSame && playerSame) {
			return NONE;
		}
		List<Put> puts = NONE;
		if (!containerSame) {
			for (Map.Entry<String, Integer> entry : containerNow.entrySet()) {
				String item = entry.getKey();
				int grew = entry.getValue() - container.getOrDefault(item, 0);
				if (grew <= 0) {
					continue;
				}
				int playerLost = player.getOrDefault(item, 0) - playerNow.getOrDefault(item, 0);
				int put = Math.min(grew, playerLost);
				// Putting back what was just taken out is not a new put.
				int back = Math.min(put, withdrawn.getOrDefault(item, 0));
				if (back > 0) {
					withdrawn.merge(item, -back, Integer::sum);
					put -= back;
				}
				if (put > 0) {
					deposited.merge(item, put, Integer::sum);
					if (puts == NONE) {
						puts = new ArrayList<>(2);
					}
					puts.add(new Put(item, put));
				}
			}
			for (Map.Entry<String, Integer> entry : container.entrySet()) {
				String item = entry.getKey();
				int shrank = entry.getValue() - containerNow.getOrDefault(item, 0);
				int playerGained = playerNow.getOrDefault(item, 0) - player.getOrDefault(item, 0);
				int taken = Math.min(shrank, playerGained);
				if (taken > 0) {
					withdrawn.merge(item, taken, Integer::sum);
					// Taking back what was just put in is not a new take.
					int returned = Math.min(taken, deposited.getOrDefault(item, 0));
					if (returned > 0) {
						deposited.merge(item, -returned, Integer::sum);
					}
					if (taken > returned) {
						if (takes == NONE) {
							takes = new ArrayList<>(2);
						}
						takes.add(new Put(item, taken - returned));
					}
				}
			}
			container = new HashMap<>(containerNow);
		}
		if (!playerSame) {
			player = new HashMap<>(playerNow);
		}
		return puts;
	}

	public void clear() {
		container = new HashMap<>();
		player = new HashMap<>();
		withdrawn.clear();
		deposited.clear();
		takes = NONE;
	}
}
