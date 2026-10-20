package io.github.leks2205.hangulquest.sense;

import io.github.leks2205.hangulquest.quest.Condition;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns client events plus the per-tick state of the local player into an
 * {@link Observation}. Nothing here talks to the server.
 */
public final class EnvironmentSensor {
	private static final List<TagKey<Biome>> BIOME_TAGS = List.of(
			tag("is_forest"), tag("is_river"), tag("is_ocean"), tag("is_mountain"), tag("is_beach"),
			tag("is_taiga"), tag("is_jungle"), tag("is_desert"), tag("is_badlands"),
			tag("is_savanna"), tag("is_swamp"), tag("is_hill"), tag("is_nether"), tag("is_end")
	);

	private final Minecraft mc = Minecraft.getInstance();
	private final List<String> broken = new ArrayList<>();
	private final List<String> usedBlocks = new ArrayList<>();
	private final List<String> usedBlocksAtNight = new ArrayList<>();
	private final List<String> usedItems = new ArrayList<>();
	private final List<String> consumed = new ArrayList<>();
	private final List<String> attacked = new ArrayList<>();
	private final List<String> defeated = new ArrayList<>();
	private final List<String> interacted = new ArrayList<>();
	private final Map<UUID, Long> attackedAt = new HashMap<>();
	private final Set<UUID> deathSeen = new HashSet<>();
	private Map<String, Integer> lastInventory = new HashMap<>();
	private long lastDamageAt;
	private float lastHealth = 20.0F;
	private final ConsumptionTracker consumption = new ConsumptionTracker();
	private boolean wasSleeping;
	private long lastBedUseAt;
	private boolean pendingPlaceNight;
	private boolean wasUseDown;
	private String lookedAtNight;
	private String lastBiomeAtNight;
	private List<String> lastBiomeTagsAtNight = List.of();
	private long lastBiomeAtNightAt;
	private long lookedAtNightAt;
	private Map<BlockPos, net.minecraft.world.level.block.state.BlockState> pendingPlaceRegion = Map.of();
	private long tick;

	public void register() {
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) ->
				broken.add(blockId(state)));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockState state = level.getBlockState(hitResult.getBlockPos());
				String usedBlock = blockId(state);
				usedBlocks.add(usedBlock);
				if (usedBlock != null && usedBlock.endsWith("_bed")) {
					lastBedUseAt = System.currentTimeMillis();
				}
				long dayTime = level.getDefaultClockTime() % 24000L;
				if (dayTime >= 13000L && dayTime <= 23000L) {
					usedBlocksAtNight.add(usedBlock);
				}
				// Snapshot the neighbourhood: the placed block usually appears on the clicked face,
				// not at the clicked position (campfire, bed, door, torch ...).
				Map<BlockPos, net.minecraft.world.level.block.state.BlockState> region = new HashMap<>();
				BlockPos center = hitResult.getBlockPos();
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
					BlockPos key = pos.immutable();
					region.put(key, level.getBlockState(key));
				}
				pendingPlaceRegion = region;
				pendingPlaceNight = isNight(level);
				ItemStack stack = player.getItemInHand(hand);
				if (!stack.isEmpty()) {
					// only a use (not placement) can consume an item
				}
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) {
				ItemStack stack = player.getItemInHand(hand);
				if (!stack.isEmpty()) {
					String usedItem = itemId(stack);
					usedItems.add(usedItem);
					consumption.used(usedItem, System.currentTimeMillis());
				}
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && entity != null) {
				attackedAt.put(entity.getUUID(), System.currentTimeMillis());
				attacked.add(entityId(entity));
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && entity != null) {
				interacted.add(entityId(entity));
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tick++;
		});
	}

	public Observation tick() {
		Observation obs = new Observation();
		obs.now = System.currentTimeMillis();
		obs.brokenBlocks = new ArrayList<>(broken);
		obs.placedBlocks = new ArrayList<>();
		obs.placedBlocksAtNight = new ArrayList<>();
		obs.consumedItemsAtNight = new ArrayList<>();
		obs.defeatedEntitiesAtNight = new ArrayList<>();
		obs.usedBlocks = new ArrayList<>(usedBlocks);
		obs.usedBlocksAtNight = new ArrayList<>(usedBlocksAtNight);
		obs.usedItems = new ArrayList<>(usedItems);
		obs.consumedItems = new ArrayList<>(consumed);
		obs.attackedEntities = new ArrayList<>(attacked);
		obs.defeatedEntities = new ArrayList<>(defeated);
		obs.interactedEntities = new ArrayList<>(interacted);
		broken.clear();
		usedBlocks.clear();
		usedBlocksAtNight.clear();
		usedItems.clear();
		consumed.clear();
		attacked.clear();
		defeated.clear();
		interacted.clear();

		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			obs.dead = true;
			return obs;
		}

		obs.position = player.position();
		obs.blockPos = player.blockPosition();
		obs.health = player.getHealth();
		obs.food = player.getFoodData().getFoodLevel();
		obs.dead = player.isDeadOrDying() || player.getHealth() <= 0.0F;
		obs.screenOpen = mc.gui.screen() != null;
		obs.creative = player.getAbilities().instabuild;
		obs.spectator = player.isSpectator();
		obs.singleplayer = mc.hasSingleplayerServer();
		obs.dimension = level.dimension().identifier().toString();
		obs.y = obs.blockPos.getY();
		obs.clockTime = level.getDefaultClockTime();
		obs.rain = level.isRaining();
		obs.thunder = level.isThundering();
		obs.idle = player.isSpectator() || isIdle();

		Holder<Biome> biome = level.getBiome(obs.blockPos);
		obs.biome = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");
		for (TagKey<Biome> tag : BIOME_TAGS) {
			if (biome.is(tag)) {
				obs.biomeTags.add(tag.location().toString());
			}
		}
		if (obs.phase() == Condition.Phase.NIGHT) {
			lastBiomeAtNight = obs.biome;
			lastBiomeTagsAtNight = new ArrayList<>(obs.biomeTags);
			lastBiomeAtNightAt = obs.now;
		}

		HitResult hit = mc.hitResult;
		boolean useDown = mc.options.keyUse.isDown();
		if (useDown && !wasUseDown && hit instanceof BlockHitResult clicked) {
			String clickedBlock = blockId(level.getBlockState(clicked.getBlockPos()));
			if (clickedBlock != null) {
				usedBlocks.add(clickedBlock);
				if (isNight(level)) {
					usedBlocksAtNight.add(clickedBlock);
				}
			}
		}
		wasUseDown = useDown;
		if (hit instanceof BlockHitResult blockHit) {
			obs.lookedBlock = blockId(level.getBlockState(blockHit.getBlockPos()));
		} else if (hit instanceof EntityHitResult entityHit) {
			Entity entity = entityHit.getEntity();
			obs.lookedEntity = entityId(entity);
			obs.lookedEntityUuid = entity.getUUID().toString();
		} else if (mc.crosshairPickEntity != null) {
			obs.lookedEntity = entityId(mc.crosshairPickEntity);
			obs.lookedEntityUuid = mc.crosshairPickEntity.getUUID().toString();
		}

		scanEntities(obs, player, level);
		updateInventory(obs, player);
		boolean asleep = player.isSleeping() || player.getSleepTimer() > 0
				|| player.getSleepingPos().isPresent() || player.getPose() == Pose.SLEEPING;
		obs.sleeping = asleep;
		obs.bedUseRecent = obs.now - lastBedUseAt < 3000L;
		if (asleep) {
			obs.sleepingBed = player.getSleepingPos()
					.map(pos -> blockId(level.getBlockState(pos)))
					.orElse("minecraft:red_bed");
		}
		detectSleep(obs, player, level);

		if (obs.health < lastHealth - 0.05F) {
			lastDamageAt = obs.now;
		}
		lastHealth = obs.health;
		obs.damageRecently = obs.now - lastDamageAt < 4000L;

		if (!pendingPlaceRegion.isEmpty()) {
			for (var entry : pendingPlaceRegion.entrySet()) {
				BlockState before = entry.getValue();
				BlockState now = level.getBlockState(entry.getKey());
				if (!now.isAir() && !now.getBlock().equals(before.getBlock())) {
					// Only this tick's observation: the next sense() must not count it again.
					String placedBlock = blockId(now);
					obs.placedBlocks.add(placedBlock);
					if (pendingPlaceNight) {
						obs.placedBlocksAtNight.add(placedBlock);
					}
				}
			}
			pendingPlaceRegion = Map.of();
			pendingPlaceNight = false;
		}

		obs.biomeTagsAtNight = new ArrayList<>();
		if (lastBiomeAtNightAt > 0 && obs.now - lastBiomeAtNightAt < 3000L) {
			obs.biomeAtNight = lastBiomeAtNight;
			obs.biomeTagsAtNight.addAll(lastBiomeTagsAtNight);
		}
		obs.lookedBlockAtNight = null;
		if (obs.lookedBlock != null && obs.now - lookedAtNightAt < 3000L) {
			if (obs.lookedBlock.equals(lookedAtNight)) {
				obs.lookedBlockAtNight = lookedAtNight;
			}
		}
		if (obs.lookedBlock != null && obs.phase() == Condition.Phase.NIGHT) {
			lookedAtNight = obs.lookedBlock;
			lookedAtNightAt = obs.now;
			obs.lookedBlockAtNight = lookedAtNight;
		}
		return obs;
	}

	private void scanEntities(Observation obs, LocalPlayer player, ClientLevel level) {
		AABB hostileBox = new AABB(player.blockPosition()).inflate(12.0);
		AABB nearBox = new AABB(player.blockPosition()).inflate(24.0);
		for (Entity entity : level.getEntities(player, nearBox, candidate -> true)) {
			String id = entityId(entity);
			obs.nearbyEntities.merge(id, 1, Integer::sum);
			if (entity instanceof Monster && entity.isAlive()) {
				double distance = Math.sqrt(entity.distanceToSqr(player));
				if (distance <= 12.0) {
					obs.hostilesNear++;
					obs.nearestHostileDistance = Math.min(obs.nearestHostileDistance, distance);
				}
			}
		}
		AABB deathBox = new AABB(player.blockPosition()).inflate(48.0);
		for (Entity entity : level.getEntities(player, deathBox, candidate -> candidate instanceof LivingEntity)) {
			UUID uuid = entity.getUUID();
			if (entity instanceof LivingEntity living && living.isDeadOrDying()
					&& attackedAt.containsKey(uuid) && !deathSeen.contains(uuid)) {
				long attackedTime = attackedAt.getOrDefault(uuid, 0L);
				if (obs.now - attackedTime < 5000L) {
					String defeatedId = entityId(entity);
					obs.defeatedEntities.add(defeatedId);
					if (isNight(level)) {
						obs.defeatedEntitiesAtNight.add(defeatedId);
					}
					deathSeen.add(uuid);
				}
			}
		}
		if (deathSeen.size() > 128) {
			deathSeen.clear();
		}
		if (attackedAt.size() > 256) {
			attackedAt.clear();
		}
	}

	private void updateInventory(Observation obs, LocalPlayer player) {
		Map<String, Integer> snapshot = snapshot(player.getInventory());
		obs.inventory = snapshot;
		for (String item : consumption.update(lastInventory, snapshot, obs.now)) {
			consumed.add(item);
			obs.consumedItems.add(item);
			if (isNight(mc.level)) {
				obs.consumedItemsAtNight.add(item);
			}
		}
		lastInventory = snapshot;
	}

	/**
	 * Sleeping itself is proof of using a bed, even if the use event was handled server-side.
	 * Entering a bed is only possible at night or during a thunderstorm, and the server may
	 * already have jumped the clock to morning, so this always counts as a night-time use.
	 */
	private static boolean isNight(Level level) {
		long time = level.getDefaultClockTime() % 24000L;
		return time >= 13000L && time <= 23000L;
	}

	private void detectSleep(Observation obs, LocalPlayer player, ClientLevel level) {
		boolean sleeping = player.isSleeping() || player.getSleepTimer() > 0
				|| player.getSleepingPos().isPresent();
		if (sleeping && !wasSleeping) {
			String bed = player.getSleepingPos()
					.map(pos -> blockId(level.getBlockState(pos)))
					.orElse("minecraft:red_bed");
			obs.usedBlocks.add(bed);
			obs.usedBlocksAtNight.add(bed);
		}
		wasSleeping = sleeping;
	}

	private static Map<String, Integer> snapshot(Inventory inventory) {
		Map<String, Integer> map = new HashMap<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			map.merge(itemId(stack), stack.getCount(), Integer::sum);
		}
		return map;
	}

	private boolean isIdle() {
		return !mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()
				&& !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()
				&& !mc.options.keyJump.isDown() && !mc.options.keyAttack.isDown()
				&& !mc.options.keyUse.isDown();
	}

	private static String blockId(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id == null ? "" : id.toString();
	}

	private static String itemId(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id == null ? "" : id.toString();
	}

	private static String entityId(Entity entity) {
		Identifier id = net.minecraft.world.entity.EntityType.getKey(entity.getType());
		return id == null ? "" : id.toString();
	}

	private static TagKey<Biome> tag(String path) {
		return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	public long ticks() {
		return tick;
	}
}
