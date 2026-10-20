package de.zirks.hangulquest.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whole progress document (schema v2: learning + quests + vocabulary import info). */
public final class ProgressData {
	public int version = 2;
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
	}

	public static final class Meta {
		public long firstPlayed;
		public long lastPlayed;
	}

	public static final class QuestData {
		public Active active;
		public Map<String, Long> completed = new LinkedHashMap<>();
		public List<String> badges = new ArrayList<>();
		public List<History> history = new ArrayList<>();
		public Adaptive adaptive;
		/** Paused trails: switching away keeps step index, partial progress and hint state. */
		public Map<String, Active> saved = new LinkedHashMap<>();
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
