package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.ProjectileTracker;
import io.github.leks2205.hangulquest.sense.ProjectileTracker.Body;
import io.github.leks2205.hangulquest.sense.ProjectileTracker.Flying;
import io.github.leks2205.hangulquest.sense.ShotFact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the sensor makes of the projectiles of the player: the creatures they struck and the target block they hit. */
class ProjectileTrackerTest {
	private static final String ARROW = "minecraft:arrow";

	private final ProjectileTracker tracker = new ProjectileTracker();
	private final List<ShotFact> facts = new ArrayList<>();
	private long now = 1000L;
	private String powered;

	private void tick(List<Flying> shots, List<Body> bodies, String held) {
		now += 50L;
		facts.addAll(tracker.update(now, shots, bodies, held, (x, y, z) -> powered));
	}

	private void quiet(int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(List.of(), List.of(), null);
		}
	}

	private static Flying arrow(int id, double x, double z) {
		return new Flying(id, ARROW, x, 65.0, z, 1.0, 0.0, 0.0);
	}

	private static Body zombie(String uuid, double x, boolean hurt) {
		return new Body(5, uuid, "minecraft:zombie", x, 64.0, 0.0, 0.3, hurt);
	}

	/** The arrow flies along x and passes the body at x = 10; the body shows the hurt animation after it. */
	private void flight(int id, String type, List<Body> bodiesAlong, boolean vanishes) {
		for (int x = 0; x <= 12; x++) {
			tick(List.of(new Flying(id, type, x, 65.0, 0.0, 1.0, 0.0, 0.0)), bodiesAlong, "minecraft:bow");
		}
		if (vanishes) {
			quiet(8);
		} else {
			for (int i = 0; i < 6; i++) { // stuck in a wall
				tick(List.of(new Flying(id, type, 12.0, 65.0, 0.0, 0.0, 0.0, 0.0)), bodiesAlong, "minecraft:bow");
			}
			quiet(8);
		}
	}

	@Test
	void anArrowThatHurtsACreatureHitsIt() {
		List<Body> miss = List.of(zombie("z1", 10.0, false));
		for (int x = 0; x <= 8; x++) {
			tick(List.of(arrow(1, x, 0.0)), miss, "minecraft:bow");
		}
		List<Body> hurt = List.of(zombie("z1", 10.0, true));
		for (int x = 9; x <= 11; x++) {
			tick(List.of(arrow(1, x, 0.0)), hurt, "minecraft:bow");
		}
		quiet(10);
		assertEquals(1, facts.size());
		ShotFact fact = facts.get(0);
		assertEquals("minecraft:bow", fact.weapon());
		assertEquals(1, fact.hits().size());
		assertEquals("minecraft:zombie", fact.hits().get(0).entity());
		assertTrue(fact.hits().get(0).distance() > 8.0, "measured from the launch: " + fact.hits().get(0).distance());
		assertNull(fact.block());
	}

	@Test
	void anArrowThatFliesPastWithoutAHurtCreatureHitsNothing() {
		flight(1, ARROW, List.of(zombie("z1", 10.0, false)), false);
		assertEquals(1, facts.size());
		assertEquals(List.of(), facts.get(0).hits());
	}

	@Test
	void aHurtCreatureThatWasNowhereNearIsNotHit() {
		flight(1, ARROW, List.of(new Body(9, "far", "minecraft:zombie", 10.0, 64.0, 30.0, 0.3, true)), false);
		assertEquals(List.of(), facts.get(0).hits());
	}

	@Test
	void aSnowballThatVanishesBesideACreatureHitsItWithoutAHurtAnimation() {
		List<Body> zombie = List.of(zombie("z1", 10.0, false));
		for (int x = 0; x <= 10; x++) {
			tick(List.of(new Flying(2, "minecraft:snowball", x, 65.0, 0.0, 1.0, 0.0, 0.0)), zombie, null);
		}
		quiet(8); // gone
		assertEquals(1, facts.size());
		assertEquals("minecraft:snowball", facts.get(0).weapon());
		assertEquals(1, facts.get(0).hits().size());
	}

	@Test
	void aSnowballThatVanishesInTheOpenHitsNothing() {
		for (int x = 0; x <= 10; x++) {
			tick(List.of(new Flying(2, "minecraft:snowball", x, 65.0, 0.0, 1.0, 0.0, 0.0)), List.of(zombie("z1", 40.0, false)), null);
		}
		quiet(8);
		assertEquals(List.of(), facts.get(0).hits());
	}

	@Test
	void aTridentIsJudgedByTheHurtAnimationOnly() {
		flight(3, "minecraft:trident", List.of(zombie("z1", 10.0, false)), true);
		assertEquals("minecraft:trident", facts.get(0).weapon());
		assertEquals(List.of(), facts.get(0).hits(), "a trident that comes back is no hit by itself");
	}

	@Test
	void anArrowThatStopsInAPoweredTargetHitsTheBlock() {
		powered = "minecraft:target";
		for (int x = 0; x <= 10; x++) {
			tick(List.of(arrow(1, x, 0.0)), List.of(), "minecraft:bow");
		}
		for (int i = 0; i < 6; i++) {
			tick(List.of(new Flying(1, ARROW, 10.0, 65.0, 0.0, 0.0, 0.0, 0.0)), List.of(), "minecraft:bow");
		}
		quiet(3);
		assertEquals(1, facts.size());
		assertEquals("minecraft:target", facts.get(0).block());
		assertTrue(facts.get(0).blockDistance() >= 9.0, "" + facts.get(0).blockDistance());
	}

	@Test
	void anArrowThatStopsInAnUnpoweredBlockHitsNoTarget() {
		powered = null;
		flight(1, ARROW, List.of(), false);
		assertEquals(1, facts.size());
		assertNull(facts.get(0).block());
	}

	@Test
	void oneArrowThroughTwoCreaturesStrikesBoth() {
		List<Body> miss = List.of(zombie("a", 8.0, false), zombie("b", 10.0, false));
		for (int x = 0; x <= 7; x++) {
			tick(List.of(arrow(1, x, 0.0)), miss, "minecraft:crossbow");
		}
		for (int x = 8; x <= 12; x++) {
			tick(List.of(arrow(1, x, 0.0)), List.of(zombie("a", 8.0, x >= 8), zombie("b", 10.0, x >= 10)), "minecraft:crossbow");
		}
		quiet(10);
		assertEquals(1, facts.size());
		assertEquals("minecraft:crossbow", facts.get(0).weapon());
		assertEquals(2, facts.get(0).hits().size());
	}

	@Test
	void theWeaponIsWhatWasHeldWhenTheArrowFirstShowed() {
		tick(List.of(arrow(1, 0.0, 0.0)), List.of(), "minecraft:crossbow");
		tick(List.of(arrow(1, 1.0, 0.0)), List.of(), "minecraft:bow"); // the hand changed in flight
		quiet(10);
		assertEquals("minecraft:crossbow", facts.get(0).weapon());
	}

	@Test
	void everyShotIsReportedOnce() {
		flight(1, ARROW, List.of(), false);
		flight(2, ARROW, List.of(), false);
		assertEquals(2, facts.size());
		assertTrue(tracker.idle());
	}
}
