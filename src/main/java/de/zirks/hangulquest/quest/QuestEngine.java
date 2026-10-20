package de.zirks.hangulquest.quest;

import de.zirks.hangulquest.data.Catalog;
import de.zirks.hangulquest.data.Word;
import de.zirks.hangulquest.learn.Achievements;
import de.zirks.hangulquest.learn.LearningService;
import de.zirks.hangulquest.learn.ProgressData;
import de.zirks.hangulquest.sense.Observation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the active trail: clue -> goal -> next clue -> ritual finale, plus the
 * hint/sidequest loop that pauses the original quest.
 */
public final class QuestEngine {
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

	private final List<Questline> trails;
	private final Catalog catalog;
	private final LearningService learn;
	private final ProgressData data;
	private final Logger log;
	private final SentenceBuilder.Namer namer;
	private final GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();

	private Questline active;
	private int stepIndex;
	private SideQuest sideQuest;
	private boolean stepHintUsed;
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

	private void restore() {
		ProgressData.Active saved = data.quests.active;
		if (saved == null || saved.trailId == null) {
			return;
		}
		Questline trail = byId(saved.trailId);
		if (trail == null) {
			data.quests.active = null;
			return;
		}
		active = trail;
		stepIndex = 0;
		List<QuestStep> steps = trail.allSteps();
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(saved.stepId)) {
				stepIndex = i;
				break;
			}
		}
		stepHintUsed = saved.hintUsed;
		QuestStep step = currentStep();
		if (step != null) {
			GoalEvaluator.seed(tracker, step.goal(), saved.goalProgress);
		}
		if (saved.sidequest != null && saved.sidequest.wordId != null) {
			catalog.byId(saved.sidequest.wordId).ifPresent(word ->
					sideQuest = SideQuest.restore(word, saved.sidequest.stage, saved.sidequest.attempts, catalog));
		}
	}

	/** Replaces all authored trails (live reload) without losing the active one. */
	public void replaceTrails(List<Questline> replacement) {
		Questline adaptive = byId("adaptive");
		String activeId = active == null ? null : active.id();
		String stepId = currentStep() == null ? null : currentStep().id();
		trails.clear();
		trails.addAll(replacement);
		if (adaptive != null && byId("adaptive") == null) {
			trails.add(adaptive);
		}
		if (activeId == null) {
			return;
		}
		Questline updated = byId(activeId);
		if (updated == null) {
			// Keep the old object so the player can still finish an edited-away trail.
			trails.add(active);
			return;
		}
		active = updated;
		List<QuestStep> steps = updated.allSteps();
		int index = -1;
		for (int i = 0; i < steps.size(); i++) {
			if (steps.get(i).id().equals(stepId)) {
				index = i;
				break;
			}
		}
		stepIndex = Math.max(0, index);
	}

	public void addTrail(Questline trail) {
		trails.removeIf(existing -> existing.id().equals(trail.id()));
		trails.add(trail);
	}

	public void tick(Observation obs, Feedback feedback) {
		lastObs = obs;
		if (active == null) {
			return;
		}
		if (sideQuest != null) {
			persist(obs.now);
			return;
		}
		QuestStep step = currentStep();
		if (step == null) {
			active = null;
			persist(obs.now);
			return;
		}
		int beforeCount = tracker.count;
		long beforeDwell = tracker.dwellMs;
		boolean complete = GoalEvaluator.tick(step.goal(), step.conditions(), obs, tracker);
		if (data.quests.active != null) {
			data.quests.active.goalProgress = tracker.count;
		}
		boolean activeProgressed = complete || tracker.count > beforeCount || tracker.dwellMs > beforeDwell;
		if (complete) {
			completeStep(obs, feedback);
		} else if (!activeProgressed && tryOpportunisticSwitch(obs, feedback)) {
			// switched to the quest that actually matches this action
		} else {
			learn.markDirty();
		}
	}


	/**
	 * If the current step did not react to this observation but another unlocked trail's current
	 * step does, switch to that trail and let the same action count there (e.g. mining coal while
	 * a torch quest is active switches to the coal quest with 1/2 already counted).
	 */
	private boolean tryOpportunisticSwitch(Observation obs, Feedback feedback) {
		if (active == null) {
			return false;
		}
		List<Questline> candidates = new ArrayList<>();
		for (Questline trail : trails) {
			if (trail == active || trail.adaptive() || isCompleted(trail.id())) {
				continue;
			}
			if (!unlocked(trail)) {
				continue;
			}
			String dimension = trail.requires().dimension();
			if (dimension != null && !dimension.equals(obs.dimension)) {
				continue;
			}
			QuestStep step = stepFor(trail);
			if (step == null) {
				continue;
			}
			if (step.goal() instanceof Goal.ReachBiome
					|| step.goal() instanceof Goal.ReachDimension
					|| step.goal() instanceof Goal.ReachHeight) {
				continue; // state goals would switch every tick while standing there
			}
			GoalEvaluator.Tracker probe = new GoalEvaluator.Tracker();
			boolean done = GoalEvaluator.tick(step.goal(), step.conditions(), obs, probe);
			boolean triggered = done || probe.count > 0 || probe.dwellMs > 0
					|| (step.goal() instanceof Goal.AcquireItem acquire
					&& GoalEvaluator.heldCount(obs, acquire.item()) > 0);
			if (triggered) {
				candidates.add(trail);
			}
		}
		if (candidates.isEmpty()) {
			return false;
		}
		candidates.sort(java.util.Comparator.comparingInt(Questline::order)
				.thenComparing(Questline::id));
		Questline target = candidates.get(0);
		startTrail(target.id(), obs, feedback);
		QuestStep step = currentStep();
		if (step != null) {
			boolean complete = GoalEvaluator.tick(step.goal(), step.conditions(), obs, tracker);
			if (data.quests.active != null) {
				data.quests.active.goalProgress = tracker.count;
			}
			if (complete) {
				completeStep(obs, feedback);
			} else {
				learn.markDirty();
			}
		}
		feedback.banner("다른 퀘스트로 바꿨어요", "Quest gewechselt: " + target.titleDe(), false);
		return true;
	}

	private QuestStep stepFor(Questline trail) {
		ProgressData.Active saved = data.quests.saved == null ? null : data.quests.saved.get(trail.id());
		if (saved != null && saved.stepId != null) {
			for (QuestStep step : trail.allSteps()) {
				if (step.id().equals(saved.stepId)) {
					return step;
				}
			}
		}
		return trail.allSteps().isEmpty() ? null : trail.allSteps().get(0);
	}

	private void completeStep(Observation obs, Feedback feedback) {
		QuestStep step = currentStep();
		if (step == null) {
			return;
		}
		for (String conceptId : step.teach()) {
			catalog.byConceptId(conceptId).ifPresent(word -> {
				learn.introduce(word, obs.now);
				learn.answer(word.id, true, false, obs.now);
			});
		}
		learn.noteQuestStepCompleted();
		boolean last = stepIndex >= active.allSteps().size() - 1;
		if (last) {
			finish(obs, feedback);
			return;
		}
		stepIndex++;
		tracker.clear();
		stepHintUsed = false;
		persist(obs.now);
		unlock(feedback, learn.checkAchievements());
		QuestStep next = currentStep();
		feedback.stepCompleted("좋아요!", next == null ? "" : clueKo(next), next == null ? "" : clueDe(next));
		feedback.sound(true);
	}

	private void finish(Observation obs, Feedback feedback) {
		String trailId = active.id();
		forgetSaved(trailId);
		data.quests.completed.put(trailId, obs.now);
		if (data.quests.badges == null) {
			data.quests.badges = new ArrayList<>();
		}
		if (!data.quests.badges.contains(active.badge())) {
			data.quests.badges.add(active.badge());
		}
		if (data.quests.history == null) {
			data.quests.history = new ArrayList<>();
		}
		data.quests.history.add(new ProgressData.History(trailId, obs.now));
		feedback.trailCompleted(active);
		feedback.sound(true);
		active = null;
		sideQuest = null;
		stepIndex = 0;
		tracker.clear();
		persist(obs.now);
		unlock(feedback, learn.checkAchievements());
		advanceAfterCompletion(trailId, obs, feedback);
	}

	/**
	 * After a trail: start its unlocked child, or the missing prerequisite of a locked child.
	 * Several options are handed to the UI for a selection popup.
	 */
	private void advanceAfterCompletion(String completedId, Observation obs, Feedback feedback) {
		// Stay on the same tree level, taking the most similar unfinished topic; only then climb.
		Questline finished = byId(completedId);
		if (finished != null) {
			java.util.Map<String, Questline> all = trails.stream()
					.collect(java.util.stream.Collectors.toMap(Questline::id, java.util.function.Function.identity()));
			int startDepth = depthOf(finished, all);
			int maxDepth = trails.stream().mapToInt(trail -> depthOf(trail, all)).max().orElse(startDepth);
			java.util.Comparator<Questline> bestFirst = java.util.Comparator
					.comparingInt((Questline trail) -> topicSimilarity(trail, finished, all)).reversed()
					.thenComparingInt(Questline::order);
			for (int depth = startDepth; depth <= maxDepth; depth++) {
				int currentDepth = depth;
				List<Questline> candidates = trails.stream()
						.filter(trail -> !"adaptive".equals(trail.id()))
						.filter(trail -> !trail.id().equals(completedId))
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
				.filter(trail -> !isCompleted(trail.id()))
				.filter(this::unlocked)
				.sorted(java.util.Comparator.comparingInt(Questline::order))
				.toList();
		if (offerNext(ready, obs, feedback)) {
			return;
		}

		java.util.Set<String> missing = new java.util.LinkedHashSet<>();
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
				.filter(this::unlocked)
				.sorted(java.util.Comparator.comparingInt(Questline::order))
				.toList();
		if (offerNext(prereq, obs, feedback)) {
			return;
		}

		List<Questline> fallback = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(this::unlocked)
				.sorted(java.util.Comparator.comparingInt(Questline::order))
				.toList();
		offerNext(fallback, obs, feedback);
	}


	/** Tree level: roots are 1, a trail is one deeper than its deepest parent. */
	private int depthOf(Questline trail, java.util.Map<String, Questline> all) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		return depthOf(trail, all, seen);
	}

	private int depthOf(Questline trail, java.util.Map<String, Questline> all, java.util.Set<String> seen) {
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
	private int topicSimilarity(Questline candidate, Questline finished, java.util.Map<String, Questline> all) {
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
		if (options.size() == 1) {
			startTrail(options.get(0).id(), obs, feedback);
		} else {
			feedback.chooseNext(options);
		}
		return true;
	}

	private boolean isCompleted(String trailId) {
		return data.quests.completed != null && data.quests.completed.containsKey(trailId);
	}

	public boolean startTrail(String trailId, Observation obs, Feedback feedback) {
		Questline trail = byId(trailId);
		if (trail == null) {
			return false;
		}
		long now = obs == null ? System.currentTimeMillis() : obs.now;
		stashActive(now);
		active = trail;
		ProgressData.Active resumed = data.quests.saved == null ? null : data.quests.saved.remove(trail.id());
		if (resumed != null && resumed.stepId != null) {
			data.quests.active = resumed;
			restore();
		} else {
			stepIndex = 0;
			sideQuest = null;
			stepHintUsed = false;
			tracker.clear();
			data.quests.active = new ProgressData.Active();
			data.quests.active.trailId = trail.id();
			data.quests.active.stepId = trail.allSteps().get(0).id();
			data.quests.active.startedAt = now;
		}
		persist(now);
		QuestStep step = currentStep();
		if (step != null) {
			feedback.banner(clueKo(step), clueDe(step), true);
		}
		return true;
	}

	public void autoStart(Observation obs, Feedback feedback) {
		if (active != null) {
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

	/** Pauses the current trail; switching back resumes at the same step and counter. */
	public void abandon() {
		stashActive(System.currentTimeMillis());
		active = null;
		sideQuest = null;
		stepIndex = 0;
		stepHintUsed = false;
		tracker.clear();
		data.quests.active = null;
		learn.markDirty();
	}

	private void stashActive(long now) {
		if (active == null || data.quests.active == null) {
			return;
		}
		filledActive();
		ProgressData.Active current = data.quests.active;
		ProgressData.Active copy = new ProgressData.Active();
		copy.trailId = current.trailId;
		copy.stepId = current.stepId;
		copy.goalProgress = current.goalProgress;
		copy.hintUsed = current.hintUsed;
		copy.startedAt = current.startedAt;
		copy.lastSeenAt = now;
		if (current.sidequest != null) {
			copy.sidequest = new ProgressData.Side();
			copy.sidequest.wordId = current.sidequest.wordId;
			copy.sidequest.stage = current.sidequest.stage;
			copy.sidequest.attempts = current.sidequest.attempts;
		}
		if (data.quests.saved == null) {
			data.quests.saved = new java.util.LinkedHashMap<>();
		}
		data.quests.saved.put(active.id(), copy);
	}

	/** Completed trails never resume from a paused snapshot. */
	private void forgetSaved(String trailId) {
		if (data.quests.saved != null) {
			data.quests.saved.remove(trailId);
		}
	}

	public HintView hint(Observation obs, Feedback feedback) {
		if (active == null) {
			return null;
		}
		if (obs.now - lastHintAt < 2000) {
			return null;
		}
		lastHintAt = obs.now;
		QuestStep step = currentStep();
		if (step == null) {
			return null;
		}
		Word word = sideQuest != null ? sideQuest.word() : firstWord(step);
		String simplified;
		if (step.hintKo() != null && !step.hintKo().isBlank()) {
			// Authored hints win even when the step has conditions; they are written to include them.
			simplified = step.hintKo();
		} else if (!step.conditions().isEmpty()) {
			simplified = word != null
					? SentenceBuilder.hint(step.goal(), word, step.conditions(), namer)
					: clueKo(step);
		} else {
			simplified = word != null
					? SentenceBuilder.hint(step.goal(), word, List.of(), namer)
					: clueKo(step);
		}
		String description = word != null && word.hasDescKo() ? word.descKo : null;
		boolean known = word != null && learn.isKnown(word.id);
		boolean started = false;
		if (sideQuest == null && !known && word != null && !stepHintUsed) {
			sideQuest = SideQuest.start(word, catalog);
			stepHintUsed = true;
			data.quests.active.sidequest = new ProgressData.Side();
			data.quests.active.sidequest.wordId = word.id;
			data.quests.active.sidequest.stage = 1;
			data.quests.active.sidequest.attempts = 0;
			filledActive();
			feedback.wordCard(word);
			started = true;
		}
		persist(obs.now);
		return new HintView(clueKo(step), simplified, description, word, started || sideQuest != null);
	}

	public boolean answerSidequest(int index, Feedback feedback) {
		if (sideQuest == null) {
			return false;
		}
		boolean correct = sideQuest.answer(index);
		Word word = sideQuest.word();
		long now = System.currentTimeMillis();
		if (correct) {
			learn.answer(word.id, true, true, now);
		}
		if (sideQuest.isDone()) {
			Word learned = sideQuest.word();
			sideQuest = null;
			if (data.quests.active != null) {
				data.quests.active.sidequest = null;
			}
			learn.noteSidequestCompleted();
			persist(now);
			feedback.sidequestCompleted(learned);
			feedback.sound(true);
			unlock(feedback, learn.checkAchievements());
		} else {
			if (data.quests.active != null && data.quests.active.sidequest != null) {
				data.quests.active.sidequest.stage = sideQuest.stage();
				data.quests.active.sidequest.attempts = sideQuest.attempts();
			}
			persist(now);
			feedback.sound(correct);
		}
		return correct;
	}

	public void hintWordCard(Feedback feedback) {
		QuestStep step = currentStep();
		if (step != null) {
			Word word = firstWord(step);
			if (word != null) {
				feedback.wordCard(word);
			}
		}
	}

	private void unlock(Feedback feedback, List<Achievements.Achievement> unlocked) {
		for (Achievements.Achievement achievement : unlocked) {
			feedback.banner(achievement.ko(), achievement.de(), true);
			feedback.sound(true);
		}
	}

	private void filledActive() {
		if (data.quests.active == null) {
			data.quests.active = new ProgressData.Active();
		}
		data.quests.active.trailId = active.id();
		QuestStep step = currentStep();
		if (step != null) {
			data.quests.active.stepId = step.id();
		}
		data.quests.active.goalProgress = tracker.count;
		data.quests.active.hintUsed = stepHintUsed;
	}

	private void persist(long now) {
		if (active == null) {
			data.quests.active = null;
			learn.markDirty();
			return;
		}
		if (data.quests.active == null) {
			data.quests.active = new ProgressData.Active();
		}
		filledActive();
		data.quests.active.lastSeenAt = now;
		learn.markDirty();
	}

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

	public Questline activeTrail() {
		return active;
	}

	public SideQuest sideQuest() {
		return sideQuest;
	}

	public boolean sidequestActive() {
		return sideQuest != null;
	}

	public QuestStep currentStep() {
		if (active == null) {
			return null;
		}
		List<QuestStep> steps = active.allSteps();
		if (stepIndex < 0 || stepIndex >= steps.size()) {
			return null;
		}
		return steps.get(stepIndex);
	}

	public int stepIndex() {
		return stepIndex;
	}

	public int stepCount() {
		return active == null ? 0 : active.allSteps().size();
	}

	/** Short Korean line: unmet condition, or progress toward the current goal. */
	public String statusText() {
		return QuestStatus.text(currentStep(), lastObs, tracker, catalog);
	}

	public long goalDwellMs() {
		return tracker.dwellMs;
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
		return tracker.count;
	}

	public int goalNeeded() {
		QuestStep step = currentStep();
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
		if (step == null) {
			return "";
		}
		int level = active == null ? 1 : active.koreanLevel();
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
		return stepHintUsed;
	}
}
