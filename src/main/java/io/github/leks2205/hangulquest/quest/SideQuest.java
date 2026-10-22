package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Two-question mini quiz about the word that blocked the player. The first
 * question goes Korean -> gloss (interface language), the second gloss -> Korean; getting both
 * right restores the original quest. Options are deterministic per word/stage/
 * attempt so a restart cannot change the right answer.
 */
public final class SideQuest {
	private final Word word;
	private final Catalog catalog;
	private int stage;
	private int attempts;
	private List<String> options = new ArrayList<>();
	private int correctIndex;
	private Lang builtFor = Lang.current();

	private SideQuest(Word word, Catalog catalog, int stage, int attempts) {
		this.word = word;
		this.catalog = catalog;
		this.stage = Math.max(1, stage);
		this.attempts = Math.max(0, attempts);
		build();
	}

	public static SideQuest start(Word word, Catalog catalog) {
		return new SideQuest(word, catalog, 1, 0);
	}

	public static SideQuest restore(Word word, int stage, int attempts, Catalog catalog) {
		return new SideQuest(word, catalog, stage, attempts);
	}

	private void build() {
		builtFor = Lang.current();
		Random random = new Random(Objects.hash(word.id, stage, attempts));
		List<String> built = new ArrayList<>();
		String correct = stage == 1 ? word.gloss(builtFor) : word.ko;
		built.add(correct);
		List<Word> distractors = catalog.distractors(word, attempts > 0 ? 1 : 2, random);
		for (Word distractor : distractors) {
			String option = stage == 1 ? distractor.gloss(builtFor) : distractor.ko;
			if (!option.isBlank() && !built.contains(option)) {
				built.add(option);
			}
		}
		java.util.Collections.shuffle(built, random);
		options = built;
		correctIndex = built.indexOf(correct);
		if (correctIndex < 0) {
			correctIndex = 0;
		}
	}

	public Word word() {
		return word;
	}

	public int stage() {
		return stage;
	}

	public int attempts() {
		return attempts;
	}

	public String prompt() {
		return stage == 1 ? word.ko : word.gloss(Lang.current());
	}

	public boolean reversed() {
		return stage == 2;
	}

	public List<String> options() {
		rebuildIfLanguageChanged();
		return options;
	}

	public int correctIndex() {
		rebuildIfLanguageChanged();
		return correctIndex;
	}

	public boolean isDone() {
		return stage > 2;
	}

	/** Returns true when the answer was right; wrong answers do not fail the quest. */
	public boolean answer(int index) {
		rebuildIfLanguageChanged();
		if (index == correctIndex) {
			stage++;
			attempts = 0;
			if (!isDone()) {
				build();
			}
			return true;
		}
		attempts++;
		build();
		return false;
	}

	/** The glosses depend on the interface language, so a switch mid-quest rebuilds the options. */
	private void rebuildIfLanguageChanged() {
		if (builtFor != Lang.current() && !isDone()) {
			build();
		}
	}

	public String wordId() {
		return word.id;
	}

	public void persistStage(int stage) {
		this.stage = stage;
	}
}
