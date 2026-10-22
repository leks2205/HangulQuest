package io.github.leks2205.hangulquest.sense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Follows the projectiles the player threw or shot and says what each one struck. The pure logic behind
 * {@link ShotFact}: the game side only lists what flies ({@link Flying}) and what stands near it ({@link Body}).
 *
 * <p>A creature is struck when the projectile passed within reach of it and either the creature showed the hurt
 * animation (arrows, tridents) or the projectile vanished beside it (snowballs and eggs hurt almost nothing, but
 * they are gone on impact). A target block is hit when the projectile came to rest or vanished and the block
 * around its last position is powered. The end of a projectile is the moment it stops moving for
 * {@value #REST_TICKS} ticks, vanishes, or has flown for {@value #MAX_AGE_MS} ms.
 */
public final class ProjectileTracker {
	/** A body this near the path of a projectile (beyond its own half width) can be struck by it. */
	static final double REACH = 0.9;
	/** The hurt animation shows within this long after the projectile passed. */
	static final long HURT_WINDOW_MS = 500L;
	static final int REST_TICKS = 3;
	static final long MAX_AGE_MS = 20_000L;
	/** After the end, wait this long for the target block to show its power. */
	static final long SETTLE_MS = 300L;
	static final int CAP = 64;

	/** A projectile of the player in flight or at rest. {@code type} is the entity id (minecraft:arrow ...). */
	public record Flying(int id, String type, double x, double y, double z, double dx, double dy, double dz) {
		boolean resting() {
			return dx * dx + dy * dy + dz * dz < 0.0004;
		}
	}

	/** A creature near a projectile; {@code hurt} is true while it shows the hurt animation. */
	public record Body(int id, String uuid, String type, double x, double y, double z, double halfWidth, boolean hurt) {
	}

	/** Says whether a target block around a point is powered (it was hit just now). */
	public interface Blocks {
		/** The id of a powered target block within one block of the point, or null. */
		String poweredTarget(double x, double y, double z);
	}

	private static final class Track {
		final String type;
		final String weapon;
		final double startX;
		final double startY;
		final double startZ;
		final long startedAt;
		double x;
		double y;
		double z;
		double dx;
		double dy;
		double dz;
		int resting;
		long endedAt;
		long lastSeen;
		/** Bodies the projectile passed near, by uuid, with when. */
		final Map<String, Long> near = new LinkedHashMap<>();
		final Map<String, Body> nearBody = new HashMap<>();
		final Map<String, ShotFact.Hit> hits = new LinkedHashMap<>();
		String block;
		double blockDistance;

		Track(Flying shot, String weapon, long now) {
			this.type = shot.type();
			this.weapon = weapon;
			this.startX = shot.x();
			this.startY = shot.y();
			this.startZ = shot.z();
			this.startedAt = now;
			update(shot, now);
		}

		void update(Flying shot, long now) {
			x = shot.x();
			y = shot.y();
			z = shot.z();
			if (!shot.resting()) {
				dx = shot.dx();
				dy = shot.dy();
				dz = shot.dz();
			}
			lastSeen = now;
		}

		double distanceTo(double px, double py, double pz) {
			return Math.sqrt((px - startX) * (px - startX) + (py - startY) * (py - startY) + (pz - startZ) * (pz - startZ));
		}
	}

	private final Map<Integer, Track> tracks = new LinkedHashMap<>();
	private final List<Track> ended = new ArrayList<>();
	/** Projectiles already judged that still lie in the world (an arrow in the ground stays for a minute). */
	private final java.util.Set<Integer> finished = new java.util.HashSet<>();

	public boolean idle() {
		return tracks.isEmpty() && ended.isEmpty();
	}

	public void clear() {
		tracks.clear();
		ended.clear();
		finished.clear();
	}

	/**
	 * One tick. {@code held} is the item in the player's hands that goes with a projectile of each type, found by
	 * the caller (a bow or a crossbow for an arrow); it is only read when the projectile first shows.
	 */
	public List<ShotFact> update(long now, List<Flying> shots, List<Body> bodies, String heldForArrow, Blocks blocks) {
		List<ShotFact> done = new ArrayList<>();
		java.util.Set<Integer> present = new java.util.HashSet<>();
		for (Flying shot : shots) {
			present.add(shot.id());
			if (finished.contains(shot.id())) {
				continue;
			}
			Track track = tracks.get(shot.id());
			if (track == null) {
				if (tracks.size() >= CAP) {
					continue;
				}
				track = new Track(shot, weaponOf(shot.type(), heldForArrow), now);
				tracks.put(shot.id(), track);
			} else {
				track.update(shot, now);
			}
			track.resting = shot.resting() ? track.resting + 1 : 0;
			passBy(track, bodies, now);
		}
		finished.retainAll(present);
		for (Iterator<Map.Entry<Integer, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Track> entry = it.next();
			Track track = entry.getValue();
			boolean gone = !present.contains(entry.getKey());
			if (gone || track.resting >= REST_TICKS || now - track.startedAt > MAX_AGE_MS) {
				it.remove();
				if (!gone) {
					finished.add(entry.getKey());
				}
				track.endedAt = now;
				if (gone) {
					struckOnVanishing(track, now);
				}
				ended.add(track);
			}
		}
		for (Iterator<Track> it = ended.iterator(); it.hasNext(); ) {
			Track track = it.next();
			String powered = blocks == null ? null : blocks.poweredTarget(track.x + track.dx, track.y + track.dy, track.z + track.dz);
			if (powered == null && blocks != null) {
				powered = blocks.poweredTarget(track.x, track.y, track.z);
			}
			for (Body body : bodies) { // a creature that shows the hurt animation just after the end was struck
				strikeIfHurt(track, body, now);
			}
			if (powered != null) {
				track.block = powered;
				track.blockDistance = track.distanceTo(track.x, track.y, track.z);
			}
			if (powered != null || now - track.endedAt >= SETTLE_MS) {
				it.remove();
				done.add(new ShotFact(track.weapon, new ArrayList<>(track.hits.values()), track.block, track.blockDistance));
			}
		}
		return done;
	}

	private static void passBy(Track track, List<Body> bodies, long now) {
		for (Body body : bodies) {
			double reach = body.halfWidth() + REACH + Math.sqrt(track.dx * track.dx + track.dy * track.dy + track.dz * track.dz);
			double ex = body.x() - track.x;
			double ey = body.y() + 0.9 - track.y;
			double ez = body.z() - track.z;
			if (ex * ex + ey * ey + ez * ez <= reach * reach) {
				track.near.put(body.uuid(), now);
				track.nearBody.put(body.uuid(), body);
			}
			strikeIfHurt(track, body, now);
		}
	}

	private static void strikeIfHurt(Track track, Body body, long now) {
		Long passed = track.near.get(body.uuid());
		if (body.hurt() && passed != null && now - passed <= HURT_WINDOW_MS && !track.hits.containsKey(body.uuid())) {
			track.hits.put(body.uuid(), new ShotFact.Hit(body.type(), body.uuid(),
					track.distanceTo(body.x(), body.y(), body.z())));
		}
	}

	/** A snowball, an egg or an arrow that is gone struck the creature it was beside, if it struck none yet. */
	private static void struckOnVanishing(Track track, long now) {
		if (!track.hits.isEmpty() || "minecraft:trident".equals(track.type)) {
			return; // a trident flies back or stays; it is judged by the hurt animation only
		}
		Body nearest = null;
		double best = Double.MAX_VALUE;
		for (Map.Entry<String, Long> entry : track.near.entrySet()) {
			Body body = track.nearBody.get(entry.getKey());
			if (body == null || now - entry.getValue() > HURT_WINDOW_MS) {
				continue;
			}
			double ex = body.x() - track.x;
			double ey = body.y() + 0.9 - track.y;
			double ez = body.z() - track.z;
			double d = ex * ex + ey * ey + ez * ez;
			double limit = body.halfWidth() + REACH + 1.0;
			if (d <= limit * limit && d < best) {
				best = d;
				nearest = body;
			}
		}
		if (nearest != null) {
			track.hits.put(nearest.uuid(), new ShotFact.Hit(nearest.type(), nearest.uuid(),
					track.distanceTo(nearest.x(), nearest.y(), nearest.z())));
		}
	}

	/** The weapon an entity type was fired from; an arrow comes from whatever bow the player held. */
	static String weaponOf(String type, String heldForArrow) {
		return switch (type) {
			case "minecraft:trident" -> "minecraft:trident";
			case "minecraft:snowball" -> "minecraft:snowball";
			case "minecraft:egg" -> "minecraft:egg";
			default -> heldForArrow == null ? "minecraft:bow" : heldForArrow;
		};
	}
}
