package io.github.leks2205.hangulquest.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Plays through the game's {@code SoundManager}. Events come from the optional voice pack's {@code sounds.json}.
 * The clips use the sound category "Voice/Speech" (so its slider and the master slider set the volume), played
 * flat and relative to the listener like a UI sound.
 */
public final class MinecraftSoundBackend implements SoundBackend {
	private static final String NAMESPACE = "hangulquest";

	private final Minecraft mc;
	private SoundInstance last;

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
			last = new SimpleSoundInstance(id, SoundSource.VOICE, volume, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
			mc.getSoundManager().play(last);
		}
	}

	@Override
	public void stopLast() {
		if (last != null) {
			mc.getSoundManager().stop(last);
			last = null;
		}
	}
}
