package io.github.leks2205.hangulquest.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Plays through the game's {@code SoundManager}. Events come from the optional voice pack's {@code sounds.json}. */
public final class MinecraftSoundBackend implements SoundBackend {
	private static final String NAMESPACE = "hangulquest";

	private final Minecraft mc;

	public MinecraftSoundBackend(Minecraft mc) {
		this.mc = mc;
	}

	@Override
	public boolean has(String event) {
		Identifier id = Identifier.tryBuild(NAMESPACE, event);
		return id != null && mc.getSoundManager().getSoundEvent(id) != null;
	}

	@Override
	public boolean hasAny(String prefix) {
		for (Identifier id : mc.getSoundManager().getAvailableSounds()) {
			if (NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void play(String event, float volume) {
		Identifier id = Identifier.tryBuild(NAMESPACE, event);
		if (id != null) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0F, volume));
		}
	}
}
