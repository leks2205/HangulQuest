package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Category;
import io.github.leks2205.hangulquest.data.Origin;
import io.github.leks2205.hangulquest.data.Word;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The single place that mutates learning progress; quests and cards call into it. */
public final class LearningService {
	private final ProgressData data;
	private final ProgressStore store;
	private final Catalog catalog;
	private int sidequestsCompleted;
	private int questStepsCompleted;

	public LearningService(ProgressData data, ProgressStore store, Catalog catalog) {
		this.data = data;
		this.store = store;
		this.catalog = catalog;
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
	 * word, but never touches the level or the due time. Only {@link #answer} moves the schedule.
	 */
	public void expose(Word word, long now) {
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
		store.markDirty();
	}

	/**
	 * Practice (sidequest answers): the counts move, the schedule and the "known" flag do not. A
	 * guessed multiple-choice answer is not proof of recall.
	 */
	public void practice(String wordId, boolean correct, long now) {
		WordProgress progress = obtain(wordId, now);
		if (progress == null) {
			return;
		}
		progress.seen++;
		progress.lastSeenAt = now;
		DayCounts day = today();
		day.seen++;
		data.totals.seen++;
		if (correct) {
			progress.correct++;
			day.correct++;
			data.totals.correct++;
		} else {
			progress.wrong++;
			data.totals.wrong++;
		}
		store.markDirty();
	}

	/** Real quiz or review answer: the only call that moves the schedule. Never clears "known". */
	public List<Achievements.Achievement> answer(String wordId, boolean correct, long now) {
		WordProgress progress = obtain(wordId, now);
		if (progress == null) {
			return List.of();
		}
		progress.seen++;
		progress.lastSeenAt = now;
		if (correct) {
			progress.correct++;
			progress.level = Srs.onCorrect(progress.level);
			progress.dueAt = Srs.dueAt(progress.level, now);
		} else {
			progress.wrong++;
			progress.level = Srs.onWrong(progress.level);
			progress.dueAt = Srs.retryAt(now);
		}

		DayCounts day = today();
		day.seen++;
		data.totals.seen++;
		data.totals.reviews++;
		if (correct) {
			day.correct++;
			data.totals.correct++;
		} else {
			data.totals.wrong++;
		}
		store.markDirty();
		return Achievements.evaluate(this);
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
		} else {
			progress.level = 0;
			progress.dueAt = now;
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
			progress.dueAt = Srs.dueAt(progress.level, now);
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

	public int mastered() {
		int count = 0;
		for (WordProgress progress : data.words.values()) {
			if (progress.known || progress.level >= Srs.MASTERED_LEVEL) {
				count++;
			}
		}
		return count;
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

	public int todaySeen() {
		return today().seen;
	}

	public int todayCorrect() {
		return today().correct;
	}

	private DayCounts today() {
		String key = LocalDate.now().toString();
		return data.days.computeIfAbsent(key, ignored -> new DayCounts());
	}

	public Streak streak() {
		return Streak.compute(data.days.keySet(), LocalDate.now());
	}

	public int completedTrails() {
		return data.quests.completed == null ? 0 : data.quests.completed.size();
	}

	public int sidequestsCompleted() {
		return sidequestsCompleted;
	}

	public int questStepsCompleted() {
		return questStepsCompleted;
	}

	public void noteSidequestCompleted() {
		sidequestsCompleted++;
		store.markDirty();
	}

	public void noteQuestStepCompleted() {
		questStepsCompleted++;
		store.markDirty();
	}

	public List<Achievements.Achievement> checkAchievements() {
		return Achievements.evaluate(this);
	}
}
