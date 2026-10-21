package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Grammar cloze: a Korean sentence with a gap, the player picks the particle or ending that fits.
 * The sentence comes from a construction's {@code clozeTemplates} (the answer and its distractors).
 * Two distractors make three options, which is logged but never moves a schedule; the first attempt
 * is graded as GRAMMAR by {@link CardRecording}, and a wrong pick gets one more try like a word card.
 */
public final class ClozeGrammarCard implements Card {
	/** At most this many options: the answer and three of its distractors. */
	public static final int MAX_OPTIONS = 4;
	private static final CardInfo NO_WORD = new CardInfo(null, null, null, null);

	private final GrammarBook.Construction construction;
	private final GrammarBook.Cloze cloze;
	private final List<String> options;
	private final int correctIndex;
	private Phase phase = Phase.ASKING;
	private boolean answered;
	private int wrongIndex = -1;
	private int lastIndex = -1;

	public ClozeGrammarCard(GrammarBook.Construction construction, GrammarBook.Cloze cloze, List<String> options,
							int correctIndex) {
		this.construction = construction;
		this.cloze = cloze;
		this.options = List.copyOf(options);
		this.correctIndex = correctIndex;
	}

	/** Picks one of the construction's clozes and shuffles its answer in among the distractors; null without clozes. */
	public static ClozeGrammarCard create(GrammarBook.Construction construction, Random random) {
		if (construction == null || construction.clozes().isEmpty()) {
			return null;
		}
		GrammarBook.Cloze cloze = construction.clozes().get(random.nextInt(construction.clozes().size()));
		List<String> options = new ArrayList<>();
		options.add(cloze.answer());
		for (String distractor : cloze.distractors()) {
			if (options.size() < MAX_OPTIONS && !cloze.accepted().contains(distractor) && !options.contains(distractor)) {
				options.add(distractor);
			}
		}
		Collections.shuffle(options, random);
		return new ClozeGrammarCard(construction, cloze, options, options.indexOf(cloze.answer()));
	}

	public GrammarBook.Construction construction() {
		return construction;
	}

	public List<String> options() {
		return options;
	}

	public int correctIndex() {
		return correctIndex;
	}

	@Override
	public CardInfo info() {
		return NO_WORD;
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
			items.add(new CardView.Item(options.get(i), true, state));
		}
		boolean right = lastIndex == correctIndex;
		List<String> rule = construction.ruleCard().lines(Lang.current());
		FeedbackCard feedback = over ? new FeedbackCard(right, cloze.filled(), cloze.gloss(), null,
				construction.form(), rule.isEmpty() ? null : rule.get(0), right ? null : options.get(lastIndex)) : null;
		return new CardView(phase == Phase.RETRY ? "다시 해 보세요." : "문법", null,
				over ? cloze.filled() : cloze.sentence(), true, cloze.gloss(),
				phase == Phase.RETRY ? I18n.t("hangulquest.ui.card.retry") : I18n.t("hangulquest.ui.card.grammar"),
				CardView.Layout.ROWS, items, 0, List.of(), over ? I18n.t("hangulquest.ui.card.next") : "",
				over ? I18n.t("hangulquest.ui.card.keys.done")
						: I18n.t("hangulquest.ui.quiz.keys", "1–" + options.size()),
				feedback);
	}
}
