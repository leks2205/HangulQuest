package io.github.leks2205.hangulquest.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.dialogue.SpeechLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The two data files the voice tooling reads say the same as the code the game runs. */
class VillagerDataFilesTest {
	private static final Path DATA = Path.of("src/main/resources/assets/hangulquest/data");

	private static JsonObject read(String name) throws IOException {
		return JsonParser.parseString(Files.readString(DATA.resolve(name), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void villagerNamesJsonIsTheTableInTheCode() throws IOException {
		List<String> file = new ArrayList<>();
		for (JsonElement element : read("villager-names.json").getAsJsonArray("names")) {
			JsonObject name = element.getAsJsonObject();
			JsonObject lines = name.getAsJsonObject("lines");
			file.add(String.join("|", name.get("ko").getAsString(), name.get("gender").getAsString(),
					name.get("id").getAsString(), lines.get("haeyo").getAsString(), lines.get("hapsyo").getAsString(),
					lines.get("banmal").getAsString()));
		}
		List<String> code = new ArrayList<>();
		for (VillagerNames.Name name : VillagerNames.all()) {
			code.add(String.join("|", name.ko(), name.gender().id(), name.id(),
					VillagerNames.selfIntro(name.ko(), SpeechLevel.HAEYO),
					VillagerNames.selfIntro(name.ko(), SpeechLevel.HAPSYO),
					VillagerNames.selfIntro(name.ko(), SpeechLevel.BANMAL)));
		}
		assertEquals(code, file);
	}

	@Test
	void voicesJsonIsTheTableInTheCode() throws IOException {
		JsonObject voices = read("voices.json");
		JsonObject personalities = voices.getAsJsonObject("personalities");
		assertEquals(Personality.values().length, personalities.size());
		for (Personality personality : Personality.values()) {
			JsonObject entry = personalities.getAsJsonObject(personality.name());
			assertEquals(personality.voice(), entry.get("voice").getAsString());
			assertEquals(personality.gender().id(), entry.get("gender").getAsString());
			assertEquals(personality.register().id(), entry.get("register").getAsString());
		}
		JsonObject children = voices.getAsJsonObject("children");
		assertEquals(Gender.MALE.childVoice(), children.get("male").getAsString());
		assertEquals(Gender.FEMALE.childVoice(), children.get("female").getAsString());
		assertEquals(10, voices.getAsJsonObject("voices").size());
	}
}
