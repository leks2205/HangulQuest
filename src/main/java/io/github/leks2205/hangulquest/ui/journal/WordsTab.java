package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The dictionary: every word, discovered ones first, and a detail page for one word. */
public final class WordsTab extends JournalTab {
	private static final int ROW_HEIGHT = 18;

	private int scroll;
	private String selectedWordId;

	private Page page;
	private Detail detail;
	// Where the rows are on screen this frame, for the one click area that covers them all.
	private int listX;
	private int listY;
	private int listWidth;
	private int listFirst;
	private int listEnd;
	private final JournalHost.PointClick rowClick = this::clickRow;

	/** One dictionary row, built when it first scrolls into view. */
	private record Row(Word word, boolean discovered, FormattedCharSequence gloss, String dots) {
	}

	/**
	 * The sorted word list and its rows. Valid while the learning state, the language and the page
	 * width are what they were when it was built.
	 */
	private static final class Page {
		final int discovered;
		final int catalogSize;
		final int reviews;
		final long epoch;
		final int language;
		final int glossWidth;
		final List<Word> words;
		final Row[] rows;

		Page(int discovered, int catalogSize, int reviews, long epoch, int language, int glossWidth,
			 List<Word> words) {
			this.discovered = discovered;
			this.catalogSize = catalogSize;
			this.reviews = reviews;
			this.epoch = epoch;
			this.language = language;
			this.glossWidth = glossWidth;
			this.words = words;
			this.rows = new Row[words.size()];
		}

		boolean matches(int discovered, int catalogSize, int reviews, long epoch, int language, int glossWidth) {
			return this.discovered == discovered && this.catalogSize == catalogSize && this.reviews == reviews
					&& this.epoch == epoch && this.language == language && this.glossWidth == glossWidth;
		}
	}

	/** The texts of the detail page for one word. */
	private record Detail(String wordId, long epoch, boolean showDe, int language, int reviews,
						  Word word, Component fullName, Component sentence, Component description,
						  String metrics, boolean known) {
	}

	public WordsTab(JournalHost host) {
		super(host);
	}

	@Override
	public void onOpen() {
		scroll = 0;
		selectedWordId = null;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double vertical) {
		int rows = Math.max(1, (host.contentHeight() - 16) / ROW_HEIGHT);
		int wordCount = page == null ? 0 : page.words.size();
		int maxScroll = Math.max(0, wordCount - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical) * 2));
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		if (selectedWordId != null) {
			wordDetail(g, x, y, width, height);
			return;
		}
		Page current = page(width - 100);
		List<Word> words = current.words;
		int maxRows = Math.max(1, (height - 16) / ROW_HEIGHT);
		int maxScroll = Math.max(0, words.size() - maxRows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int end = Math.min(words.size(), scroll + maxRows);
		boolean showDe = host.showDe();
		for (int i = scroll; i < end; i++) {
			Row row = row(current, i);
			Word word = row.word();
			int rowY = y + (i - scroll) * ROW_HEIGHT;
			Draw.itemIcon(g, word.iconItem, x, rowY + 1);
			Draw.korean(g, host.font(), word.ko, x + 20, rowY + 3, row.discovered() ? Ink.INK : Ink.INK_FAINT);
			if (showDe) {
				Draw.text(g, host.font(), row.gloss(), x + 72, rowY + 3,
						row.discovered() ? Ink.INK_SOFT : Ink.INK_FAINT, false);
			}
			Draw.label(g, host.font(), row.dots(), x + width - 30, rowY + 3, Ink.ACCENT);
		}
		listX = x;
		listY = y;
		listWidth = width;
		listFirst = scroll;
		listEnd = end;
		host.hit(x, y, width, (end - scroll) * ROW_HEIGHT, rowClick);
		Draw.label(g, host.font(), words.size() + Tr.t(" Wörter · Mausrad scrollt"), x, y + height - 10,
				Ink.INK_FAINT);
	}

	private boolean clickRow(double mx, double my) {
		int index = listFirst + (int) ((my - listY) / ROW_HEIGHT);
		if (page == null || index < listFirst || index >= listEnd || mx < listX || mx >= listX + listWidth) {
			return false;
		}
		selectedWordId = page.words.get(index).id;
		return true;
	}

	private Page page(int glossWidth) {
		LearningService learn = host.hq().learn();
		int discovered = learn == null ? -1 : learn.discovered();
		int reviews = learn == null ? -1 : learn.totalReviews();
		int catalogSize = host.hq().catalog().all().size();
		if (page != null && page.matches(discovered, catalogSize, reviews, host.epoch(), language(), glossWidth)) {
			return page;
		}
		List<Word> words = new ArrayList<>(host.hq().catalog().all());
		words.sort(Comparator
				.comparing((Word word) -> learn == null || !learn.introduced(word.id))
				.thenComparing(word -> word.category.ordinal())
				.thenComparing(word -> word.ko));
		page = new Page(discovered, catalogSize, reviews, host.epoch(), language(), glossWidth, words);
		return page;
	}

	private Row row(Page current, int index) {
		Row row = current.rows[index];
		if (row == null) {
			Word word = current.words.get(index);
			LearningService learn = host.hq().learn();
			row = new Row(word, learn != null && learn.introduced(word.id),
					Draw.fit(host.font(), Component.literal(Tr.pick(word.en, word.deShort)), current.glossWidth),
					levelDots(word.id));
			current.rows[index] = row;
		}
		return row;
	}

	private String levelDots(String wordId) {
		if (host.hq().learn() == null) {
			return "";
		}
		WordProgress progress = host.hq().learn().data().words.get(wordId);
		if (progress == null) {
			return "·";
		}
		if (progress.known) {
			return "★";
		}
		return "●".repeat(Math.max(1, Math.min(Srs.MAX_LEVEL + 1, progress.level + 1)));
	}

	private void wordDetail(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		Detail d = detail();
		if (d == null) {
			selectedWordId = null;
			return;
		}
		Word word = d.word();
		Draw.itemIcon(g, word.iconItem, x, y);
		Draw.korean(g, host.font(), word.ko, x + 22, y + 3, Ink.ACCENT);
		int yy;
		if (d.showDe()) {
			Draw.wrapped(g, host.font(), d.fullName(), x + 22, y + 17, width - 24, Ink.INK, 2);
			yy = y + 34;
		} else {
			yy = y + 20;
		}
		if (d.sentence() != null) {
			Draw.wrapped(g, host.font(), d.sentence(), x, yy, width, Ink.INK_SOFT, 2);
			yy += 22;
		}
		if (d.description() != null) {
			Draw.wrapped(g, host.font(), d.description(), x, yy, width, Ink.ACCENT, 2);
			yy += 22;
		}
		Draw.label(g, host.font(), d.metrics(), x, yy, Ink.INK_FAINT);
		yy += 20;
		boolean known = d.known();
		host.button(g, x, yy, 110, 18, known ? Tr.t("Wieder lernen") : Tr.t("Als bekannt"), false, () -> {
			host.hq().learn().markKnown(word.id, !known, System.currentTimeMillis());
			host.changed();
			host.status(known ? Tr.t("Wird wieder gelernt.") : Tr.t("Als bekannt markiert."));
		});
		host.button(g, x + 118, yy, 90, 18, Tr.t("Zurück"), false, () -> selectedWordId = null);
	}

	/** The detail texts for the selected word, rebuilt only when something they show may have changed. */
	private Detail detail() {
		LearningService learn = host.hq().learn();
		int reviews = learn == null ? -1 : learn.totalReviews();
		Detail cached = detail;
		if (cached != null && cached.wordId().equals(selectedWordId) && cached.epoch() == host.epoch()
				&& cached.showDe() == host.showDe() && cached.language() == language()
				&& cached.reviews() == reviews) {
			return cached;
		}
		Word word = host.hq().catalog().byId(selectedWordId).orElse(null);
		if (word == null) {
			return null;
		}
		boolean showDe = host.showDe();
		Concept concept = host.hq().catalog().concept(word.conceptId == null ? "" : word.conceptId).orElse(null);
		Component sentence = null;
		if (concept != null && concept.sentenceKo != null) {
			sentence = showDe
					? KoText.line(concept.sentenceKo, concept.sentenceDe == null ? "" : concept.sentenceDe)
					: KoText.ko(concept.sentenceKo);
		}
		WordProgress progress = learn == null ? null : learn.data().words.get(word.id);
		String metrics = progress == null ? Tr.t("noch nicht entdeckt")
				: Tr.t("gesehen ") + progress.seen + Tr.t(" · richtig ") + progress.correct + Tr.t(" · falsch ")
				+ progress.wrong + (progress.known ? Tr.t(" · bekannt") : "");
		detail = new Detail(selectedWordId, host.epoch(), showDe, language(), reviews, word,
				Component.literal(Tr.pick(word.en, word.de)), sentence,
				word.hasDescKo() ? KoText.ko(word.descKo) : null, metrics,
				progress != null && progress.known);
		return detail;
	}
}
