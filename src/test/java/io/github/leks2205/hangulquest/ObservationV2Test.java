package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.sense.BlockNeighborhood;
import io.github.leks2205.hangulquest.sense.CraftFact;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.HeldTracker;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;
import io.github.leks2205.hangulquest.sense.SkyExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationV2Test {
	@Test
	void aQuietObservationSharesOneEmptyListPerFact() {
		Observation a = new Observation();
		Observation b = new Observation();
		assertSame(a.placements, b.placements);
		assertSame(a.crafted, b.crafted);
		assertSame(a.gave, b.gave);
		assertSame(a.putIn, b.putIn);
		assertSame(a.nearbyEntityPositions, b.nearbyEntityPositions);
		assertTrue(a.placements.isEmpty());
		assertNull(a.mainHand);
		assertFalse(a.inWater || a.canSeeSky || a.roofed || a.underground);
	}

	@Test
	void addingAFactNeverLeaksIntoAnotherObservation() {
		Observation a = new Observation();
		a.addCrafted(new CraftFact("minecraft:torch", 4, "crafting_table"));
		a.addCrafted(new CraftFact("minecraft:stick", 4, "inventory2x2"));
		a.addPlacement(new PlacementFact("minecraft:torch", BlockPos.ZERO, 7L, true, Vec3.ZERO,
				BlockNeighborhood.capture(0, 0, 0, (x, y, z) -> null)));

		Observation b = new Observation();
		assertEquals(2, a.crafted.size());
		assertEquals("minecraft:stick", a.crafted.get(1).item());
		assertEquals(1, a.placements.size());
		assertTrue(b.crafted.isEmpty());
		assertTrue(b.placements.isEmpty());
	}

	@Test
	void anEntityFactLooksAlongItsYawAndPitch() {
		UUID id = UUID.randomUUID();
		// Yaw 0 faces south (+z), 90 faces west (-x), -90 faces east (+x).
		Vec3 south = new EntityFact("minecraft:villager", id, 1, 2, 3, 0F, 0F).facing();
		Vec3 west = new EntityFact("minecraft:villager", id, 1, 2, 3, 90F, 0F).facing();
		Vec3 up = new EntityFact("minecraft:villager", id, 1, 2, 3, 0F, -90F).facing();
		assertEquals(1.0, south.z, 1e-9);
		assertEquals(-1.0, west.x, 1e-9);
		assertEquals(1.0, up.y, 1e-9);
		assertEquals(new Vec3(1, 2, 3), new EntityFact("x", id, 1, 2, 3, 0F, 0F).position());
	}

	@Test
	void aHandTimerRestartsWhenTheItemChangesNotWhenTheCountDoes() {
		HeldTracker held = new HeldTracker();
		held.update("minecraft:torch", null, 1_000L);
		held.update("minecraft:torch", null, 4_000L);
		assertEquals(3_000L, held.mainMs(4_000L));
		assertEquals(0L, held.offMs(4_000L), "an empty hand has held nothing");

		held.update("minecraft:stone", "minecraft:shield", 5_000L);
		assertEquals(0L, held.mainMs(5_000L));
		assertEquals(500L, held.offMs(5_500L));
		held.update("minecraft:stone", "minecraft:shield", 6_000L);
		assertEquals(1_000L, held.mainMs(6_000L));

		held.update(null, "minecraft:shield", 7_000L);
		assertEquals(0L, held.mainMs(7_000L));
		assertEquals(2_000L, held.offMs(7_000L));
	}

	@Test
	void roofedLooksSixBlocksUpAndNoFurther() {
		assertTrue(SkyExposure.roofed(64, y -> y == 70));
		assertFalse(SkyExposure.roofed(64, y -> y == 71));
		assertFalse(SkyExposure.roofed(64, y -> y == 64), "the head's own block is not a roof");
		assertTrue(SkyExposure.roofed(64, y -> y == 65));
		assertFalse(SkyExposure.roofed(64, y -> false));
	}

	@Test
	void undergroundMeansBelowTheSurfaceInADimensionWithASky() {
		// A heightmap value of 70 means the topmost blocking block is at y 69.
		assertTrue(SkyExposure.underground(true, 40, 70));
		assertFalse(SkyExposure.underground(true, 70, 70), "standing on the surface");
		assertFalse(SkyExposure.underground(true, 69, 70), "level with the top block");
		assertTrue(SkyExposure.underground(true, 68, 70), "head below the top block");
		assertFalse(SkyExposure.underground(false, 10, 128), "no sky, no surface (the Nether)");
	}

	@Test
	void theSkyIsSeenFromTheEyeAtOrAboveTheSurface() {
		assertTrue(SkyExposure.canSeeSky(70, 70));
		assertFalse(SkyExposure.canSeeSky(69, 70));
	}
}
