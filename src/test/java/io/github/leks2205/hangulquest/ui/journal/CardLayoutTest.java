package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest tab's cards: how wide they are, how many fit in a row, and that none covers another. */
class CardLayoutTest {
	private static List<Questline> roots(int count) {
		List<Questline> trails = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			trails.add(QuestTreeLayoutTest.trail("r" + i, "돌", i));
		}
		return trails;
	}

	private static QuestTreeLayout cards(List<Questline> trails, int viewWidth) {
		return QuestTreeLayout.buildCards(trails, TrailGraph.of(trails), viewWidth);
	}

	@Test
	void aNarrowViewFitsFewCardsAWideOneAtMostTen() {
		assertEquals(1, QuestTreeLayout.cardColumns(30), "never fewer than one");
		assertEquals(6, QuestTreeLayout.cardColumns(390), "a 427 px wide GUI: the journal page is about 390");
		assertEquals(10, QuestTreeLayout.cardColumns(600), "a 640 px wide GUI");
		assertEquals(QuestTreeLayout.MAX_COLUMNS, QuestTreeLayout.cardColumns(5000), "a huge window does not add columns");
	}

	@Test
	void cardsShareTheWidthButStayWithinTheirLimits() {
		for (int view : new int[] {60, 200, 390, 600, 1000, 5000}) {
			int width = QuestTreeLayout.cardWidth(view);
			int columns = QuestTreeLayout.cardColumns(view);
			assertTrue(width >= QuestTreeLayout.MIN_CARD_WIDTH, "legible at " + view + ": " + width);
			assertTrue(width <= QuestTreeLayout.MAX_CARD_WIDTH, "not stretched at " + view + ": " + width);
			if (view >= QuestTreeLayout.MIN_CARD_WIDTH) {
				assertTrue(columns * (width + QuestTreeLayout.CARD_GAP) - QuestTreeLayout.CARD_GAP <= view,
						"a full row fits into " + view);
			}
		}
		assertEquals(62, QuestTreeLayout.cardWidth(390));
	}

	@Test
	void aLevelWiderThanTheViewWrapsIntoRows() {
		QuestTreeLayout layout = cards(roots(14), 390);

		assertTrue(layout.cards());
		assertEquals(6, layout.columns());
		Map<Integer, Integer> perRow = new HashMap<>();
		for (int i = 0; i < layout.size(); i++) {
			perRow.merge(layout.baseTop(i), 1, Integer::sum);
		}
		assertEquals(3, perRow.size(), "14 cards at 6 per row: 6, 6, 2");
		assertEquals(6, perRow.values().stream().mapToInt(Integer::intValue).max().orElse(0));
		assertTrue(layout.baseWidth() <= 390, "the block fits the view: " + layout.baseWidth());
	}

	@Test
	void noTwoCardsOverlapAndRowsKeepTheirOrder() {
		for (int view : new int[] {150, 390, 600, 1200}) {
			List<Questline> trails = new ArrayList<>(roots(23));
			for (int i = 0; i < 17; i++) {
				trails.add(QuestTreeLayoutTest.trail("c" + i, "물", 40 + i, "r" + (i % 23)));
			}
			QuestTreeLayout layout = cards(trails, view);
			for (int i = 0; i < layout.size(); i++) {
				assertTrue(layout.baseLeft(i) >= 0, "inside the block on the left");
				assertTrue(layout.baseLeft(i) + layout.baseNodeWidth() <= layout.baseWidth(),
						"inside the block on the right, view " + view);
				assertTrue(layout.baseTop(i) + layout.baseNodeHeight() <= layout.baseHeight());
				for (int j = i + 1; j < layout.size(); j++) {
					assertFalse(overlap(layout, i, j), layout.trail(i).id() + " and " + layout.trail(j).id()
							+ " overlap at view width " + view);
				}
			}
		}
	}

	@Test
	void noTwoCardsOverlapOnTheScreenEitherAndTheChildLevelStartsBelowTheParents() {
		List<Questline> trails = new ArrayList<>(roots(9));
		trails.add(QuestTreeLayoutTest.trail("child", "물", 20, "r0"));
		QuestTreeLayout layout = cards(trails, 390);
		layout.project(12, 40, 1.0F);

		for (int i = 0; i < layout.size(); i++) {
			for (int j = i + 1; j < layout.size(); j++) {
				boolean apart = layout.left(i) + layout.nodeWidth() <= layout.left(j)
						|| layout.left(j) + layout.nodeWidth() <= layout.left(i)
						|| layout.top(i) + layout.nodeHeight() <= layout.top(j)
						|| layout.top(j) + layout.nodeHeight() <= layout.top(i);
				assertTrue(apart, i + " and " + j + " overlap on screen");
			}
		}
		int lastRoot = 0;
		for (int i = 0; i < layout.size(); i++) {
			if (layout.depth(i) == 1) {
				lastRoot = Math.max(lastRoot, layout.baseTop(i) + layout.baseNodeHeight());
			}
		}
		assertTrue(layout.baseTop(layout.indexOf("child")) >= lastRoot, "the follow-up sits below every root row");
	}

	@Test
	void cardsKeepTheirSizeWhenProjected() {
		QuestTreeLayout layout = cards(roots(3), 390);
		layout.project(0, 0, 1.0F);

		assertEquals(QuestTreeLayout.cardWidth(390), layout.nodeWidth());
		assertEquals(QuestTreeLayout.CARD_HEIGHT, layout.nodeHeight());
	}

	@Test
	void theDotTreeIsUnchangedByTheCardSupport() {
		QuestTreeLayout layout = QuestTreeLayout.build(roots(40), TrailGraph.of(roots(40)));

		assertFalse(layout.cards());
		assertEquals(40 * QuestTreeLayout.SLOT, layout.baseWidth(), "one long row, as before");
		assertEquals(QuestTreeLayout.NODE_HEIGHT, layout.baseHeight());
	}

	@Test
	void aTitleThatFitsStaysOnOneLine() {
		assertArrayEquals(new String[] {"꽃의 길", ""}, QuestTab.splitTitle("꽃의 길", 60, text -> text.length() * 9));
	}

	@Test
	void aLongTitleBreaksAtTheLastSpaceThatFitsAndKeepsTheRest() {
		// 6 characters per line
		String[] lines = QuestTab.splitTitle("심장 이식 수술 병원", 54, text -> text.length() * 9);

		assertArrayEquals(new String[] {"심장 이식", "수술 병원"}, lines);
	}

	@Test
	void aLongWordIsCutWhereItStopsFitting() {
		String[] lines = QuestTab.splitTitle("은밀하게위대하게", 36, text -> text.length() * 9);

		assertArrayEquals(new String[] {"은밀하게", "위대하게"}, lines);
	}

	@Test
	void aTitleBreakRightAtASpaceDoesNotLeaveASpaceBehind() {
		String[] lines = QuestTab.splitTitle("가나다라마바 사아", 54, text -> text.length() * 9);

		assertArrayEquals(new String[] {"가나다라마바", "사아"}, lines);
	}

	private static boolean overlap(QuestTreeLayout layout, int a, int b) {
		int w = layout.baseNodeWidth();
		int h = layout.baseNodeHeight();
		return layout.baseLeft(a) < layout.baseLeft(b) + w && layout.baseLeft(b) < layout.baseLeft(a) + w
				&& layout.baseTop(a) < layout.baseTop(b) + h && layout.baseTop(b) < layout.baseTop(a) + h;
	}
}
