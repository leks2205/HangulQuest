package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notices items taken out of a result slot (crafting, smelting, a merchant's trade) from what the
 * client sees before and after a click, and counts what really arrived once the server has
 * answered. A shift-click makes the client predict one result while the server fills the inventory
 * with as many as the ingredients allow, so a take is only closed after the inventory has been
 * still for {@link #SETTLE_MS}.
 *
 * <p>Tallies are item id to count over everything the player holds (inventory plus the cursor
 * stack); for a trade they also include the payment slots, so the price shows up as a loss.
 */
public final class ResultTakeTracker {
	public static final long SETTLE_MS = 250L;
	public static final long MAX_MS = 2_500L;

	/** A finished take. {@code paidItem} is only set for trades. */
	public record Taken(Station station, String item, int count, String paidItem, int paidCount) {
	}

	private static final class Pending {
		Station station;
		String item;
		int resultCount;
		boolean all;
		String paidItem;
		Map<String, Integer> before;
		Map<String, Integer> latest;
		long startedAt;
		long changedAt;
	}

	private static final List<Taken> NONE = List.of();

	private final List<Pending> pending = new ArrayList<>(2);
	private final List<Taken> ready = new ArrayList<>(2);

	/**
	 * Reports one click (or key press) on a screen with a result slot. A take is registered when the
	 * result slot held something before, holds less of it afterwards, and the player now has at
	 * least one more of that item. {@code all} marks a shift-click, which keeps taking until the
	 * ingredients run out. Returns whether a take was registered.
	 */
	public boolean click(Station station, StackView resultBefore, StackView resultAfter,
						 Map<String, Integer> tallyBefore, Map<String, Integer> tallyAfter,
						 boolean all, String paidItem, long now) {
		if (resultBefore.isEmpty() || !shrank(resultBefore, resultAfter)) {
			return false;
		}
		String item = resultBefore.item();
		if (tallyAfter.getOrDefault(item, 0) <= tallyBefore.getOrDefault(item, 0)) {
			return false;
		}
		// This click's "before" is where an earlier take ended, so close that one first.
		finish(tallyBefore, now, true);
		Pending p = new Pending();
		p.station = station;
		p.item = item;
		p.resultCount = resultBefore.count();
		p.all = all;
		p.paidItem = paidItem;
		p.before = new HashMap<>(tallyBefore);
		p.latest = new HashMap<>(tallyAfter);
		p.startedAt = now;
		p.changedAt = now;
		pending.add(p);
		return true;
	}

	private static boolean shrank(StackView before, StackView after) {
		return after.isEmpty() || !before.item().equals(after.item()) || after.count() < before.count();
	}

	public boolean hasPending() {
		return !pending.isEmpty();
	}

	/**
	 * Feeds the current tally and returns the takes that are finished. {@code force} closes every
	 * open take at once, for when the screen is closed.
	 */
	public List<Taken> poll(Map<String, Integer> tally, long now, boolean force) {
		if (pending.isEmpty() && ready.isEmpty()) {
			return NONE;
		}
		finish(tally, now, force);
		if (ready.isEmpty()) {
			return NONE;
		}
		List<Taken> done = new ArrayList<>(ready);
		ready.clear();
		return done;
	}

	private void finish(Map<String, Integer> tally, long now, boolean force) {
		for (int i = 0; i < pending.size(); ) {
			Pending p = pending.get(i);
			if (!tally.equals(p.latest)) {
				p.latest = new HashMap<>(tally);
				p.changedAt = now;
			}
			if (force || now - p.changedAt >= SETTLE_MS || now - p.startedAt >= MAX_MS) {
				pending.remove(i);
				close(p);
			} else {
				i++;
			}
		}
	}

	private void close(Pending p) {
		int gain = p.latest.getOrDefault(p.item, 0) - p.before.getOrDefault(p.item, 0);
		int count = p.all ? gain : Math.min(gain, p.resultCount);
		if (count <= 0) {
			return;
		}
		int paid = p.paidItem == null ? 0
				: Math.max(0, p.before.getOrDefault(p.paidItem, 0) - p.latest.getOrDefault(p.paidItem, 0));
		ready.add(new Taken(p.station, p.item, count, p.paidItem, paid));
	}

	public void clear() {
		pending.clear();
		ready.clear();
	}
}
