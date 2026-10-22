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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

	/** Ticks after a world change during which the inventory is still being synced. */
	private static final int SETTLE_TICKS = 20;
	/** Radius around the player in which the player's own arrows and tridents are followed. */
	private static final double PROJECTILE_SCAN = 48.0;

	// Ids never change, so build each string once per registry object instead of once per tick.
	private static final Map<Item, String> ITEM_IDS = new IdentityHashMap<>();
	private static final Map<Block, String> BLOCK_IDS = new IdentityHashMap<>();
	private static final Map<EntityType<?>, String> ENTITY_IDS = new IdentityHashMap<>();
	private static final List<String> BIOME_TAG_IDS = new ArrayList<>();

	static {
		for (TagKey<Biome> tag : BIOME_TAGS) {
			BIOME_TAG_IDS.add(tag.location().toString());
		}
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final EventBuffer events = new EventBuffer();
	private final DeathTracker deaths = new DeathTracker();
	private final ProjectileTracker shotTracker = new ProjectileTracker();
	private final DeathTracker.World deathWorld = this::findForDeath;

	/** A young item entity near the player, held until every creature near them has been scanned. */
	private record SeenItem(int id, String item, int count, double distance) {
	}
	private final Map<String, Integer> scratchInventory = new HashMap<>();
	private Map<String, Integer> lastInventory = new HashMap<>();
	private Map<String, String> lastWorn = Map.of();
	private long lastDamageAt;
	private float lastHealth = 20.0F;
	private final ConsumptionTracker consumption = new ConsumptionTracker();
	private boolean wasSleeping;
	private long lastBedUseAt;
	private boolean pendingPlaceNight;
	private String lookedAtNight;
	private String lastBiomeAtNight;
	private List<String> lastBiomeTagsAtNight = List.of();
	private long lastBiomeAtNightAt;
	private long lookedAtNightAt;
	private Map<BlockPos, String> pendingPlaceRegion = Map.of();
	private BlockPos pendingPlaceClicked = BlockPos.ZERO;
	private BlockPos pendingPlaceFace = BlockPos.ZERO;
	private final ScreenSensor screens = new ScreenSensor();
	private final GiveTracker gives = new GiveTracker();
	private final HeldTracker held = new HeldTracker();
	private final ToggleTracker toggles = new ToggleTracker();
	private final ToggleTracker lights = new ToggleTracker();
	private long musicAt;
	private double musicDistance = -1;
	private double bellDistance = -1;
	private long nearBlockAt;
	private BlockPos nearBlockFrom;
	private Map<String, Integer> nearBlockWanted = Map.of();
	private Map<String, Double> nearBlockFound = Map.of();
	private final List<GiveTracker.Target> dropTargets = new ArrayList<>();
	private final List<SeenItem> seenItems = new ArrayList<>();
	/** The item entities in sight and the villagers near, while an item lies in front of a villager. */
	private final java.util.Set<Integer> itemsInSight = new java.util.HashSet<>();
	private final java.util.Set<java.util.UUID> pickersNear = new java.util.HashSet<>();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private BlockPos skyPos;
	private long skyAt;
	private boolean canSeeSky;
	private boolean roofed;
	private boolean underground;
	private long tick;
	private int settleTicks = SETTLE_TICKS;
	/** The previous observation: the evaluator flags on it what the sensor should work out this tick. */
	private Observation previous;
	private Identifier dimensionKey;
	private String dimensionId = "";
	private Holder<Biome> biomeHolder;
	private String biomeId = "";
	private List<String> biomeTagIds = List.of();

	/**
	 * Forgets everything seen so far. Called when leaving a world so the first tick in the next one
	 * does not diff against the old world's inventory or credit its pending events.
	 */
	public void reset() {
		events.clear();
		deaths.clear();
		shotTracker.clear();
		consumption.clear();
		lastInventory = new HashMap<>();
		scratchInventory.clear();
		wasSleeping = false;
		lastBedUseAt = 0L;
		pendingPlaceRegion = Map.of();
		pendingPlaceNight = false;
		screens.reset();
		gives.clear();
		held.clear();
		toggles.clear();
		lights.clear();
		musicAt = 0L;
		musicDistance = -1;
		bellDistance = -1;
		nearBlockAt = 0L;
		nearBlockFrom = null;
		nearBlockWanted = Map.of();
		nearBlockFound = Map.of();
		skyPos = null;
		lookedAtNight = null;
		lookedAtNightAt = 0L;
		lastBiomeAtNight = null;
		lastBiomeTagsAtNight = List.of();
		lastBiomeAtNightAt = 0L;
		lastHealth = 20.0F;
		lastDamageAt = 0L;
		previous = null;
		settleTicks = SETTLE_TICKS;
	}

	public void register() {
		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) ->
				events.blockBroken(blockId(state)));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockState state = level.getBlockState(hitResult.getBlockPos());
				String usedBlock = blockId(state);
				// The only source of block-use events (the callback fires once per hand).
				events.blockUsed(usedBlock, isNight(level), hand == InteractionHand.MAIN_HAND);
				toggles.clicked(hitResult.getBlockPos(), usedBlock, openState(state), System.currentTimeMillis());
				lights.clicked(hitResult.getBlockPos(), usedBlock, litState(state), System.currentTimeMillis());
				if (usedBlock.endsWith("_bed")) {
					lastBedUseAt = System.currentTimeMillis();
				}
				// Snapshot the neighbourhood: the placed block usually appears on the clicked face,
				// not at the clicked position (campfire, bed, door, torch ...).
				Map<BlockPos, String> region = new HashMap<>();
				BlockPos center = hitResult.getBlockPos();
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
					BlockPos key = pos.immutable();
					region.put(key, blockId(level.getBlockState(key)));
				}
				pendingPlaceRegion = region;
				pendingPlaceClicked = center;
				pendingPlaceFace = center.relative(hitResult.getDirection());
				screens.blockUsed(center, usedBlock, System.currentTimeMillis());
				pendingPlaceNight = isNight(level);
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) {
				ItemStack stack = player.getItemInHand(hand);
				if (!stack.isEmpty()) {
					String usedItem = itemId(stack);
					long now = System.currentTimeMillis();
					events.itemUsed(usedItem);
					consumption.used(usedItem, now);
					if (isRangedWeapon(usedItem)) {
						deaths.rangedWeaponUsed(now);
					}
				}
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && entity != null) {
				Entity target = resolve(entity);
				deaths.attacked(target.getId(), System.currentTimeMillis());
				events.entityAttacked(entityId(target));
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && entity != null) {
				Entity target = resolve(entity);
				String type = entityId(target);
				events.entityInteracted(type, target.getUUID());
				long now = System.currentTimeMillis();
				screens.entityUsed(type, target.getUUID(), now);
				ItemStack heldStack = player.getItemInHand(hand);
				if (!heldStack.isEmpty()) {
					boolean food = target instanceof Animal animal && animal.isFood(heldStack);
					gives.used(itemId(heldStack), type, target.getUUID(), food, now);
				}
			}
			return InteractionResult.PASS;
		});
		screens.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tick++;
		});
	}

	public Observation tick() {
		Observation obs = new Observation();
		obs.now = System.currentTimeMillis();
		events.drainInto(obs);
		boolean wantFarLook = previous != null && previous.farEntityLookWanted;
		boolean wantMusic = previous != null && previous.musicScanWanted;
		boolean wantShots = previous != null && previous.shotScanWanted || !shotTracker.idle();
		boolean wantFluid = previous != null && previous.fluidLookWanted;
		Map<String, Integer> wantNear = previous == null ? Map.of() : previous.nearBlocksWanted;
		previous = obs;

		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			obs.dead = true;
			return obs;
		}
		obs.inventorySettling = settleTicks > 0;
		if (settleTicks > 0) {
			settleTicks--;
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
		obs.dimension = dimensionId(level);
		obs.y = obs.blockPos.getY();
		obs.clockTime = level.getDefaultClockTime();
		obs.rain = level.isRaining();
		obs.thunder = level.isThundering();
		obs.idle = player.isSpectator() || isIdle();
		readHands(obs, player);
		readArmor(obs, player);
		readSky(obs, player, level);
		obs.yaw = player.getYRot();
		Entity vehicle = player.getVehicle();
		obs.vehicle = vehicle == null ? null : entityId(resolve(vehicle));
		if (!toggles.idle()) {
			toggles.poll(pos -> openState(level.getBlockState(pos)), obs.now, obs::addOpened, obs::addClosed);
		}
		if (!lights.idle()) {
			lights.poll(pos -> litState(level.getBlockState(pos)), obs.now, obs::addLit, obs::addSnuffed);
		}
		if (wantMusic) {
			readMusic(obs, player, level);
		}
		readNearBlocks(obs, level, wantNear);

		readBiome(obs, level);
		if (obs.phase() == Condition.Phase.NIGHT) {
			lastBiomeAtNight = obs.biome;
			lastBiomeTagsAtNight = biomeTagIds;
			lastBiomeAtNightAt = obs.now;
		}

		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult blockHit) {
			obs.lookedBlockPos = blockHit.getBlockPos();
			obs.lookedBlock = blockId(level.getBlockState(obs.lookedBlockPos));
		} else if (hit instanceof EntityHitResult entityHit) {
			setLookedEntity(obs, resolve(entityHit.getEntity()));
		} else if (mc.crosshairPickEntity != null) {
			setLookedEntity(obs, resolve(mc.crosshairPickEntity));
		}
		if (wantFluid) {
			lookThroughFluid(obs, player, level);
		}
		if (obs.lookedEntity == null && wantFarLook && !obs.spectator) {
			Entity far = farPick(player, level);
			if (far != null) {
				setLookedEntity(obs, far);
			}
		}

		boolean night = isNight(level);
		scanEntities(obs, player, level, night);
		if (deaths.rangedActive(obs.now)) {
			followProjectiles(player, level, obs.now);
		}
		if (wantShots) {
			followShots(obs, player, level);
		}
		updateInventory(obs, player, night);
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
			List<PlacementDiff.Placed> changed =
					PlacementDiff.find(pendingPlaceRegion, pos -> blockId(level.getBlockState(pos)));
			for (PlacementDiff.Placed placed : changed) {
				// Only this tick's observation: the next sense() must not count it again.
				obs.placedBlocks.add(placed.block());
				if (pendingPlaceNight) {
					obs.placedBlocksAtNight.add(placed.block());
				}
			}
			// The 13x13x13 snapshot is only taken here, once per placement.
			for (PlacementDiff.Placed placed : PlacementDiff.primary(changed, pendingPlaceFace, pendingPlaceClicked)) {
				obs.addPlacement(new PlacementFact(placed.block(), placed.pos(), tick, pendingPlaceNight,
						obs.position, neighborhood(level, placed.pos()), roofedAbove(level, placed.pos())));
			}
			skyPos = null; // a new block may have made a roof
			pendingPlaceRegion = Map.of();
			pendingPlaceNight = false;
		}
		screens.tick(obs, player);

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

	/** The armour slots, kept as the same map object while nothing is put on or taken off. */
	private void readArmor(Observation obs, LocalPlayer player) {
		Map<String, String> now = WornItems.of(slotItem(player, EquipmentSlot.HEAD),
				slotItem(player, EquipmentSlot.CHEST), slotItem(player, EquipmentSlot.LEGS),
				slotItem(player, EquipmentSlot.FEET));
		if (!now.equals(lastWorn)) {
			lastWorn = now;
		}
		obs.worn = lastWorn;
	}

	private static String slotItem(LocalPlayer player, EquipmentSlot slot) {
		ItemStack stack = player.getItemBySlot(slot);
		return stack.isEmpty() ? null : itemId(stack);
	}

	private void readHands(Observation obs, LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		obs.mainHand = main.isEmpty() ? null : itemId(main);
		obs.offHand = off.isEmpty() ? null : itemId(off);
		held.update(obs.mainHand, obs.offHand, obs.now);
		obs.mainHandHeldMs = held.mainMs(obs.now);
		obs.offHandHeldMs = held.offMs(obs.now);
	}

	/**
	 * Water, sky and roof flags. The sky and roof lookups touch a handful of blocks, so they are only
	 * redone when the player changes block, every ten ticks, or after a placement.
	 */
	private void readSky(Observation obs, LocalPlayer player, ClientLevel level) {
		obs.inWater = player.isInWater();
		obs.onGround = player.onGround();
		obs.fallDistance = (float) player.fallDistance;
		// a falling player is the landing the tick it stops: the block it is in (water, powder snow), else the one below
		BlockPos feet = player.blockPosition();
		var inside = level.getBlockState(feet);
		obs.landedOn = !inside.isAir() ? blockId(inside) : blockId(level.getBlockState(feet.below()));
		if (skyPos == null || !skyPos.equals(obs.blockPos) || tick - skyAt >= 10) {
			skyPos = obs.blockPos;
			skyAt = tick;
			int x = obs.blockPos.getX();
			int z = obs.blockPos.getZ();
			int eyeY = Mth.floor(player.getEyeY());
			canSeeSky = SkyExposure.canSeeSky(eyeY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
			roofed = SkyExposure.roofed(eyeY, y -> !level.getBlockState(probe.set(x, y, z))
					.getCollisionShape(level, probe).isEmpty());
			underground = SkyExposure.underground(level.dimensionType().hasSkyLight(), obs.blockPos.getY(),
					level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		}
		obs.canSeeSky = canSeeSky;
		obs.roofed = roofed;
		obs.underground = underground;
	}

	/** The {@code open} state of a door, trapdoor or fence gate; null for every other block. */
	private static Boolean openState(BlockState state) {
		Block block = state.getBlock();
		boolean hand = block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
		return hand && state.hasProperty(BlockStateProperties.OPEN) ? state.getValue(BlockStateProperties.OPEN) : null;
	}

	/** The {@code lit} state of a candle, candle cake or campfire; null for every other block. */
	private static Boolean litState(BlockState state) {
		Block block = state.getBlock();
		boolean flame = block instanceof AbstractCandleBlock || block instanceof CampfireBlock;
		return flame && state.hasProperty(BlockStateProperties.LIT) ? state.getValue(BlockStateProperties.LIT) : null;
	}

	/**
	 * Distance to the nearest jukebox with a disc in it. A jukebox holds its disc in the block state
	 * ({@code has_record}) and drops it when the song is over, so the state says "playing". A bell shakes for
	 * about two and a half seconds after it was rung, by whoever rang it; the client keeps that in the block
	 * entity ({@code shaking}), which is how a ringing bell near the player is heard. The scan reads
	 * the block entities of the chunks around the player, twice a second, and only while a step listens.
	 */
	private void readMusic(Observation obs, LocalPlayer player, ClientLevel level) {
		if (obs.now - musicAt >= 500L) {
			musicAt = obs.now;
			musicDistance = -1;
			bellDistance = -1;
			int chunkX = obs.blockPos.getX() >> 4;
			int chunkZ = obs.blockPos.getZ() >> 4;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (BlockEntity entity : level.getChunk(chunkX + dx, chunkZ + dz).getBlockEntities().values()) {
						BlockState state = entity.getBlockState();
						if (state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) {
							BlockPos at = entity.getBlockPos();
							double distance = Math.sqrt(player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5,
									at.getZ() + 0.5));
							musicDistance = musicDistance < 0 ? distance : Math.min(musicDistance, distance);
						} else if (entity instanceof BellBlockEntity bell && bell.shaking) {
							BlockPos at = entity.getBlockPos();
							double distance = Math.sqrt(player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5,
									at.getZ() + 0.5));
							bellDistance = bellDistance < 0 ? distance : Math.min(bellDistance, distance);
						}
					}
				}
			}
		}
		obs.jukeboxDistance = musicDistance;
		obs.bellDistance = bellDistance;
	}

	/**
	 * The nearest block of each kind a NEAR_BLOCK condition asks for. The scan runs twice a second, or when the
	 * player has stepped to another block, and only while a step asks; the answer is carried over in between.
	 */
	private void readNearBlocks(Observation obs, ClientLevel level, Map<String, Integer> wanted) {
		if (wanted.isEmpty()) {
			nearBlockWanted = Map.of();
			nearBlockFound = Map.of();
			return;
		}
		if (!wanted.equals(nearBlockWanted) || !obs.blockPos.equals(nearBlockFrom) || obs.now - nearBlockAt >= 500L) {
			nearBlockWanted = Map.copyOf(wanted);
			nearBlockFrom = obs.blockPos;
			nearBlockAt = obs.now;
			nearBlockFound = BlockProximity.nearest(obs.position.x, obs.position.y, obs.position.z, wanted,
					(x, y, z) -> blockId(level.getBlockState(probe.set(x, y, z))),
					io.github.leks2205.hangulquest.quest.goal.Matching::matchesBlock);
		}
		obs.nearestBlock = nearBlockFound;
	}

	/** Whether a solid block covers the spot from above, the way {@link #readSky} asks it for the player. */
	private boolean roofedAbove(ClientLevel level, BlockPos pos) {
		int x = pos.getX();
		int z = pos.getZ();
		return SkyExposure.roofed(pos.getY(), y -> !level.getBlockState(probe.set(x, y, z))
				.getCollisionShape(level, probe).isEmpty());
	}

	private BlockNeighborhood neighborhood(ClientLevel level, BlockPos center) {
		return BlockNeighborhood.capture(center.getX(), center.getY(), center.getZ(),
				(x, y, z) -> blockId(level.getBlockState(probe.set(x, y, z))));
	}

	private String dimensionId(ClientLevel level) {
		Identifier key = level.dimension().identifier();
		if (key != dimensionKey) {
			dimensionKey = key;
			dimensionId = key.toString();
		}
		return dimensionId;
	}

	/** Biome id and tags only change with the biome holder, so they are rebuilt only then. */
	private void readBiome(Observation obs, ClientLevel level) {
		Holder<Biome> biome = level.getBiome(obs.blockPos);
		if (biome != biomeHolder) {
			biomeHolder = biome;
			biomeId = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");
			List<String> tags = new ArrayList<>();
			for (int i = 0; i < BIOME_TAGS.size(); i++) {
				if (biome.is(BIOME_TAGS.get(i))) {
					tags.add(BIOME_TAG_IDS.get(i));
				}
			}
			biomeTagIds = List.copyOf(tags);
		}
		obs.biome = biomeId;
		obs.biomeTags.addAll(biomeTagIds);
	}

	private static void setLookedEntity(Observation obs, Entity entity) {
		obs.lookedEntity = entityId(entity);
		obs.lookedEntityUuid = entity.getUUID().toString();
	}

	/**
	 * The vanilla block pick passes through water and lava, so a pond is seen as its floor. While a step looks
	 * at a fluid, a second ray that stops at fluid sources says what is really in front.
	 */
	private void lookThroughFluid(Observation obs, LocalPlayer player, ClientLevel level) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
				ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.getFluidState().isEmpty()) {
				obs.lookedBlock = blockId(state);
				obs.lookedBlockPos = hit.getBlockPos();
			}
		}
	}

	/**
	 * Vanilla's crosshair entity only reaches a few blocks in survival. Casts the camera ray out to
	 * {@link LookPick#range} (further through a spyglass) and returns the first entity that no block
	 * hides. Only called while a tracked step needs it.
	 */
	private Entity farPick(LocalPlayer player, ClientLevel level) {
		double range = LookPick.range(player.isScoping());
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 to = from.add(look.x * range, look.y * range, look.z * range);
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, from, to, sweep,
				candidate -> !candidate.isSpectator() && candidate.isPickable(), range * range);
		if (entityHit == null) {
			return null;
		}
		BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, player));
		double blockSq = blockHit.getType() == HitResult.Type.MISS
				? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
		if (!LookPick.visible(from.distanceToSqr(entityHit.getLocation()), blockSq)) {
			return null;
		}
		return resolve(entityHit.getEntity());
	}

	/** Dragon parts are separate entities; everything about them is credited to the dragon. */
	private static Entity resolve(Entity entity) {
		return entity instanceof EnderDragonPart part ? part.parentMob : entity;
	}

	private void scanEntities(Observation obs, LocalPlayer player, ClientLevel level, boolean night) {
		AABB nearBox = new AABB(player.blockPosition()).inflate(24.0);
		boolean watchDrops = gives.hasDropCandidates(obs.now);
		boolean watchPickups = gives.hasPickups(obs.now);
		for (Entity entity : level.getEntities(player, nearBox, candidate -> true)) {
			double distance = Math.sqrt(entity.distanceToSqr(player));
			obs.addNearby(entityId(entity), distance);
			if (watchPickups) {
				if (entity instanceof ItemEntity) {
					itemsInSight.add(entity.getId());
				} else if (distance <= GiveTracker.PICKUP_RANGE && GiveTracker.PICKUP_TYPES.contains(entityId(entity))) {
					pickersNear.add(entity.getUUID());
				}
			}
			if (entity instanceof ItemEntity dropped) {
				if (watchDrops && dropped.tickCount <= 40 && distance <= GiveTracker.SPAWN_RANGE) {
					ItemStack stack = dropped.getItem();
					seenItems.add(new SeenItem(dropped.getId(), itemId(stack), stack.getCount(), distance));
				}
			} else if (!(entity instanceof ExperienceOrb)) {
				obs.addEntityPosition(new EntityFact(entityId(entity), entity.getUUID(), entity.getX(),
						entity.getY(), entity.getZ(), facingYaw(entity), entity.getXRot(),
						entity instanceof LivingEntity living && living.isBaby()));
				if (watchDrops && entity instanceof LivingEntity && distance <= GiveTracker.TARGET_RANGE) {
					dropTargets.add(new GiveTracker.Target(entityId(entity), entity.getUUID(), distance));
				}
			}
			if (entity instanceof Monster && entity.isAlive() && distance <= 12.0) {
				obs.hostilesNear++;
				obs.nearestHostileDistance = Math.min(obs.nearestHostileDistance, distance);
			}
		}
		if (!seenItems.isEmpty()) {
			for (SeenItem seen : seenItems) {
				GiveFact gave = gives.itemSpawned(seen.id(), seen.item(), seen.count(), seen.distance(),
						dropTargets, obs.now);
				if (gave != null) {
					obs.addGave(gave);
				}
			}
			seenItems.clear();
		}
		dropTargets.clear();
		if (watchPickups || gives.hasPickups(obs.now)) {
			for (GiveFact gave : gives.pickupsChecked(itemsInSight, pickersNear, obs.now)) {
				obs.addGave(gave);
			}
		}
		itemsInSight.clear();
		pickersNear.clear();
		for (DeathTracker.Kill kill : deaths.poll(obs.now, deathWorld)) {
			obs.defeatedEntities.add(kill.type());
			if (night) {
				obs.defeatedEntitiesAtNight.add(kill.type());
			}
		}
	}

	private DeathTracker.Subject findForDeath(int entityId) {
		ClientLevel level = mc.level;
		Entity entity = level == null ? null : level.getEntity(entityId);
		if (entity instanceof LivingEntity living) {
			return new DeathTracker.Subject(entityId(living), living.isDeadOrDying());
		}
		return null;
	}

	/**
	 * Arrows and tridents fired by the player: the client knows their owner. A fast one is followed
	 * one step ahead; any living entity in that path counts as hurt, so a kill shortly afterwards is
	 * credited. A shot that only flies past a mob can mark it too, which is harmless unless it dies
	 * of something else within seconds.
	 */
	private void followProjectiles(LocalPlayer player, ClientLevel level, long now) {
		AABB area = player.getBoundingBox().inflate(PROJECTILE_SCAN);
		for (Entity shot : level.getEntities(player, area,
				candidate -> candidate instanceof AbstractArrow arrow && arrow.getOwner() == player)) {
			Vec3 motion = shot.getDeltaMovement();
			if (motion.lengthSqr() < 0.25) {
				continue; // stuck in the ground or about to drop
			}
			AABB path = shot.getBoundingBox().expandTowards(motion).inflate(0.5);
			for (Entity victim : level.getEntities(shot, path,
					candidate -> candidate instanceof LivingEntity && candidate != player && candidate.isAlive())) {
				deaths.attacked(resolve(victim).getId(), now);
			}
		}
	}

	/**
	 * Hands the projectiles of the player and the creatures near them to the {@link ProjectileTracker}, which says
	 * what each one struck once it has landed or vanished. Only runs while a step shoots or throws.
	 */
	private void followShots(Observation obs, LocalPlayer player, ClientLevel level) {
		AABB area = player.getBoundingBox().inflate(PROJECTILE_SCAN);
		List<ProjectileTracker.Flying> flying = new ArrayList<>();
		for (Entity shot : level.getEntities(player, area,
				candidate -> candidate instanceof Projectile projectile && projectile.getOwner() == player)) {
			Vec3 motion = shot.getDeltaMovement();
			flying.add(new ProjectileTracker.Flying(shot.getId(), entityId(shot), shot.getX(), shot.getY(), shot.getZ(),
					motion.x, motion.y, motion.z));
		}
		List<ProjectileTracker.Body> bodies = new ArrayList<>();
		if (!flying.isEmpty() || !shotTracker.idle()) {
			for (Entity near : level.getEntities(player, area,
					candidate -> candidate instanceof LivingEntity && candidate != player && !(candidate instanceof EnderDragonPart))) {
				LivingEntity living = (LivingEntity) near;
				bodies.add(new ProjectileTracker.Body(living.getId(), living.getUUID().toString(), entityId(living),
						living.getX(), living.getY(), living.getZ(), living.getBbWidth() / 2.0, living.hurtTime > 0));
			}
		}
		String held = rangedInHands(player);
		for (ShotFact fact : shotTracker.update(obs.now, flying, bodies, held,
				(x, y, z) -> poweredTarget(level, x, y, z))) {
			obs.addShot(fact);
		}
	}

	/** The bow or crossbow in a hand, which the next arrow was shot from; null when neither is held. */
	private static String rangedInHands(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			String id = itemId(player.getItemInHand(hand));
			if (id.equals("minecraft:bow") || id.equals("minecraft:crossbow")) {
				return id;
			}
		}
		return null;
	}

	/** A target block within a block of the point that shows a signal (it was hit just now), else null. */
	private static String poweredTarget(ClientLevel level, double x, double y, double z) {
		BlockPos center = BlockPos.containing(x, y, z);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.TARGET) && state.getValue(BlockStateProperties.POWER) > 0) {
				return "minecraft:target";
			}
		}
		return null;
	}

	private void updateInventory(Observation obs, LocalPlayer player, boolean night) {
		snapshot(player.getInventory(), scratchInventory);
		// Reuse the previous snapshot while nothing changed; a snapshot is never modified afterwards.
		Map<String, Integer> snapshot = scratchInventory.equals(lastInventory)
				? lastInventory : new HashMap<>(scratchInventory);
		obs.inventory = snapshot;
		consumption.credit(obs, lastInventory, snapshot, night);
		if (snapshot != lastInventory && !obs.inventorySettling) {
			// With a screen open, items move to the cursor and back: not drops.
			for (GiveFact gave : gives.inventoryChanged(lastInventory, snapshot, obs.now, !obs.screenOpen)) {
				obs.addGave(gave);
			}
		}
		lastInventory = snapshot;
	}

	/**
	 * Entering a bed is only possible at night or during a thunderstorm, and the server may
	 * already have jumped the clock to morning, so the night is captured separately.
	 */
	private static boolean isNight(Level level) {
		long time = level.getDefaultClockTime() % 24000L;
		return time >= 13000L && time <= 23000L;
	}

	/** Sleeping itself is proof of using a bed, even if the use event was handled server-side. */
	private void detectSleep(Observation obs, LocalPlayer player, ClientLevel level) {
		boolean sleeping = player.isSleeping() || player.getSleepTimer() > 0
				|| player.getSleepingPos().isPresent();
		// A right-click on the bed already reported the use; only add it when that event was missed.
		if (sleeping && !wasSleeping && !obs.bedUseRecent) {
			String bed = player.getSleepingPos()
					.map(pos -> blockId(level.getBlockState(pos)))
					.orElse("minecraft:red_bed");
			obs.usedBlocks.add(bed);
			obs.usedBlocksAtNight.add(bed);
		}
		wasSleeping = sleeping;
	}

	private static void snapshot(Inventory inventory, Map<String, Integer> into) {
		into.clear();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			into.merge(itemId(stack), stack.getCount(), Integer::sum);
		}
	}

	private boolean isIdle() {
		return !mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()
				&& !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()
				&& !mc.options.keyJump.isDown() && !mc.options.keyAttack.isDown()
				&& !mc.options.keyUse.isDown();
	}

	private static boolean isRangedWeapon(String itemId) {
		return itemId.endsWith("bow") || itemId.equals("minecraft:trident");
	}

	/** Which way a creature faces: the yaw of its body, not of its head (a frog or a rabbit turns the head alone). */
	private static float facingYaw(Entity entity) {
		return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
	}

	private static String blockId(BlockState state) {
		return BLOCK_IDS.computeIfAbsent(state.getBlock(), block -> {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			return id == null ? "" : id.toString();
		});
	}

	static String itemId(ItemStack stack) {
		return ITEM_IDS.computeIfAbsent(stack.getItem(), item -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			return id == null ? "" : id.toString();
		});
	}

	private static String entityId(Entity entity) {
		return ENTITY_IDS.computeIfAbsent(entity.getType(), type -> {
			Identifier id = EntityType.getKey(type);
			return id == null ? "" : id.toString();
		});
	}

	private static TagKey<Biome> tag(String path) {
		return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	public long ticks() {
		return tick;
	}
}
