package io.github.leks2205.hangulquest.sense;

import java.util.List;

/**
 * One projectile of the player, seen to its end: what was held when it left ({@code weapon}, an item id such as
 * {@code minecraft:bow}), the creatures it struck on its way ({@code hits}, each once) and the target block it
 * powered, if any. Emitted once, when the projectile has landed or vanished.
 *
 * @param blockDistance blocks between the launch and the powered block, 0 when no block was hit
 */
public record ShotFact(String weapon, List<Hit> hits, String block, double blockDistance) {
	public ShotFact {
		hits = hits == null ? List.of() : List.copyOf(hits);
	}

	/** A creature struck: its type, its uuid (the same creature hit twice counts once) and the distance from the launch. */
	public record Hit(String entity, String uuid, double distance) {
	}
}
