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

	// Observation v2: facts for spatial and give/craft tasks. All transient, never persisted. The lists
	// start as a shared empty list so a quiet tick allocates nothing; use the add methods to fill them.
	/** Blocks the player placed this tick, each with a 7x7x7 snapshot of its surroundings. */
	public List<PlacementFact> placements = List.of();
	/** Item ids in the hands; null when empty. */
	public String mainHand;
	public String offHand;
	/** Milliseconds the hand has held the same item (0 when empty). */
	public long mainHandHeldMs;
	public long offHandHeldMs;
	public boolean inWater;
	public boolean canSeeSky;
	/** A block that stops movement within {@value SkyExposure#ROOF_RANGE} blocks above the head. */
	public boolean roofed;
	/** Below the surface heightmap of a dimension that has a sky. */
	public boolean underground;
	public List<CraftFact> crafted = List.of();
	public List<GiveFact> gave = List.of();
	public List<ContainerPutFact> putIn = List.of();
	/** Trades made on a merchant screen this tick. */
	public List<TradeFact> traded = List.of();
	/** Items the player moved out of an open container this tick. */
	public List<ContainerTakeFact> takenOut = List.of();
	/** Doors, trapdoors and fence gates the player opened this tick (the id of the block clicked). */
	public List<String> openedBlocks = List.of();
	/** The same for the ones the player closed. */
	public List<String> closedBlocks = List.of();
	/** Entity id of what the player sits in or rides (a horse, a boat, a minecart); null on foot. */
	public String vehicle;
	/** Distance to the nearest jukebox that is playing a disc; -1 when none is near or no scan was asked for. */
	public double jukeboxDistance = -1;
	/**
	 * Set by the goal evaluator while a tracked step listens for a jukebox, so the sensor only scans the
	 * surroundings for one when it is worth it. Read on the following tick, like {@link #farEntityLookWanted}.
	 */
	public boolean musicScanWanted;
	/** Set while a tracked step looks at water or lava, which the vanilla crosshair pick passes through. */
	public boolean fluidLookWanted;
	/** Position and facing of the entities counted in {@link #nearbyEntities} (items and orbs excluded). */
	public List<EntityFact> nearbyEntityPositions = List.of();

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

	public void addPlacement(PlacementFact fact) {
		placements = grow(placements);
		placements.add(fact);
	}

	public void addCrafted(CraftFact fact) {
		crafted = grow(crafted);
		crafted.add(fact);
	}

	public void addGave(GiveFact fact) {
		gave = grow(gave);
		gave.add(fact);
	}

	public void addPutIn(ContainerPutFact fact) {
		putIn = grow(putIn);
		putIn.add(fact);
	}

	public void addTraded(TradeFact fact) {
		traded = grow(traded);
		traded.add(fact);
	}

	public void addTakenOut(ContainerTakeFact fact) {
		takenOut = grow(takenOut);
		takenOut.add(fact);
	}

	public void addOpened(String block) {
		openedBlocks = grow(openedBlocks);
		openedBlocks.add(block);
	}

	public void addClosed(String block) {
		closedBlocks = grow(closedBlocks);
		closedBlocks.add(block);
	}

	public void addEntityPosition(EntityFact fact) {
		nearbyEntityPositions = grow(nearbyEntityPositions);
		nearbyEntityPositions.add(fact);
	}

	/** Swaps the shared empty list for a real one the first time something is added. */
	private static <T> List<T> grow(List<T> list) {
		return list.isEmpty() && !(list instanceof ArrayList) ? new ArrayList<>(2) : list;
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
