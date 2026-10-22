package io.github.leks2205.hangulquest.sense;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tells whether a right-click really opened or closed a door, trapdoor or fence gate, or lit or put out
 * a candle or campfire (a second tracker watches the {@code lit} state; "opened" then means "lit"). The click
 * is remembered with the state the block had at that moment; the next ticks compare the block's state with it. The state only changes once the block took the click, so an iron door (hand
 * clicks do nothing), a click while sneaking with a block in hand, or a door another player holds shut
 * report nothing. A change that does not show within {@value #WINDOW_MS} ms is dropped.
 */
public final class ToggleTracker {
	/** How long a click waits for its block to change. */
	public static final long WINDOW_MS = 1500L;

	/** The watched state ({@code open} or {@code lit}) of the block now, or null when it has none (or is gone). */
	public interface Probe {
		Boolean open(BlockPos pos);
	}

	private record Pending(BlockPos pos, String block, boolean wasOpen, long since) {
	}

	private final List<Pending> pending = new ArrayList<>(2);

	/** A right-click on a block; {@code open} is its state before the click, null when the block has none. */
	public void clicked(BlockPos pos, String block, Boolean open, long now) {
		if (open == null) {
			return;
		}
		for (Pending waiting : pending) {
			if (waiting.pos().equals(pos)) {
				return; // the callback fires once per hand
			}
		}
		pending.add(new Pending(pos.immutable(), block, open, now));
	}

	public boolean idle() {
		return pending.isEmpty();
	}

	/** Hands the blocks whose state changed since the click to {@code opened} or {@code closed}. */
	public void poll(Probe probe, long now, Consumer<String> opened, Consumer<String> closed) {
		for (int i = pending.size() - 1; i >= 0; i--) {
			Pending waiting = pending.get(i);
			Boolean open = probe.open(waiting.pos());
			if (open == null || now - waiting.since() > WINDOW_MS) {
				pending.remove(i);
			} else if (open != waiting.wasOpen()) {
				(open ? opened : closed).accept(waiting.block());
				pending.remove(i);
			}
		}
	}

	public void clear() {
		pending.clear();
	}
}
