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
	public int hostilesNear;
	public double nearestHostileDistance = 999;

	public Map<String, Integer> inventory = new HashMap<>();
	public Map<String, Integer> nearbyEntities = new HashMap<>();
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
