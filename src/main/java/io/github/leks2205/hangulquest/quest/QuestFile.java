package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One questline file after reading: the parsed trail, or why there is none. Reading never throws,
 * so one broken file cannot stop the others.
 *
 * @param name      file name, for logs
 * @param digest    SHA-256 of the raw bytes
 * @param trail     null when the file is broken, has no id, or is {@code identical}
 * @param override  the file asks to replace a shipped trail of the same id
 * @param hasTopic  the file sets its own "topic" (an override otherwise keeps the shipped one)
 * @param identical byte-for-byte a shipped file (an old exported copy): not parsed, not loaded
 * @param error     null unless the file is unreadable
 */
public record QuestFile(String name, String digest, Questline trail, boolean override,
						boolean hasTopic, boolean identical, String error) {

	public static QuestFile read(String name, byte[] bytes) {
		String digest = digest(bytes);
		try {
			JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			// a misspelt or missing field in a goal or condition makes the file unreadable, with the trail and step named
			java.util.List<String> fieldProblems = StrictFields.problems(root);
			if (!fieldProblems.isEmpty()) {
				return new QuestFile(name, digest, null, false, false, false, String.join("; ", fieldProblems));
			}
			Questline trail = QuestLoader.parse(root);
			if (trail == null) {
				return new QuestFile(name, digest, null, false, false, false, "no id");
			}
			boolean override = root.has("override") && root.get("override").getAsBoolean();
			return new QuestFile(name, digest, trail, override, root.has("topic"), false, null);
		} catch (Exception broken) {
			return new QuestFile(name, digest, null, false, false, false, broken.toString());
		}
	}

	public static QuestFile identical(String name, String digest) {
		return new QuestFile(name, digest, null, false, false, true, null);
	}

	public static String digest(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
