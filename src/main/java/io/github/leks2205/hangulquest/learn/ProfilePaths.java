package io.github.leks2205.hangulquest.learn;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Where a Minecraft account keeps its progress: {@code <config>/profiles/<account-uuid>/}. The progress
 * file of the single-profile versions ({@code <config>/progress.json}) is copied into the first profile
 * that is opened, once, and is never moved or deleted.
 */
public final class ProfilePaths {
	public static final String PROFILES = "profiles";
	public static final String DEFAULT_PROFILE = "default";
	/** Written next to the profiles once the old file has been copied. */
	public static final String MARKER = ".legacy-progress-copied";
	private static final String FILE = "progress.json";
	private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private static final UUID NIL = new UUID(0L, 0L);

	private ProfilePaths() {
	}

	/** The folder name for an account; {@code default} when there is no usable UUID (offline, dev). */
	public static String profileName(UUID account) {
		return account == null || NIL.equals(account) ? DEFAULT_PROFILE : account.toString();
	}

	/** Same, for text; anything that is not a UUID can never become a path. */
	public static String profileName(String account) {
		if (account == null) {
			return DEFAULT_PROFILE;
		}
		String text = account.trim().toLowerCase(Locale.ROOT);
		if (!UUID_TEXT.matcher(text).matches()) {
			return DEFAULT_PROFILE;
		}
		return profileName(UUID.fromString(text));
	}

	/** The profile folder, without touching the disk. */
	public static Path profileDir(Path configDir, String profileName) {
		return configDir.resolve(PROFILES).resolve(profileName);
	}

	/**
	 * Returns the profile folder for {@code account}, creating it. The first time any profile is opened
	 * and an old {@code progress.json} exists, that file is copied in (unless the profile already has
	 * one) and the marker is written, so it never happens again.
	 */
	public static Path open(Path configDir, UUID account, Logger log) {
		Path profile = profileDir(configDir, profileName(account));
		try {
			Files.createDirectories(profile);
			adoptLegacy(configDir, profile, log);
		} catch (IOException failed) {
			log.warn("could not prepare profile folder {}: {}", profile, failed.toString());
		}
		return profile;
	}

	static void adoptLegacy(Path configDir, Path profile, Logger log) throws IOException {
		Path marker = configDir.resolve(PROFILES).resolve(MARKER);
		Path legacy = configDir.resolve(FILE);
		if (Files.exists(marker) || !Files.isRegularFile(legacy)) {
			return;
		}
		Path target = profile.resolve(FILE);
		if (!Files.exists(target)) {
			Path partial = profile.resolve(FILE + ".copy");
			Files.copy(legacy, partial, StandardCopyOption.REPLACE_EXISTING);
			Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
			log.info("copied progress.json into profile {}", profile.getFileName());
		}
		Files.writeString(marker, profile.getFileName() + "\n");
	}
}
