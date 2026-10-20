package de.zirks.hangulquest.quest;

import de.zirks.hangulquest.data.Catalog;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.learn.Achievements;
import de.zirks.hangulquest.learn.LearningService;
import de.zirks.hangulquest.learn.ProgressData;
import de.zirks.hangulquest.sense.Observation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drives the tracked trails: clue -> goal -> next clue -> ritual finale, plus the
 * hint/sidequest loop that pauses the original quest.
 *
 * <p>The quest log shows a chosen set of trails — the ones the player checked in the journal. Only
 * those are <em>tracked</em>: their goals are checked against the world and they advance in parallel.
 * Every other trail keeps its progress but is paused until it is checked again. One tracked trail is
 * <em>focused</em>: the hint key, the sidequest and the follow-up chain work on it.
 */
public final class QuestEngine {
	/** How many trails the quest log holds at once, the focused one included. */
	public static final int MAX_TRACKED = 6;

	public interface Feedback {
		void banner(String ko, String de, boolean success);

		void wordCard(Word word);

		void sound(boolean success);

		/** Small celebration after a step; the next clue is passed along for display. */
		default void stepCompleted(String praiseKo, String nextClueKo, String nextClueDe) {
		}

		/** Medium celebration after the hint sidequest is finished. */
		default void sidequestCompleted(Word word) {
		}

		/** Big celebration when the whole trail is finished. */
		default void trailCompleted(Questline trail) {
		}

		/** Several follow-up trails are possible; the player picks one. */
		default void chooseNext(java.util.List<Questline> options) {
		}
	}

	public record HintView(String clueKo, String simplifiedKo, String descriptionKo, Word word,
						   boolean sidequestStarted) {
	}

	/** One row of the quest log, as the HUD and the journal render it. */
	public record TrackView(String id, String titleKo, String titleDe, String topicKey, int koreanLevel,
							String clueKo, String clueDe, String status, int progress, int needed,
							boolean countable, boolean focused, int stepIndex, int stepCount) {
	}

	/** Runtime state of one tracked trail. */
	private static final class Track {
		Questline trail;
		int stepIndex;
		SideQuest sideQuest;
		boolean hintUsed;
		final GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		long startedAt;
		long lastSeenAt;

		Track(Questline trail, long now) {
			this.trail = trail;
			this.startedAt = now;
			this.lastSeenAt = now;
		}

		QuestStep step() {
			List<QuestStep> steps = trail.allSteps();
			if (stepIndex < 0 || stepIndex >= steps.size()) {
				return null;
			}
			return steps.get(stepIndex);
		}

		int stepCount() {
			return trail.allSteps().size();
		}
	}

	private final List<Questline> trails;
	private final Catalog catalog;
	private final LearningService learn;
	private final ProgressData data;
	private final Logger log;
	private final SentenceBuilder.Namer namer;

	/** Runtime state of every trail the player has started, tracked or not. */
	private final LinkedHashMap<String, Track> tracks = new LinkedHashMap<>();
	/** Insertion order of the quest log: what the HUD shows and what is checked against the world. */
	private final java.util.LinkedHashSet<String> trackedIds = new java.util.LinkedHashSet<>();
	private String focusedId;

	private long lastHintAt;
	private Observation lastObs;

	public QuestEngine(List<Questline> trails, Catalog catalog, LearningService learn, Logger log) {
		this.trails = new ArrayList<>(trails);
		this.catalog = catalog;
		this.learn = learn;
		this.data = learn.data();
		this.log = log;
		this.namer = new SentenceBuilder.Namer() {
			@Override
			public String biome(String id) {
				return catalog.byBiomeId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String entity(String id) {
				return catalog.byEntityId(id).map(word -> word.ko).orElse(null);
			}

			@Override
			public String block(String id) {
				return catalog.byItemId(id).map(word -> word.ko).orElse(null);
			}
		};
		restore();
	}

	// ------------------------------------------------------------------ restore

	private void restore() {
		ProgressData.QuestData quests = data.quests;
		if (quests == null) {
			return;
		}
		if (quests.active != null && quests.active.trailId != null) {
			Track track = trackOf(quests.active);
			if (track != null) {
				tracks.put(track.trail.id(), track);
				focusedId = track.trail.id();
			} else {
				quests.active = null;
			}
		}
		if (quests.saved != null) {
			for (ProgressData.Active snapshot : quests.saved.values()) {
				Track track = trackOf(snapshot);
				if (track != null && !tracks.containsKey(track.trail.id())) {
					tracks.put(track.trail.id(), track);
				}
			}
			// Rebuilt from the live tracks on the next persist.
			quests.saved = new LinkedHashMap<>();
		}
		if (quests.tracked != null) {
			for (String id : quests.tracked) {
				if (tracks.containsKey(id)) {
					trackedIds.add(id);
				}
			}
		} else if (focusedId != null) {
			// Before the log had checkboxes only the active quest advanced; keep it that way.
			trackedIds.add(focusedId);
		}
		if (focusedId != null) {
			trackedIds.add(focusedId);
		}
		if (trackedIds.size() > MAX_TRACKED) {
			List<String> keep = new ArrayList<>(trackedIds).subList(0, MAX_TRACKED);
			trackedIds.retainAll(keep);
		}
		if (focusedId == null || !trackedIds.contains(focusedId)) {
			focusedId = trackedIds.isEmpty() ? null : trackedIds.iterator().next();
		}
	}

	private Track trackOf(ProgressData.Active snapshot) {
		if (snapshot == null || snapshot.trailId == null) {
			return null;
		}
		Questline trail = byId(snapshot.trailId);
		if (trail == null) {
			return null;
		}
		Track track = new Track(trail, snapshot.startedAt);
		track.stepIndex = 0;
		List<QuestStep> steps = trail.allSteps();
		if (snapshot.stepId != null) {
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(snapshot.stepId)) {
					track.stepIndex = i;
					break;
				}
			}
		}
		track.hintUsed = snapshot.hintUsed;
		track.lastSeenAt = snapshot.lastSeenAt == 0 ? snapshot.startedAt : snapshot.lastSeenAt;
		QuestStep step = track.step();
		if (step != null) {
			GoalEvaluator.seed(track.tracker, step.goal(), snapshot.goalProgress);
		}
		if (snapshot.sidequest != null && snapshot.sidequest.wordId != null) {
			catalog.byId(snapshot.sidequest.wordId).ifPresent(word ->
					track.sideQuest = SideQuest.restore(word, snapshot.sidequest.stage,
							snapshot.sidequest.attempts, catalog));
		}
		return track;
	}

	/** Replaces all authored trails (live reload) without losing a tracked one. */
	public void replaceTrails(List<Questline> replacement) {
		Questline adaptive = byId("adaptive");
		trails.clear();
		trails.addAll(replacement);
		if (adaptive != null && byId("adaptive") == null) {
			trails.add(adaptive);
		}
		for (Track track : tracks.values()) {
			Questline updated = byId(track.trail.id());
			if (updated == null) {
				continue;
			}
			String stepId = track.step() == null ? null : track.step().id();
			track.trail = updated;
			List<QuestStep> steps = updated.allSteps();
			int index = 0;
			for (int i = 0; i < steps.size(); i++) {
				if (steps.get(i).id().equals(stepId)) {
					index = i;
					break;
				}
			}
			track.stepIndex = index;
		}
	}

	public void addTrail(Questline trail) {
		trails.removeIf(existing -> existing.id().equals(trail.id()));
		trails.add(trail);
	}

	// --------------------------------------------------------------------- tick

	public void tick(Observation obs, Feedback feedback) {
		lastObs = obs;
		if (tracks.isEmpty()) {
			return;
		}
		boolean dirty = false;
		List<Track> snapshot = new ArrayList<>(tracks.values());
		for (Track track : snapshot) {
			if (!tracks.containsKey(track.trail.id()) || !trackedIds.contains(track.trail.id())) {
				continue; // finished this tick, or not in the quest log
			}
			QuestStep step = track.step();
			if (step == null) {
				removeTrack(track.trail.id());
				dirty = true;
				continue;
			}
			if (track.sideQuest != null) {
				continue; // frozen until its sidequest is answered
			}
			int beforeCount = track.tracker.count;
			long beforeDwell = track.tracker.dwellMs;
			boolean complete = GoalEvaluator.tick(step.goal(), step.conditions(), obs, track.tracker);
			if (complete) {
				completeStep(track, obs, feedback);
				dirty = true;
			} else if (track.tracker.count > beforeCount || track.tracker.dwellMs > beforeDwell) {
				track.lastSeenAt = obs.now;
				dirty = true;
			}
		}
		if (dirty) {
			persist(obs.now);
			learn.markDirty();
		}
	}

	private void completeStep(Track track, Observation obs, Feedback feedback) {
		QuestStep step = track.step();
		if (step == null) {
			return;
		}
		boolean focused = track.trail.id().equals(focusedId);
		for (String conceptId : step.teach()) {
			catalog.byConceptId(conceptId).ifPresent(word -> {
				learn.introduce(word, obs.now);
				learn.answer(word.id, true, false, obs.now);
			});
		}
		learn.noteQuestStepCompleted();
		boolean last = track.stepIndex >= track.stepCount() - 1;
		if (last) {
			finish(track, obs, feedback);
			return;
		}
		track.stepIndex++;
		track.tracker.clear();
		track.hintUsed = false;
		track.lastSeenAt = obs.now;
		persist(obs.now);
		unlock(feedback, learn.checkAchievements());
		QuestStep next = track.step();
		if (focused) {
			feedback.stepCompleted("좋아요!", next == null ? "" : clueKo(track, next),
					next == null ? "" : clueDe(next));
		} else {
			feedback.banner(track.trail.titleKo(), "Ziel erreicht · " + track.trail.titleDe(), true);
		}
		feedback.sound(true);
	}

	private void finish(Track track, Observation obs, Feedback feedback) {
		String trailId = track.trail.id();
		boolean focused = trailId.equals(focusedId);
		data.quests.completed.put(trailId, obs.now);
		if (data.quests.badges == null) {
			data.quests.badges = new ArrayList<>();
		}
		if (!data.quests.badges.contains(track.trail.badge())) {
			data.quests.badges.add(track.trail.badge());
		}
		if (data.quests.history == null) {
			data.quests.history = new ArrayList<>();
		}
		data.quests.history.add(new ProgressData.History(trailId, obs.now));
		removeTrack(trailId);
		persist(obs.now);
		unlock(feedback, learn.checkAchievements());
		if (focused) {
			feedback.trailCompleted(track.trail);
			feedback.sound(true);
			advanceAfterCompletion(trailId, obs, feedback);
		} else {
			feedback.banner(track.trail.titleKo(), "Questline geschafft · " + track.trail.titleDe(), true);
			feedback.sound(true);
		}
	}

	/**
	 * After the focused trail: start its unlocked child, or the missing prerequisite of a locked child.
	 * Several options are handed to the UI for a selection popup.
	 */
	private void advanceAfterCompletion(String completedId, Observation obs, Feedback feedback) {
		// Stay on the same tree level, taking the most similar unfinished topic; only then climb.
		Questline finished = byId(completedId);
		if (finished != null) {
			Map<String, Questline> all = trails.stream()
					.collect(Collectors.toMap(Questline::id, java.util.function.Function.identity()));
			int startDepth = depthOf(finished, all);
			int maxDepth = trails.stream().mapToInt(trail -> depthOf(trail, all)).max().orElse(startDepth);
			Comparator<Questline> bestFirst = Comparator
					.comparingInt((Questline trail) -> topicSimilarity(trail, finished, all)).reversed()
					.thenComparingInt(Questline::order);
			for (int depth = startDepth; depth <= maxDepth; depth++) {
				int currentDepth = depth;
				List<Questline> candidates = trails.stream()
						.filter(trail -> !"adaptive".equals(trail.id()))
						.filter(trail -> !trail.id().equals(completedId))
						.filter(trail -> !tracks.containsKey(trail.id()))
						.filter(trail -> !isCompleted(trail.id()))
						.filter(this::unlocked)
						.filter(trail -> depthOf(trail, all) == currentDepth)
						.filter(trail -> topicSimilarity(trail, finished, all) > 0)
						.sorted(bestFirst)
						.toList();
				if (!candidates.isEmpty()) {
					offerNext(candidates.subList(0, 1), obs, feedback);
					return;
				}
			}
		}

		List<Questline> children = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> trail.requires().completed().contains(completedId))
				.toList();
		List<Questline> ready = children.stream()
				.filter(trail -> !tracks.containsKey(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(this::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offerNext(ready, obs, feedback)) {
			return;
		}

		Set<String> missing = new LinkedHashSet<>();
		for (Questline child : children) {
			for (String parent : child.requires().completed()) {
				if (!isCompleted(parent)) {
					missing.add(parent);
				}
			}
		}
		List<Questline> prereq = missing.stream()
				.map(this::byId)
				.filter(java.util.Objects::nonNull)
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !tracks.containsKey(trail.id()))
				.filter(this::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offerNext(prereq, obs, feedback)) {
			return;
		}

		List<Questline> fallback = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !tracks.containsKey(trail.id()))
				.filter(this::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		offerNext(fallback, obs, feedback);
	}

	/** Tree level: roots are 1, a trail is one deeper than its deepest parent. */
	private int depthOf(Questline trail, Map<String, Questline> all) {
		return depthOf(trail, all, new java.util.HashSet<>());
	}

	private int depthOf(Questline trail, Map<String, Questline> all, Set<String> seen) {
		if (!seen.add(trail.id())) {
			return 1; // defensive: a cycle would be rejected by validation
		}
		int depth = 1;
		for (String parentId : trail.requires().completed()) {
			Questline parent = all.get(parentId);
			if (parent != null) {
				depth = Math.max(depth, 1 + depthOf(parent, all, seen));
			}
		}
		seen.remove(trail.id());
		return depth;
	}

	/** 3 = same branch topic, 1 = sibling (shares a parent), 0 = unrelated. */
	private int topicSimilarity(Questline candidate, Questline finished, Map<String, Questline> all) {
		if (java.util.Objects.equals(TopicResolver.keyFor(candidate, all), TopicResolver.keyFor(finished, all))) {
			return 3;
		}
		for (String parent : candidate.requires().completed()) {
			if (finished.requires().completed().contains(parent)) {
				return 1;
			}
		}
		return 0;
	}

	private boolean offerNext(List<Questline> options, Observation obs, Feedback feedback) {
		if (options.isEmpty()) {
			return false;
		}
		if (options.size() == 1 || tracks.size() >= MAX_TRACKED) {
			startTrail(options.get(0).id(), obs, feedback);
		} else {
			feedback.chooseNext(options);
		}
		return true;
	}

	private boolean isCompleted(String trailId) {
		return data.quests.completed != null && data.quests.completed.containsKey(trailId);
	}

	// ----------------------------------------------------------------- tracking

	/**
	 * Starts tracking a trail and focuses it. A trail that is already tracked is only focused, so the
	 * quest log never loses progress by starting the same quest twice.
	 */
	public boolean startTrail(String trailId, Observation obs, Feedback feedback) {
		Questline trail = byId(trailId);
		if (trail == null) {
			return false;
		}
		long now = obs == null ? System.currentTimeMillis() : obs.now;
		Track existing = tracks.get(trailId);
		if (existing == null) {
			existing = resumeOrCreate(trail, now);
			tracks.put(trailId, existing);
		}
		if (!trackedIds.contains(trailId) && !makeRoomInLog()) {
			return false;
		}
		trackedIds.add(trailId);
		focusedId = trailId;
		Track track = existing;
		persist(now);
		QuestStep step = track.step();
		if (step != null) {
			feedback.banner(clueKo(track, step), clueDe(step), true);
		}
		return true;
	}

	private Track resumeOrCreate(Questline trail, long now) {
		ProgressData.Active snapshot = data.quests.saved == null ? null : data.quests.saved.get(trail.id());
		if (snapshot != null) {
			Track resumed = trackOf(snapshot);
			if (resumed != null) {
				return resumed;
			}
		}
		return new Track(trail, now);
	}

	/**
	 * Makes room in the quest log by taking the oldest background quest out of it. Its progress is kept,
	 * so it can be checked again later.
	 */
	private boolean makeRoomInLog() {
		while (trackedIds.size() >= MAX_TRACKED) {
			String oldest = null;
			for (String id : trackedIds) {
				if (!id.equals(focusedId)) {
					oldest = id;
					break;
				}
			}
			if (oldest == null) {
				return false;
			}
			trackedIds.remove(oldest);
		}
		return true;
	}

	private void removeTrack(String trailId) {
		tracks.remove(trailId);
		trackedIds.remove(trailId);
		if (data.quests.saved != null) {
			data.quests.saved.remove(trailId);
		}
		if (trailId.equals(focusedId)) {
			focusedId = tracks.isEmpty() ? null : tracks.keySet().iterator().next();
		}
	}

	/** Focuses a tracked trail without touching its progress. */
	public boolean focus(String trailId) {
		if (!trackedIds.contains(trailId)) {
			return false;
		}
		focusedId = trailId;
		persist(lastObs == null ? System.currentTimeMillis() : lastObs.now);
		return true;
	}

	/**
	 * Adds or removes a trail in the quest log. Checked trails are drawn on the HUD and their goals are
	 * checked against the world; unchecked trails keep their progress but do not advance.
	 */
	public boolean setTracked(String trailId, boolean on) {
		long now = lastObs == null ? System.currentTimeMillis() : lastObs.now;
		if (!on) {
			if (trackedIds.remove(trailId)) {
				if (trailId.equals(focusedId)) {
					focusedId = trackedIds.isEmpty() ? null : trackedIds.iterator().next();
				}
				persist(now);
			}
			return true;
		}
		if (!tracks.containsKey(trailId) && byId(trailId) == null) {
			return false;
		}
		if (!tracks.containsKey(trailId)) {
			tracks.put(trailId, new Track(byId(trailId), now));
		}
		if (!trackedIds.contains(trailId) && !makeRoomInLog()) {
			return false;
		}
		trackedIds.add(trailId);
		focusedId = trailId;
		persist(now);
		return true;
	}

	/** Takes a trail out of the log; its progress is kept for when it is checked again. */
	public void untrack(String trailId) {
		setTracked(trailId, false);
	}

	public void autoStart(Observation obs, Feedback feedback) {
		if (!trackedIds.isEmpty()) {
			return;
		}
		Questline best = null;
		for (Questline trail : trails) {
			if (trail.adaptive() || !unlocked(trail)) {
				continue;
			}
			if (data.quests.completed != null && data.quests.completed.containsKey(trail.id())) {
				continue;
			}
			String dimension = trail.requires().dimension();
			if (dimension != null && obs != null && !dimension.equals(obs.dimension)) {
				continue;
			}
			if (best == null || trail.order() < best.order()
					|| (trail.order() == best.order() && trail.id().compareTo(best.id()) < 0)) {
				best = trail;
			}
		}
		if (best != null) {
			startTrail(best.id(), obs, feedback);
		}
	}

	/** Takes the focused trail out of the log (the journal's "Abbrechen"); its progress is kept. */
	public void abandon() {
		if (focusedId == null) {
			return;
		}
		setTracked(focusedId, false);
	}

	// -------------------------------------------------------------------- hint

	public HintView hint(Observation obs, Feedback feedback) {
		Track track = focused();
		if (track == null) {
			return null;
		}
		if (obs.now - lastHintAt < 2000) {
			return null;
		}
		lastHintAt = obs.now;
		QuestStep step = track.step();
		if (step == null) {
			return null;
		}
		Word word = track.sideQuest != null ? track.sideQuest.word() : firstWord(step);
		String simplified;
		if (step.hintKo() != null && !step.hintKo().isBlank()) {
			// Authored hints win even when the step has conditions; they are written to include them.
			simplified = step.hintKo();
		} else if (!step.conditions().isEmpty()) {
			simplified = word != null
					? SentenceBuilder.hint(step.goal(), word, step.conditions(), namer)
					: clueKo(track, step);
		} else {
			simplified = word != null
					? SentenceBuilder.hint(step.goal(), word, List.of(), namer)
					: clueKo(track, step);
		}
		String description = word != null && word.hasDescKo() ? word.descKo : null;
		boolean known = word != null && learn.isKnown(word.id);
		boolean started = false;
		if (track.sideQuest == null && !known && word != null && !track.hintUsed) {
			track.sideQuest = SideQuest.start(word, catalog);
			track.hintUsed = true;
			feedback.wordCard(word);
			persist(obs.now);
			started = true;
		}
		return new HintView(clueKo(track, step), simplified, description, word,
				started || track.sideQuest != null);
	}

	public boolean answerSidequest(int index, Feedback feedback) {
		Track track = focused();
		if (track == null || track.sideQuest == null) {
			return false;
		}
		boolean correct = track.sideQuest.answer(index);
		Word word = track.sideQuest.word();
		long now = System.currentTimeMillis();
		if (correct) {
			learn.answer(word.id, true, true, now);
		}
		if (track.sideQuest.isDone()) {
			Word learned = track.sideQuest.word();
			track.sideQuest = null;
			learn.noteSidequestCompleted();
			persist(now);
			feedback.sidequestCompleted(learned);
			feedback.sound(true);
			unlock(feedback, learn.checkAchievements());
		} else {
			persist(now);
			feedback.sound(correct);
		}
		return correct;
	}

	public void hintWordCard(Feedback feedback) {
		Track track = focused();
		if (track == null) {
			return;
		}
		Word word = firstWord(track.step());
		if (word != null) {
			feedback.wordCard(word);
		}
	}

	private void unlock(Feedback feedback, List<Achievements.Achievement> unlocked) {
		for (Achievements.Achievement achievement : unlocked) {
			feedback.banner(achievement.ko(), achievement.de(), true);
			feedback.sound(true);
		}
	}

	// --------------------------------------------------------------- persistence

	private void persist(long now) {
		ProgressData.Active focusedSnapshot = null;
		Map<String, ProgressData.Active> background = new LinkedHashMap<>();
		for (Track track : tracks.values()) {
			ProgressData.Active snapshot = snapshot(track, now);
			if (track.trail.id().equals(focusedId)) {
				focusedSnapshot = snapshot;
			} else {
				background.put(track.trail.id(), snapshot);
			}
		}
		data.quests.active = focusedSnapshot;
		data.quests.saved = background;
		data.quests.tracked = new ArrayList<>(trackedIds);
		learn.markDirty();
	}

	private ProgressData.Active snapshot(Track track, long now) {
		ProgressData.Active snapshot = new ProgressData.Active();
		snapshot.trailId = track.trail.id();
		QuestStep step = track.step();
		snapshot.stepId = step == null ? null : step.id();
		snapshot.goalProgress = track.tracker.count;
		snapshot.hintUsed = track.hintUsed;
		snapshot.startedAt = track.startedAt;
		snapshot.lastSeenAt = now;
		if (track.sideQuest != null) {
			snapshot.sidequest = new ProgressData.Side();
			snapshot.sidequest.wordId = track.sideQuest.word().id;
			snapshot.sidequest.stage = track.sideQuest.stage();
			snapshot.sidequest.attempts = track.sideQuest.attempts();
		}
		return snapshot;
	}

	// ------------------------------------------------------------------ queries

	public List<Questline> trails() {
		return trails;
	}

	public Questline byId(String trailId) {
		for (Questline trail : trails) {
			if (trail.id().equals(trailId)) {
				return trail;
			}
		}
		return null;
	}

	public boolean unlocked(Questline trail) {
		if (learn.discovered() < trail.requires().minWordsKnown()) {
			return false;
		}
		for (String parent : trail.requires().completed()) {
			if (data.quests.completed == null || !data.quests.completed.containsKey(parent)) {
				return false;
			}
		}
		return true;
	}

	private Track focused() {
		return focusedId == null ? null : tracks.get(focusedId);
	}

	public Questline activeTrail() {
		Track track = focused();
		return track == null ? null : track.trail;
	}

	public String focusedId() {
		return focusedId;
	}

	public boolean isTracked(String trailId) {
		return trackedIds.contains(trailId);
	}

	/** Partial counter of any trail that has a state, tracked or not. */
	public int progressOf(String trailId) {
		Track track = tracks.get(trailId);
		return track == null ? 0 : track.tracker.count;
	}

	/** True when the trail was started at some point and still has a saved state. */
	public boolean hasProgress(String trailId) {
		return tracks.containsKey(trailId);
	}

	public int trackedCount() {
		return trackedIds.size();
	}

	/** The quest log, focused trail first, then in the order the player added them. */
	public List<TrackView> trackedViews() {
		List<TrackView> views = new ArrayList<>();
		for (Track track : tracks.values()) {
			if (!trackedIds.contains(track.trail.id())) {
				continue;
			}
			QuestStep step = track.step();
			if (step == null) {
				continue;
			}
			String status = lastObs == null ? "" : QuestStatus.text(step, lastObs, track.tracker, catalog);
			views.add(new TrackView(track.trail.id(), track.trail.titleKo(), track.trail.titleDe(),
					track.trail.topicKey(), track.trail.koreanLevel(),
					clueKo(track, step), clueDe(step), status,
					track.tracker.count, needed(step), countable(step),
					track.trail.id().equals(focusedId), track.stepIndex, track.stepCount()));
		}
		views.sort(Comparator.comparing((TrackView view) -> !view.focused()));
		return views;
	}

	public SideQuest sideQuest() {
		Track track = focused();
		return track == null ? null : track.sideQuest;
	}

	public boolean sidequestActive() {
		return sideQuest() != null;
	}

	public QuestStep currentStep() {
		Track track = focused();
		return track == null ? null : track.step();
	}

	public int stepIndex() {
		Track track = focused();
		return track == null ? 0 : track.stepIndex;
	}

	public int stepCount() {
		Track track = focused();
		return track == null ? 0 : track.stepCount();
	}

	private static boolean countable(QuestStep step) {
		if (step == null) {
			return false;
		}
		return switch (step.goal()) {
			case Goal.BreakBlock ignored -> true;
			case Goal.PlaceBlock ignored -> true;
			case Goal.AcquireItem ignored -> true;
			case Goal.ConsumeItem ignored -> true;
			case Goal.DefeatEntity ignored -> true;
			default -> false;
		};
	}

	private static int needed(QuestStep step) {
		if (step == null) {
			return 0;
		}
		return switch (step.goal()) {
			case Goal.BreakBlock g -> g.count();
			case Goal.PlaceBlock g -> g.count();
			case Goal.AcquireItem g -> g.count();
			case Goal.ConsumeItem g -> g.count();
			case Goal.DefeatEntity g -> g.count();
			default -> 1;
		};
	}

	/** Short Korean line for the focused trail: unmet condition, or progress toward the goal. */
	public String statusText() {
		Track track = focused();
		if (track == null || lastObs == null) {
			return "";
		}
		return QuestStatus.text(track.step(), lastObs, track.tracker, catalog);
	}

	public long goalDwellMs() {
		Track track = focused();
		return track == null ? 0L : track.tracker.dwellMs;
	}

	public long goalDwellNeededMs() {
		QuestStep step = currentStep();
		if (step == null) {
			return 0L;
		}
		return switch (step.goal()) {
			case Goal.LookAtEntity entity -> entity.dwellMs();
			case Goal.LookAtBlock block -> block.dwellMs();
			default -> 0L;
		};
	}

	public int goalProgress() {
		Track track = focused();
		return track == null ? 0 : track.tracker.count;
	}

	public int goalNeeded() {
		return needed(currentStep());
	}

	public List<Word> currentWords() {
		List<Word> words = new ArrayList<>();
		QuestStep step = currentStep();
		if (step == null) {
			return words;
		}
		for (String concept : step.teach()) {
			catalog.byConceptId(concept).ifPresent(words::add);
		}
		return words;
	}

	public Word firstWord(QuestStep step) {
		if (step == null || step.teach().isEmpty()) {
			return null;
		}
		return catalog.byConceptId(step.teach().get(0)).orElse(null);
	}

	public String clueKo(QuestStep step) {
		return clueKo(focused(), step);
	}

	private String clueKo(Track track, QuestStep step) {
		if (step == null) {
			return "";
		}
		int level = track == null ? 1 : track.trail.koreanLevel();
		boolean finale = "finale".equals(step.id());
		if (!step.conditions().isEmpty()) {
			// Every condition must show up in the Korean sentence; generated text guarantees it.
			return SentenceBuilder.normal(step.goal(), firstWord(step), step.conditions(),
					level, finale, namer);
		}
		if (step.hasClueKo()) {
			return step.clueKo();
		}
		return SentenceBuilder.normal(step.goal(), firstWord(step), step.conditions(),
				level, finale, namer);
	}

	public String clueDe(QuestStep step) {
		if (step == null) {
			return "";
		}
		return step.clueDe() == null ? "" : step.clueDe();
	}

	public String hintKo(QuestStep step) {
		if (step == null) {
			return "";
		}
		if (step.hintKo() != null && !step.hintKo().isBlank()) {
			return step.hintKo();
		}
		Word word = firstWord(step);
		return word == null ? clueKo(step)
				: SentenceBuilder.hint(step.goal(), word, step.conditions(), namer);
	}

	public boolean hintUsed() {
		Track track = focused();
		return track != null && track.hintUsed;
	}

	/** Dev-only: seeds a partial counter on a tracked trail so UI demos show filled progress bars. */
	public void demoProgress(String trailId, int count) {
		Track track = tracks.get(trailId);
		if (track == null) {
			return;
		}
		track.tracker.count = Math.max(0, count);
		persist(lastObs == null ? System.currentTimeMillis() : lastObs.now);
	}

	/** Unused, kept so subclasses/tests can share the engine's logger. */
	public Logger log() {
		return log;
	}
}
