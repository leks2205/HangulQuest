package io.github.leks2205.hangulquest.sense;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What the client can tell of the End without seeing the server: that the player was thrown from beside an end
 * gateway, and that an end crystal appeared next to the player while the item was in the hand. The sensor feeds
 * it what it reads each tick; the decisions are here so they can be tested without a world.
 */
public final class EndWatch {
	/** A jump this long between two ticks, from beside a gateway, is the gateway. A walk or a fall is far shorter. */
	static final double JUMP = 16.0;
	/** A crystal this young, this close, counts as set down by the player. */
	static final int MAX_AGE_TICKS = 10;
	static final double REACH = 8.0;
	/** The crystal item must have been in a hand this recently (ms) for a new crystal to be the player's. */
	static final long HELD_WINDOW_MS = 3000L;

	private static final long NEVER = Long.MIN_VALUE / 2;

	private double lastX;
	private double lastY;
	private double lastZ;
	private String lastDimension;
	private boolean lastNearGateway;
	private boolean hasLast;
	private long crystalInHandAt = NEVER;
	private final Set<UUID> knownCrystals = new HashSet<>();

	public void reset() {
		hasLast = false;
		lastNearGateway = false;
		lastDimension = null;
		crystalInHandAt = NEVER;
		knownCrystals.clear();
	}

	/**
	 * @param nearGateway whether an end gateway block lies within a few blocks of the player now
	 * @return true on the tick the player was thrown: they stood beside a gateway last tick and are far away now
	 */
	public boolean gatewayUsed(String dimension, double x, double y, double z, boolean nearGateway) {
		boolean thrown = hasLast && lastNearGateway && dimension.equals(lastDimension)
				&& Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ)) > JUMP;
		lastX = x;
		lastY = y;
		lastZ = z;
		lastDimension = dimension;
		lastNearGateway = nearGateway;
		hasLast = true;
		return thrown;
	}

	/** Called every tick with whether either hand holds an end crystal. */
	public void hands(boolean holdsCrystal, long now) {
		if (holdsCrystal) {
			crystalInHandAt = now;
		}
	}

	/**
	 * A crystal in sight. True the first time one that is young and near shows up while the item was just in a hand;
	 * a crystal met for the first time that is old (on the island) is remembered and never counts.
	 */
	public boolean crystalSeen(UUID id, int ageTicks, double distance, long now) {
		boolean fresh = knownCrystals.add(id);
		return fresh && ageTicks <= MAX_AGE_TICKS && distance <= REACH && now - crystalInHandAt <= HELD_WINDOW_MS;
	}

	/** Forgets crystals that are gone, so the set does not grow for ever. */
	public void keepOnly(Set<UUID> inSight) {
		knownCrystals.retainAll(inSight);
	}
}
