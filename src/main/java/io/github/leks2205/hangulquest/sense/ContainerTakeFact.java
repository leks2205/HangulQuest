package io.github.leks2205.hangulquest.sense;

import net.minecraft.core.BlockPos;

/**
 * Items the player moved out of an open container. {@code block} and {@code pos} describe the block
 * that was clicked to open it and are null when that is unknown (a chest minecart, say).
 */
public record ContainerTakeFact(String item, int count, String block, BlockPos pos) {
}
