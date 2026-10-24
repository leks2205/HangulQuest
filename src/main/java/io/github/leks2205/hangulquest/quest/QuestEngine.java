package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.quest.goal.Accepted;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Drives the tracked trails: clue -> goal -> next clue -> ritual finale, plus the
 * hint/sidequest loop that pauses the original quest.
 *
 * <p>The quest log shows a chosen set of trails — the ones the player checked in the journal. Only
 * those are <em>tracked</em>: their goals are checked against the world and they advance in parallel.
 * Every other trail keeps its progress but is paused until it is checked again. One tracked trail is
 * <em>focused</em>: the hint key, the sidequest and the follow-up chain work on it.
 *
 * <p>This class is the facade and owns the tick loop; the state, the save format, the follow-up
 * logic, the hint key and the sentences live in the package-private classes around it.
 */
public final class QuestEngine {
	/** How many trails the quest log holds at once, the focused one included: three regular and one situational. */
	public static final int MAX_TRACKED = TrackRegistry.MAX_TRACKED;

	public interface Feedback {
		void banner(String ko, String de, boolean success);

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

		/**
		 * Same, with the finale line as this player reads it: a generated finale follows the level of the clues the
		 * player read, so it never names a colour they were not told. Defaults to the one without the line.
		 */
		default void trailCompleted(Questline trail, String finaleKo) {
			trailCompleted(trail);
		}

		/** Several follow-up trails are possible; the player picks one. */
		default void chooseNext(java.util.List<Questline> options) {
		}

		/** Why the game tracked trails for the player. */
		enum AutoReason {
			/** The first picks of a new quest log. */
			START,
			/** A tracked trail was finished; the first of the picks directly follows it when one was available. */
			FOLLOW_UP,
			/** A regular slot was empty. */
			FILL
		}

		/**
		 * The game tracked these trails by itself ("pick quests automatically"), best first. The announcement
		 * window is already queued through {@link #notice}; this tells the listener what was picked and why.
		 */
		default void autoTracked(java.util.List<Questline> picked, AutoReason reason) {
		}

		/**
		 * The situational slot got a new trail. No window: the quest log only marks the row (see
		 * {@link TrackView#situational()}), so the slot can never spam the screen.
		 */
		default void situationalChanged(Questline trail) {
		}

		/**
		 * An announcement window (a new quest started, say) that stays until the player closes it with the hint
		 * key. {@code key} keeps the same announcement from being queued twice. The texts are shown as given, so
		 * pass an empty string for a line that should not be there (translations only when they are switched on).
		 */
		default void notice(String key, String titleKo, String titleText, String bodyKo, String bodyText) {
			banner(titleKo, titleText, true);
		}
	}

	public record HintView(String clueKo, String simplifiedKo, String descriptionKo, Word word,
						   boolean sidequestStarted) {
	}

	/** One row of the quest log, as the HUD and the journal render it. */
	public record TrackView(String id, String titleKo, String titleDe, String titleEn, String topicKey,
							int koreanLevel,
							String clueKo, String clueDe, String status, int progress, int needed,
							boolean countable, boolean focused, int stepIndex, int stepCount,
							boolean reverse, boolean situational) {
		public TrackView(String id, String titleKo, String titleDe, String titleEn, String topicKey,
						 int koreanLevel, String clueKo, String clueDe, String status, int progress, int needed,
						 boolean countable, boolean focused, int stepIndex, int stepCount) {
			this(id, titleKo, titleDe, titleEn, topicKey, koreanLevel, clueKo, clueDe, status, progress, needed,
					countable, focused, stepIndex, stepCount, false, false);
		}

		public TrackView(String id, String titleKo, String titleDe, String titleEn, String topicKey,
						 int koreanLevel, String clueKo, String clueDe, String status, int progress, int needed,
						 boolean countable, boolean focused, int stepIndex, int stepCount, boolean reverse) {
			this(id, titleKo, titleDe, titleEn, topicKey, koreanLevel, clueKo, clueDe, status, progress, needed,
					countable, focused, stepIndex, stepCount, reverse, false);
		}
	}

	/**
	 * Entries of the quest log that are not trails: the villager requests the player tracks. Each takes a regular
	 * slot; their rows follow the trails' rows.
	 */
	public interface Extra {
		/** How many entries are in the log now. */
		int trackedCount();

		/** Their rows, in the order they were added. */
		List<TrackView> views();
	}

	/** The rule card of a construction the player meets for the first time: example lines and the rule. */
	public record RuleCardView(String id, String form, List<String> examplesKo, List<String> rule) {
	}

	private final Catalog catalog;
	private final LearningSink learn;
	private final ProgressData data;
	private final Logger log;
	private final TrailSet book;
	private final TrackRegistry registry = new TrackRegistry();
	private final QuestPersistence persistence;
	private final QuestTextService text;
	private final HintService hints;
	private final NextQuestPolicy next;
	private final TrackingService tracking;
	private final StepCompletion completion;

	/** What each step accepts besides its goal's own ids (the referents of its taught word), built on first use. */
	private final java.util.Map<QuestStep, Accepted> accepted = new java.util.IdentityHashMap<>();

	private Observation lastObs;
	private Extra extra;
	/** "Pick quests automatically"; off until the game switches it on, so the bare engine tracks only what it is told to. */
	private BooleanSupplier autoPick = () -> false;
	private BooleanSupplier translations = () -> false;
	private BooleanSupplier popupsExpire = () -> true;
	private Map<String, TrailVisibility.State> visibility = Map.of();
	private Object visibilityTrails;
	private int visibilityCompleted = -1;
	private int visibilityWords = -1;
	private List<String> visibilityTracked = List.of();
	/** The step that has shown its rule card already. */
	private QuestStep ruleCardStep;
	/**
	 * True when the step that was completed last needed creative or spectator mode to progress.
	 * Valid inside the {@link Feedback} callbacks of that completion; learning grades should skip it.
	 */
	private boolean lastCompletionAssisted;

	public QuestEngine(List<Questline> trails, Catalog catalog, LearningSink learn, Logger log) {
		this.catalog = catalog;
		this.learn = learn;
		this.data = learn.data();
		this.log = log;
		this.book = new TrailSet(trails, log);
		this.text = new QuestTextService(catalog, learn);
		this.persistence = new QuestPersistence(learn, catalog, this::byId);
		this.hints = new HintService(registry, text, persistence, learn, catalog);
		this.next = new NextQuestPolicy(this, registry, data);
		this.tracking = new TrackingService(registry, persistence, next, this::byId);
		this.completion = new StepCompletion(registry, persistence, text, learn, catalog,
				this::advanceAfterCompletion);
		persistence.restore(registry);
	}

	/** Replaces all authored trails (live reload) without losing a tracked one. */
	public void replaceTrails(List<Questline> replacement) {
		book.replace(replacement);
		accepted.clear();
		text.invalidate();
		registry.rebind(this::byId);
	}

	public void addTrail(Questline trail) {
		book.add(trail);
		text.invalidate();
	}

	public boolean lastCompletionAssisted() {
		return lastCompletionAssisted;
	}

	public void tick(Observation obs, Feedback feedback) {
		lastObs = obs;
		next.tick(obs, feedback);
		if (registry.isEmpty()) {
			return;
		}
		boolean dirty = false;
		for (Track track : new ArrayList<>(registry.all())) {
			if (!registry.has(track.trail.id()) || !registry.isTracked(track.trail.id())) {
				continue; // finished this tick, or not in the quest log
			}
			QuestStep step = track.step();
			if (step == null) {
				persistence.forget(registry, track.trail.id());
				dirty = true;
				continue;
			}
			if (track.sideQuest != null) {
				continue; // frozen until its sidequest is answered
			}
			int beforeCount = track.tracker.count;
			long beforeDwell = track.tracker.dwellMs;
			track.tracker.accepted = acceptedFor(step).withExact(text.exactColours(step, text.effectiveLevel(
					track.trail.koreanLevel())));
			track.tracker.refBlocks = step.placedBefore();
			boolean complete = GoalEvaluator.tick(step.goal(), step.conditions(), obs, track.tracker);
			completion.formMisses(track, obs);
			if (complete) {
				lastCompletionAssisted = track.tracker.assisted;
				completion.complete(track, obs, feedback);
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

	private Accepted acceptedFor(QuestStep step) {
		return accepted.computeIfAbsent(step, key -> key.teach().isEmpty() ? Accepted.NONE
				: Accepted.of(key, catalog.concept(key.teach().get(0)).orElse(null)));
	}

	/** What follows a finished focused trail (the hook the graph test calls). */
	private void advanceAfterCompletion(String completedId, Observation obs, Feedback feedback) {
		advanceAfterCompletion(completedId, obs, feedback, true);
	}

	/** What follows a finished trail. */
	private void advanceAfterCompletion(String completedId, Observation obs, Feedback feedback, boolean focused) {
		next.afterCompletion(completedId, obs, feedback, focused);
	}

	private void persist(long now) {
		persistence.persist(registry, now);
	}

	private long clock() {
		return lastObs == null ? System.currentTimeMillis() : lastObs.now;
	}

	/**
	 * Starts tracking a trail and focuses it. A trail that is already tracked is only focused, so the
	 * quest log never loses progress by starting the same quest twice. Returns false when the trail is
	 * unknown or the log is full of quests with progress.
	 */
	public boolean startTrail(String trailId, Observation obs, Feedback feedback) {
		if (byId(trailId) == null || !tracking.track(trailId, obs == null ? System.currentTimeMillis() : obs.now)) {
			return false;
		}
		QuestStep step = registry.get(trailId).step();
		if (step != null) {
			feedback.banner(clueKo(registry.focused(), step), clueTranslation(step), true);
		}
		return true;
	}

	/** Focuses a tracked trail without touching its progress. */
	public boolean focus(String trailId) {
		return tracking.focus(trailId, clock());
	}

	/**
	 * Adds or removes a trail in the quest log. Checked trails are drawn on the HUD and their goals are
	 * checked against the world; unchecked trails keep their progress but do not advance. Adding fails
	 * when the log is full of quests with progress.
	 */
	public boolean setTracked(String trailId, boolean on) {
		return tracking.setTracked(trailId, on, clock());
	}

	/** Takes a trail out of the log; its progress is kept for when it is checked again. */
	public void untrack(String trailId) {
		setTracked(trailId, false);
	}

	/** On joining a world: with "pick quests automatically" on, tracks trails into the empty regular slots. */
	public void autoStart(Observation obs, Feedback feedback) {
		next.autoStart(obs, feedback);
	}

	/**
	 * Switches "pick quests automatically" (read every time it matters, so the setting takes effect at once).
	 * Off by default for a bare engine; the game passes the config value.
	 */
	public void setAutoPick(BooleanSupplier autoPick) {
		this.autoPick = autoPick == null ? () -> false : autoPick;
	}

	/** Whether quest texts carry a translation line (the "show translations" setting); off for a bare engine. */
	public void setTranslations(BooleanSupplier translations) {
		this.translations = translations == null ? () -> false : translations;
	}

	boolean translations() {
		return translations.getAsBoolean();
	}

	/**
	 * Whether the "pick the next quest" popup runs out after its offer time. The game passes
	 * {@code autoCloseWindows}: windows that wait for the hint key must not expire in the engine either.
	 * A bare engine lets it expire.
	 */
	public void setPopupsExpire(BooleanSupplier popupsExpire) {
		this.popupsExpire = popupsExpire == null ? () -> true : popupsExpire;
	}

	boolean popupsExpire() {
		return popupsExpire.getAsBoolean();
	}

	boolean autoPick() {
		return autoPick.getAsBoolean();
	}

	/**
	 * Tells the picker where the chapters are: the chapter id of a trail (or null) and the chapter ids in
	 * order. The first chapters weigh in the start pick, and the situational slot reads the chapter of a trail.
	 */
	public void setChapters(Function<String, String> chapterOf, List<String> order) {
		book.chapterOf(chapterOf);
		next.chapterRank(id -> {
			String chapter = chapterOf == null ? null : chapterOf.apply(id);
			return chapter == null ? -1 : order.indexOf(chapter);
		});
	}

	/** The picker, for tests in this package. */
	NextQuestPolicy picker() {
		return next;
	}

	TrailTraits traits(Questline trail) {
		return book.traits(trail);
	}

	int discovered() {
		return learn.discovered();
	}

	/** The game checks a trail for the player (situational slot or a regular one); keeps the focus. */
	boolean autoTrack(String trailId, boolean situational, long now) {
		return tracking.trackAuto(trailId, situational, now);
	}

	void autoUntrack(String trailId, long now) {
		tracking.untrackAuto(trailId, now);
	}

	/** True when the game picked this tracked trail, false when the player chose it (or it is not tracked). */
	public boolean isAutoTracked(String trailId) {
		return registry.isAuto(trailId);
	}

	/** The trail in the situational slot, or null. */
	public String situationalId() {
		return registry.situationalId();
	}

	/**
	 * What the journal draws of every trail now ({@link TrailVisibility}). Rebuilt only when the trails, the
	 * finished set, the words met or the quest log changed.
	 */
	public Map<String, TrailVisibility.State> visibility() {
		TrailGraph graph = book.graph();
		int completed = data.quests.completed == null ? 0 : data.quests.completed.size();
		int words = learn.discovered();
		List<String> tracked = new ArrayList<>(registry.trackedIds());
		if (visibilityTrails != graph || visibilityCompleted != completed || visibilityWords != words
				|| !visibilityTracked.equals(tracked)) {
			visibility = TrailVisibility.of(graph,
					id -> data.quests.completed != null && data.quests.completed.containsKey(id),
					registry::isTracked, this::unlocked);
			visibilityTrails = graph;
			visibilityCompleted = completed;
			visibilityWords = words;
			visibilityTracked = tracked;
		}
		return visibility;
	}

	/** Takes the focused trail out of the log (the journal's "Abbrechen"); its progress is kept. */
	/**
	 * After "Reset progress", once the caller has emptied {@code data.quests}: forgets every started trail
	 * (tracked or not), the open next-quest choice and the rule card, so nothing is written back.
	 */
	public void reset() {
		registry.clear();
		next.reset();
		ruleCardStep = null;
		persistence.restore(registry);
	}

	public void abandon() {
		if (registry.focusedId() != null) {
			setTracked(registry.focusedId(), false);
		}
	}

	/** The open "pick the next quest" popup; empty when there is none. */
	public List<Questline> pendingNextChoices() {
		return next.pending(clock());
	}

	public HintView hint(Observation obs, Feedback feedback) {
		return hints.hint(obs, feedback);
	}

	public boolean answerSidequest(int index, Feedback feedback) {
		return hints.answerSidequest(index, feedback);
	}

	public boolean hintUsed() {
		return hints.hintUsed();
	}

	public List<Questline> trails() {
		return book.trails();
	}

	public Questline byId(String trailId) {
		return book.byId(trailId);
	}

	/** Lookup, parents, children and tree depth of the current trail list. */
	public TrailGraph graph() {
		return book.graph();
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
		Track track = registry.focused();
		return track == null ? null : track.trail;
	}

	public String focusedId() {
		return registry.focusedId();
	}

	public boolean isTracked(String trailId) {
		return registry.isTracked(trailId);
	}

	/** Partial counter of any trail that has a state, tracked or not. */
	public int progressOf(String trailId) {
		Track track = registry.get(trailId);
		return track == null ? 0 : track.tracker.count;
	}

	/** True when the trail was started at some point and still has a saved state. */
	public boolean hasProgress(String trailId) {
		return registry.has(trailId);
	}

	public int trackedCount() {
		return registry.trackedCount();
	}

	/**
	 * The quest log in the order the player added the quests. The focused one is only marked
	 * ({@link TrackView#focused()}); it does not move to the top, so cycling the focus visits every
	 * quest instead of bouncing between the first two.
	 */
	public List<TrackView> trackedViews() {
		List<TrackView> views = QuestViews.of(registry, text, catalog, lastObs);
		if (extra != null) {
			views.addAll(extra.views());
		}
		return views;
	}

	/** Puts entries that are not trails into the quest log (the villager requests); null removes them. */
	public void setExtra(Extra extra) {
		this.extra = extra;
		registry.extra(extra == null ? null : extra::trackedCount);
	}

	/**
	 * Makes room in the quest log for one more entry that is not a trail. When the log is full, the oldest trail the
	 * game picked is taken out (it keeps its progress); a trail the player chose never is. False when every slot holds
	 * a trail the player chose: the entry then waits outside the log.
	 */
	public boolean makeRoomForExtra(long now) {
		if (registry.slotCount() < MAX_TRACKED) {
			return true;
		}
		String oldest = registry.oldestAuto();
		if (oldest == null) {
			return false;
		}
		tracking.untrackAuto(oldest, now);
		return registry.slotCount() < MAX_TRACKED;
	}

	/** The clue the quest log shows for a step built in code (a request's goal), at the player's level. */
	public String clueOf(QuestStep step, int koreanLevel) {
		return text.clueKo(step, koreanLevel);
	}

	/** The status line of a goal's progress (what is missing), as the quest log shows it; empty before the first tick. */
	public String statusOf(QuestStep step, GoalEvaluator.Tracker tracker) {
		return lastObs == null || step == null ? "" : QuestStatus.text(step, lastObs, tracker, catalog);
	}

	public SideQuest sideQuest() {
		Track track = registry.focused();
		return track == null ? null : track.sideQuest;
	}

	public boolean sidequestActive() {
		return sideQuest() != null;
	}

	public QuestStep currentStep() {
		Track track = registry.focused();
		return track == null ? null : track.step();
	}

	public int stepIndex() {
		Track track = registry.focused();
		return track == null ? 0 : track.stepIndex;
	}

	public int stepCount() {
		Track track = registry.focused();
		return track == null ? 0 : track.stepCount();
	}

	/** Short Korean line for the focused trail: unmet condition, or progress toward the goal. */
	public String statusText() {
		Track track = registry.focused();
		if (track == null || lastObs == null) {
			return "";
		}
		return QuestStatus.text(track.step(), lastObs, track.tracker, catalog);
	}

	public long goalDwellMs() {
		Track track = registry.focused();
		return track == null ? 0L : track.tracker.dwellMs;
	}

	public long goalDwellNeededMs() {
		QuestStep step = currentStep();
		return step == null ? 0L : GoalTypes.of(step.goal()).dwellNeededMs(step.goal());
	}

	public int goalProgress() {
		Track track = registry.focused();
		return track == null ? 0 : track.tracker.count;
	}

	public int goalNeeded() {
		QuestStep step = currentStep();
		return step == null ? 0 : GoalTypes.of(step.goal()).needed(step.goal());
	}

	public List<Word> currentWords() {
		return text.words(currentStep());
	}

	public Word firstWord(QuestStep step) {
		return text.firstWord(step);
	}

	public String clueKo(QuestStep step) {
		return clueKo(registry.focused(), step);
	}

	private String clueKo(Track track, QuestStep step) {
		return text.clueKo(step, track == null ? 1 : track.trail.koreanLevel());
	}

	/** Translation of the clue in the interface language (English or German). */
	public String clueTranslation(QuestStep step) {
		return text.clueTranslation(step);
	}

	/** @deprecated kept for callers that still ask for the German text. */
	@Deprecated
	public String clueDe(QuestStep step) {
		return clueTranslation(step);
	}

	public String hintKo(QuestStep step) {
		Track track = registry.focused();
		return text.hintKo(step, track == null ? 1 : track.trail.koreanLevel());
	}

	/**
	 * The rule card to show for the focused step: the first construction of its clue whose card the player
	 * has not seen. One card per step, so a step that brings several new forms shows them one after the
	 * other over the next steps. Null when there is nothing new.
	 */
	public RuleCardView pendingRuleCard() {
		Track track = registry.focused();
		QuestStep step = track == null ? null : track.step();
		if (step == null || track.sideQuest != null || step == ruleCardStep) {
			return null;
		}
		GrammarBook book = GrammarBook.shared();
		for (String id : text.grammarIds(step, track.trail.koreanLevel())) {
			if (!learn.grammarCardSeen(id)) {
				GrammarBook.Construction c = book.get(id);
				return new RuleCardView(id, c.form(), c.ruleCard().ko(), c.ruleCard().lines(Lang.current()));
			}
		}
		return null;
	}

	/** The card was put on screen: the construction counts as met and the focused step shows no second card. */
	public void ruleCardShown(String id) {
		learn.grammarCardShown(id, clock());
		ruleCardStep = currentStep();
	}

	/** Dev-only: seeds a partial counter on a tracked trail so UI demos show filled progress bars. */
	public void demoProgress(String trailId, int count) {
		Track track = registry.get(trailId);
		if (track == null) {
			return;
		}
		track.tracker.count = Math.max(0, count);
		persist(clock());
	}

	/** Unused, kept so subclasses/tests can share the engine's logger. */
	public Logger log() {
		return log;
	}
}
