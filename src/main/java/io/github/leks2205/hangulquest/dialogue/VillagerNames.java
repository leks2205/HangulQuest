package io.github.leks2205.hangulquest.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.JsonIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A stable Korean given name for every villager the player talks to, kept per entity UUID in a small
 * file of the profile folder. Names are only for display: they stay on this computer and are not part
 * of the progress data. A name is picked from the list by the UUID, skipping names already given out
 * while any are left; once given it never changes, even if the list does.
 */
public final class VillagerNames {
	public static final String FILE = "villager-names.json";
	/** The oldest assignments are forgotten beyond this many. */
	static final int MAX_ENTRIES = 500;
	static final List<String> GIVEN = List.of(
			"민준", "서연", "지우", "하준", "서윤", "도윤", "수아", "예준", "지민", "하은", "시우", "나은",
			"준서", "채원", "현우", "유나", "지호", "소율", "태민", "다은", "보라", "한결", "가온", "나래");

	private final Path file;
	private final Logger log;
	private final Map<String, String> names = new LinkedHashMap<>();

	private VillagerNames(Path file, Logger log) {
		this.file = file;
		this.log = log;
	}

	/** Reads the file of a profile folder; a missing or broken file starts empty. */
	public static VillagerNames open(Path profileDir, Logger log) {
		VillagerNames names = new VillagerNames(profileDir.resolve(FILE), log);
		names.read();
		return names;
	}

	/** The villager's name; given and saved on first use. */
	public String nameFor(UUID villager) {
		String key = villager.toString();
		String known = names.get(key);
		if (known != null) {
			return known;
		}
		String name = pick(villager, new HashSet<>(names.values()));
		names.put(key, name);
		for (Iterator<String> oldest = names.keySet().iterator(); names.size() > MAX_ENTRIES && oldest.hasNext(); ) {
			oldest.next();
			oldest.remove();
		}
		save();
		return name;
	}

	/** The name {@code villager} gets when {@code taken} are in use: its own slot, or the next free one. */
	static String pick(UUID villager, Set<String> taken) {
		int slot = Math.floorMod(Long.hashCode(villager.getMostSignificantBits() * 31 + villager.getLeastSignificantBits()),
				GIVEN.size());
		for (int step = 0; step < GIVEN.size(); step++) {
			String candidate = GIVEN.get((slot + step) % GIVEN.size());
			if (!taken.contains(candidate)) {
				return candidate;
			}
		}
		return GIVEN.get(slot);
	}

	private void read() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject stored = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("names");
			for (Map.Entry<String, JsonElement> entry : stored.entrySet()) {
				if (validEntry(entry.getKey(), entry.getValue())) {
					names.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		} catch (IOException | RuntimeException broken) {
			// Names are cosmetic: start again rather than fail.
			log.warn("could not read {}: {}", file, broken.toString());
			names.clear();
		}
	}

	private static boolean validEntry(String key, JsonElement value) {
		if (!value.isJsonPrimitive() || value.getAsString().isBlank() || value.getAsString().length() > 8) {
			return false;
		}
		try {
			UUID.fromString(key);
			return true;
		} catch (IllegalArgumentException notAUuid) {
			return false;
		}
	}

	private void save() {
		JsonObject stored = new JsonObject();
		names.forEach(stored::addProperty);
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("names", stored);
		try {
			JsonIO.writeString(file, JsonIO.GSON.toJson(root));
		} catch (IOException failed) {
			log.warn("could not save {}: {}", file, failed.toString());
		}
	}
}
