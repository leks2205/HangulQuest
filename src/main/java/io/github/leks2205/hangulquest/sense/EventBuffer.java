package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.List;

/**
 * Events reported by client callbacks between two sensor ticks. Each event has exactly one entry
 * point here, and {@link #drainInto} moves it into one observation exactly once.
 */
public final class EventBuffer {
	private final List<String> broken = new ArrayList<>();
	private final List<String> usedBlocks = new ArrayList<>();
	private final List<String> usedBlocksAtNight = new ArrayList<>();
	private final List<String> usedItems = new ArrayList<>();
	private final List<String> attacked = new ArrayList<>();
	private final List<String> interacted = new ArrayList<>();
	private final List<java.util.UUID> interactedUuids = new ArrayList<>();

	public void blockBroken(String block) {
		broken.add(block);
	}

	/**
	 * A right-click on a block. The client callback fires once per hand for the same click, so only
	 * the main hand is recorded; the off-hand pass of that click is the same event.
	 */
	public void blockUsed(String block, boolean night, boolean mainHand) {
		if (!mainHand) {
			return;
		}
		usedBlocks.add(block);
		if (night) {
			usedBlocksAtNight.add(block);
		}
	}

	public void itemUsed(String item) {
		usedItems.add(item);
	}

	public void entityAttacked(String entity) {
		attacked.add(entity);
	}

	public void entityInteracted(String entity) {
		entityInteracted(entity, null);
	}

	public void entityInteracted(String entity, java.util.UUID uuid) {
		interacted.add(entity);
		interactedUuids.add(uuid);
	}

	/** Appends the buffered events to the observation and empties the buffer. */
	public void drainInto(Observation obs) {
		drain(broken, obs.brokenBlocks);
		drain(usedBlocks, obs.usedBlocks);
		drain(usedBlocksAtNight, obs.usedBlocksAtNight);
		drain(usedItems, obs.usedItems);
		drain(attacked, obs.attackedEntities);
		drain(interacted, obs.interactedEntities);
		drain(interactedUuids, obs.interactedUuids);
	}

	public void clear() {
		broken.clear();
		usedBlocks.clear();
		usedBlocksAtNight.clear();
		usedItems.clear();
		attacked.clear();
		interacted.clear();
		interactedUuids.clear();
	}

	private static <T> void drain(List<T> from, List<T> to) {
		if (!from.isEmpty()) {
			to.addAll(from);
			from.clear();
		}
	}
}
