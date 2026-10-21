package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.learn.ReviewKind;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Held-out probes: a small, fixed share of the words that reach three days of stability is set aside and
 * left alone, to measure what the player really remembers without the schedule's help.
 *
 * <ul>
 *   <li>Which words are eligible is a hash of the install salt and the word id ({@link #RATE_PERCENT}%),
 *       so it is the same on every run.</li>
 *   <li>A reserved word gets no reviews and no quest recalls. After 7 and 30 days it is asked once each
 *       (mode {@code PROBE}, never scheduling), and then it is scheduled like any other word.</li>
 *   <li>The state is a small {@code probes.json} (word id to the time of reservation) plus the probe lines
 *       of the review log. Progress.json needs no new fields.</li>
 * </ul>
 *
 * <p>A probe that is missed by a week or more is skipped rather than asked late, so "7 days" and "30 days"
 * keep their meaning; a word whose 30-day window has passed is released.
 */
public final class Probes {
	public static final String FILE = "probes.json";
	/** Share of the words reaching {@link #STABILITY_DAYS} that are held out. */
	public static final int RATE_PERCENT = 5;
	public static final double STABILITY_DAYS = 3;
	public static final int FIRST_PROBE_DAYS = 7;
	public static final int SECOND_PROBE_DAYS = 30;
	/** A probe may be asked up to this many days late; after that the slot is skipped. */
	public static final int LATE_DAYS = 7;
	private static final long DAY = 86_400_000L;
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-research");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Where a word is in its probe schedule. */
	public enum Phase {
		/** Never reserved. */
		NONE,
		/** Reserved and waiting for its next probe. */
		HELD,
		/** The 7-day probe can be asked now. */
		DUE_7,
		/** The 30-day probe can be asked now. */
		DUE_30,
		/** Back to normal scheduling. */
		RELEASED
	}

	public record Due(String wordId, int days) {
		/** The label the probe is logged with. */
		public String ctx() {
			return ctxFor(days);
		}
	}

	private final Path file;
	private final String salt;
	private final Map<String, Long> reserved = new LinkedHashMap<>();
	private final Map<String, Set<Integer>> done = new HashMap<>();

	private Probes(Path dir, String salt) {
		this.file = dir == null ? null : dir.resolve(FILE);
		this.salt = salt == null ? "" : salt;
	}

	/** The ctx a probe of this age is logged with. */
	public static String ctxFor(int days) {
		return "probe:" + days;
	}

	/**
	 * The probes of a profile folder ({@code dir} null keeps everything in memory). {@code history} is the
	 * review log so far, which tells which probes were already asked.
	 */
	public static Probes open(Path dir, String salt, List<ReviewLog.Event> history) {
		Probes probes = new Probes(dir, salt);
		probes.load();
		for (ReviewLog.Event event : history) {
			if (event.mode() == ReviewMode.PROBE && event.kind() == ReviewKind.WORD && event.ctx() != null
					&& event.ctx().startsWith("probe:")) {
				try {
					probes.markDone(event.item(), Integer.parseInt(event.ctx().substring("probe:".length())));
				} catch (NumberFormatException ignored) {
					// a line from a build with another label
				}
			}
		}
		return probes;
	}

	/** True when this word would be held out if it reached the stability threshold. */
	public static boolean eligible(String salt, String wordId) {
		return Hashing.bucket((salt == null ? "" : salt) + ":probe:" + wordId, 100) < RATE_PERCENT;
	}

	/** Reserves the word if it is eligible and has not been reserved before. Returns true when it was reserved now. */
	public synchronized boolean reserveIfEligible(String wordId, long now) {
		if (reserved.containsKey(wordId) || !eligible(salt, wordId)) {
			return false;
		}
		reserved.put(wordId, now);
		save();
		return true;
	}

	/** True while the word is set aside: it must not be reviewed, recalled in a quest or planned. */
	public synchronized boolean held(String wordId, long now) {
		Phase phase = phase(wordId, now);
		return phase == Phase.HELD || phase == Phase.DUE_7 || phase == Phase.DUE_30;
	}

	public synchronized Phase phase(String wordId, long now) {
		Long at = reserved.get(wordId);
		if (at == null) {
			return Phase.NONE;
		}
		Set<Integer> asked = done.getOrDefault(wordId, Set.of());
		long age = now - at;
		if (asked.contains(SECOND_PROBE_DAYS) || age > (SECOND_PROBE_DAYS + LATE_DAYS) * DAY) {
			return Phase.RELEASED;
		}
		if (age >= SECOND_PROBE_DAYS * DAY) {
			return Phase.DUE_30;
		}
		if (!asked.contains(FIRST_PROBE_DAYS) && age >= FIRST_PROBE_DAYS * DAY
				&& age <= (FIRST_PROBE_DAYS + LATE_DAYS) * DAY) {
			return Phase.DUE_7;
		}
		return Phase.HELD;
	}

	/** The word whose probe has waited longest, if any is due. */
	public synchronized Optional<Due> nextDue(long now) {
		Due best = null;
		long bestAt = Long.MAX_VALUE;
		for (Map.Entry<String, Long> entry : reserved.entrySet()) {
			Phase phase = phase(entry.getKey(), now);
			if ((phase == Phase.DUE_7 || phase == Phase.DUE_30) && entry.getValue() < bestAt) {
				bestAt = entry.getValue();
				best = new Due(entry.getKey(), phase == Phase.DUE_7 ? FIRST_PROBE_DAYS : SECOND_PROBE_DAYS);
			}
		}
		return Optional.ofNullable(best);
	}

	/** The probe a word is due for right now, if any. */
	public synchronized Optional<Due> dueFor(String wordId, long now) {
		Phase phase = phase(wordId, now);
		return switch (phase) {
			case DUE_7 -> Optional.of(new Due(wordId, FIRST_PROBE_DAYS));
			case DUE_30 -> Optional.of(new Due(wordId, SECOND_PROBE_DAYS));
			default -> Optional.empty();
		};
	}

	/** Remembers that a probe was asked (the log line itself is written by the caller). */
	public synchronized void markDone(String wordId, int days) {
		done.computeIfAbsent(wordId, ignored -> new HashSet<>()).add(days);
	}

	public synchronized Map<String, Long> reservedAt() {
		return Map.copyOf(reserved);
	}

	public String salt() {
		return salt;
	}

	// ----------------------------------------------------------------- file

	private void load() {
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject words = root.getAsJsonObject("reserved");
			if (words != null) {
				for (Map.Entry<String, JsonElement> entry : words.entrySet()) {
					reserved.put(entry.getKey(), entry.getValue().getAsLong());
				}
			}
		} catch (IOException | RuntimeException unreadable) {
			LOG.warn("could not read {}: {}", file, unreadable.toString());
		}
	}

	private void save() {
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("v", 1);
		JsonObject words = new JsonObject();
		reserved.forEach(words::addProperty);
		root.add("reserved", words);
		try {
			Files.createDirectories(file.getParent());
			Path partial = file.resolveSibling(FILE + ".tmp");
			Files.writeString(partial, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			LOG.warn("could not write {}: {}", file, failed.toString());
		}
	}

	/** Every reservation as a plain list, oldest first (for tests and the export). */
	public synchronized List<String> reservedIds() {
		return new ArrayList<>(reserved.keySet());
	}
}
