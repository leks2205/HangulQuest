package io.github.leks2205.hangulquest.sense;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Where a nearby entity is and which way it looks. Yaw is in degrees as the game stores it
 * (0 = south, 90 = west); items and experience orbs are left out. {@code baby} is the synced "young"
 * flag of an animal, which a breeding goal waits for.
 */
public record EntityFact(String type, UUID uuid, double x, double y, double z, float yaw, float pitch,
						 boolean baby) {
	public EntityFact(String type, UUID uuid, double x, double y, double z, float yaw, float pitch) {
		this(type, uuid, x, y, z, yaw, pitch, false);
	}

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	/** Unit vector the entity looks along, the same convention the game uses for view vectors. */
	public Vec3 facing() {
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		double flat = Math.cos(pitchRad);
		return new Vec3(-Math.sin(yawRad) * flat, -Math.sin(pitchRad), Math.cos(yawRad) * flat);
	}
}
