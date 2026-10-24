package io.github.leks2205.hangulquest.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.util.JsonIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Every villager the player has talked to, by entity UUID, in the order they were met; kept in
 * {@code villagers.json} of the profile folder. It stays on this computer and is not part of the progress data
 * (a lost file loses names and friendship, never learning progress).
 *
 * <p>Gender and personality are drawn from the UUID, so they come out the same even if the file is lost; the
 * name is drawn from the UUID too, skipping names in use while any are free. Once given, everything is saved and
 * read back as it is. A {@code villager-names.json} of 3.0 is read once and its names are kept.
 */
public final class VillagerProfiles {
	public static final String FILE = "villagers.json";
	/** The file of 3.0 and earlier: only names. */
	public static final String LEGACY_FILE = "villager-names.json";
	/** Beyond this many villagers the oldest one that is not protected is forgotten. */
	public static final int MAX_ENTRIES = 500;
	static final int VERSION = 1;

	/** A profile and whether it was made just now. */
	public record Meeting(VillagerProfile profile, boolean fresh) {
	}

	private final Path file;
	private final Logger log;
	private final Map<String, VillagerProfile> profiles = new LinkedHashMap<>();
	/** True for a villager whose request is still open: never forgotten. */
	private Predicate<String> busy = uuid -> false;

	private VillagerProfiles(Path file, Logger log) {
		this.file = file;
		this.log = log;
	}

	/** Reads the file of a profile folder, or migrates the 3.0 names file; a missing or broken file starts empty. */
	public static VillagerProfiles open(Path profileDir, Logger log) {
		VillagerProfiles profiles = new VillagerProfiles(profileDir.resolve(FILE), log);
		if (Files.isRegularFile(profiles.file)) {
			profiles.read();
		} else if (Files.isRegularFile(profileDir.resolve(LEGACY_FILE))) {
			profiles.migrate(profileDir.resolve(LEGACY_FILE));
			profiles.save();
		}
		return profiles;
	}

	/** Tells which villagers have an open request; they are never forgotten. */
	public void protect(Predicate<String> hasOpenRequest) {
		this.busy = hasOpenRequest == null ? uuid -> false : hasOpenRequest;
	}

	public VillagerProfile get(UUID villager) {
		return profiles.get(villager.toString());
	}

	public VillagerProfile get(String uuid) {
		return profiles.get(uuid);
	}

	/** Every known villager in the order they were met. */
	public Collection<VillagerProfile> all() {
		return List.copyOf(profiles.values());
	}

	public int size() {
		return profiles.size();
	}

	/**
	 * The profile of a villager the player is talking to: made and saved the first time, then only the things
	 * that may change (region when it was unknown, profession, age) are updated. Not saved here when it existed;
	 * call {@link #save()} after the conversation changed it.
	 */
	public Meeting meet(UUID villager, Region region, boolean baby, String profession, long now) {
		String key = villager.toString();
		VillagerProfile known = profiles.get(key);
		if (known != null) {
			if (known.region == null) {
				known.region = region;
			}
			known.baby = baby;
			if (profession != null) {
				known.profession = profession;
			}
			return new Meeting(known, false);
		}
		Gender gender = genderOf(villager);
		Personality personality = personalityOf(villager, gender);
		Set<String> taken = new HashSet<>();
		profiles.values().forEach(profile -> taken.add(profile.name));
		VillagerProfile made = new VillagerProfile(key, VillagerNames.pick(villager, gender, taken).ko(), gender,
				personality, personality.voice());
		made.region = region;
		made.baby = baby;
		made.profession = profession;
		made.firstMet = now;
		profiles.put(key, made);
		evict(key);
		save();
		return new Meeting(made, true);
	}

	/**
	 * What {@link #meet} would give, without keeping or saving anything: a copy of the known profile with the meeting
	 * applied, or the profile a new villager would get.
	 */
	public VillagerProfile preview(UUID villager, Region region, boolean baby, String profession, long now) {
		VillagerProfile known = profiles.get(villager.toString());
		VillagerProfile copy;
		if (known != null) {
			copy = known.copy();
			if (copy.region == null) {
				copy.region = region;
			}
		} else {
			Gender gender = genderOf(villager);
			Personality personality = personalityOf(villager, gender);
			Set<String> taken = new HashSet<>();
			profiles.values().forEach(profile -> taken.add(profile.name));
			copy = new VillagerProfile(villager.toString(), VillagerNames.pick(villager, gender, taken).ko(), gender,
					personality, personality.voice());
			copy.region = region;
			copy.firstMet = now;
		}
		copy.baby = baby;
		if (profession != null) {
			copy.profession = profession;
		}
		return copy;
	}

	/** Forgets the oldest villagers that are neither close friends nor waiting for a request, down to the cap. */
	private void evict(String keep) {
		for (Iterator<VillagerProfile> oldest = profiles.values().iterator();
			 profiles.size() > MAX_ENTRIES && oldest.hasNext(); ) {
			VillagerProfile profile = oldest.next();
			if (!profile.uuid.equals(keep) && !isProtected(profile)) {
				oldest.remove();
			}
		}
	}

	boolean isProtected(VillagerProfile profile) {
		return profile.friendship >= VillagerProfile.KEEP_FRIENDSHIP || busy.test(profile.uuid);
	}

	// ---------------------------------------------------------------- drawing from the uuid

	/** A well-mixed hash of the uuid for one purpose ({@code salt}), so gender, personality and name do not correlate. */
	static int mix(UUID uuid, long salt) {
		long z = uuid.getMostSignificantBits() * 31 + uuid.getLeastSignificantBits() + salt * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (int) (z ^ (z >>> 32));
	}

	public static Gender genderOf(UUID villager) {
		return (mix(villager, 1) & 1) == 0 ? Gender.MALE : Gender.FEMALE;
	}

	public static Personality personalityOf(UUID villager, Gender gender) {
		List<Personality> options = Personality.of(gender);
		return options.get(Math.floorMod(mix(villager, 2), options.size()));
	}

	// ---------------------------------------------------------------- file

	/** The 3.0 names: kept, gender from the pool the name is in (from the uuid for a name in neither). */
	private void migrate(Path legacy) {
		try {
			JsonObject names = JsonParser.parseString(Files.readString(legacy, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("names");
			long order = 0;
			for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
				UUID uuid = uuidOf(entry.getKey());
				JsonElement value = entry.getValue();
				if (uuid == null || !value.isJsonPrimitive() || value.getAsString().isBlank()
						|| value.getAsString().length() > 8) {
					continue;
				}
				String name = value.getAsString();
				VillagerNames.Name pooled = VillagerNames.byKo(name);
				Gender gender = pooled != null ? pooled.gender() : genderOf(uuid);
				Personality personality = personalityOf(uuid, gender);
				VillagerProfile profile = new VillagerProfile(uuid.toString(), name, gender, personality,
						personality.voice());
				profile.firstMet = order++;
				profiles.put(profile.uuid, profile);
			}
			log.info("villager names of {} moved to {} ({} villagers)", LEGACY_FILE, FILE, profiles.size());
		} catch (IOException | RuntimeException broken) {
			log.warn("could not read {}: {}", legacy, broken.toString());
			profiles.clear();
		}
	}

	private void read() {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("villagers")) {
				VillagerProfile profile = fromJson(element);
				if (profile != null) {
					profiles.putIfAbsent(profile.uuid, profile);
				}
			}
		} catch (IOException | RuntimeException broken) {
			// Profiles are cosmetic and social: start again rather than fail.
			log.warn("could not read {}: {}", file, broken.toString());
			profiles.clear();
		}
	}

	private static UUID uuidOf(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException | NullPointerException notAUuid) {
			return null;
		}
	}

	/** One entry; null when it is unusable. A stored personality of the wrong gender is not trusted. */
	static VillagerProfile fromJson(JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject json = element.getAsJsonObject();
		UUID uuid = uuidOf(string(json, "uuid"));
		String name = string(json, "name");
		if (uuid == null || name == null || name.isBlank() || name.length() > 8) {
			return null;
		}
		Gender gender = Gender.fromId(string(json, "gender"));
		if (gender == null) {
			VillagerNames.Name pooled = VillagerNames.byKo(name);
			gender = pooled != null ? pooled.gender() : genderOf(uuid);
		}
		Personality personality = Personality.fromId(string(json, "personality"));
		if (personality == null || personality.gender() != gender) {
			personality = personalityOf(uuid, gender);
		}
		String voice = string(json, "voice");
		if (voice == null || !voice.matches("[a-z0-9_]+")) {
			voice = personality.voice();
		}
		VillagerProfile profile = new VillagerProfile(uuid.toString(), name, gender, personality, voice);
		profile.region = Region.fromId(string(json, "region"));
		profile.friendship = Math.max(0, Math.min(VillagerProfile.MAX_FRIENDSHIP, integer(json, "friendship", 0)));
		profile.firstMet = number(json, "firstMet", 0);
		profile.lastTalkDay = number(json, "lastTalkDay", -1);
		profile.requestDay = number(json, "requestDay", -1);
		profile.reminderDay = number(json, "reminderDay", -1);
		profile.banmal = json.has("banmal") && json.get("banmal").isJsonPrimitive() && json.get("banmal").getAsBoolean();
		profile.baby = json.has("baby") && json.get("baby").isJsonPrimitive() && json.get("baby").getAsBoolean();
		profile.profession = string(json, "profession");
		profile.introduced = json.has("introduced") && json.get("introduced").isJsonPrimitive()
				&& json.get("introduced").getAsBoolean();
		if (json.has("once") && json.get("once").isJsonArray()) {
			for (JsonElement once : json.getAsJsonArray("once")) {
				if (once.isJsonPrimitive()) {
					profile.markOnce(once.getAsString());
				}
			}
		}
		if (json.has("seen") && json.get("seen").isJsonArray()) {
			for (JsonElement seen : json.getAsJsonArray("seen")) {
				if (seen.isJsonPrimitive()) {
					profile.markSeen(seen.getAsString());
				}
			}
		}
		return profile;
	}

	static JsonObject toJson(VillagerProfile profile) {
		JsonObject json = new JsonObject();
		json.addProperty("uuid", profile.uuid);
		json.addProperty("name", profile.name);
		json.addProperty("gender", profile.gender.id());
		json.addProperty("personality", profile.personality.name());
		if (profile.region != null) {
			json.addProperty("region", profile.region.name());
		}
		json.addProperty("voice", profile.voice);
		json.addProperty("friendship", profile.friendship);
		json.addProperty("firstMet", profile.firstMet);
		json.addProperty("lastTalkDay", profile.lastTalkDay);
		json.addProperty("requestDay", profile.requestDay);
		json.addProperty("reminderDay", profile.reminderDay);
		JsonArray seen = new JsonArray();
		profile.seen.forEach(seen::add);
		json.add("seen", seen);
		JsonArray once = new JsonArray();
		profile.once.forEach(once::add);
		json.add("once", once);
		json.addProperty("introduced", profile.introduced);
		json.addProperty("banmal", profile.banmal);
		json.addProperty("baby", profile.baby);
		if (profile.profession != null) {
			json.addProperty("profession", profile.profession);
		}
		return json;
	}

	public void save() {
		JsonArray villagers = new JsonArray();
		profiles.values().forEach(profile -> villagers.add(toJson(profile)));
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("villagers", villagers);
		try {
			JsonIO.writeString(file, JsonIO.GSON.toJson(root));
		} catch (IOException failed) {
			log.warn("could not save {}: {}", file, failed.toString());
		}
	}

	private static String string(JsonObject json, String name) {
		JsonElement value = json.get(name);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static long number(JsonObject json, String name, long fallback) {
		JsonElement value = json.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong()
				: fallback;
	}

	private static int integer(JsonObject json, String name, int fallback) {
		return (int) number(json, name, fallback);
	}

	/** For tests: the profiles in file order. */
	List<VillagerProfile> list() {
		return new ArrayList<>(profiles.values());
	}
}
