package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningSink;
import io.github.leks2205.hangulquest.sense.Observation;

/** The hint key and its sidequest: a small quiz that pauses the focused trail until it is answered. */
final class HintService {
	private final TrackRegistry registry;
	private final QuestTextService text;
	private final QuestPersistence persistence;
	private final LearningSink learn;
	private final Catalog catalog;

	private long lastHintAt;

	HintService(TrackRegistry registry, QuestTextService text, QuestPersistence persistence,
				LearningSink learn, Catalog catalog) {
		this.registry = registry;
		this.text = text;
		this.persistence = persistence;
		this.learn = learn;
		this.catalog = catalog;
	}

	QuestEngine.HintView hint(Observation obs, QuestEngine.Feedback feedback) {
		Track track = registry.focused();
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
		int level = track.trail.koreanLevel();
		Word word = track.sideQuest != null ? track.sideQuest.word() : text.firstWord(step);
		String simplified = text.hintKo(step, level, word);
		String description = word != null && word.hasDescKo() ? word.descKo : null;
		boolean known = word != null && learn.isKnown(word.id);
		boolean started = false;
		if (track.sideQuest == null && !known && word != null && !track.hintUsed) {
			track.sideQuest = SideQuest.start(word, catalog);
			track.hintUsed = true;
			feedback.wordCard(word);
			persistence.persist(registry, obs.now);
			started = true;
		}
		return new QuestEngine.HintView(text.clueKo(step, level), simplified, description, word,
				started || track.sideQuest != null);
	}

	boolean answerSidequest(int index, QuestEngine.Feedback feedback) {
		Track track = registry.focused();
		if (track == null || track.sideQuest == null) {
			return false;
		}
		boolean correct = track.sideQuest.answer(index);
		Word word = track.sideQuest.word();
		long now = System.currentTimeMillis();
		learn.practice(word.id, correct, now);
		if (track.sideQuest.isDone()) {
			Word learned = track.sideQuest.word();
			track.sideQuest = null;
			learn.noteSidequestCompleted();
			persistence.persist(registry, now);
			feedback.sidequestCompleted(learned);
			feedback.sound(true);
			StepCompletion.unlock(feedback, learn.checkAchievements());
		} else {
			persistence.persist(registry, now);
			feedback.sound(correct);
		}
		return correct;
	}

	void hintWordCard(QuestEngine.Feedback feedback) {
		Track track = registry.focused();
		if (track == null) {
			return;
		}
		Word word = text.firstWord(track.step());
		if (word != null) {
			feedback.wordCard(word);
		}
	}

	boolean hintUsed() {
		Track track = registry.focused();
		return track != null && track.hintUsed;
	}
}
