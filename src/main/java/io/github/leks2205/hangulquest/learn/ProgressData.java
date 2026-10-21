package io.github.leks2205.hangulquest.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whole progress document (schema v3: learning + quests + vocabulary import info + install metadata). */
public final class ProgressData {
	/** Highest schema this build reads and writes; a file with a higher version is opened read-only. */
	public static final int CURRENT_VERSION = 3;

	public int version = CURRENT_VERSION;
	public Map<String, WordProgress> words = new LinkedHashMap<>();
	public Map<String, DayCounts> days = new LinkedHashMap<>();
	public Map<String, Long> achievements = new LinkedHashMap<>();
	public Totals totals = new Totals();
	public Meta meta = new Meta();
	public QuestData quests = new QuestData();
	public VocabData vocab = new VocabData();

	public static final class Totals {
		public int seen;
		public int correct;
		public int wrong;
		public int reviews;
		/** Sidequests finished, over all sessions. */
		public int sidequestsCompleted;
		/** Quest steps finished, over all sessions. */
		public int questStepsCompleted;
	}

	public static final class Meta {
		public long firstPlayed;
		public long lastPlayed;
		/** Schema versions this file has been through, ascending. */
		public List<Integer> schemaHistory = new ArrayList<>();
		/** Random hex made once per install, local only, for future A/B assignment. */
		public String installSalt;
	}

	public static final class QuestData {
		public Active active;
		public Map<String, Long> completed = new LinkedHashMap<>();
		public List<String> badges = new ArrayList<>();
		public List<History> history = new ArrayList<>();
		public Adaptive adaptive;
		/**
		 * Quest states that are not the focused one: tracked-but-background quests, and quests that are
		 * not in the HUD log at all. Both keep step index, partial progress and hint state; only the
		 * tracked ones advance.
		 */
		public Map<String, Active> saved = new LinkedHashMap<>();
		/**
		 * Trails whose rows are shown in the HUD quest log and whose goals are checked against the world.
		 * {@code null} means "an old save before this field existed": every saved trail is treated as tracked.
		 */
		public List<String> tracked;
	}

	public static final class Active {
		public String trailId;
		public String stepId;
		public int goalProgress;
		public boolean hintUsed;
		public Side sidequest;
		public long startedAt;
		public long lastSeenAt;
	}

	public static final class Side {
		public String wordId;
		public int stage;
		public int attempts;
	}

	public static final class History {
		public String trailId;
		public long finishedAt;

		public History() {
		}

		public History(String trailId, long finishedAt) {
			this.trailId = trailId;
			this.finishedAt = finishedAt;
		}
	}

	public static final class Adaptive {
		public long seed;
		public String generatedFor;
		public List<String> wordIds = new ArrayList<>();
	}

	public static final class VocabData {
		public LuteInfo luteImport;
	}

	public static final class LuteInfo {
		public String file;
		public long mtime;
		public int imported;
		public int skipped;
		public int phrases;
		public long at;
	}
}
