package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Credits kills to the player. Only entities the player actually hurt (melee, or a projectile the
 * player fired that was seen reaching the entity) are watched, by entity id, so each tick costs
 * O(attacked entities) and never a scan of the neighbourhood.
 */
public final class DeathTracker {
	/** A death counts when the entity was hurt by the player this recently. */
	public static final long WINDOW_MS = 5_000L;
	/** After shooting a bow, crossbow or trident, projectiles are followed for this long. */
	public static final long RANGED_WINDOW_MS = 30_000L;
	public static final int CAP = 256;

	/** What the client currently sees of an entity. */
	public record Subject(String type, boolean dying) {
	}

	/** Resolves an entity id to what is loaded right now; null when it is not in the level. */
	public interface World {
		Subject find(int entityId);
	}

	public record Kill(int entityId, String type) {
	}

	private final Map<Integer, Long> attackedAt = new LinkedHashMap<>();
	/**
	 * Entities already credited, so one still in its death animation is not counted again. Bounded
	 * LRU: the eldest entry is dropped, never the whole set.
	 */
	private final Set<Integer> credited = Collections.newSetFromMap(
			new LinkedHashMap<Integer, Boolean>(64, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
					return size() > CAP;
				}
			});
	private long rangedUntil;

	/** The player hurt this entity at {@code now}. Dragon parts must be mapped to the dragon first. */
	public void attacked(int entityId, long now) {
		if (credited.contains(entityId)) {
			return;
		}
		attackedAt.remove(entityId);
		attackedAt.put(entityId, now);
		if (attackedAt.size() > CAP) {
			Iterator<Integer> eldest = attackedAt.keySet().iterator();
			eldest.next();
			eldest.remove();
		}
	}

	public void rangedWeaponUsed(long now) {
		rangedUntil = now + RANGED_WINDOW_MS;
	}

	public boolean rangedActive(long now) {
		return now < rangedUntil;
	}

	/** Returns the entities that died since the last call, each exactly once. */
	public List<Kill> poll(long now, World world) {
		if (attackedAt.isEmpty()) {
			return List.of();
		}
		List<Kill> kills = null;
		Iterator<Map.Entry<Integer, Long>> it = attackedAt.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			int id = entry.getKey();
			if (now - entry.getValue() > WINDOW_MS) {
				it.remove();
				continue;
			}
			Subject subject = world.find(id);
			if (subject == null || !subject.dying()) {
				continue;
			}
			it.remove();
			if (credited.add(id)) {
				if (kills == null) {
					kills = new ArrayList<>(2);
				}
				kills.add(new Kill(id, subject.type()));
			}
		}
		return kills == null ? List.of() : kills;
	}

	public void clear() {
		attackedAt.clear();
		credited.clear();
		rangedUntil = 0L;
	}
}
