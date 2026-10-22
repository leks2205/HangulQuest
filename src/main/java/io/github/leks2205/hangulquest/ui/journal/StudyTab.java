package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.ui.Draw;
import io.github.leks2205.hangulquest.ui.StampMemo;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** The review list: the cards that are due. Finding them sorts every word, so it happens once a second. */
public final class StudyTab extends JournalTab {
	private static final int LIST_SIZE = 8;

	private record Due(Word word, FormattedCharSequence gloss) {
	}

	/** The list as drawn; {@code width} is part of what it was built for, because the gloss is cut to it. */
	private record Page(String header, List<Due> due, int width) {
	}

	private final StampMemo<Page> memo = new StampMemo<>();
	private final Runnable reviewNow = () -> {
		host.hq().hud().startFromDue();
		host.close();
	};

	public StudyTab(JournalHost host) {
		super(host);
	}

	@Override
	public void render(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		LearningService learn = host.hq().learn();
		if (learn == null) {
			return;
		}
		long now = System.currentTimeMillis();
		long stamp = StampMemo.perSecond(now, host.epoch(), language());
		Page page = memo.value(stamp);
		if (page == null || page.width() != width) {
			page = memo.store(stamp, build(learn, now, width));
		}
		Draw.label(g, host.font(), page.header(), x, y, Ink.INK);
		int yy = y + 16;
		for (Due entry : page.due()) {
			Draw.korean(g, host.font(), entry.word().ko, x, yy, Ink.INK);
			Draw.text(g, host.font(), entry.gloss(), x + 70, yy, Ink.INK_SOFT, false);
			yy += 14;
		}
		if (!page.due().isEmpty()) {
			host.button(g, x, y + height - 30, 150, 18, I18n.t("hangulquest.ui.study.review_now"), false, reviewNow);
		}
		Draw.label(g, host.font(), I18n.t("hangulquest.ui.study.auto_hint"), x,
				y + height - 10, Ink.INK_FAINT);
	}

	private Page build(LearningService learn, long now, int width) {
		List<Word> words = learn.dueWords(now, LIST_SIZE);
		String header = words.isEmpty() ? I18n.t("hangulquest.ui.study.nothing_due")
				: I18n.t("hangulquest.ui.study.cards_due", words.size());
		List<Due> due = words.stream()
				.map(word -> new Due(word, Draw.fit(host.font(),
						Component.literal(word.gloss(Lang.current())), width - 80)))
				.toList();
		return new Page(header, due, width);
	}
}
