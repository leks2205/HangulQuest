package io.github.leks2205.hangulquest.ui.quiz.cards;

import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.util.I18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Productive recall without a keyboard: the picture and the gloss are shown and the player builds the Korean
 * word from syllable tiles. Between two and four of the tiles are look-alike syllables that are not in the
 * word. Graded by how many jamo the result is off (exact is Good, one off Hard, more Again); the first
 * check counts, a second try after a miss is practice.
 */
public final class SyllableTileCard implements Card {
	public static final int MIN_TILES = 4;
	public static final int MAX_TILES = 8;
	public static final int MAX_SYLLABLES = 6;

	private final CardInfo info;
	private final String answer;
	private final List<String> tiles;
	/** Indexes into {@link #tiles}, in the order the player placed them. */
	private final List<Integer> placed = new ArrayList<>();
	private Phase phase = Phase.ASKING;
	private boolean answered;
	private String lastTry = "";

	SyllableTileCard(CardInfo info, List<String> tiles) {
		this.info = info;
		this.answer = info.word().ko;
		this.tiles = List.copyOf(tiles);
	}

	/** True when the word can be built from tiles: only syllables, between one and six of them. */
	public static boolean eligible(String ko) {
		return Syllables.onlySyllables(ko) && ko.length() <= MAX_SYLLABLES;
	}

	/** How many distractor tiles a word of this length gets: enough for four tiles, never fewer than two. */
	public static int distractorCount(int syllables) {
		return Math.min(4, Math.min(MAX_TILES - syllables, Math.max(2, MIN_TILES - syllables)));
	}

	/** The tiles for a word, shuffled; null when the word is not eligible. */
	public static List<String> tilesFor(String ko, Random random) {
		if (!eligible(ko)) {
			return null;
		}
		List<String> tiles = new ArrayList<>();
		for (char c : ko.toCharArray()) {
			tiles.add(String.valueOf(c));
		}
		tiles.addAll(Syllables.distractors(ko, distractorCount(ko.length()), random));
		List<String> order = new ArrayList<>(tiles);
		for (int attempt = 0; attempt < 8; attempt++) {
			Collections.shuffle(order, random);
			if (!String.join("", order.subList(0, ko.length())).equals(ko)) {
				break; // do not hand the answer over in order
			}
		}
		return order;
	}

	/** A card for the word, or null when it cannot be built from tiles. */
	public static SyllableTileCard create(CardInfo info, Random random) {
		List<String> tiles = tilesFor(info.word().ko, random);
		return tiles == null ? null : new SyllableTileCard(info, tiles);
	}

	public List<String> tiles() {
		return tiles;
	}

	@Override
	public CardInfo info() {
		return info;
	}

	@Override
	public ReviewMode mode() {
		return ReviewMode.TILE;
	}

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public boolean answered() {
		return answered;
	}

	/** What the player has placed so far. */
	public String assembled() {
		StringBuilder text = new StringBuilder();
		for (int index : placed) {
			text.append(tiles.get(index));
		}
		return text.toString();
	}

	@Override
	public Optional<Answer> choose(int index) {
		if (phase != Phase.ASKING && phase != Phase.RETRY) {
			return Optional.empty();
		}
		if (index < 0 || index >= tiles.size()) {
			return Optional.empty();
		}
		if (placed.contains(index)) {
			placed.remove((Integer) index); // a tile that is already placed goes back
		} else if (placed.size() < answer.length()) {
			placed.add(index);
		}
		return Optional.empty();
	}

	@Override
	public void undo() {
		if ((phase == Phase.ASKING || phase == Phase.RETRY) && !placed.isEmpty()) {
			placed.remove(placed.size() - 1);
		}
	}

	@Override
	public Optional<Answer> confirm() {
		switch (phase) {
			case ASKING -> {
				if (placed.size() < answer.length()) {
					return Optional.empty();
				}
				lastTry = assembled();
				int off = Syllables.jamoDistance(lastTry, answer);
				answered = true;
				placed.clear();
				phase = off == 0 ? Phase.FEEDBACK : Phase.RETRY;
				return Optional.of(Answer.tiles(off));
			}
			case RETRY -> {
				if (placed.size() >= answer.length()) {
					lastTry = assembled();
					phase = Phase.FEEDBACK;
				}
				return Optional.empty();
			}
			case FEEDBACK -> {
				phase = Phase.DONE;
				return Optional.empty();
			}
			default -> {
				return Optional.empty();
			}
		}
	}

	@Override
	public CardView view() {
		boolean over = phase == Phase.FEEDBACK || phase == Phase.DONE;
		List<CardView.Item> items = new ArrayList<>();
		for (int i = 0; i < tiles.size(); i++) {
			items.add(new CardView.Item(tiles.get(i), true,
					over ? CardView.State.DISABLED : placed.contains(i) ? CardView.State.USED : CardView.State.NORMAL));
		}
		List<String> shown = new ArrayList<>();
		if (over) {
			for (char c : lastTry.toCharArray()) {
				shown.add(String.valueOf(c));
			}
		} else {
			for (int index : placed) {
				shown.add(tiles.get(index));
			}
		}
		boolean ready = !over && placed.size() == answer.length();
		FeedbackCard feedback = over ? FeedbackCard.of(info, lastTry.equals(answer), lastTry) : null;
		String keys = over ? I18n.t("hangulquest.ui.card.keys.done")
				: I18n.t("hangulquest.ui.card.keys.tiles", tiles.size());
		return new CardView(phase == Phase.RETRY ? "다시 해 보세요." : "단어를 만들어 보세요.", info.icon(), info.gloss(), false,
				"", phase == Phase.RETRY ? I18n.t("hangulquest.ui.card.retry") : I18n.t("hangulquest.ui.card.build"),
				CardView.Layout.TILES, items, answer.length(), shown,
				over ? I18n.t("hangulquest.ui.card.next") : ready ? I18n.t("hangulquest.ui.card.check") : "", keys, feedback);
	}
}
