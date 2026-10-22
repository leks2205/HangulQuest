package io.github.leks2205.hangulquest.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * tools/audio/build_pack.py writes the pack's sounds.json in this shape. The game's own parser must accept it,
 * and the event names must be the ones {@link AudioService} asks for.
 */
class VoicePackFormatTest {
	private static final Gson GSON = new GsonBuilder()
			.registerTypeAdapter(SoundEventRegistration.class, new SoundEventRegistrationSerializer())
			.create();

	@Test
	void theGamesOwnParserReadsThePackFormat() throws IOException {
		try (Reader in = new InputStreamReader(
				getClass().getResourceAsStream("/audio/voice-pack-sounds.json"), StandardCharsets.UTF_8)) {
			Map<String, SoundEventRegistration> events = GSON.fromJson(in,
					new TypeToken<Map<String, SoundEventRegistration>>() { }.getType());
			SoundEventRegistration stone = events.get(AudioService.WORD_PREFIX + "stone");
			assertNotNull(stone);
			assertEquals(2, stone.getSounds().size());
			assertEquals("hangulquest:ko/sohee/word.stone", stone.getSounds().get(0).getLocation().toString());
			// a sound forge stimulus: one voice per event, named like HvptSet.event
			SoundEventRegistration forge = events.get(io.github.leks2205.hangulquest.learn.hvpt.HvptSet.event("dt_w1", "t", "sohee"));
			assertNotNull(forge);
			assertEquals(1, forge.getSounds().size());
			assertEquals("hangulquest:ko/sohee/hvpt.dt_w1.t", forge.getSounds().get(0).getLocation().toString());
		}
	}
}
