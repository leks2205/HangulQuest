package io.github.leks2205.hangulquest.learn.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Word-level experiments from {@code experiments.json}. A word's arm is a hash of the install salt, the
 * experiment id and the word id, so it is the same on every run and nothing has to be stored. Words, not
 * people, are the unit: the same player sees both arms.
 */
public final class Experiments {
	public static final String RESOURCE = "/assets/hangulquest/data/experiments.json";
	public static final String RETENTION = "retention";
	public static final String PRODUCTIVE = "productive";
	public static final String ENACTMENT = "enactment";
	/** The stability at which productive cards unlock when no experiment says otherwise. */
	public static final double DEFAULT_UNLOCK_DAYS = 3;
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest-research");
	private static final Experiments NONE = new Experiments(List.of());

	public record Experiment(String id, List<String> arms, boolean active, String unit) {
		public Experiment {
			arms = List.copyOf(arms);
		}
	}

	private static volatile Experiments shared;

	private final Map<String, Experiment> byId = new LinkedHashMap<>();

	private Experiments(List<Experiment> list) {
		for (Experiment experiment : list) {
			byId.put(experiment.id(), experiment);
		}
	}

	public static Experiments of(List<Experiment> list) {
		return new Experiments(list);
	}

	/** No experiments: every word behaves as the settings say. */
	public static Experiments none() {
		return NONE;
	}

	/** The file in the jar, loaded once. */
	public static Experiments shared() {
		Experiments loaded = shared;
		if (loaded == null) {
			loaded = load();
			shared = loaded;
		}
		return loaded;
	}

	/** An unreadable or missing file gives no experiments rather than a crash. */
	public static Experiments load() {
		try (InputStream stream = Experiments.class.getResourceAsStream(RESOURCE)) {
			return stream == null ? NONE : parse(stream);
		} catch (IOException | RuntimeException broken) {
			LOG.warn("could not read experiments.json: {}", broken.toString());
			return NONE;
		}
	}

	static Experiments parse(InputStream stream) throws IOException {
		List<Experiment> list = new ArrayList<>();
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("experiments")) {
				JsonObject object = element.getAsJsonObject();
				List<String> arms = new ArrayList<>();
				for (JsonElement arm : object.getAsJsonArray("arms")) {
					arms.add(arm.getAsString());
				}
				JsonElement unit = object.get("unit");
				list.add(new Experiment(object.get("id").getAsString(), arms,
						object.has("active") && object.get("active").getAsBoolean(),
						unit == null ? "word" : unit.getAsString()));
			}
		}
		return new Experiments(list);
	}

	public List<Experiment> all() {
		return List.copyOf(byId.values());
	}

	public Experiment get(String id) {
		return byId.get(id);
	}

	/** The arm of a word in an experiment, active or not; null for an unknown experiment or one without arms. */
	public String arm(String salt, String experimentId, String wordId) {
		Experiment experiment = byId.get(experimentId);
		if (experiment == null || experiment.arms().isEmpty() || wordId == null) {
			return null;
		}
		String key = (salt == null ? "" : salt) + ":" + experimentId + ":" + wordId;
		return experiment.arms().get(Hashing.bucket(key, experiment.arms().size()));
	}

	/** The arm of a word, or null when the experiment is not defined or switched off. */
	public String activeArm(String salt, String experimentId, String wordId) {
		Experiment experiment = byId.get(experimentId);
		return experiment == null || !experiment.active() ? null : arm(salt, experimentId, wordId);
	}

	/** The desired retention this word's arm asks for, if the retention experiment is on. */
	public OptionalDouble retention(String salt, String wordId) {
		String arm = activeArm(salt, RETENTION, wordId);
		try {
			return arm == null ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(arm));
		} catch (NumberFormatException bad) {
			return OptionalDouble.empty();
		}
	}

	/** Stability in days at which productive cards unlock for this word. */
	public double productiveUnlockDays(String salt, String wordId) {
		String arm = activeArm(salt, PRODUCTIVE, wordId);
		if (arm == null) {
			return DEFAULT_UNLOCK_DAYS;
		}
		try {
			return Double.parseDouble(arm.endsWith("d") ? arm.substring(0, arm.length() - 1) : arm);
		} catch (NumberFormatException bad) {
			return DEFAULT_UNLOCK_DAYS;
		}
	}

	/**
	 * What the review log's {@code arm} field says for a word: every active experiment as
	 * {@code expId:arm}, comma-separated, in file order; null when none is active. {@code skip} names
	 * experiments that did not apply (the retention arm while the player picked their own retention).
	 */
	public String tag(String salt, String wordId, Set<String> skip) {
		StringBuilder tag = new StringBuilder();
		for (Experiment experiment : byId.values()) {
			if (!experiment.active() || experiment.arms().isEmpty() || skip.contains(experiment.id())) {
				continue;
			}
			if (tag.length() > 0) {
				tag.append(',');
			}
			tag.append(experiment.id()).append(':').append(arm(salt, experiment.id(), wordId));
		}
		return tag.length() == 0 ? null : tag.toString();
	}

	/** True when at least one experiment is switched on. */
	public boolean anyActive() {
		return byId.values().stream().anyMatch(Experiment::active);
	}
}
