package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.I18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * What to track for the player. With "pick quests automatically" on, the quest log keeps three regular
 * slots filled and one situational slot (see {@link SituationalSlot}):
 * <ul>
 * <li>on the first join, and whenever a regular slot is empty, the best-scoring trails are tracked;</li>
 * <li>when a tracked trail is finished, its best available follow-up is tracked at once, else the
 * same scoring picks;</li>
 * <li>a trail the player took out of the log is left out for {@link Weights#DISMISS_PLAY_MS} of play or until
 * the next finished trail, and the log is not refilled meanwhile;</li>
 * <li>only the game's own picks are ever replaced, and only while they have no progress.</li>
 * </ul>
 * With it off, nothing is tracked by itself and a finished focused trail only offers the next ones in a
 * popup, which is kept alive across ticks until the player picks, starts something else or it runs out.
 */
final class NextQuestPolicy {
	/** How long the player has to pick before the first option is started for them. */
	static final long OFFER_MS = 60_000L;
	private static final long GRACE_MS = 5_000L;

	/**
	 * Every number of the scoring, in one place. A trail's score is the sum of the lines below; trails under
	 * {@link #MIN_PICK} are not picked by themselves. Higher is better.
	 *
	 * <pre>
	 * level fit     30 at the player's level (the highest finished trail, at least 1), -12 per level above,
	 *               -5 per level below; trails more than {@link #MAX_LEVEL_JUMP} level above are never picked
	 * trail order   20 - order/10, at least 0: the curated beginner trails come first
	 * gameplay loop 14 - 2 per step of wood, stone, light, food, animals, water and night, mountain and village
	 * chapter       12 - 4 per chapter rank, for the first three chapters (house, light, field)
	 * reach         0 .. -16 for a biome, creature or height to find; -25 for a village or rare biome;
	 *               -10 for each advanced material (see {@link Reach}); out of reach is never picked
	 * length        +2 up to 3 steps, then -2 per step beyond 5, at most -10
	 * words         -3 for each word the trail wants beyond those the player has met (up to {@link #WORD_SLACK})
	 * follow-up     +40 when it directly follows the trail just finished, +12 same topic, +6 sibling
	 * variety       -15 for each trail already in the log or just picked with the same topic
	 * resume        +10 for a trail the player already worked on
	 * </pre>
	 */
	static final class Weights {
		static final int LEVEL_FIT = 30;
		static final int LEVEL_ABOVE = 12;
		static final int LEVEL_BELOW = 5;
		static final int MAX_LEVEL_JUMP = 1;
		static final int ORDER_MAX = 20;
		static final int LOOP_MAX = 14;
		static final int LOOP_STEP = 2;
		static final int CHAPTER_MAX = 12;
		static final int CHAPTER_STEP = 4;
		static final int SHORT_STEPS = 3;
		static final int SHORT_BONUS = 2;
		static final int LONG_FROM = 5;
		static final int PER_EXTRA_STEP = -2;
		static final int LONG_CAP = -10;
		static final int PER_MISSING_WORD = -3;
		static final int CHILD = 40;
		static final int SAME_TOPIC = 12;
		static final int SIBLING = 6;
		static final int TOPIC_TAKEN = -15;
		static final int RESUME = 10;
		/** Below this a trail is not picked by itself. */
		static final int MIN_PICK = 35;
		/** A trail asking for this many more words than met so far still counts: its first steps teach them. */
		static final int WORD_SLACK = 4;
		/** How long a trail the player took out of the log stays out, in play time. */
		static final long DISMISS_PLAY_MS = 30L * 60_000L;
		/** How often an empty regular slot is looked at. */
		static final long FILL_EVERY_MS = 4_000L;
		/** The most one tick adds to the play clock, so a long pause does not count as play. */
		static final long MAX_TICK_MS = 2_000L;

		private Weights() {
		}
	}

	/** The topics in the order of the gameplay loop: wood, tools, light, food, farming, then the rest. */
	private static final List<String> LOOP = List.of("나무", "돌", "불", "음식", "동물", "물", "밤", "산", "마을", "가족");

	private final QuestEngine engine;
	private final TrackRegistry registry;
	private final ProgressData data;

	private List<Questline> pending = List.of();
	private long pendingUntil;
	private final SituationalSlot slot = new SituationalSlot();
	private long lastClock;
	private long lastFill = Long.MIN_VALUE / 2;
	/** Chapter rank of a trail (0 is the first chapter), -1 when it is in none; set by the game. */
	private ToIntFunction<String> chapterRank = id -> -1;

	NextQuestPolicy(QuestEngine engine, TrackRegistry registry, ProgressData data) {
		this.engine = engine;
		this.registry = registry;
		this.data = data;
	}

	// ------------------------------------------------------------------ popup

	/**
	 * The options of the open popup, empty when there is none. Other trails being active does not close
	 * it: it ends when the player picks, checks another trail, every option is done, or, only while windows close
	 * by themselves, the time is up.
	 */
	List<Questline> pending(long now) {
		if (pending.isEmpty()) {
			return pending;
		}
		if ((now > pendingUntil && engine.popupsExpire()) || pending.stream().allMatch(trail -> isCompleted(trail.id()))) {
			pending = List.of();
		}
		return pending;
	}

	void dismiss() {
		pending = List.of();
	}

	// ----------------------------------------------------------------- policy

	// ------------------------------------------------------------ automatic picks

	void chapterRank(ToIntFunction<String> rank) {
		this.chapterRank = rank == null ? id -> -1 : rank;
	}

	/** On joining a world: fills the empty regular slots. Does nothing with automatic picks off. */
	void autoStart(Observation obs, QuestEngine.Feedback feedback) {
		if (!engine.autoPick()) {
			return;
		}
		clock(obs.now);
		boolean first = registry.slotCount() == 0;
		fillRegular(obs, feedback, null,
				first ? QuestEngine.Feedback.AutoReason.START : QuestEngine.Feedback.AutoReason.FILL, false);
	}

	/** Every tick: the play clock, the situational slot and a look at empty regular slots every few seconds. */
	void tick(Observation obs, QuestEngine.Feedback feedback) {
		clock(obs.now);
		if (!engine.autoPick()) {
			return;
		}
		situational(obs, feedback);
		if (obs.now - lastFill >= Weights.FILL_EVERY_MS) {
			lastFill = obs.now;
			if (registry.situationalId() == null && registry.slotCount() < TrackRegistry.MAX_TRACKED) {
				slot.reevaluate(); // the slot is open: look again at the context that already holds
			}
			if (freeRegular() > 0) {
				fillRegular(obs, feedback, null, QuestEngine.Feedback.AutoReason.FILL, false);
			}
		}
	}

	private void clock(long now) {
		if (lastClock > 0 && now > lastClock) {
			data.quests.playMs += Math.min(now - lastClock, Weights.MAX_TICK_MS);
		}
		lastClock = now;
	}

	/** The player took a trail out of the log: it stays out for a while, and the log is not refilled meanwhile. */
	void dismissed(String trailId) {
		if (data.quests.dismissed == null) {
			data.quests.dismissed = new LinkedHashMap<>();
		}
		data.quests.dismissed.put(trailId, data.quests.playMs);
	}

	private boolean isDismissed(String trailId) {
		Long at = data.quests.dismissed == null ? null : data.quests.dismissed.get(trailId);
		return at != null && data.quests.playMs - at < Weights.DISMISS_PLAY_MS;
	}

	/** True while any dismissal is still in force. */
	private boolean quiet() {
		if (data.quests.dismissed == null || data.quests.dismissed.isEmpty()) {
			return false;
		}
		data.quests.dismissed.values().removeIf(at -> data.quests.playMs - at >= Weights.DISMISS_PLAY_MS);
		return !data.quests.dismissed.isEmpty();
	}

	/** Regular slots that are empty and may be filled now. */
	private int freeRegular() {
		return Math.max(0, Math.min(TrackRegistry.REGULAR_SLOTS - registry.regularCount(),
				TrackRegistry.MAX_TRACKED - registry.slotCount()));
	}

	/** What the quest picker knows about the player at one moment. */
	record Ctx(int level, int discovered, Questline finished, Map<String, Questline> all,
			   List<String> takenTopics) {
	}

	Ctx context(Questline finished) {
		int level = 1;
		if (data.quests.completed != null) {
			for (String id : data.quests.completed.keySet()) {
				Questline done = engine.byId(id);
				if (done != null && !done.adaptive()) {
					level = Math.max(level, done.koreanLevel());
				}
			}
		}
		Map<String, Questline> all = engine.graph().byId();
		List<String> taken = new ArrayList<>();
		for (String id : registry.trackedIds()) {
			Questline tracked = all.get(id);
			if (tracked != null && !tracked.adaptive()) {
				taken.add(TopicResolver.keyFor(tracked, all));
			}
		}
		return new Ctx(level, engine.discovered(), finished, all, taken);
	}

	/** Whether the game may track this trail for the player now (the score decides whether it should). */
	boolean eligible(Questline trail, Ctx ctx, Observation obs) {
		if (trail.adaptive() || isCompleted(trail.id()) || registry.isTracked(trail.id()) || isDismissed(trail.id())) {
			return false;
		}
		if (trail.koreanLevel() > ctx.level() + Weights.MAX_LEVEL_JUMP) {
			return false;
		}
		for (String parent : trail.requires().completed()) {
			if (!isCompleted(parent)) {
				return false;
			}
		}
		if (trail.requires().minWordsKnown() - ctx.discovered() > Weights.WORD_SLACK) {
			return false;
		}
		String dimension = trail.requires().dimension();
		if (dimension != null && obs != null && !dimension.equals(obs.dimension)) {
			return false;
		}
		return !engine.traits(trail).reach().outOfReach();
	}

	/** The score of a trail for this player; see {@link Weights}. */
	int score(Questline trail, Ctx ctx) {
		TrailTraits traits = engine.traits(trail);
		int score = trail.koreanLevel() >= ctx.level()
				? Weights.LEVEL_FIT - Weights.LEVEL_ABOVE * (trail.koreanLevel() - ctx.level())
				: Weights.LEVEL_FIT - Weights.LEVEL_BELOW * (ctx.level() - trail.koreanLevel());
		score += Math.max(0, Weights.ORDER_MAX - trail.order() / 10);
		String topic = TopicResolver.keyFor(trail, ctx.all());
		int loop = LOOP.indexOf(topic);
		score += Math.max(0, Weights.LOOP_MAX - Weights.LOOP_STEP * (loop < 0 ? LOOP.size() : loop));
		int chapter = chapterRank.applyAsInt(trail.id());
		if (chapter >= 0) {
			score += Math.max(0, Weights.CHAPTER_MAX - Weights.CHAPTER_STEP * chapter);
		}
		score += traits.reach().penalty();
		int steps = traits.steps();
		score += steps <= Weights.SHORT_STEPS ? Weights.SHORT_BONUS
				: Math.max(Weights.LONG_CAP, Weights.PER_EXTRA_STEP * Math.max(0, steps - Weights.LONG_FROM));
		score += Weights.PER_MISSING_WORD * Math.max(0, trail.requires().minWordsKnown() - ctx.discovered());
		if (ctx.finished() != null) {
			if (trail.requires().completed().contains(ctx.finished().id())) {
				score += Weights.CHILD;
			} else {
				int similarity = topicSimilarity(trail, ctx.finished(), ctx.all());
				score += similarity >= 3 ? Weights.SAME_TOPIC : similarity == 1 ? Weights.SIBLING : 0;
			}
		}
		for (String taken : ctx.takenTopics()) {
			if (taken.equals(topic)) {
				score += Weights.TOPIC_TAKEN;
			}
		}
		if (registry.has(trail.id())) {
			score += Weights.RESUME;
		}
		return score;
	}

	/** The best eligible trail that scores enough, or null; ties go to the lower order, then the id. */
	private Questline best(Ctx ctx, Observation obs, Predicate<Questline> only) {
		Questline best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Questline trail : engine.trails()) {
			if (!only.test(trail) || !eligible(trail, ctx, obs)) {
				continue;
			}
			int score = score(trail, ctx);
			if (score < Weights.MIN_PICK) {
				continue;
			}
			if (best == null || score > bestScore
					|| (score == bestScore && (trail.order() < best.order()
					|| (trail.order() == best.order() && trail.id().compareTo(best.id()) < 0)))) {
				best = trail;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * What the picker would track now for the empty regular slots, without tracking it: first the best direct
	 * follow-up of {@code finished} (if any), then the best of the rest, one at a time so that the picks differ
	 * in topic.
	 */
	List<Questline> choose(Observation obs, Questline finished, int free) {
		Ctx ctx = context(finished);
		List<Questline> picked = new ArrayList<>();
		if (finished != null) {
			Questline child = best(ctx, obs, trail -> trail.requires().completed().contains(finished.id()));
			if (child != null) {
				picked.add(child);
				ctx.takenTopics().add(TopicResolver.keyFor(child, ctx.all()));
			}
		}
		while (picked.size() < free) {
			Questline next = best(ctx, obs, trail -> !picked.contains(trail));
			if (next == null) {
				break;
			}
			picked.add(next);
			ctx.takenTopics().add(TopicResolver.keyFor(next, ctx.all()));
		}
		return picked;
	}

	private void fillRegular(Observation obs, QuestEngine.Feedback feedback, Questline finished,
							 QuestEngine.Feedback.AutoReason reason, boolean wasFocused) {
		int free = freeRegular();
		if (free <= 0 || (finished == null && quiet())) {
			return;
		}
		List<Questline> tracked = new ArrayList<>();
		for (Questline trail : choose(obs, finished, free)) {
			if (engine.autoTrack(trail.id(), false, obs.now)) {
				tracked.add(trail);
			}
		}
		if (!tracked.isEmpty()) {
			if (wasFocused && reason == QuestEngine.Feedback.AutoReason.FOLLOW_UP) {
				engine.focus(tracked.get(0).id());
			}
			announce(List.copyOf(tracked), reason, feedback);
		}
	}

	/** The window that tells the player what the game tracked: "다음 퀘스트: ..." for a follow-up or a refill. */
	private void announce(List<Questline> picked, QuestEngine.Feedback.AutoReason reason, QuestEngine.Feedback feedback) {
		boolean gloss = engine.translations();
		String key = (reason == QuestEngine.Feedback.AutoReason.START ? "start:" : "next:") + picked.get(0).id();
		if (reason == QuestEngine.Feedback.AutoReason.START) {
			String[] counters = {"한", "두", "세", "네"};
			int count = picked.size();
			String ko = "퀘스트 " + (count <= counters.length ? counters[count - 1] : String.valueOf(count)) + " 개를 골랐어요.";
			feedback.notice(key, ko, gloss ? I18n.t("hangulquest.ui.quest.auto_started", count) : "",
					String.join(" · ", picked.stream().map(Questline::titleKo).toList()),
					gloss ? String.join(" · ", picked.stream().map(Questline::title).toList()) : "");
		} else {
			Questline first = picked.get(0);
			feedback.notice(key, "다음 퀘스트: " + first.titleKo(),
					gloss ? I18n.t("hangulquest.ui.quest.next_quest", first.title()) : "", "", "");
		}
		feedback.autoTracked(picked, reason);
	}

	/** A tracked trail is finished: the picker's turn. */
	private void afterAutoCompletion(String completedId, Observation obs, QuestEngine.Feedback feedback,
									 boolean focused) {
		if (data.quests.dismissed != null) {
			data.quests.dismissed.clear();
		}
		slot.reevaluate();
		Questline finished = engine.byId(completedId);
		lastFill = obs.now;
		fillRegular(obs, feedback, finished,
				finished == null ? QuestEngine.Feedback.AutoReason.FILL : QuestEngine.Feedback.AutoReason.FOLLOW_UP,
				focused);
	}

	// -------------------------------------------------------- situational slot

	private void situational(Observation obs, QuestEngine.Feedback feedback) {
		Set<Situation.Tag> context = slot.settled(Situation.of(obs), obs.now);
		if (context == null || context.isEmpty()) {
			return; // no new context, or nothing to match: the slot keeps what it has
		}
		String current = registry.situationalId();
		if (current != null) {
			Track track = registry.get(current);
			if (track != null && track.hasProgress()) {
				return; // the player is on it: it stays until it is done or taken out
			}
		}
		Questline best = bestFor(context, current, obs);
		if (best == null || best.id().equals(current)) {
			return;
		}
		if (current != null) {
			engine.autoUntrack(current, obs.now);
		}
		if (engine.autoTrack(best.id(), true, obs.now)) {
			feedback.situationalChanged(best);
		}
	}

	/** The trail that fits the context best, the current one included; null when none fits. */
	Questline bestFor(Set<Situation.Tag> context, String current, Observation obs) {
		Ctx ctx = context(null);
		Questline best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Questline trail : engine.trails()) {
			if (!trail.id().equals(current) && !eligible(trail, ctx, obs)) {
				continue;
			}
			int fit = engine.traits(trail).fit(context);
			if (fit <= 0) {
				continue;
			}
			int total = fit * 2 + score(trail, ctx);
			if (best == null || total > bestScore || (total == bestScore && trail.id().compareTo(best.id()) < 0)) {
				best = trail;
				bestScore = total;
			}
		}
		return best;
	}

	/**
	 * After the focused trail: start its unlocked child, or the missing prerequisite of a locked child.
	 * Several options are handed to the UI for a selection popup.
	 */
	void afterCompletion(String completedId, Observation obs, QuestEngine.Feedback feedback, boolean focused) {
		if (engine.autoPick()) {
			afterAutoCompletion(completedId, obs, feedback, focused);
		} else if (focused) {
			offerAfter(completedId, obs, feedback);
		}
	}

	private void offerAfter(String completedId, Observation obs, QuestEngine.Feedback feedback) {
		// Stay on the same tree level, taking the most similar unfinished topic; only then climb.
		Questline finished = engine.byId(completedId);
		TrailGraph graph = engine.graph();
		List<Questline> trails = engine.trails();
		if (finished != null) {
			Map<String, Questline> all = graph.byId();
			int startDepth = graph.depth(finished.id());
			int maxDepth = graph.maxDepth();
			Comparator<Questline> bestFirst = Comparator
					.comparingInt((Questline trail) -> topicSimilarity(trail, finished, all)).reversed()
					.thenComparingInt(Questline::order);
			for (int depth = startDepth; depth <= maxDepth; depth++) {
				int currentDepth = depth;
				List<Questline> candidates = trails.stream()
						.filter(trail -> !"adaptive".equals(trail.id()))
						.filter(trail -> !trail.id().equals(completedId))
						.filter(trail -> !registry.has(trail.id()))
						.filter(trail -> !isCompleted(trail.id()))
						.filter(engine::unlocked)
						.filter(trail -> graph.depth(trail.id()) == currentDepth)
						.filter(trail -> topicSimilarity(trail, finished, all) > 0)
						.sorted(bestFirst)
						.toList();
				if (!candidates.isEmpty()) {
					offer(candidates.subList(0, 1), obs, feedback);
					return;
				}
			}
		}

		List<Questline> children = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> trail.requires().completed().contains(completedId))
				.toList();
		List<Questline> ready = children.stream()
				.filter(trail -> !registry.has(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offer(ready, obs, feedback)) {
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
				.map(engine::byId)
				.filter(Objects::nonNull)
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !registry.has(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		if (offer(prereq, obs, feedback)) {
			return;
		}

		List<Questline> fallback = trails.stream()
				.filter(trail -> !"adaptive".equals(trail.id()))
				.filter(trail -> !isCompleted(trail.id()))
				.filter(trail -> !registry.has(trail.id()))
				.filter(engine::unlocked)
				.sorted(Comparator.comparingInt(Questline::order))
				.toList();
		offer(fallback, obs, feedback);
	}

	/** 3 = same branch topic, 1 = sibling (shares a parent), 0 = unrelated. */
	int topicSimilarity(Questline candidate, Questline finished, Map<String, Questline> all) {
		if (Objects.equals(TopicResolver.keyFor(candidate, all), TopicResolver.keyFor(finished, all))) {
			return 3;
		}
		for (String parent : candidate.requires().completed()) {
			if (finished.requires().completed().contains(parent)) {
				return 1;
			}
		}
		return 0;
	}

	/**
	 * Starts a single option, or opens the popup for several. A full quest log (checked trails, not
	 * every started one) also starts the first option directly instead of asking.
	 */
	private boolean offer(List<Questline> options, Observation obs, QuestEngine.Feedback feedback) {
		if (options.isEmpty()) {
			return false;
		}
		if (options.size() == 1 || registry.trackedCount() >= TrackRegistry.MAX_TRACKED) {
			engine.startTrail(options.get(0).id(), obs, feedback);
		} else {
			feedback.chooseNext(options);
			pending = List.copyOf(options.subList(0, Math.min(5, options.size())));
			// A little longer than the UI's own timer, so the UI can still start the first option itself.
			pendingUntil = (obs == null ? System.currentTimeMillis() : obs.now) + OFFER_MS + GRACE_MS;
		}
		return true;
	}

	private boolean isCompleted(String trailId) {
		return data.quests.completed != null && data.quests.completed.containsKey(trailId);
	}
}
