package io.github.leks2205.hangulquest.sense;

import io.github.leks2205.hangulquest.quest.Condition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable per-tick snapshot the quest engine evaluates. Everything is plain
 * data so goal evaluation is unit-testable without a running client.
 */
public final class Observation {
	public long now;
	public Vec3 position = Vec3.ZERO;
	public BlockPos blockPos = BlockPos.ZERO;
	public float health = 20.0F;
	public int food = 20;
	public boolean dead;
	public boolean screenOpen;
	public boolean creative;
	public boolean spectator;
	public boolean singleplayer;
	public boolean damageRecently;
	public boolean idle = true;
	public boolean rain;
	public boolean thunder;
	public String dimension = "minecraft:overworld";
	public String biome = "";
	public Set<String> biomeTags = new HashSet<>();
	public int y;
	public long clockTime;
	public String lookedBlock;
	public String lookedEntity;
	public String lookedEntityUuid;
	/** Where the looked-at block is, so NEAR_BLOCK can honour its radius. Null when unknown. */
	public BlockPos lookedBlockPos;
	public int hostilesNear;
	public double nearestHostileDistance = 999;

	public Map<String, Integer> inventory = new HashMap<>();
	public Map<String, Integer> nearbyEntities = new HashMap<>();
	/** Distance to the closest entity of each type in {@link #nearbyEntities}; transient, never persisted. */
	public Map<String, Double> nearestEntityDistance = new HashMap<>();
	public List<String> brokenBlocks = new ArrayList<>();
	public List<String> placedBlocks = new ArrayList<>();
	public List<String> usedBlocks = new ArrayList<>();
	/** Blocks used while it was night — survives the server jumping to morning after a sleep. */
	public List<String> usedBlocksAtNight = new ArrayList<>();
	/** The client is currently in bed (or in the sleep animation). */
	public boolean sleeping;
	public String sleepingBed;
	public boolean bedUseRecent;
	/** Placements/looks captured while it was still night, before a clock jump. */
	public List<String> placedBlocksAtNight = new ArrayList<>();
	public String lookedBlockAtNight;
	public List<String> consumedItemsAtNight = new ArrayList<>();
	public List<String> defeatedEntitiesAtNight = new ArrayList<>();
	public String biomeAtNight;
	public List<String> biomeTagsAtNight = new ArrayList<>();
	public List<String> usedItems = new ArrayList<>();
	public List<String> consumedItems = new ArrayList<>();
	public List<String> attackedEntities = new ArrayList<>();
	public List<String> defeatedEntities = new ArrayList<>();
	public List<String> interactedEntities = new ArrayList<>();

	/**
	 * Set by the goal evaluator while some tracked step needs a long-range look at an entity, so the
	 * sensor only does its own camera pick when it is worth it. Read on the following tick.
	 */
	public boolean farEntityLookWanted;
	/**
	 * True for the first moments after joining or leaving a world: the inventory is still being
	 * synced, so a rise in item counts is not a gain made by the player.
	 */
	public boolean inventorySettling;

	/** Creative and spectator progress counts, but must not feed learning grades. */
	public boolean assisted() {
		return creative || spectator;
	}

	/** Records one nearby entity of this type and keeps the closest distance. */
	public void addNearby(String id, double distance) {
		nearbyEntities.merge(id, 1, Integer::sum);
		nearestEntityDistance.merge(id, distance, Math::min);
	}

	public Condition.Phase phase() {
		long time = clockTime % 24000L;
		if (time >= 13000 && time < 23000) {
			return Condition.Phase.NIGHT;
		}
		if (time >= 23000 || time < 1000) {
			return Condition.Phase.DAWN;
		}
		if (time >= 12000) {
			return Condition.Phase.DUSK;
		}
		return Condition.Phase.DAY;
	}

	public int count(String itemId) {
		return inventory.getOrDefault(itemId, 0);
	}

	public boolean biomeIs(String biomeOrTag) {
		if (biomeOrTag == null) {
			return false;
		}
		if (biomeOrTag.startsWith("#")) {
			return biomeTags.contains(biomeOrTag.substring(1));
		}
		return biomeOrTag.equals(biome);
	}
}
