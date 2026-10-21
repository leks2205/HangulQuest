package io.github.leks2205.hangulquest.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One block the player put into the world. {@code around} is the 7x7x7 cube centred on it, taken
 * at the moment the placement was noticed.
 */
public record PlacementFact(String block, BlockPos pos, long tick, boolean night, Vec3 playerPos,
							BlockNeighborhood around) {
}
