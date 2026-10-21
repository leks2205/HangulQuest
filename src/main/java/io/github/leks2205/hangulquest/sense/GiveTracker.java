package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns "the player right-clicked an entity with an item" and "the player lost items and a new item
 * entity appeared next to them" into {@link GiveFact}s. Both are best-effort guesses from what the
 * client sees: a use only counts when the held stack really shrinks afterwards (so nothing is
 * credited in creative mode), and a drop only counts when a creature stands within
 * {@link #TARGET_RANGE} blocks.
 */
public final class GiveTracker {
	/** How long after a right-click the held stack may shrink and still count. */
	public static final long USE_WINDOW_MS = 1_500L;
	/** How long after an inventory loss the dropped item entity may still show up. */
	public static final long DROP_WINDOW_MS = 2_000L;
	/** A creature this close to the player when the item lands is the one it was given to. */
	public static final double TARGET_RANGE = 3.0;
	/** A freshly spawned item entity this close to the player is their drop, not a mob's. */
	public static final double SPAWN_RANGE = 4.5;
	private static final int CAP = 16;

	/** A creature near the player when an item was dropped. */
	public record Target(String type, UUID uuid, double distance) {
	}

	private record Use(String item, String targetType, UUID uuid, GiveFact.Method method, long at) {
	}

	private record Drop(String item, int count, long at) {
	}

	private static final List<GiveFact> NONE = List.of();

	private final List<Use> uses = new ArrayList<>(2);
	private final List<Drop> drops = new ArrayList<>(2);
	private final Set<Integer> claimed = new LinkedHashSet<>();

	/** The player right-clicked an entity holding {@code item}; {@code food} is true for an animal's food. */
	public void used(String item, String targetType, UUID uuid, boolean food, long now) {
		if (item == null) {
			return;
		}
		if (uses.size() >= CAP) {
			uses.remove(0);
		}
		uses.add(new Use(item, targetType, uuid, food ? GiveFact.Method.FEED : GiveFact.Method.USE_WITH, now));
	}

	/**
	 * Compares two inventory tallies. A loss that follows a recent use becomes that use's give; any
	 * other loss is remembered as a possible drop until the item entity appears, unless
	 * {@code mayDrop} is false (a screen is open and stacks are moving to and from the cursor).
	 */
	public List<GiveFact> inventoryChanged(Map<String, Integer> before, Map<String, Integer> after, long now,
										   boolean mayDrop) {
		expire(now);
		List<GiveFact> gave = NONE;
		for (Map.Entry<String, Integer> entry : before.entrySet()) {
			int lost = entry.getValue() - after.getOrDefault(entry.getKey(), 0);
			if (lost <= 0) {
				continue;
			}
			String item = entry.getKey();
			Use use = takeUse(item);
			if (use != null) {
				if (gave == NONE) {
					gave = new ArrayList<>(2);
				}
				gave.add(new GiveFact(item, lost, use.targetType(), use.uuid(), use.method()));
			} else if (mayDrop) {
				if (drops.size() >= CAP) {
					drops.remove(0);
				}
				drops.add(new Drop(item, lost, now));
			}
		}
		return gave;
	}

	private Use takeUse(String item) {
		for (int i = uses.size() - 1; i >= 0; i--) {
			if (uses.get(i).item().equals(item)) {
				return uses.remove(i);
			}
		}
		return null;
	}

	/** True while some inventory loss may still turn into a dropped item entity. */
	public boolean hasDropCandidates(long now) {
		expire(now);
		return !drops.isEmpty();
	}

	/**
	 * A young item entity was seen {@code distance} blocks from the player. Returns the give when it
	 * matches a recent inventory loss of the same item and a creature is within {@link #TARGET_RANGE};
	 * a matching drop with nobody close is consumed without a give.
	 */
	public GiveFact itemSpawned(int entityId, String item, int count, double distance,
								List<Target> targets, long now) {
		if (distance > SPAWN_RANGE || claimed.contains(entityId)) {
			return null;
		}
		expire(now);
		for (int i = 0; i < drops.size(); i++) {
			Drop drop = drops.get(i);
			// Stacks on the ground merge, so the entity may hold more than was dropped.
			if (!drop.item().equals(item) || count < drop.count()) {
				continue;
			}
			drops.remove(i);
			claimed.add(entityId);
			if (claimed.size() > CAP) {
				claimed.remove(claimed.iterator().next());
			}
			Target nearest = null;
			for (Target target : targets) {
				if (target.distance() <= TARGET_RANGE && (nearest == null || target.distance() < nearest.distance())) {
					nearest = target;
				}
			}
			return nearest == null ? null
					: new GiveFact(item, drop.count(), nearest.type(), nearest.uuid(), GiveFact.Method.DROP_NEAR);
		}
		return null;
	}

	private void expire(long now) {
		// Guarded: a capturing lambda would allocate on every idle tick.
		if (!uses.isEmpty()) {
			uses.removeIf(use -> now - use.at() > USE_WINDOW_MS);
		}
		if (!drops.isEmpty()) {
			drops.removeIf(drop -> now - drop.at() > DROP_WINDOW_MS);
		}
	}

	public void clear() {
		uses.clear();
		drops.clear();
		claimed.clear();
	}
}
