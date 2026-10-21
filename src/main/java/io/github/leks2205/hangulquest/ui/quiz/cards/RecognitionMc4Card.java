package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Recognition: the Korean word is shown, the player picks its meaning among four. The picture stays hidden
 * until the question is done, since it would be the answer. A wrong pick is recorded at once (the first
 * attempt counts), then the card asks for one more try before it shows the correct form.
 */
public final class RecognitionMc4Card implements Card {
	public static final int OPTIONS = 4;

	private final CardInfo info;
	private final List<String> options;
	private final int correctIndex;
	private Phase phase = Phase.ASKING;
	private boolean answered;
	private int wrongIndex = -1;
	private int lastIndex = -1;

	public RecognitionMc4Card(CardInfo info, List<String> options, int correctIndex) {
		this.info = info;
		this.options = List.copyOf(options);
		this.correctIndex = correctIndex;
	}

	/** Builds the question: the right gloss and distractors from other semantic sets, in random order. */
	public static RecognitionMc4Card create(CardInfo info, Collection<Word> pool, LexiconTags tags, Random random) {
		Lang lang = Lang.current();
		List<String> options = new ArrayList<>();
		options.add(info.word().gloss(lang));
		for (Word distractor : Distractors.pick(info.word(), pool, tags, lang, OPTIONS - 1, random)) {
			options.add(distractor.gloss(lang));
		}
		String right = options.get(0);
		Collections.shuffle(options, random);
		return new RecognitionMc4Card(info, options, options.indexOf(right));
	}

	public List<String> options() {
		return options;
	}

	public int correctIndex() {
		return correctIndex;
	}

	@Override
	public CardInfo info() {
		return info;
	}

	@Override
	public ReviewMode mode() {
		return ReviewMode.multipleChoice(options.size());
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
		if (index < 0 || index >= options.size() || index == wrongIndex) {
			return Optional.empty();
		}
		switch (phase) {
			case ASKING -> {
				answered = true;
				lastIndex = index;
				boolean right = index == correctIndex;
				phase = right ? Phase.FEEDBACK : Phase.RETRY;
				wrongIndex = right ? -1 : index;
				return Optional.of(Answer.multipleChoice(options.size(), right));
			}
			case RETRY -> {
				lastIndex = index;
				phase = Phase.FEEDBACK;
				return Optional.empty();
			}
			default -> {
				return Optional.empty();
			}
		}
	}

	@Override
	public Optional<Answer> confirm() {
		if (phase == Phase.FEEDBACK) {
			phase = Phase.DONE;
		}
		return Optional.empty();
	}

	@Override
	public CardView view() {
		boolean over = phase == Phase.FEEDBACK || phase == Phase.DONE;
		List<CardView.Item> items = new ArrayList<>();
		for (int i = 0; i < options.size(); i++) {
			CardView.State state = CardView.State.NORMAL;
			if (over) {
				state = i == correctIndex ? CardView.State.CORRECT
						: i == lastIndex || i == wrongIndex ? CardView.State.WRONG : CardView.State.DISABLED;
			} else if (i == wrongIndex) {
				state = CardView.State.DISABLED;
			}
			items.add(new CardView.Item(options.get(i), false, state));
		}
		boolean right = lastIndex == correctIndex;
		FeedbackCard feedback = over ? FeedbackCard.of(info, right, null) : null;
		return new CardView(phase == Phase.RETRY ? "다시 해 보세요." : "복습", over ? info.icon() : null,
				info.word().ko, true, "", phase == Phase.RETRY ? I18n.t("hangulquest.ui.card.retry") : "",
				CardView.Layout.ROWS, items, 0, List.of(), over ? I18n.t("hangulquest.ui.card.next") : "",
				over ? I18n.t("hangulquest.ui.card.keys.done")
						: I18n.t("hangulquest.ui.quiz.keys", "1–" + options.size()),
				feedback);
	}
}
