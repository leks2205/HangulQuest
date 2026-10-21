package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.learn.GrammarProgress;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the grammar tab lists: the met constructions open up, the others are locked lines. */
class GrammarShelfTest {
	private static final GrammarBook BOOK = GrammarBook.shared();

	@Test
	void aNewPlayerSeesEveryLineLockedAndNoForm() {
		List<GrammarShelf.Row> rows = GrammarShelf.rows(BOOK, new GrammarProgress());
		assertEquals(BOOK.all().size(), rows.size());
		assertEquals(0, GrammarShelf.metCount(rows));
		for (GrammarShelf.Row row : rows) {
			assertFalse(row.met(), row.id());
			assertNull(row.form(), row.id() + " must not give its form away");
			assertEquals(0, row.mastery());
			assertTrue(row.level() >= 1 && row.level() <= 6);
		}
	}

	@Test
	void onlyMetConstructionsAreListedWithTheirFormAndTheRestStayLocked() {
		GrammarProgress progress = new GrammarProgress();
		progress.step("L1.eoyo");          // met by a step
		progress.cardShown("L2.seyo");     // met by its card
		progress.outcome("L1.obj_eul", true); // met by an answer
		List<GrammarShelf.Row> rows = GrammarShelf.rows(BOOK, progress);

		Set<String> listed = rows.stream().filter(GrammarShelf.Row::met).map(GrammarShelf.Row::id)
				.collect(Collectors.toSet());
		assertEquals(Set.of("L1.eoyo", "L2.seyo", "L1.obj_eul"), listed);
		assertEquals(progress.met(), listed);
		assertEquals(3, GrammarShelf.metCount(rows));
		for (GrammarShelf.Row row : rows) {
			if (row.met()) {
				assertEquals(BOOK.get(row.id()).form(), row.form());
			} else {
				assertNull(row.form(), row.id());
			}
		}
	}

	@Test
	void theBookIsInLadderOrder() {
		List<GrammarShelf.Row> rows = GrammarShelf.rows(BOOK, new GrammarProgress());
		int level = 1;
		for (GrammarShelf.Row row : rows) {
			assertTrue(row.level() >= level, row.id() + " comes after a higher level");
			level = row.level();
		}
		assertEquals("L1.eoyo", rows.get(0).id());
	}

	@Test
	void aLockedConstructionCannotBeOpened() {
		GrammarProgress progress = new GrammarProgress();
		assertNull(GrammarShelf.open(BOOK, progress, "L2.seyo"));
		assertNull(GrammarShelf.open(BOOK, progress, "no.such"));
		assertNull(GrammarShelf.open(BOOK, progress, null));
		progress.step("L2.seyo");
		GrammarBook.Construction seyo = GrammarShelf.open(BOOK, progress, "L2.seyo");
		assertNotNull(seyo);
		assertEquals(2, seyo.ruleCard().ko().size());
		assertFalse(seyo.examples().isEmpty());
	}

	@Test
	void masteryShowsAsOneToFivePipsAndAStarWhenMastered() {
		assertEquals(1, GrammarShelf.pips(GrammarProgress.P_L0));
		assertEquals(2, GrammarShelf.pips(GrammarProgress.update(GrammarProgress.P_L0, true)));
		assertEquals(5, GrammarShelf.pips(0.94));
		assertEquals(6, GrammarShelf.pips(GrammarProgress.MASTERED));

		GrammarProgress progress = new GrammarProgress();
		progress.step("L1.eoyo");
		GrammarShelf.Row fresh = GrammarShelf.rows(BOOK, progress).get(0);
		assertEquals(1, fresh.mastery());
		assertEquals(10, fresh.percent());
		assertEquals(1, fresh.steps());
	}
}
