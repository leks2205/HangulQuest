package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;

import java.util.ArrayList;
import java.util.List;

/**
 * What the grammar tab shows, without any drawing: every construction in ladder order, the met ones
 * with their form and mastery, the others only as a locked line with their level. A locked construction
 * gives nothing away, not even its form.
 */
public final class GrammarShelf {
	private GrammarShelf() {
	}

	/**
	 * One line of the book. {@code form} is null while the construction is locked; {@code mastery} is
	 * 1 to 5 pips, 6 for mastered.
	 */
	public record Row(String id, int level, boolean met, String form, int steps, int answers, int percent,
					  int mastery) {
	}

	public static List<Row> rows(GrammarBook book, GrammarProgress progress) {
		List<Row> rows = new ArrayList<>();
		for (GrammarBook.Construction c : book.all()) {
			boolean met = progress.met(c.id());
			double pL = progress.pL(c.id());
			rows.add(new Row(c.id(), c.level(), met, met ? c.form() : null, met ? progress.steps(c.id()) : 0,
					met ? progress.answers(c.id()) : 0, (int) Math.round(pL * 100), met ? pips(pL) : 0));
		}
		return rows;
	}

	static int pips(double pL) {
		if (pL >= GrammarProgress.MASTERED) {
			return 6;
		}
		return Math.max(1, Math.min(5, (int) Math.ceil(pL * 5)));
	}

	public static long metCount(List<Row> rows) {
		return rows.stream().filter(Row::met).count();
	}

	/** The construction for the detail page; null when the id is unknown or still locked. */
	public static GrammarBook.Construction open(GrammarBook book, GrammarProgress progress, String id) {
		return id == null || !progress.met(id) ? null : book.get(id);
	}
}
