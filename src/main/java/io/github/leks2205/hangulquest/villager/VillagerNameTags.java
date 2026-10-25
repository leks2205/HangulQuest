package io.github.leks2205.hangulquest.villager;

import io.github.leks2205.hangulquest.dialogue.DialogueHook;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Name tags for villagers that have introduced themselves: once {@link VillagerProfile#introduced} is set, the name
 * from the player's {@code villagers.json} floats above the villager's head, like a name tag.
 *
 * <p>Purely client side: the villager entity on the client gets a custom name and a visible name tag, nothing is
 * sent to the server and nothing is saved in the world. The server may reset the entity data when it starts
 * tracking the villager again (chunk reload, walking away and back); the next check puts the name back. A villager
 * that carries a name from a real name tag keeps that name.
 */
public final class VillagerNameTags {
	/** Ticks between two checks (a second); the entities in range are few, the check is cheap. */
	private static final int INTERVAL = 20;
	private static int ticks;

	private VillagerNameTags() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VillagerNameTags::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null || ++ticks % INTERVAL != 0) {
			return;
		}
		VillagerProfiles known = DialogueHook.villagers();
		if (known == null) {
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			VillagerProfile profile = known.get(villager.getUUID());
			if (profile == null || !profile.introduced || profile.name == null || profile.name.isEmpty()) {
				continue;
			}
			Component current = villager.getCustomName();
			if (current == null) {
				villager.setCustomName(Component.literal(profile.name));
			} else if (!profile.name.equals(current.getString())) {
				continue; // a real name tag (or another mod): leave it alone
			}
			if (!villager.isCustomNameVisible()) {
				villager.setCustomNameVisible(true);
			}
		}
	}
}
