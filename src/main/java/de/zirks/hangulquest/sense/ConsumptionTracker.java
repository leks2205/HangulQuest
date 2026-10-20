package de.zirks.hangulquest.sense;

import java.util.ArrayList;
import java.util.HashMap;
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
		List<String> consumed = new ArrayList<>();
		for (String item : new ArrayList<>(recent.keySet())) {
			long usedAt = recent.getOrDefault(item, 0L);
			if (now - usedAt > WINDOW_MS) {
				recent.remove(item);
				continue;
			}
			int had = before.getOrDefault(item, 0);
			int has = after.getOrDefault(item, 0);
			if (has < had) {
				consumed.add(item);
				recent.remove(item);
			}
		}
		return consumed;
	}

	public void clear() {
		recent.clear();
	}
}
