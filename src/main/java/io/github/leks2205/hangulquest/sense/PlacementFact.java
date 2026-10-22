package io.github.leks2205.hangulquest.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One block the player put into the world. {@code around} is the 13x13x13 cube centred on it, taken
 * at the moment the placement was noticed. {@code roofed} says a solid block stands within
 * {@link SkyExposure#ROOF_RANGE} above the placed block itself (not above the player).
 */
public record PlacementFact(String block, BlockPos pos, long tick, boolean night, Vec3 playerPos,
							BlockNeighborhood around, boolean roofed) {
	public PlacementFact(String block, BlockPos pos, long tick, boolean night, Vec3 playerPos,
						 BlockNeighborhood around) {
		this(block, pos, tick, night, playerPos, around, false);
	}
}
