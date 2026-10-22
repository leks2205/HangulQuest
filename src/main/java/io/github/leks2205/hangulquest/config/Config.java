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

	/**
	 * 3 added {@code retention} and {@code newWordsPerDay}; 4 added {@code typedCards}; 5 added
	 * {@code autoCloseWindows}, {@code autoPickQuests} and {@code showAllTrails}.
	 */
	public static final int CURRENT_VERSION = 5;
	public static final double MIN_RETENTION = 0.80;
	public static final double MAX_RETENTION = 0.95;
	public static final double DEFAULT_RETENTION = 0.88;
	/** The choices offered for new words per study day. */
	public static final int[] NEW_WORD_CHOICES = {5, 10, 15};
	public static final int DEFAULT_NEW_WORDS = 10;

	public int version = CURRENT_VERSION;
	public boolean enabled = true;
	public boolean hudCards = true;
	public boolean tooltips = true;
	public boolean autoQuiz = true;
	public int quizIntervalMinutes = 7;
	/** How long an ambient word card stays when windows close automatically; it only counts with {@link #autoCloseWindows}. */
	public int cardDurationMs = 4500;
	/**
	 * Off: every HUD window (hint, cards, banners, quiz prompt) stays until the hint key closes it.
	 * On: windows close after their old timeouts.
	 */
	public boolean autoCloseWindows = false;
	public boolean lookAtCards = true;
	public boolean mobCards = true;
	public boolean biomeCards = true;
	public boolean timeCards = true;
	public boolean sound = true;
	/** Spoken words from the optional "Hangul Quest Voices" pack. Does nothing while the pack is not loaded. */
	public boolean audio = true;
	public String hudCorner = "BOTTOM_RIGHT";

	// Quests
	public boolean quests = true;
	public boolean questTracker = true;
	public String questTrackerCorner = "TOP_LEFT";
	/** How many tracked quests the HUD log shows at once (the focused one always counts). */
	public int questTrackerRows = 4;
	/**
	 * Interface and gloss language: "AUTO" (default, follows Minecraft's language) or a language code the mod
	 * ships text for, such as "en_us" or "de_de". Older configs hold "EN" or "DE"; they are migrated on load.
	 */
	public String language = Lang.AUTO;
	/** Show the English/German line under the Korean quest text. Off by default: Korean only. */
	public boolean questTranslations = false;
	/** Legacy field, only read while migrating an old config. */
	public boolean questGerman = false;
	/** Journal shows German translations only when the eye button is on. */
	public boolean journalTranslations = false;
	public boolean luteImport = true;
	/** The game tracks quests for the player: three regular slots and one situational (see NextQuestPolicy). */
	public boolean autoPickQuests = true;
	/** The journal's quest tree draws every trail, not only what can be started and what waits behind it. */
	public boolean showAllTrails = false;

	// Learning
	/** Desired recall probability when a card comes due, 0.80 to 0.95 (FSRS). Higher means more reviews. */
	public double retention = DEFAULT_RETENTION;
	/** New words introduced for study per study day: 5, 10 or 15. */
	public int newWordsPerDay = DEFAULT_NEW_WORDS;
	/** Review cards that ask for a typed Korean answer. Off for players who cannot type Korean comfortably. */
	public boolean typedCards = true;

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
			JsonElement stored = raw.get("language");
			JsonElement storedVersion = raw.get("version");
			boolean oldVersion = storedVersion == null || !storedVersion.isJsonPrimitive()
					|| storedVersion.getAsInt() < CURRENT_VERSION;
			if (oldVersion || stored == null || !stored.isJsonPrimitive()
					|| !stored.getAsString().equals(loaded.language)) {
				// Write the migrated values back so they survive the next start.
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
			loaded.language = wantedGerman ? Lang.DE.code() : Lang.AUTO;
			if (wantedGerman) {
				loaded.questTranslations = true;
			}
		}
		// Version 2 and older have neither learning setting; Gson left the defaults in place.
		JsonElement retention = raw.get("retention");
		if (retention == null || !retention.isJsonPrimitive() || !retention.getAsJsonPrimitive().isNumber()) {
			loaded.retention = DEFAULT_RETENTION;
		}
		JsonElement newWords = raw.get("newWordsPerDay");
		if (newWords == null || !newWords.isJsonPrimitive() || !newWords.getAsJsonPrimitive().isNumber()) {
			loaded.newWordsPerDay = DEFAULT_NEW_WORDS;
		}
		// Version 3 and older have no typedCards; a missing or broken value means on, the default.
		JsonElement typed = raw.get("typedCards");
		if (typed == null || !typed.isJsonPrimitive() || !typed.getAsJsonPrimitive().isBoolean()) {
			loaded.typedCards = true;
		}
		// Version 4 and older have neither quest switch; a missing or broken value means the default.
		JsonElement autoPick = raw.get("autoPickQuests");
		if (autoPick == null || !autoPick.isJsonPrimitive() || !autoPick.getAsJsonPrimitive().isBoolean()) {
			loaded.autoPickQuests = true;
		}
		JsonElement showAll = raw.get("showAllTrails");
		if (showAll == null || !showAll.isJsonPrimitive() || !showAll.getAsJsonPrimitive().isBoolean()) {
			loaded.showAllTrails = false;
		}
		// Version 4 and older have no autoCloseWindows; a missing or broken value means off, the default.
		JsonElement autoClose = raw.get("autoCloseWindows");
		if (autoClose == null || !autoClose.isJsonPrimitive() || !autoClose.getAsJsonPrimitive().isBoolean()) {
			loaded.autoCloseWindows = false;
		}
		if (!raw.has("audio") && raw.has("sound") && !loaded.sound) {
			loaded.audio = false; // whoever muted the mod's sounds does not want voices either
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
		if (version < CURRENT_VERSION) {
			version = CURRENT_VERSION;
		}
		if (retention > 1 && retention <= 100) {
			retention /= 100; // written as a percentage by hand
		}
		retention = Double.isNaN(retention) ? DEFAULT_RETENTION
				: Math.round(Math.max(MIN_RETENTION, Math.min(MAX_RETENTION, retention)) * 100) / 100.0;
		newWordsPerDay = nearestNewWords(newWordsPerDay);
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

	/** The offered choice closest to {@code value}; the smaller one when two are equally close. */
	public static int nearestNewWords(int value) {
		int best = NEW_WORD_CHOICES[0];
		for (int choice : NEW_WORD_CHOICES) {
			if (Math.abs(choice - value) < Math.abs(best - value)) {
				best = choice;
			}
		}
		return best;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
