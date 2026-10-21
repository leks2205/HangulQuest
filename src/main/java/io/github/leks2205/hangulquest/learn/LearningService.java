package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.config.Config;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;

import io.github.leks2205.hangulquest.learn.fsrs.CardState;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The single place that mutates learning progress; quests and cards call into it. */
public final class LearningService implements LearningSink {
	/** A word first met in a quest step counts as due for recall only after it has had this long to settle. */
	static final long SETTLE_MS = 10 * Srs.MINUTE;

	private final ProgressData data;
	private final ProgressStore store;
	private final Catalog catalog;
	private final Clock clock;
	private volatile Fsrs fsrs;
	private final Grader grader = new Grader();
	private final ReviewLog log;
	private final List<Runnable> resetListeners = new CopyOnWriteArrayList<>();
	private final LexiconTags tags = LexiconTags.shared();
	/** Right or wrong for the newest graded reviews, oldest first; the 85% controller reads it. */
	private final Deque<Boolean> recentGraded = new ArrayDeque<>();
	/** Words graded for the first time on {@link #newDay}, which is a study-day key. */
	private final Set<String> newToday = new HashSet<>();
	private String newDay = "";
	/** Semantic sets that got a new word since the game started. */
	private final Set<String> sessionSets = new HashSet<>();
	private volatile int newWordsPerDay = Config.DEFAULT_NEW_WORDS;

	public LearningService(ProgressData data, ProgressStore store, Catalog catalog) {
		this(data, store, catalog, Clock.systemDefaultZone());
	}

	public LearningService(ProgressData data, ProgressStore store, Catalog catalog, Clock clock) {
		this(data, store, catalog, clock, Srs.fsrs(), null);
	}

	/** {@code log} null opens the review log in the profile folder of {@code store} (none when read-only). */
	public LearningService(ProgressData data, ProgressStore store, Catalog catalog, Clock clock, Fsrs fsrs,
						   ReviewLog log) {
		this.data = data;
		this.store = store;
		this.catalog = catalog;
		this.clock = clock;
		this.fsrs = fsrs;
		this.log = log != null ? log
				: store.readOnly() ? ReviewLog.disabled() : ReviewLog.open(store.file().toAbsolutePath().getParent(), clock);
		loadHistory();
	}

	/** The learning settings from the config: desired retention (0.80 to 0.95) and new words per day. */
	public void configure(double retention, int newWords) {
		fsrs = new Fsrs(fsrs.params().withDesiredRetention(retention));
		newWordsPerDay = Config.nearestNewWords(newWords);
	}

	public Fsrs fsrs() {
		return fsrs;
	}

	public ReviewLog reviewLog() {
		return log;
	}

	public Grader grader() {
		return grader;
	}

	public ProgressData data() {
		return data;
	}

	public ProgressStore store() {
		return store;
	}

	public Catalog catalog() {
		return catalog;
	}

	public void markDirty() {
		store.markDirty();
	}

	public void touch(long now) {
		if (data.meta.firstPlayed == 0) {
			data.meta.firstPlayed = now;
		}
		data.meta.lastPlayed = now;
		store.markDirty();
	}

	public void introduce(Word word, long now) {
		if (word == null) {
			return;
		}
		WordProgress progress = data.words.get(word.id);
		if (progress == null) {
			progress = newProgress(word, now);
			progress.introducedAt = now;
			progress.dueAt = now;
			progress.lastSeenAt = now;
			data.words.put(word.id, progress);
			today().seen++;
			data.totals.seen++;
			store.markDirty();
		}
	}

	private WordProgress newProgress(Word word, long now) {
		WordProgress progress = new WordProgress();
		progress.level = 0;
		progress.dueAt = now;
		progress.origin = word.origin.name();
		progress.fsrs = new CardState();
		progress.fsrs.due = now;
		return progress;
	}

	public boolean isKnown(String wordId) {
		WordProgress progress = data.words.get(wordId);
		return progress != null && progress.known;
	}

	public int level(String wordId) {
		WordProgress progress = data.words.get(wordId);
		return progress == null ? -1 : progress.level;
	}

	public boolean introduced(String wordId) {
		return data.words.containsKey(wordId);
	}

	/**
	 * An encounter without recall (a quest step taught the word): counts as seen and introduces the
	 * word, but never touches the level or the due time. Only a graded answer moves the schedule.
	 */
	@Override
	public void expose(Word word, long now, ExposureLedger.Context context) {
		exposeLabelled(word, now, context, context == null ? null : context.trail() + "/" + context.role());
	}

	private void exposeLabelled(Word word, long now, ExposureLedger.Context context, String label) {
		if (word == null) {
			return;
		}
		WordProgress progress = data.words.get(word.id);
		if (progress == null) {
			progress = newProgress(word, now);
			progress.introducedAt = now;
			data.words.put(word.id, progress);
		}
		progress.seen++;
		progress.lastSeenAt = now;
		today().seen++;
		data.totals.seen++;
		ExposureLedger.encounter(progress, context, now);
		log.append(new ReviewLog.Event(now, word.id, ReviewKind.WORD, ReviewMode.EXPOSURE, null,
				Grader.UNKNOWN_LATENCY, elapsedDays(progress, now), label));
		store.markDirty();
	}

	/**
	 * A quest step taught this word. The word was due and the step unassisted: a recall, graded by
	 * {@link Grader#quest}. Anything else is an encounter and leaves the schedule alone.
	 */
	@Override
	public void questStep(Word word, long now, boolean assisted, boolean hinted, ExposureLedger.Context context,
						  String step) {
		if (word == null) {
			return;
		}
		ExposureLedger.Context where = context == null ? ExposureLedger.Context.NONE : context;
		String label = where.trail() + "/" + (step == null ? "" : step) + "/" + where.role();
		WordProgress existing = data.words.get(word.id);
		Grader.Graded graded = dueForRecall(existing, now) ? Grader.quest(assisted, hinted) : Grader.EXPOSURE;
		if (!graded.schedules()) {
			exposeLabelled(word, now, where, label);
			return;
		}
		existing.seen++;
		existing.lastSeenAt = now;
		today().seen++;
		data.totals.seen++;
		finishGraded(existing, word.id, graded, Grader.UNKNOWN_LATENCY, where, label, now);
	}

	/** True when a quest step may count as a recall of this word right now. */
	private boolean dueForRecall(WordProgress progress, long now) {
		if (progress == null || progress.known) {
			return false;
		}
		if (progress.fsrs == null || progress.fsrs.isNew()) {
			return now - progress.introducedAt >= SETTLE_MS;
		}
		return progress.fsrs.due <= now;
	}

	/**
	 * Practice (sidequest answers): the counts move and the answer is logged, the schedule and the
	 * "known" flag do not. A guess among a few options is not proof of recall.
	 */
	@Override
	public void practice(String wordId, int optionCount, boolean correct, long now) {
		ReviewMode mode = ReviewMode.multipleChoice(Math.min(optionCount, 3));
		recordAnswer(wordId, new Grader.Graded(mode, correct ? Fsrs.GOOD : Fsrs.AGAIN), Grader.UNKNOWN_LATENCY,
				"sidequest", now);
	}

	/**
	 * A multiple-choice answer from the review quiz. Four options are graded by {@link Grader}
	 * (Good, Hard when slow, Again when wrong); two or three options are logged without a schedule change.
	 */
	public List<Achievements.Achievement> answerMc(String wordId, int optionCount, boolean correct, long latencyMs,
												   long now) {
		return recordAnswer(wordId, grader.multipleChoice(optionCount, correct, latencyMs), latencyMs, "quiz", now);
	}

	/** Real quiz or review answer with four options and no timing: the old entry point. Never clears "known". */
	public List<Achievements.Achievement> answer(String wordId, boolean correct, long now) {
		return answerMc(wordId, 4, correct, Grader.UNKNOWN_LATENCY, now);
	}

	/** A card the player graded themselves (1 Again to 4 Easy). */
	public List<Achievements.Achievement> answerSelfGraded(String wordId, int grade, String source, long now) {
		return recordAnswer(wordId, Grader.selfGraded(grade), Grader.UNKNOWN_LATENCY, source, now);
	}

	/** Any other graded outcome (typed, tile, probe), already mapped by {@link Grader}. */
	public List<Achievements.Achievement> answerGraded(String wordId, Grader.Graded graded, long latencyMs,
													   String source, long now) {
		return recordAnswer(wordId, graded, latencyMs, source, now);
	}

	private List<Achievements.Achievement> recordAnswer(String wordId, Grader.Graded graded, long latencyMs,
														String source, long now) {
		WordProgress progress = obtain(wordId, now);
		if (progress == null) {
			return List.of();
		}
		progress.seen++;
		progress.lastSeenAt = now;
		DayCounts day = today();
		day.seen++;
		data.totals.seen++;
		if (graded.correct()) {
			progress.correct++;
			day.correct++;
			data.totals.correct++;
		} else {
			progress.wrong++;
			data.totals.wrong++;
		}
		finishGraded(progress, wordId, graded, latencyMs, ExposureLedger.Context.NONE, source, now);
		return Achievements.evaluate(this);
	}

	/** The part every graded event shares: schedule (when the mode does), ledger, log. */
	private void finishGraded(WordProgress progress, String wordId, Grader.Graded graded, long latencyMs,
							  ExposureLedger.Context context, String label, long now) {
		int elapsed = elapsedDays(progress, now);
		if (graded.schedules()) {
			noteGraded(wordId, isFresh(progress), graded.correct(), now);
			schedule(progress, graded.grade(), now);
			data.totals.reviews++;
		}
		if (graded.mode().recall()) {
			ExposureLedger.retrieval(progress, context, now, graded.correct(), studyDay(now));
		} else {
			ExposureLedger.encounter(progress, context, now);
		}
		log.append(new ReviewLog.Event(now, wordId, ReviewKind.WORD, graded.mode(), graded.grade(), latencyMs,
				elapsed, label));
		store.markDirty();
	}

	/** Moves the FSRS card and keeps the display level and the old due field in step with it. */
	private void schedule(WordProgress progress, int grade, long now) {
		CardState before = progress.fsrs != null ? progress.fsrs : Srs.seed(progress.level, now, fsrs);
		CardState next = fsrs.review(before, grade, now);
		progress.fsrs = next;
		progress.level = progress.known ? Srs.MAX_LEVEL : Srs.levelFor(next.stability);
		progress.dueAt = progress.known ? Long.MAX_VALUE : next.due;
	}

	/** A word that was met but never graded: it is a new card. */
	private static boolean isFresh(WordProgress progress) {
		return progress.fsrs == null || progress.fsrs.isNew();
	}

	private synchronized void noteGraded(String wordId, boolean fresh, boolean correct, long now) {
		recentGraded.addLast(correct);
		while (recentGraded.size() > SessionPlanner.WINDOW) {
			recentGraded.removeFirst();
		}
		if (fresh) {
			rollNewDay(studyDay(now));
			newToday.add(wordId);
			catalog.byId(wordId).ifPresent(word -> sessionSets.add(tags.semSet(word)));
		}
	}

	private void rollNewDay(String day) {
		if (!day.equals(newDay)) {
			newDay = day;
			newToday.clear();
		}
	}

	/** Rebuilds the controller's window and today's new words from the review log of earlier sessions. */
	private void loadHistory() {
		try {
			List<ReviewLog.Event> graded = new ArrayList<>();
			for (ReviewLog.Event event : ReviewLog.readAll(log.dir())) {
				if (event.kind() == ReviewKind.WORD && event.grade() != null && event.mode().schedules()) {
					graded.add(event);
				}
			}
			graded.sort(Comparator.comparingLong(ReviewLog.Event::t));
			Map<String, Long> first = new HashMap<>();
			for (ReviewLog.Event event : graded) {
				first.putIfAbsent(event.item(), event.t());
				recentGraded.addLast(event.grade() > Fsrs.AGAIN);
				while (recentGraded.size() > SessionPlanner.WINDOW) {
					recentGraded.removeFirst();
				}
			}
			String today = dayKey();
			newDay = today;
			first.forEach((item, at) -> {
				if (studyDay(at).equals(today)) {
					newToday.add(item);
				}
			});
		} catch (RuntimeException unreadable) {
			// A log that cannot be read only costs the controller its history.
			recentGraded.clear();
		}
	}

	/** Days since the previous review of the word; -1 when it has none. */
	private static int elapsedDays(WordProgress progress, long now) {
		if (progress.fsrs == null || progress.fsrs.lastReview <= 0) {
			return -1;
		}
		return (int) Math.max(0, Math.floorDiv(now - progress.fsrs.lastReview, Srs.DAY));
	}

	/** The study day of a moment, {@code yyyy-MM-dd}; the day turns over at 04:00 local time. */
	private String studyDay(long now) {
		return Streak.dayKey(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), clock.getZone()));
	}

	/** The saved progress of a word, created from the catalog when it is new; null for unknown ids. */
	private WordProgress obtain(String wordId, long now) {
		WordProgress progress = data.words.get(wordId);
		if (progress == null) {
			Optional<Word> word = catalog.byId(wordId);
			if (word.isEmpty()) {
				return null;
			}
			progress = newProgress(word.get(), now);
			progress.introducedAt = now;
			data.words.put(wordId, progress);
		}
		return progress;
	}

	public List<Achievements.Achievement> markKnown(String wordId, boolean known, long now) {
		WordProgress progress = obtain(wordId, now);
		if (progress == null) {
			return List.of();
		}
		progress.known = known;
		progress.lastSeenAt = now;
		if (known) {
			progress.level = Srs.MAX_LEVEL;
			progress.dueAt = Long.MAX_VALUE;
			progress.fsrs = Srs.seedKnown(now, fsrs);
		} else {
			progress.level = 0;
			progress.dueAt = now;
			progress.fsrs = Srs.seed(0, now, fsrs);
			progress.fsrs.due = now;
		}
		store.markDirty();
		return Achievements.evaluate(this);
	}

	public void seedFromLute(Map<String, Integer> levels, List<String> known, long now) {
		for (Map.Entry<String, Integer> entry : levels.entrySet()) {
			Optional<Word> word = catalog.byId(entry.getKey());
			if (word.isEmpty()) {
				continue;
			}
			WordProgress progress = data.words.get(entry.getKey());
			if (progress != null) {
				continue; // local progress always wins
			}
			progress = newProgress(word.get(), now);
			progress.introducedAt = now;
			progress.level = Math.max(0, Math.min(Srs.MAX_LEVEL, entry.getValue()));
			progress.fsrs = Srs.seed(progress.level, now, fsrs);
			progress.dueAt = progress.fsrs.due;
			progress.origin = Origin.LUTE.name();
			progress.luteStatus = entry.getValue();
			data.words.put(entry.getKey(), progress);
		}
		for (String id : known) {
			if (data.words.containsKey(id) || catalog.byId(id).isEmpty()) {
				continue;
			}
			WordProgress progress = newProgress(catalog.byId(id).get(), now);
			progress.introducedAt = now;
			progress.level = Srs.MAX_LEVEL;
			progress.dueAt = Long.MAX_VALUE;
			progress.fsrs = Srs.seedKnown(now, fsrs);
			progress.known = true;
			progress.origin = Origin.LUTE.name();
			progress.luteStatus = 99;
			data.words.put(id, progress);
		}
		store.markDirty();
	}

	public List<Word> dueWords(long now, int limit) {
		List<Map.Entry<String, WordProgress>> due = new ArrayList<>();
		for (Map.Entry<String, WordProgress> entry : data.words.entrySet()) {
			WordProgress progress = entry.getValue();
			if (!progress.known && progress.dueAt <= now) {
				due.add(entry);
			}
		}
		due.sort(Comparator.comparingLong(entry -> entry.getValue().dueAt));
		List<Word> words = new ArrayList<>();
		for (Map.Entry<String, WordProgress> entry : due) {
			if (words.size() >= limit) {
				break;
			}
			catalog.byId(entry.getKey()).ifPresent(words::add);
		}
		return words;
	}

	/**
	 * The due words ranked for play (quests, the review quiz): reviews first, most forgotten at the top,
	 * then the new words the daily budget, the 85% controller and the one-per-semantic-set rule allow.
	 * {@link #dueWords} stays the plain list.
	 */
	public synchronized List<Word> dueForPlay(long now, int limit) {
		List<SessionPlanner.Due> due = new ArrayList<>();
		for (Map.Entry<String, WordProgress> entry : data.words.entrySet()) {
			WordProgress progress = entry.getValue();
			if (progress.known || progress.dueAt > now) {
				continue;
			}
			Word word = catalog.byId(entry.getKey()).orElse(null);
			if (word == null) {
				continue;
			}
			boolean fresh = isFresh(progress);
			double retrievability = fresh || progress.fsrs == null ? 1 : fsrs.retrievability(progress.fsrs, now);
			LexiconTags.Tag tag = tags.tag(word);
			due.add(new SessionPlanner.Due(word, tags.semSet(word), fresh, retrievability, progress.dueAt,
					tag == null ? 0 : tag.curriculumGrade(), tag != null && tag.mcOnly()));
		}
		return SessionPlanner.rank(due, newWordsLeft(now), sessionSets, limit);
	}

	/** New words the player may still start today (daily budget, adjusted by the last reviews). */
	public synchronized int newWordsLeft(long now) {
		rollNewDay(studyDay(now));
		return SessionPlanner.newWordsLeft(newWordsPerDay, recentGraded, newToday.size());
	}

	/** Right or wrong of the newest graded reviews, oldest first. */
	public synchronized List<Boolean> recentOutcomes() {
		return List.copyOf(recentGraded);
	}

	public int dueCount(long now) {
		int count = 0;
		for (WordProgress progress : data.words.values()) {
			if (!progress.known && progress.dueAt <= now) {
				count++;
			}
		}
		return count;
	}

	public int discovered() {
		return data.words.size();
	}

	/** Words marked known or durably known ({@link Mastery}). */
	public int mastered() {
		return Mastery.count(data);
	}

	public int totalReviews() {
		return data.totals.reviews;
	}

	public int discoveredIn(Category category) {
		int count = 0;
		for (String id : data.words.keySet()) {
			Optional<Word> word = catalog.byId(id);
			if (word.isPresent() && word.get().category == category) {
				count++;
			}
		}
		return count;
	}

	/** Today's counts for display; reading never creates a day, only real activity does. */
	public int todaySeen() {
		DayCounts day = data.days.get(dayKey());
		return day == null ? 0 : day.seen;
	}

	public int todayCorrect() {
		DayCounts day = data.days.get(dayKey());
		return day == null ? 0 : day.correct;
	}

	/** The study day as a key: {@code yyyy-MM-dd}, where the day turns over at 04:00 local time. */
	public String dayKey() {
		return Streak.dayKey(LocalDateTime.now(clock));
	}

	/** The day for recording activity; created on first use. */
	private DayCounts today() {
		return data.days.computeIfAbsent(dayKey(), ignored -> new DayCounts());
	}

	public Streak streak() {
		return Streak.compute(data.days.keySet(), Streak.studyDay(LocalDateTime.now(clock)));
	}

	public int completedTrails() {
		return data.quests.completed == null ? 0 : data.quests.completed.size();
	}

	public int sidequestsCompleted() {
		return data.totals.sidequestsCompleted;
	}

	public int questStepsCompleted() {
		return data.totals.questStepsCompleted;
	}

	public void noteSidequestCompleted() {
		data.totals.sidequestsCompleted++;
		store.markDirty();
	}

	public void noteQuestStepCompleted() {
		data.totals.questStepsCompleted++;
		store.markDirty();
	}

	/** Runs after {@link #reset()} so screens can drop what they cached (the dictionary list, for one). */
	public void onReset(Runnable listener) {
		resetListeners.add(listener);
	}

	/**
	 * Wipes learning progress: words, days, achievements, totals and the adaptive trail. {@code meta.firstPlayed}
	 * stays, and so do the finished and running quests. Saves the current state first and keeps it as a
	 * {@code progress.json.backup-pre-reset-*} copy.
	 *
	 * @return false when nothing was reset because progress is read-only
	 */
	public boolean reset() {
		if (store.readOnly()) {
			return false;
		}
		store.flush();
		store.backup("pre-reset");
		data.words.clear();
		data.days.clear();
		data.achievements.clear();
		data.totals = new ProgressData.Totals();
		data.quests.adaptive = null;
		store.markDirty();
		store.flush();
		resetListeners.forEach(Runnable::run);
		return true;
	}

	public List<Achievements.Achievement> checkAchievements() {
		return Achievements.evaluate(this);
	}
}
