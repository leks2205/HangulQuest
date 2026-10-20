package de.zirks.hangulquest.config;

import de.zirks.hangulquest.util.JsonIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings; unknown/missing fields fall back to the defaults below. */
public final class Config {
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
	public boolean questGerman = false;
	/** Journal shows German translations only when the eye button is on. */
	public boolean journalTranslations = false;
	public boolean luteImport = true;

	public static Config load(Path file) {
		if (!Files.exists(file)) {
			Config config = new Config();
			config.save(file);
			return config;
		}
		try {
			Config loaded = JsonIO.read(file, Config.class);
			if (loaded == null) {
				return new Config();
			}
			loaded.sanitize();
			return loaded;
		} catch (IOException | RuntimeException broken) {
			// Never crash the client because of a bad config; keep the broken file once.
			try {
				Files.move(file, file.resolveSibling("config.json.broken-" + System.currentTimeMillis()));
			} catch (IOException ignored) {
				// ignore
			}
			Config config = new Config();
			config.save(file);
			return config;
		}
	}

	public void save(Path file) {
		try {
			JsonIO.write(file, this);
		} catch (IOException ignored) {
			// logging happens in the caller where a logger is available
		}
	}

	public void sanitize() {
		if (version < 2) {
			version = 2;
		}
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
