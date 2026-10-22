package io.github.leks2205.hangulquest.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAudioTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void audioIsOnByDefault() {
		assertTrue(new Config().audio);
	}

	@Test
	void oldConfigWithoutAudioKeepsItOn() {
		assertTrue(Config.parse(json("{\"version\":2,\"sound\":true}")).audio);
	}

	@Test
	void oldConfigWithMutedSoundsStaysMuted() {
		assertFalse(Config.parse(json("{\"version\":2,\"sound\":false}")).audio);
	}

	@Test
	void anExplicitAudioChoiceWins() {
		assertTrue(Config.parse(json("{\"version\":2,\"sound\":false,\"audio\":true}")).audio);
		assertFalse(Config.parse(json("{\"version\":2,\"audio\":false}")).audio);
	}
}
