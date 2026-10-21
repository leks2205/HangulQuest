package io.github.leks2205.hangulquest.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.util.JsonIO;
import io.github.leks2205.hangulquest.util.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings; unknown/missing fields fall back to the defaults below. */
public final class Config {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");

	public int version = 2;
	public boolean enabled = true;
	public boolean hudCards = true;
	public boolean tooltips = true;
	public boolean autoQuiz = true;
	public int quizIntervalMinutes = 7;
	public int cardDurationMs = 4500;
	public boolean lookAtCards = true;
	public boolean mobCards = true;
	public boolean biomeCards = true;
	public boolean timeCards = true;
	public boolean sound = true;
	public String hudCorner = "BOTTOM_RIGHT";

	// Quests
	public boolean quests = true;
	public boolean questTracker = true;
	public String questTrackerCorner = "TOP_LEFT";
	/** How many tracked quests the HUD log shows at once (the focused one always counts). */
	public int questTrackerRows = 4;
	/** Interface and translation language: "AUTO" (default, follows Minecraft's language), "EN" or "DE". */
	public String language = Lang.AUTO;
	/** Show the English/German line under the Korean quest text. Off by default: Korean only. */
	public boolean questTranslations = false;
	/** Legacy field, only read while migrating an old config. */
	public boolean questGerman = false;
	/** Journal shows German translations only when the eye button is on. */
	public boolean journalTranslations = false;
	public boolean luteImport = true;

	// Hangul welcome screen (see WelcomePolicy)
	/** True once the player confirmed, opted out, or the reminder was used up. */
	public boolean welcomeDone = false;
	/** How many times the welcome screen has been shown automatically. */
	public int welcomeShown = 0;

	public static Config load(Path file) {
		if (!Files.exists(file)) {
			Config config = new Config();
			config.save(file);
			return config;
		}
		try {
			JsonObject raw = JsonIO.readObject(file);
			Config loaded = parse(raw);
			if (!raw.has("language") || raw.get("language").isJsonNull()) {
				// Write the migrated value back so it survives the next start.
				loaded.save(file);
			}
			return loaded;
		} catch (IOException | RuntimeException broken) {
			// Never crash the client because of a bad config; keep the broken file once.
			try {
				Files.move(file, file.resolveSibling("config.json.broken-" + System.currentTimeMillis()));
			} catch (IOException failed) {
				LOG.warn("could not set the broken config aside: {}", failed.toString());
			}
			Config config = new Config();
			config.save(file);
			return config;
		}
	}

	/**
	 * Builds a config from the raw JSON. Migrations look at the raw object, not at the field values:
	 * a field that is missing in the file already holds its default by then.
	 */
	public static Config parse(JsonObject raw) {
		Config loaded = JsonIO.GSON.fromJson(raw, Config.class);
		if (loaded == null) {
			loaded = new Config();
		}
		JsonElement language = raw.get("language");
		boolean hasLanguage = language != null && language.isJsonPrimitive()
				&& !language.getAsString().isBlank();
		if (!hasLanguage) {
			// Configs from before the interface language existed only knew "German in quests".
			JsonElement legacy = raw.get("questGerman");
			boolean wantedGerman = legacy != null && legacy.isJsonPrimitive() && legacy.getAsBoolean();
			loaded.language = wantedGerman ? "DE" : Lang.AUTO;
			if (wantedGerman) {
				loaded.questTranslations = true;
			}
		}
		loaded.sanitize();
		return loaded;
	}

	public void save(Path file) {
		try {
			JsonIO.write(file, this);
		} catch (IOException | RuntimeException failed) {
			LOG.warn("could not write config {}: {}", file, failed.toString());
		}
	}

	public void sanitize() {
		if (version < 2) {
			version = 2;
		}
		language = language == null || language.isBlank() ? Lang.AUTO : Lang.normalise(language);
		welcomeShown = Math.max(0, welcomeShown);
		quizIntervalMinutes = clamp(quizIntervalMinutes, 1, 120);
		questTrackerRows = clamp(questTrackerRows, 1, 6);
		cardDurationMs = clamp(cardDurationMs, 1000, 20000);
		if (hudCorner == null) {
			hudCorner = "BOTTOM_RIGHT";
		}
		if (questTrackerCorner == null) {
			questTrackerCorner = "TOP_LEFT";
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
