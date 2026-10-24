package io.github.leks2205.hangulquest.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole progress document (schema v5: learning + quests + villager requests + vocabulary import info + install
 * metadata).
 */
public final class ProgressData {
	/** Highest schema this build reads and writes; a file with a higher version is opened read-only. */
	public static final int CURRENT_VERSION = 5;

	public int version = CURRENT_VERSION;
	public Map<String, WordProgress> words = new LinkedHashMap<>();
	public Map<String, DayCounts> days = new LinkedHashMap<>();
	public Map<String, Long> achievements = new LinkedHashMap<>();
	public Totals totals = new Totals();
	public Meta meta = new Meta();
	public QuestData quests = new QuestData();
	public VocabData vocab = new VocabData();
	/**
	 * The sound forge (HVPT mini-game): runs and per-contrast accuracy. Added without a version bump: a file without
	 * it reads as "never played", and a build that does not know it keeps it.
	 */
	public HvptData hvpt = new HvptData();
	/** Villager requests: the open ones and how often each was handed in (schema v5). */
	public RequestData requests = new RequestData();

	public static final class RequestData {
		/** The open requests in the order they were accepted; at most one per villager and three in all. */
		public List<OpenRequest> open = new ArrayList<>();
		/** How often each request was handed in, by request id. */
		public Map<String, Integer> finished = new LinkedHashMap<>();
	}

	public static final class OpenRequest {
		/** The request id (requests/<id>.json). */
		public String id;
		/** The UUID of the villager who asked. */
		public String villager;
		/** The villager's name when it asked, for the quest log and the journal. */
		public String name;
		public long acceptedAt;
		/** True while it is in the quest log (and its goal is checked against the world). */
		public boolean tracked;
		/** True once the goal is met; the player still has to talk to the villager. */
		public boolean done;
		/** Partial counter of the goal. */
		public int progress;
	}

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
		/**
		 * The tracked trails the game picked for the player; the rest of {@code tracked} the player chose.
		 * Added without a version bump: a file without it means "the player chose every one", and a build that
		 * does not know it keeps it. Only these may be replaced by the quest picker; a chosen trail never is.
		 */
		public List<String> auto;
		/** The tracked trail that sits in the situational slot (it is in {@code auto} too); null when empty. */
		public String situational;
		/**
		 * Trails the player took out of the log, by the {@link #playMs} at that moment. The picker leaves them out
		 * for 30 minutes of play or until the next finished trail. Added without a version bump, like {@code auto}.
		 */
		public Map<String, Long> dismissed;
		/** Milliseconds of play the quest picker has seen (clipped per tick, so a paused game does not count). */
		public long playMs;
	}

	public static final class Active {
		public String trailId;
		public String stepId;
		public int goalProgress;
		public boolean hintUsed;
		public Side sidequest;
		/**
		 * Where the block that finished an earlier step stands (x, y, z), by step id: the memory behind an
		 * "@stepId" anchor. Left out when the trail has none. Added without a version bump: a file without
		 * it reads as "nothing remembered", and a build that does not know it keeps it.
		 */
		public Map<String, int[]> placed;
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

	public static final class HvptData {
		/** When the first answer was given; the schedule of the 3 weeks counts from here. */
		public long startedAt;
		/** Finished runs by start time in milliseconds (as text, so unknown fields in an entry survive a save). */
		public Map<String, HvptSession> sessions = new LinkedHashMap<>();
		/** Training answers over all runs, by contrast id. */
		public Map<String, HvptTally> contrasts = new LinkedHashMap<>();
	}

	public static final class HvptSession {
		public long t;
		/** PRE, TRAIN or POST. */
		public String phase;
		public int trials;
		public int correct;
		public Map<String, HvptTally> byContrast = new LinkedHashMap<>();
	}

	public static final class HvptTally {
		public int trials;
		public int correct;
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
