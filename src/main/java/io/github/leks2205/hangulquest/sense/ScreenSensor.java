package io.github.leks2205.hangulquest.sense;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Watches the container screens that matter for crafting, trading and putting items away. It hooks
 * Fabric's per-screen mouse and key events (no mixins): a snapshot before and after each click on
 * a screen with a result slot feeds {@link ResultTakeTracker}, and while a chest-like screen is
 * open a per-tick tally feeds {@link ContainerDiff}. With no such screen open, a tick costs one
 * field read.
 */
final class ScreenSensor {
	/** A block or entity clicked this recently is taken to be what opened the next screen. */
	private static final long OPENER_WINDOW_MS = 5_000L;

	private final Minecraft mc = Minecraft.getInstance();
	private final ResultTakeTracker takes = new ResultTakeTracker();
	private final ContainerDiff diff = new ContainerDiff();
	private final Map<String, Integer> scratchPlayer = new HashMap<>();
	private final Map<String, Integer> scratchContainer = new HashMap<>();

	private AbstractContainerScreen<?> watched;
	private Station station;
	private boolean container;
	private String containerBlock;
	private BlockPos containerPos;
	private String merchantType;
	private UUID merchantUuid;

	// The state captured just before a click or key press, consumed by the matching "after" event.
	private boolean haveBefore;
	private StackView resultBefore = StackView.EMPTY;
	private Map<String, Integer> tallyBefore = Map.of();
	private String payItem;

	private String openerBlock;
	private BlockPos openerPos;
	private long openerBlockAt;
	private String openerEntityType;
	private UUID openerEntityUuid;
	private long openerEntityAt;

	void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			Station kind = stationOf(containerScreen.getMenu());
			boolean storage = isStorage(containerScreen.getMenu());
			if (kind == null && !storage) {
				return;
			}
			opened(containerScreen, kind, storage);
			if (kind == null) {
				return;
			}
			ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> captureBefore());
			ScreenMouseEvents.afterMouseClick(screen).register((s, event, handled) -> {
				captureAfter(event.hasShiftDown());
				return handled;
			});
			// Number keys swap the result into the hotbar; that takes it too.
			ScreenKeyboardEvents.beforeKeyPress(screen).register((s, event) -> captureBefore());
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> captureAfter(false));
		});
	}

	void blockUsed(BlockPos pos, String block, long now) {
		openerPos = pos;
		openerBlock = block;
		openerBlockAt = now;
	}

	void entityUsed(String type, UUID uuid, long now) {
		openerEntityType = type;
		openerEntityUuid = uuid;
		openerEntityAt = now;
	}

	private void opened(AbstractContainerScreen<?> screen, Station kind, boolean storage) {
		if (watched == screen) {
			return; // a resize re-runs init on the same screen
		}
		long now = System.currentTimeMillis();
		watched = screen;
		station = kind;
		container = storage;
		haveBefore = false;
		containerBlock = null;
		containerPos = null;
		if (storage && now - openerBlockAt <= OPENER_WINDOW_MS) {
			containerBlock = openerBlock;
			containerPos = openerPos;
		}
		merchantType = null;
		merchantUuid = null;
		if (kind == Station.MERCHANT && now - openerEntityAt <= OPENER_WINDOW_MS) {
			merchantType = openerEntityType;
			merchantUuid = openerEntityUuid;
		}
		LocalPlayer player = mc.player;
		if (storage && player != null) {
			fillPlayerTally(player, scratchPlayer, null);
			fillContainerTally(screen.getMenu(), player.getInventory(), scratchContainer);
			diff.open(scratchContainer, scratchPlayer);
		}
	}

	private void captureBefore() {
		LocalPlayer player = mc.player;
		if (station == null || player == null || watched == null || mc.gui.screen() != watched) {
			haveBefore = false;
			return;
		}
		AbstractContainerMenu menu = watched.getMenu();
		resultBefore = stackOf(resultSlot(menu));
		payItem = station == Station.MERCHANT ? payment(menu) : null;
		tallyBefore = new HashMap<>();
		fillPlayerTally(player, tallyBefore, station == Station.MERCHANT ? menu : null);
		haveBefore = true;
	}

	private void captureAfter(boolean shift) {
		LocalPlayer player = mc.player;
		if (!haveBefore || player == null || watched == null) {
			return;
		}
		haveBefore = false; // a duplicated listener must not count the same click twice
		AbstractContainerMenu menu = watched.getMenu();
		Map<String, Integer> after = new HashMap<>();
		fillPlayerTally(player, after, station == Station.MERCHANT ? menu : null);
		takes.click(station, resultBefore, stackOf(resultSlot(menu)), tallyBefore, after, shift, payItem,
				System.currentTimeMillis());
		tallyBefore = Map.of();
	}

	/** Called once per sensor tick; adds whatever finished since the last one. */
	void tick(Observation obs, LocalPlayer player) {
		if (watched == null) {
			return;
		}
		boolean closing = mc.gui.screen() != watched;
		if (container && !closing) {
			fillPlayerTally(player, scratchPlayer, null);
			fillContainerTally(watched.getMenu(), player.getInventory(), scratchContainer);
			for (ContainerDiff.Put put : diff.update(scratchContainer, scratchPlayer)) {
				obs.addPutIn(new ContainerPutFact(put.item(), put.count(), containerBlock, containerPos));
			}
		}
		if (takes.hasPending()) {
			AbstractContainerMenu menu = closing ? null : watched.getMenu();
			fillPlayerTally(player, scratchPlayer, station == Station.MERCHANT ? menu : null);
			for (ResultTakeTracker.Taken taken : takes.poll(scratchPlayer, obs.now, closing)) {
				if (taken.station() == Station.MERCHANT) {
					if (taken.paidItem() != null && taken.paidCount() > 0) {
						obs.addGave(new GiveFact(taken.paidItem(), taken.paidCount(), merchantType, merchantUuid,
								GiveFact.Method.TRADE));
					}
				} else {
					obs.addCrafted(new CraftFact(taken.item(), taken.count(), taken.station().id()));
				}
			}
		}
		if (closing) {
			watched = null;
			station = null;
			container = false;
			haveBefore = false;
			diff.clear();
		}
	}

	void reset() {
		takes.clear();
		diff.clear();
		watched = null;
		station = null;
		container = false;
		haveBefore = false;
		openerBlockAt = 0L;
		openerEntityAt = 0L;
	}

	private static Station stationOf(AbstractContainerMenu menu) {
		if (menu instanceof InventoryMenu) {
			return Station.INVENTORY_2X2;
		} else if (menu instanceof CraftingMenu) {
			return Station.CRAFTING_TABLE;
		} else if (menu instanceof FurnaceMenu) {
			return Station.FURNACE;
		} else if (menu instanceof BlastFurnaceMenu) {
			return Station.BLAST_FURNACE;
		} else if (menu instanceof SmokerMenu) {
			return Station.SMOKER;
		} else if (menu instanceof StonecutterMenu) {
			return Station.STONECUTTER;
		} else if (menu instanceof SmithingMenu) {
			return Station.SMITHING;
		} else if (menu instanceof LoomMenu) {
			return Station.LOOM;
		} else if (menu instanceof CartographyTableMenu) {
			return Station.CARTOGRAPHY;
		} else if (menu instanceof GrindstoneMenu) {
			return Station.GRINDSTONE;
		} else if (menu instanceof MerchantMenu) {
			return Station.MERCHANT;
		}
		return null;
	}

	private static boolean isStorage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu
				|| menu instanceof DispenserMenu;
	}

	private Slot resultSlot(AbstractContainerMenu menu) {
		int index = station.resultSlot();
		return index < menu.slots.size() ? menu.getSlot(index) : null;
	}

	private static StackView stackOf(Slot slot) {
		return slot == null ? StackView.EMPTY : stackOf(slot.getItem());
	}

	private static StackView stackOf(ItemStack stack) {
		return stack.isEmpty() ? StackView.EMPTY : new StackView(EnvironmentSensor.itemId(stack), stack.getCount());
	}

	/** The item in the first filled payment slot of a merchant screen. */
	private static String payment(AbstractContainerMenu menu) {
		for (int i = 0; i < 2; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				return EnvironmentSensor.itemId(stack);
			}
		}
		return null;
	}

	/**
	 * Everything on the player's side: inventory plus the stack on the cursor. For a merchant the
	 * two payment slots count too, so the price shows as a loss.
	 */
	private static void fillPlayerTally(LocalPlayer player, Map<String, Integer> into, AbstractContainerMenu merchant) {
		into.clear();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			add(into, inventory.getItem(slot));
		}
		add(into, player.containerMenu.getCarried());
		if (merchant != null) {
			add(into, merchant.getSlot(0).getItem());
			add(into, merchant.getSlot(1).getItem());
		}
	}

	/** The slots that belong to the container itself, not to the player's inventory. */
	private static void fillContainerTally(AbstractContainerMenu menu, Inventory inventory, Map<String, Integer> into) {
		into.clear();
		List<Slot> slots = menu.slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (slot.container != inventory) {
				add(into, slot.getItem());
			}
		}
	}

	private static void add(Map<String, Integer> into, ItemStack stack) {
		if (!stack.isEmpty()) {
			into.merge(EnvironmentSensor.itemId(stack), stack.getCount(), Integer::sum);
		}
	}
}
