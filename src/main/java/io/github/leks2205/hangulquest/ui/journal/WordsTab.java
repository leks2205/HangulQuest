package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.HanjaMeanings;
import io.github.leks2205.hangulquest.data.LexiconTags;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.Srs;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.quest.grammar.Pronunciation;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The dictionary: every word, discovered ones first, and a detail page for one word. */
public final class WordsTab extends JournalTab {
	private static final int ROW_HEIGHT = 18;
	/** Most hanja lines, family words per line and warnings the detail page shows. */
	private static final int MAX_FAMILIES = 4;
	private static final int MAX_MEMBERS = 6;
	private static final int MAX_WARNINGS = 2;

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
						  String metrics, boolean known, List<Component> family, List<Component> warnings,
						  String pron, Component ruleTag, boolean listen) {
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
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.words.count_hint", words.size()), x, y + height - 10,
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
					Draw.fit(host.font(), Component.literal(word.gloss(Lang.current())), current.glossWidth),
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
		if (d.pron() != null) {
			Draw.korean(g, host.font(), d.pron(), x + 22 + host.font().width(KoText.ko(word.ko)) + 8, y + 3, Ink.INK_SOFT);
		}
		if (d.listen()) {
			String label = I18n.t("hangulquest.ui.audio.listen");
			int w = Math.max(40, host.font().width(label) + 12);
			host.button(g, x + width - w, y, w, 14, label, false, () -> host.hq().audio().playWord(word.audioId()));
		}
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
		if (d.ruleTag() != null) {
			Draw.truncated(g, host.font(), d.ruleTag(), x, yy, width, Ink.INK_FAINT);
			yy += 11;
		}
		Draw.label(g, host.font(), d.metrics(), x, yy, Ink.INK_FAINT);
		yy += 14;
		if (!d.family().isEmpty()) {
			Draw.label(g, host.font(), I18n.t("hangulquest.ui.words.family"), x, yy, Ink.ACCENT);
			yy += 11;
			for (Component line : d.family()) {
				Draw.truncated(g, host.font(), line, x + 4, yy, width - 4, Ink.INK);
				yy += 11;
			}
			for (Component warning : d.warnings()) {
				Draw.wrapped(g, host.font(), warning, x + 4, yy, width - 4, Ink.BAD, 2);
				yy += 22;
			}
		}
		yy += 6;
		boolean known = d.known();
		host.button(g, x, yy, 110, 18, known ? I18n.t("hangulquest.ui.words.relearn") : I18n.t("hangulquest.ui.words.mark_known"), false, () -> {
			host.hq().learn().markKnown(word.id, !known, System.currentTimeMillis());
			host.changed();
			host.status(known ? I18n.t("hangulquest.ui.words.status_relearn") : I18n.t("hangulquest.ui.words.status_known"));
		});
		host.button(g, x + 118, yy, 90, 18, I18n.t("hangulquest.ui.words.back"), false, () -> selectedWordId = null);
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
		String metrics = progress == null ? I18n.t("hangulquest.ui.words.not_discovered")
				: I18n.t(progress.known ? "hangulquest.ui.words.metrics_known" : "hangulquest.ui.words.metrics",
						progress.seen, progress.correct, progress.wrong);
		MorphemeFamilies.Panel panel = MorphemeFamilies.of(word, LexiconTags.shared(), host.hq().catalog().all(),
				other -> learn != null && learn.introduced(other.id));
		Pronunciation.Result pron = Pronunciation.of(word.ko, word.conceptId, LexiconTags.shared());
		boolean listen = host.hq().audio() != null && host.hq().audio().hasWord(word.audioId());
		detail = new Detail(selectedWordId, host.epoch(), showDe, language(), reviews, word,
				Component.literal(word.glossFull(Lang.current())), sentence,
				word.hasDescKo() ? KoText.ko(word.descKo) : null, metrics,
				progress != null && progress.known, familyLines(panel), warningLines(panel),
				pron.bracket(), ruleTag(pron), listen);
		return detail;
	}

	/** The rules behind a pronunciation, each as the grammar term and a short gloss: "비음화 nasalisation · ...". */
	static Component ruleTag(Pronunciation.Result pron) {
		if (!pron.differs() || pron.rules().isEmpty()) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (Pronunciation.Rule rule : pron.rules()) {
			text.append(text.length() == 0 ? "" : " · ").append(rule.ko()).append(' ').append(I18n.t(rule.key()));
		}
		return KoText.mixed(text.toString());
	}

	/** One line per hanja of a Sino-Korean word: the character, its meaning and the words that share it. */
	private static List<Component> familyLines(MorphemeFamilies.Panel panel) {
		List<Component> lines = new ArrayList<>();
		HanjaMeanings meanings = HanjaMeanings.shared();
		for (MorphemeFamilies.Family family : panel.families()) {
			if (lines.size() >= MAX_FAMILIES) {
				break;
			}
			StringBuilder text = new StringBuilder(family.hanja());
			String meaning = meanings.meaning(family.hanja());
			if (!meaning.isEmpty()) {
				text.append(' ').append(meaning);
			}
			if (family.reading() != null) {
				text.append(" (").append(family.reading()).append(')');
			}
			List<MorphemeFamilies.Member> members = family.members();
			for (int i = 0; i < Math.min(MAX_MEMBERS, members.size()); i++) {
				text.append(i == 0 ? " · " : ", ").append(members.get(i).ko());
			}
			if (members.size() > MAX_MEMBERS) {
				text.append(" +").append(members.size() - MAX_MEMBERS);
			}
			lines.add(KoText.mixed(text.toString()));
		}
		return lines;
	}

	private static List<Component> warningLines(MorphemeFamilies.Panel panel) {
		List<Component> lines = new ArrayList<>();
		HanjaMeanings meanings = HanjaMeanings.shared();
		for (MorphemeFamilies.Warning warning : panel.warnings()) {
			if (lines.size() >= MAX_WARNINGS) {
				break;
			}
			lines.add(KoText.mixed(I18n.t("hangulquest.ui.words.homophone", warning.reading(), warning.other(),
					meanings.meaning(warning.other()), warning.otherWordKo())));
		}
		return lines;
	}
}
