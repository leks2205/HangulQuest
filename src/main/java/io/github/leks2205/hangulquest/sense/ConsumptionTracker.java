package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Detects eaten/drunk items from an inventory drop after the item was used.
 * A generous window covers server latency between "use" and the stack update.
 */
public final class ConsumptionTracker {
	private static final long WINDOW_MS = 15_000L;
	private final Map<String, Long> recent = new HashMap<>();

	public void used(String itemId, long now) {
		if (itemId != null) {
			recent.put(itemId, now);
		}
	}

	public List<String> update(Map<String, Integer> before, Map<String, Integer> after, long now) {
		if (recent.isEmpty()) {
			return List.of();
		}
		List<String> consumed = new ArrayList<>(1);
		Iterator<Map.Entry<String, Long>> it = recent.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			String item = entry.getKey();
			if (now - entry.getValue() > WINDOW_MS) {
				it.remove();
				continue;
			}
			if (after.getOrDefault(item, 0) < before.getOrDefault(item, 0)) {
				consumed.add(item);
				it.remove();
			}
		}
		return consumed;
	}

	/**
	 * Writes what was eaten straight into {@code obs}, the only place a consumption is recorded, so
	 * one bite can never show up in two observations. Returns the consumed items.
	 */
	public List<String> credit(Observation obs, Map<String, Integer> before, Map<String, Integer> after,
							   boolean night) {
		List<String> eaten = update(before, after, obs.now);
		for (String item : eaten) {
			obs.consumedItems.add(item);
			if (night) {
				obs.consumedItemsAtNight.add(item);
			}
		}
		return eaten;
	}

	public void clear() {
		recent.clear();
	}
}
