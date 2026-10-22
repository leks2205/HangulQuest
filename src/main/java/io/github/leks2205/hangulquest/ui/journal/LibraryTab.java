package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.HangulQuestClient;
import io.github.leks2205.hangulquest.culture.CultureHook;
import io.github.leks2205.hangulquest.culture.CultureNote;
import io.github.leks2205.hangulquest.culture.LunarHolidays;
import io.github.leks2205.hangulquest.dialogue.DialogueCoverage;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.Streak;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.story.LibraryContent;
import io.github.leks2205.hangulquest.story.LibraryHook;
import io.github.leks2205.hangulquest.story.MarkFile;
import io.github.leks2205.hangulquest.story.Story;
import io.github.leks2205.hangulquest.story.StoryCoverage;
import io.github.leks2205.hangulquest.story.StoryReading;
import io.github.leks2205.hangulquest.story.StoryText;
import io.github.leks2205.hangulquest.story.StoryVocabulary;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.KoText;
import io.github.leks2205.hangulquest.ui.StampMemo;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Library (책장): graded stories and culture notes. The list shows every story as open, read or locked;
 * an open story is a reading page where a word shows its gloss on hover or click, and a conjugated verb
 * its breakdown into stem, ending and grammar point. Opening a story logs an encounter for its words, never
 * a review. Everything that needs the whole catalog is built once a second, not per frame.
 */
public final class LibraryTab extends JournalTab {
	private static final Logger LOG = LoggerFactory.getLogger("hangulquest");
	private static final String READ_FILE = "stories-read.json";
	private static final int ROW_HEIGHT = 16;
	private static final int INFO_HEIGHT = 40;

	private enum Mode {
		LIST, STORY, NOTE
	}

	/** Everything the tab shows, built from the content, the catalog and what the player knows. */
	private record Data(LibraryContent content, StoryVocabulary vocabulary, List<StoryCoverage.Status> statuses,
						List<LibraryShelf.Row> rows, List<Component> glosses, Map<String, Component> titles, String header,
						int discovered, int completed) {
	}

	/** The page of a story that is still locked, texts built once per data version. */
	private record LockedPage(String storyId, Data data, String title, List<Component> lines, List<Component> words) {
	}

	/** A word of the reading page and where it sits, relative to the top of the text. */
	private record Placed(StoryText.Token token, int x, int y, int width, String note) {
		Placed(StoryText.Token token, int x, int y, int width) {
			this(token, x, y, width, null);
		}
	}

	private record Gloss(List<FormattedCharSequence> lines, int y) {
	}

	/** The reading page laid out for one width: the placed words, the translations (when shown) and the height. */
	private record Reader(String storyId, int width, boolean glossShown, int language, Data data,
						  List<Placed> words, List<Gloss> glosses, int height) {
	}

	private final StampMemo<Data> memo = new StampMemo<>();
	private final JournalHost.PointClick rowClick = this::clickRow;
	private final JournalHost.PointClick wordClick = this::clickWord;
	private MarkFile readLog;
	private MarkFile shownLog;
	private ProgressStore logStore;

	private Mode mode = Mode.LIST;
	private String selectedId;
	private int scroll;
	private int readerScroll;
	private boolean showGloss;
	/** Index in the placed words of the word the player clicked; -1 for none. Stays valid when the layout is rebuilt. */
	private int pinned = -1;
	private Reader reader;
	private LockedPage locked;

	// Where the list rows are on screen this frame, for the one click area that covers them.
	private List<LibraryShelf.Row> rows = List.of();
	private int listX;
	private int listY;
	private int listWidth;
	private int listFirst;
	private int listEnd;
	// Where the reading text is on screen this frame.
	private int textX;
	private int textY;
	private int textHeight;

	public LibraryTab(JournalHost host) {
		super(host);
	}

	@Override
	public void onOpen() {
		mode = Mode.LIST;
		selectedId = null;
		scroll = 0;
		pinned = -1;
		logStore = null;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double vertical) {
		if (mode == Mode.STORY && reader != null) {
			int max = Math.max(0, reader.height() - textHeight);
			readerScroll = Math.max(0, Math.min(max, readerScroll - (int) Math.signum(vertical) * 24));
			return true;
		}
		int visible = Math.max(1, (host.contentHeight() - 16) / ROW_HEIGHT);
		scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), scroll - (int) Math.signum(vertical) * 2));
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		HangulQuestClient hq = host.hq();
		LearningService learn = hq.learn();
		if (learn == null || hq.catalog() == null) {
			return;
		}
		openLogs(learn);
		Data data = data(hq, learn);
		rows = data.rows();
		switch (mode) {
			case STORY -> {
				if (!story(g, data, x, y, width, height)) {
					mode = Mode.LIST;
				}
			}
			case NOTE -> {
				if (!note(g, data, x, y, width, height)) {
					mode = Mode.LIST;
				}
			}
			default -> list(g, data, x, y, width, height);
		}
	}

	// ----------------------------------------------------------------------- data

	private void openLogs(LearningService learn) {
		if (readLog == null || logStore != learn.store()) {
			Path dir = learn.store().file().toAbsolutePath().getParent();
			readLog = MarkFile.open(dir, READ_FILE, LOG);
			shownLog = MarkFile.open(dir, CultureHook.FILE, LOG);
			logStore = learn.store();
		}
	}

	private Data data(HangulQuestClient hq, LearningService learn) {
		LibraryContent content = LibraryHook.content();
		int discovered = learn.discovered();
		int completed = learn.data().quests.completed.size();
		long stamp = StampMemo.perSecond(System.currentTimeMillis(), host.epoch(), language());
		Data cached = memo.value(stamp);
		if (cached != null && cached.content() == content && cached.discovered() == discovered
				&& cached.completed() == completed) {
			return cached;
		}
		StoryVocabulary vocabulary = StoryVocabulary.of(hq.catalog(), content.lexicon(),
				id -> learn.isKnown(id) || learn.introduced(id), Lang.current());
		List<StoryCoverage.Status> statuses = new ArrayList<>();
		for (Story story : content.stories()) {
			statuses.add(StoryCoverage.status(story, vocabulary, trail -> learn.data().quests.completed.containsKey(trail),
					discovered));
		}
		List<LibraryShelf.Row> shelf = LibraryShelf.rows(statuses, content.chapters(), content.culture(),
				id -> readLog.get(id) != null, id -> shownLog.get(id) != null, Lang.current());
		reader = null;
		locked = null;
		List<Component> glosses = shelf.stream().map(row -> (Component) Component.literal(row.gloss())).toList();
		String header = statuses.isEmpty() ? I18n.t("hangulquest.ui.library.empty")
				: I18n.t("hangulquest.ui.library.open_count", LibraryShelf.openStories(statuses), statuses.size());
		Map<String, Component> titles = new HashMap<>();
		statuses.forEach(status -> titles.put(status.story().id(), Component.literal(I18n.t(status.story().titleKey()))));
		return memo.store(stamp, new Data(content, vocabulary, statuses, shelf, glosses, Map.copyOf(titles), header,
				discovered, completed));
	}

	// ------------------------------------------------------------------------ list

	private void list(GuiGraphicsExtractor g, Data data, int x, int y, int width, int height) {
		Draw.label(g, host.font(), data.header(), x, y, Ink.INK_FAINT);
		int top = y + 14;
		int visible = Math.max(1, (height - 14) / ROW_HEIGHT);
		scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), scroll));
		int end = Math.min(rows.size(), scroll + visible);
		for (int i = scroll; i < end; i++) {
			LibraryShelf.Row row = rows.get(i);
			int rowY = top + (i - scroll) * ROW_HEIGHT;
			if (row.kind() == LibraryShelf.Kind.HEADING) {
				Draw.korean(g, host.font(), row.title(), x, rowY + 3, Ink.ACCENT);
				Draw.truncated(g, host.font(), data.glosses().get(i), x + 70, rowY + 3, width - 80, Ink.INK_FAINT);
				continue;
			}
			boolean rowLocked = row.state() == LibraryShelf.State.LOCKED;
			boolean hover = host.mouseX() >= x && host.mouseX() < x + width && host.mouseY() >= rowY && host.mouseY() < rowY + ROW_HEIGHT;
			if (hover) {
				g.fill(x - 2, rowY, x + width, rowY + ROW_HEIGHT - 1, Ink.PARCHMENT_DARK);
			}
			int color = rowLocked ? Ink.INK_FAINT : Ink.INK;
			Draw.label(g, host.font(), row.state() == LibraryShelf.State.DONE ? "✓" : rowLocked ? "·" : "●", x + 4, rowY + 3,
					row.state() == LibraryShelf.State.DONE ? Ink.GOOD : color);
			Draw.korean(g, host.font(), row.title(), x + 18, rowY + 3, color);
			Draw.truncated(g, host.font(), data.glosses().get(i), x + 110, rowY + 3, width - 210, Ink.INK_FAINT);
			if (row.kind() == LibraryShelf.Kind.STORY) {
				Draw.label(g, host.font(), row.tag(), x + width - 84, rowY + 3, rowLocked ? Ink.BAD : Ink.INK_SOFT);
			}
		}
		listX = x;
		listY = top;
		listWidth = width;
		listFirst = scroll;
		listEnd = end;
		host.hit(x, top, width, (end - scroll) * ROW_HEIGHT, rowClick);
	}

	private boolean clickRow(double mx, double my) {
		int index = listFirst + (int) ((my - listY) / ROW_HEIGHT);
		if (index < listFirst || index >= listEnd || mx < listX || mx >= listX + listWidth) {
			return false;
		}
		LibraryShelf.Row row = rows.get(index);
		if (row.kind() == LibraryShelf.Kind.HEADING) {
			return true;
		}
		selectedId = row.id();
		pinned = -1;
		readerScroll = 0;
		reader = null;
		showGloss = false;
		if (row.kind() == LibraryShelf.Kind.STORY) {
			mode = Mode.STORY;
			openStory(row.id());
		} else {
			mode = Mode.NOTE;
		}
		return true;
	}

	/** Reading an open story is an encounter for its words, once per study day. */
	private void openStory(String id) {
		Data data = memo.value(StampMemo.perSecond(System.currentTimeMillis(), host.epoch(), language()));
		HangulQuestClient hq = host.hq();
		if (data == null || hq.learn() == null || hq.catalog() == null) {
			return;
		}
		data.statuses().stream().filter(status -> status.story().id().equals(id) && status.open()).findFirst()
				.ifPresent(status -> {
					int exposed = StoryReading.open(hq.learn(), hq.catalog(), readLog, status.story(),
							System.currentTimeMillis(), Streak.dayKey(LocalDateTime.now()));
					if (exposed > 0) {
						host.changed();
					}
				});
	}

	// ----------------------------------------------------------------------- story

	/** Draws the story page; false when the story is gone (a reload dropped it). */
	private boolean story(GuiGraphicsExtractor g, Data data, int x, int y, int width, int height) {
		StoryCoverage.Status status = data.statuses().stream()
				.filter(candidate -> candidate.story().id().equals(selectedId)).findFirst().orElse(null);
		if (status == null) {
			return false;
		}
		Story story = status.story();
		Draw.korean(g, host.font(), story.titleKo(), x, y + 2, Ink.ACCENT);
		Draw.truncated(g, host.font(), data.titles().get(story.id()), x + 90, y + 2, width - 260, Ink.INK_SOFT);
		host.button(g, x + width - 62, y - 1, 58, 16, I18n.t("hangulquest.ui.words.back"), false, () -> mode = Mode.LIST);
		if (!status.open()) {
			locked(g, data, status, x, y + 20, width, height - 20);
			return true;
		}
		host.button(g, x + width - 140, y - 1, 74, 16, I18n.t("hangulquest.ui.library.translation"), showGloss,
				() -> {
					showGloss = !showGloss;
					reader = null;
				});
		textX = x;
		textY = y + 20;
		textHeight = height - 20 - INFO_HEIGHT;
		Reader layout = readerFor(status, data, width);
		readerScroll = Math.max(0, Math.min(Math.max(0, layout.height() - textHeight), readerScroll));
		Font font = host.font();
		g.enableScissor(textX, textY, textX + width, textY + textHeight);
		Placed hovered = null;
		for (int i = 0; i < layout.words().size(); i++) {
			Placed placed = layout.words().get(i);
			int screenY = textY + placed.y() - readerScroll;
			if (screenY + font.lineHeight < textY || screenY > textY + textHeight) {
				continue;
			}
			StoryText.Token token = placed.token();
			if (placed.note() != null) {
				Draw.label(g, font, placed.note(), textX + placed.x(), screenY, Ink.INK_FAINT);
				continue;
			}
			boolean glossed = status.isGlossed(token);
			int color = !token.word() || token.known() ? Ink.INK
					: token.state() == DialogueCoverage.State.UNRESOLVED ? Ink.BAD : Ink.ACCENT;
			Draw.korean(g, font, token.text(), textX + placed.x(), screenY, color);
			if (glossed) {
				// a glossed word is underlined all the time, its gloss stands behind it
				g.fill(textX + placed.x(), screenY + font.lineHeight, textX + placed.x() + placed.width(),
						screenY + font.lineHeight + 1, Ink.ACCENT);
			}
			boolean over = token.word() && inside(host.mouseX(), host.mouseY(), textX + placed.x(), screenY - 1, placed.width(),
					font.lineHeight + 2);
			if (over && host.mouseY() >= textY && host.mouseY() < textY + textHeight) {
				hovered = placed;
			}
			if (i == pinned || over) {
				g.fill(textX + placed.x(), screenY + font.lineHeight, textX + placed.x() + placed.width(),
						screenY + font.lineHeight + 1, Ink.ACCENT);
			}
		}
		for (Gloss gloss : layout.glosses()) {
			int screenY = textY + gloss.y() - readerScroll;
			if (screenY + 40 >= textY && screenY <= textY + textHeight) {
				Draw.drawLines(g, font, gloss.lines(), textX + 8, screenY, Ink.INK_FAINT, 0);
			}
		}
		g.disableScissor();
		host.hit(textX, textY, width, textHeight, wordClick);
		Placed shown = hovered != null ? hovered : pinned >= 0 && pinned < layout.words().size() ? layout.words().get(pinned) : null;
		info(g, shown, x, y + height - INFO_HEIGHT + 4, width);
		return true;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private boolean clickWord(double mx, double my) {
		if (reader == null) {
			return false;
		}
		Font font = host.font();
		for (int i = 0; i < reader.words().size(); i++) {
			Placed placed = reader.words().get(i);
			int screenY = textY + placed.y() - readerScroll;
			if (placed.token().word() && inside(mx, my, textX + placed.x(), screenY - 1, placed.width(), font.lineHeight + 2)
					&& my >= textY && my < textY + textHeight) {
				pinned = i == pinned ? -1 : i;
				return true;
			}
		}
		pinned = -1;
		return false;
	}

	/** The words of the story placed in lines of the given width; rebuilt only when the width, the language or the data change. */
	private Reader readerFor(StoryCoverage.Status status, Data data, int width) {
		Reader cached = reader;
		if (cached != null && cached.storyId().equals(status.story().id()) && cached.width() == width
				&& cached.glossShown() == showGloss && cached.language() == language() && cached.data() == data) {
			return cached;
		}
		Font font = host.font();
		int space = Math.max(3, font.width(" "));
		int lineStep = font.lineHeight + 4;
		List<Placed> words = new ArrayList<>();
		List<Gloss> glosses = new ArrayList<>();
		int cursorY = 2;
		for (int p = 0; p < status.paragraphs().size(); p++) {
			int cursorX = 0;
			for (StoryText.Token token : status.paragraphs().get(p)) {
				int wordWidth = font.width(KoText.ko(token.text()));
				if (cursorX > 0 && cursorX + wordWidth > width) {
					cursorX = 0;
					cursorY += lineStep;
				}
				words.add(new Placed(token, cursorX, cursorY, wordWidth));
				cursorX += wordWidth + space;
				if (status.isGlossed(token) && token.gloss() != null) {
					// the gloss of a glossed word is always on the page, not only on hover
					String note = "(" + token.gloss() + ")";
					int noteWidth = font.width(note);
					if (cursorX + noteWidth > width) {
						cursorX = 0;
						cursorY += lineStep;
					}
					words.add(new Placed(new StoryText.Token(note, false, token.state(), null, null, null), cursorX, cursorY,
							noteWidth, note));
					cursorX += noteWidth + space;
				}
			}
			cursorY += lineStep;
			if (showGloss) {
				List<FormattedCharSequence> lines = Draw.lines(font,
						Component.literal(I18n.t(status.story().paragraphKey(p))), width - 16);
				glosses.add(new Gloss(lines, cursorY));
				cursorY += lines.size() * (font.lineHeight + 1) + 2;
			}
			cursorY += 5;
		}
		reader = new Reader(status.story().id(), width, showGloss, language(), data, List.copyOf(words),
				List.copyOf(glosses), cursorY);
		return reader;
	}

	/** The strip under the text: the word, its meaning and, for a conjugated form, how it is built. */
	private void info(GuiGraphicsExtractor g, Placed placed, int x, int y, int width) {
		g.fill(x, y - 3, x + width, y - 2, Ink.PAGE_EDGE);
		if (placed == null) {
			Draw.label(g, host.font(), I18n.t("hangulquest.ui.library.hint"), x, y, Ink.INK_FAINT);
			return;
		}
		StoryText.Token token = placed.token();
		String core = token.text().replaceAll("[^가-힣ㄱ-ㅣ ]", "");
		String gloss = token.gloss() == null ? "?" : token.gloss();
		int color = token.known() ? Ink.INK : token.state() == DialogueCoverage.State.UNRESOLVED ? Ink.BAD : Ink.ACCENT;
		Draw.truncated(g, host.font(), Component.empty().append(KoText.ko(core)).append(Component.literal("  " + gloss)), x, y, width, color);
		StoryText.Breakdown parts = token.breakdown();
		if (parts == null) {
			return;
		}
		boolean verb = parts.base().endsWith("다");
		String how = verb ? I18n.t("hangulquest.ui.library.from_verb", parts.base(), parts.stem(), parts.ending())
				: parts.ending().isEmpty() ? "" : I18n.t("hangulquest.ui.library.from_noun", parts.stem(), parts.ending());
		if (!how.isEmpty()) {
			Draw.truncated(g, host.font(), KoText.mixed(how), x, y + 12, width, Ink.INK_SOFT);
		}
		if (!parts.grammar().isEmpty()) {
			GrammarBook.Construction rule = GrammarBook.shared().get(parts.grammar());
			if (rule != null) {
				Draw.truncated(g, host.font(), KoText.mixed(I18n.t("hangulquest.ui.library.rule", rule.form(), parts.grammar())),
						x, y + 24, width, Ink.INK_FAINT);
			}
		}
	}

	/** The page of a story that is not open yet: what is missing, and the words to learn first. */
	private void locked(GuiGraphicsExtractor g, Data data, StoryCoverage.Status status, int x, int y, int width, int height) {
		LockedPage page = locked;
		if (page == null || !page.storyId().equals(status.story().id()) || page.data() != data) {
			page = locked = lockedPage(data, status);
		}
		Font font = host.font();
		Draw.label(g, font, page.title(), x, y, Ink.BAD);
		int cursor = y + 14;
		for (Component line : page.lines()) {
			Draw.wrapped(g, font, line, x, cursor, width, Ink.INK, 2);
			cursor += Draw.wrappedHeight(font, line, width, 2) + 3;
		}
		if (!page.words().isEmpty()) {
			Draw.label(g, font, I18n.t("hangulquest.ui.library.new_words"), x, cursor, Ink.INK_FAINT);
			cursor += 13;
			for (int i = 0; i < page.words().size(); i++) {
				int rowY = cursor + (i / 2) * 12;
				if (rowY + 12 > y + height) {
					break;
				}
				Draw.truncated(g, font, page.words().get(i), x + (i % 2) * (width / 2), rowY, width / 2 - 8, Ink.ACCENT);
			}
		}
	}

	/** The word as a dictionary would list it: the dictionary form of a verb, the stem of a noun with its particle. */
	private static String baseForm(StoryText.Token token) {
		StoryText.Breakdown parts = token.breakdown();
		if (parts == null) {
			return token.text().replaceAll("[^가-힣ㄱ-ㅣ ]", "");
		}
		return parts.base().endsWith("다") ? parts.base() : parts.stem();
	}

	private LockedPage lockedPage(Data data, StoryCoverage.Status status) {
		List<Component> lines = new ArrayList<>();
		if (!status.missingTrails().isEmpty()) {
			List<String> names = new ArrayList<>();
			for (String trail : status.missingTrails()) {
				Questline line = host.hq().quests() == null ? null : host.hq().quests().byId(trail);
				names.add(line == null ? trail : line.titleKo() + " (" + line.title(Lang.current()) + ")");
			}
			lines.add(KoText.mixed(I18n.t("hangulquest.ui.library.need_trails", String.join(", ", names))));
		}
		if (!status.wordsEnough()) {
			lines.add(Component.literal(I18n.t("hangulquest.ui.library.need_words", status.story().unlock().minWords(),
					status.wordsMet())));
		}
		if (!status.coverage().meets()) {
			lines.add(Component.literal(I18n.t("hangulquest.ui.library.need_coverage", status.coverage().percent())));
		}
		List<Component> words = new ArrayList<>();
		for (StoryText.Token token : status.newWords()) {
			words.add(Component.empty().append(KoText.ko(baseForm(token)))
					.append(Component.literal("  " + (token.gloss() == null ? "?" : token.gloss()))));
		}
		return new LockedPage(status.story().id(), data, "· " + I18n.t("hangulquest.ui.library.locked"), List.copyOf(lines),
				List.copyOf(words));
	}

	// ------------------------------------------------------------------------ note

	private boolean note(GuiGraphicsExtractor g, Data data, int x, int y, int width, int height) {
		Optional<CultureNote> found = data.content().culture().byId(selectedId);
		if (found.isEmpty()) {
			return false;
		}
		CultureNote note = found.get();
		Font font = host.font();
		Draw.itemIcon(g, note.icon(), x, y);
		Draw.korean(g, font, note.titleKo(), x + 22, y + 1, Ink.ACCENT);
		Draw.truncated(g, font, Component.literal(I18n.t(note.titleKey())), x + 22, y + 11, width - 100, Ink.INK_SOFT);
		host.button(g, x + width - 62, y - 1, 58, 16, I18n.t("hangulquest.ui.words.back"), false, () -> mode = Mode.LIST);
		int cursor = y + 28;
		Component summary = KoText.ko(note.ko());
		Draw.wrapped(g, font, summary, x, cursor, width, Ink.INK, 3);
		cursor += Draw.wrappedHeight(font, summary, width, 3) + 2;
		Component translation = Component.literal(I18n.t(note.summaryKey()));
		Draw.wrapped(g, font, translation, x, cursor, width, Ink.INK_FAINT, 3);
		cursor += Draw.wrappedHeight(font, translation, width, 3) + 6;
		Component text = Component.literal(I18n.t(note.textKey()));
		Draw.wrapped(g, font, text, x, cursor, width, Ink.INK, 8);
		cursor += Draw.wrappedHeight(font, text, width, 8) + 6;
		Draw.truncated(g, font, Component.literal(noteMeta(note, host.hq())), x, cursor, width, Ink.ACCENT);
		return true;
	}

	/** Where a note comes from: the next date of a holiday, or the item that makes it appear. */
	private static String noteMeta(CultureNote note, HangulQuestClient hq) {
		if (note.isHoliday()) {
			Optional<LocalDate> next = LibraryHook.content().culture().holiday(note.holiday())
					.flatMap(holiday -> holiday.nextFrom(LocalDate.now()));
			return next.map(date -> I18n.t("hangulquest.ui.library.note_date", LunarHolidays.display(date, Lang.current())))
					.orElse(I18n.t("hangulquest.ui.library.note_no_date"));
		}
		if (note.anchors().isEmpty()) {
			return "";
		}
		String anchor = note.anchors().get(0);
		String name = hq.catalog() == null ? anchor
				: hq.catalog().byItemId(anchor).map(word -> word.ko + " (" + word.gloss(Lang.current()) + ")").orElse(anchor);
		return I18n.t("hangulquest.ui.library.note_anchor", name);
	}
}
