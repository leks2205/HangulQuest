package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.Achievements;
import io.github.leks2205.hangulquest.learn.Grader;
import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.learn.LearningService;

import java.util.List;

/** Turns a card's answer into the right {@link LearningService} call. The grading itself lives in {@link Grader}. */
public final class CardRecording {
	private CardRecording() {
	}

	public static List<Achievements.Achievement> record(LearningService learn, String wordId, Card.Answer answer,
														long latencyMs, long now) {
		if (GrammarProgress.isConstruction(wordId)) {
			// a grammar cloze: the item is a construction id, logged as GRAMMAR and fed to the knowledge tracing
			learn.answerGrammarMc(wordId, answer.options(), answer.correct(), latencyMs, now);
			return List.of();
		}
		switch (answer.mode()) {
			case SELF -> {
				return learn.answerSelfGraded(wordId, answer.grade(), "card:self", now);
			}
			case TILE -> {
				return learn.answerGraded(wordId, Grader.tile(answer.jamoOff()), latencyMs, "card:tile", now);
			}
			case TYPED -> {
				return learn.answerGraded(wordId, Grader.typed(answer.jamoOff()), latencyMs, "card:typed", now);
			}
			default -> {
				return learn.answerMc(wordId, answer.options(), answer.correct(), latencyMs, now);
			}
		}
	}
}
