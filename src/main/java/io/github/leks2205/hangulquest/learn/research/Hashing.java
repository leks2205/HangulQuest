package io.github.leks2205.hangulquest.learn.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The one hash the research code uses: SHA-256, so buckets and exported ids do not depend on the JVM. */
public final class Hashing {
	private Hashing() {
	}

	/** SHA-256 of the text as lower-case hex. */
	public static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException missing) {
			throw new IllegalStateException(missing); // every JVM has SHA-256
		}
	}

	/** A stable number in {@code [0, buckets)} for the text. */
	public static int bucket(String text, int buckets) {
		if (buckets <= 0) {
			throw new IllegalArgumentException("buckets must be positive");
		}
		String hex = sha256(text);
		long head = Long.parseUnsignedLong(hex.substring(0, 15), 16); // 60 bits, never negative
		return (int) (head % buckets);
	}
}
