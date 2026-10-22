package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.util.I18n;

import java.util.List;
import java.util.Optional;

/**
 * Free recall without a keyboard: the picture and the gloss are shown, the player thinks of the Korean,
 * reveals it and grades themselves Again, Hard, Good or Easy (keys 1 to 4). The grade goes straight to the
 * scheduler; nothing is judged by the game.
 */
public final class RecallSelfGradeCard implements Card {
	private static final String[] GRADE_KEYS = {
			"hangulquest.ui.card.grade.again", "hangulquest.ui.card.grade.hard",
			"hangulquest.ui.card.grade.good", "hangulquest.ui.card.grade.easy"};

	private final CardInfo info;
	private Phase phase = Phase.ASKING;
	private boolean answered;

	public RecallSelfGradeCard(CardInfo info) {
		this.info = info;
	}

	@Override
	public CardInfo info() {
		return info;
	}

	@Override
	public ReviewMode mode() {
		return ReviewMode.SELF;
	}

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public boolean answered() {
		return answered;
	}

	@Override
	public Optional<Answer> choose(int index) {
		if (phase != Phase.REVEALED || index < 0 || index >= GRADE_KEYS.length) {
			return Optional.empty();
		}
		phase = Phase.DONE;
		answered = true;
		return Optional.of(Answer.selfGraded(index + 1));
	}

	@Override
	public Optional<Answer> confirm() {
		if (phase == Phase.ASKING) {
			phase = Phase.REVEALED;
		}
		return Optional.empty();
	}

	@Override
	public CardView view() {
		boolean revealed = phase != Phase.ASKING;
		List<CardView.Item> grades = new java.util.ArrayList<>();
		if (revealed) {
			for (String key : GRADE_KEYS) {
				grades.add(new CardView.Item(I18n.t(key), false));
			}
		}
		FeedbackCard feedback = revealed ? FeedbackCard.of(info, true, null) : null;
		return new CardView(revealed ? "복습" : "한국어로 뭐라고 해요?", info.icon(), info.gloss(), false, "",
				revealed ? "" : I18n.t("hangulquest.ui.card.think"),
				revealed ? CardView.Layout.BUTTONS : CardView.Layout.ROWS, grades, 0, List.of(),
				revealed ? "" : I18n.t("hangulquest.ui.card.show"),
				revealed ? I18n.t("hangulquest.ui.card.keys.grade") : I18n.t("hangulquest.ui.card.keys.show"),
				feedback);
	}
}
