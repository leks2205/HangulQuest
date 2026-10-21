package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Export research data": a zip of the review log with every word id replaced by a hash, and a consent text
 * in English and German. Nothing is sent anywhere; the player decides whether to share the file.
 *
 * <p>The hash key is made fresh for each export and thrown away, so the ids cannot be turned back into words
 * and two exports cannot be matched up. The zip holds no world, server, player or account name: the log has
 * none, and where the log's context text names a quest it is reduced to the word {@code quest}.
 */
public final class Export {
	public static final String LOG_ENTRY = "reviews.ndjson";
	public static final String CONSENT_ENTRY = "consent.txt";
	public static final String CONSENT_KEY = "hangulquest.ui.research.consent";
	private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

	private Export() {
	}

	/**
	 * Writes {@code hangulquest-research-<date>.zip} into {@code targetDir} (a number is added when the name
	 * is taken) and returns its path.
	 *
	 * @param profileDir the profile folder holding the review log
	 * @param isCore     whether a word id is a core word (curriculum grade 1 or 2, or NIKL grade A); may be null
	 */
	public static Path write(Path profileDir, Path targetDir, LocalDate date, Predicate<String> isCore) throws IOException {
		byte[] key = new byte[16];
		new SecureRandom().nextBytes(key);
		return write(profileDir, targetDir, date, isCore, HexFormat.of().formatHex(key));
	}

	/** Same with a given hash key (tests). */
	public static Path write(Path profileDir, Path targetDir, LocalDate date, Predicate<String> isCore, String key)
			throws IOException {
		Files.createDirectories(targetDir);
		Path zip = freeName(targetDir, date);
		Path partial = zip.resolveSibling(zip.getFileName() + ".tmp");
		List<ReviewLog.Event> events = ReviewLog.readAll(profileDir);
		try (OutputStream out = Files.newOutputStream(partial); ZipOutputStream archive = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
			entry(archive, CONSENT_ENTRY, consent());
			entry(archive, LOG_ENTRY, log(events, key, isCore));
		}
		Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
		return zip;
	}

	private static Path freeName(Path dir, LocalDate date) {
		Path path = dir.resolve("hangulquest-research-" + date + ".zip");
		for (int n = 2; Files.exists(path); n++) {
			path = dir.resolve("hangulquest-research-" + date + "-" + n + ".zip");
		}
		return path;
	}

	private static void entry(ZipOutputStream archive, String name, String text) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0); // the same bytes for the same content
		archive.putNextEntry(entry);
		archive.write(text.getBytes(StandardCharsets.UTF_8));
		archive.closeEntry();
	}

	/** The consent text: English first, then German, whatever the interface language is. */
	public static String consent() {
		return I18n.t(Lang.EN, CONSENT_KEY) + "\n\n----------------------------------------\n\n" + I18n.t(Lang.DE, CONSENT_KEY) + "\n";
	}

	/** The review log as ndjson with hashed ids. */
	static String log(List<ReviewLog.Event> events, String key, Predicate<String> isCore) {
		StringBuilder text = new StringBuilder();
		for (ReviewLog.Event event : events) {
			JsonObject json = new JsonObject();
			json.addProperty("t", event.t());
			json.addProperty("item", hashId(key, event.item()));
			json.addProperty("kind", event.kind().name());
			json.addProperty("mode", event.mode().name());
			json.add("grade", event.grade() == null ? JsonNull.INSTANCE : GSON.toJsonTree(event.grade()));
			json.addProperty("latencyMs", event.latencyMs());
			json.addProperty("elapsedDays", event.elapsedDays());
			json.add("ctx", event.ctx() == null ? JsonNull.INSTANCE : GSON.toJsonTree(coarse(event.ctx())));
			json.add("arm", event.arm() == null ? JsonNull.INSTANCE : GSON.toJsonTree(event.arm()));
			json.addProperty("v", event.v());
			if (isCore != null) {
				json.addProperty("core", isCore.test(event.item()));
			}
			text.append(GSON.toJson(json)).append('\n');
		}
		return text.toString();
	}

	/** A word id as 16 hex characters; the key is not part of the output. */
	static String hashId(String key, String id) {
		return Hashing.sha256(key + ":" + id).substring(0, 16);
	}

	/** Where something happened, without quest or trail names: quest steps become {@code quest}. */
	static String coarse(String ctx) {
		if (ctx.startsWith("probe:") || ctx.equals("placement") || ctx.startsWith("card:")) {
			return ctx.length() <= 16 && ctx.matches("[a-z0-9:_]+") ? ctx : "other";
		}
		if (ctx.contains("/") || ctx.contains("|")) {
			return "quest";
		}
		return ctx.matches("[a-z_]{1,16}") ? ctx : "other";
	}
}
