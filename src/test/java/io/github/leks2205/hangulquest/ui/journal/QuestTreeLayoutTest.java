package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest tree's shape, computed once per trail list. */
class QuestTreeLayoutTest {
	static Questline trail(String id, String topic, int order, String... requires) {
		return new Questline(id, id + "-ko", id + "-de", topic, 1, List.of(),
				new Questline.Requires(0, null, List.of(requires)), List.of(), null, "", "", null, order, 1, false);
	}

	/**
	 * a(돌) and e(물) are roots; b(나무) and c(돌) follow a; d(돌) needs both b and c, so its
	 * connector comes from c, the parent with the same topic.
	 */
	static List<Questline> sample() {
		return List.of(
				trail("a", "돌", 1),
				trail("b", "나무", 2, "a"),
				trail("c", "돌", 3, "a"),
				trail("d", "돌", 4, "b", "c"),
				trail("e", "물", 5));
	}

	static QuestTreeLayout layout(List<Questline> trails) {
		return QuestTreeLayout.build(trails, TrailGraph.of(trails));
	}

	@Test
	void trailsAreIndexedInTrailOrder() {
		List<Questline> shuffled = new ArrayList<>(sample());
		Collections.reverse(shuffled);
		QuestTreeLayout layout = layout(shuffled);

		assertEquals("a", layout.trail(0).id());
		assertEquals("e", layout.trail(4).id());
		assertEquals(3, layout.indexOf("d"));
		assertEquals(-1, layout.indexOf("missing"));
		assertEquals(-1, layout.indexOf(null));
	}

	@Test
	void depthGivesTheLevelsAndTheBaseSize() {
		QuestTreeLayout layout = layout(sample());

		assertEquals(3, layout.levels());
		assertEquals(1, layout.depth(layout.indexOf("a")));
		assertEquals(2, layout.depth(layout.indexOf("c")));
		assertEquals(3, layout.depth(layout.indexOf("d")));
		assertEquals(2 * QuestTreeLayout.SLOT, layout.baseWidth(), "the widest row has two trails");
		assertEquals(2 * QuestTreeLayout.ROW_GAP + QuestTreeLayout.NODE_HEIGHT, layout.baseHeight());
	}

	@Test
	void theConnectorComesFromTheParentWithTheSameTopic() {
		QuestTreeLayout layout = layout(sample());
		int a = layout.indexOf("a");
		int b = layout.indexOf("b");
		int c = layout.indexOf("c");
		int d = layout.indexOf("d");

		assertEquals(-1, layout.primaryParent(a));
		assertEquals(a, layout.primaryParent(b));
		assertEquals(c, layout.primaryParent(d), "c has d's topic, b does not");
		assertArrayEquals(new int[] {b, c}, layout.parents(d), "the detail still lists every prerequisite");
		assertArrayEquals(new int[] {b, c}, layout.dependents(a));
	}

	@Test
	void withoutASameTopicParentTheFirstOneDrawsTheConnector() {
		List<Questline> trails = List.of(
				trail("a", "돌", 1), trail("b", "물", 2), trail("c", "불", 3, "a", "b"));
		QuestTreeLayout layout = layout(trails);

		assertEquals(layout.indexOf("a"), layout.primaryParent(layout.indexOf("c")));
	}

	@Test
	void slotsAreCentredPerRow() {
		QuestTreeLayout layout = layout(sample());
		layout.project(0, 0, 1.0F);

		// level 1: a and e side by side; level 2: b and c; level 3: d alone, centred
		assertEquals(QuestTreeLayout.SLOT / 2, layout.cx(layout.indexOf("a")));
		assertEquals(QuestTreeLayout.SLOT + QuestTreeLayout.SLOT / 2, layout.cx(layout.indexOf("e")));
		assertEquals(QuestTreeLayout.SLOT, layout.cx(layout.indexOf("d")));
		assertEquals(0, layout.top(layout.indexOf("a")));
		assertEquals(2 * QuestTreeLayout.ROW_GAP, layout.top(layout.indexOf("d")));
	}

	@Test
	void aDeeperRowFollowsItsParentsPosition() {
		// x sits right of y at level 1, so x's child must sit right of y's child at level 2.
		List<Questline> trails = List.of(
				trail("y", "돌", 1), trail("x", "돌", 2),
				trail("child-of-x", "돌", 3, "x"), trail("child-of-y", "돌", 9, "y"));
		QuestTreeLayout layout = layout(trails);
		layout.project(0, 0, 1.0F);

		assertTrue(layout.cx(layout.indexOf("child-of-y")) < layout.cx(layout.indexOf("child-of-x")),
				"order 9 would put child-of-y last, the parent's slot puts it first");
	}

	@Test
	void projectionScalesWithZoomAndMovesWithTheOrigin() {
		QuestTreeLayout layout = layout(sample());
		int a = layout.indexOf("a");

		layout.project(100, 50, 1.0F);
		assertEquals(115, layout.cx(a));
		assertEquals(50, layout.top(a));
		assertEquals(22, layout.nodeWidth());
		assertEquals(20, layout.nodeHeight());

		layout.project(100, 50, 0.5F);
		assertEquals(108, layout.cx(a));
		assertEquals(11, layout.nodeWidth());
		assertEquals(10, layout.nodeHeight());

		layout.project(100, 50, 0.1F);
		assertEquals(10, layout.nodeWidth(), "nodes stay clickable when zoomed far out");
		assertEquals(8, layout.nodeHeight());
	}

	@Test
	void anUnchangedViewIsNotProjectedAgain() {
		QuestTreeLayout layout = layout(sample());

		layout.project(100, 50, 1.0F);
		layout.project(100, 50, 1.0F);
		layout.project(100, 50, 1.0F);
		assertEquals(1, layout.projections(), "same zoom and origin: nothing to recompute");

		layout.project(100, 50, 1.2F);
		assertEquals(2, layout.projections(), "zoom changed");
		layout.project(101, 50, 1.2F);
		assertEquals(3, layout.projections(), "panned");
		layout.project(101, 49, 1.2F);
		assertEquals(4, layout.projections(), "panned vertically");
	}

	@Test
	void theNodeUnderThePointIsFoundInsideTheView() {
		QuestTreeLayout layout = layout(sample());
		layout.project(0, 0, 1.0F);
		int d = layout.indexOf("d");
		int centreX = layout.cx(d);
		int centreY = layout.top(d) + 5;

		assertEquals(d, layout.nodeAt(centreX, centreY, 0, 200, 0, 200));
		assertEquals(-1, layout.nodeAt(centreX + 40, centreY, 0, 200, 0, 200), "empty space");
		assertEquals(-1, layout.nodeAt(centreX, centreY, 100, 200, 0, 200), "the node is above the view");
	}

	@Test
	void selectionLightsTheChainAboveAndEverythingBelow() {
		QuestTreeLayout layout = layout(sample());
		int a = layout.indexOf("a");
		int b = layout.indexOf("b");
		int c = layout.indexOf("c");
		int d = layout.indexOf("d");
		int e = layout.indexOf("e");

		boolean[] fromD = layout.connected(d);
		assertTrue(fromD[d] && fromD[c] && fromD[a], "d, its connector parent c and the root a");
		assertFalse(fromD[b] || fromD[e]);

		boolean[] fromA = layout.connected(a);
		assertTrue(fromA[a] && fromA[b] && fromA[c] && fromA[d]);
		assertFalse(fromA[e]);
	}

	@Test
	void theLastSelectionIsReused() {
		QuestTreeLayout layout = layout(sample());

		assertTrue(layout.connected(0) == layout.connected(0));
		assertFalse(layout.connected(0) == layout.connected(3));
	}

	@Test
	void aLayoutMatchesTheListItWasBuiltFrom() {
		List<Questline> trails = sample();
		QuestTreeLayout layout = layout(trails);

		assertTrue(layout.matches(trails));
		assertTrue(layout.matches(new ArrayList<>(trails)), "a copy of the same trails is the same tree");
		assertFalse(layout.matches(trails.subList(0, 4)), "a trail was removed");
		List<Questline> replaced = new ArrayList<>(trails);
		replaced.set(2, trail("c", "돌", 3, "a"));
		assertFalse(layout.matches(replaced), "a reloaded trail with the same id is a different tree");
		List<Questline> reordered = new ArrayList<>(trails);
		Collections.reverse(reordered);
		assertFalse(layout.matches(reordered));
	}

	@Test
	void aDuplicatedIdIsLaidOutOnce() {
		List<Questline> trails = new ArrayList<>(sample());
		trails.add(trail("a", "물", 9));
		QuestTreeLayout layout = layout(trails);

		assertEquals(5, layout.size());
	}

	@Test
	void aPrerequisiteCycleStillGetsALayout() {
		List<Questline> trails = List.of(trail("p", "돌", 1, "q"), trail("q", "돌", 2, "p"));
		QuestTreeLayout layout = layout(trails);
		layout.project(0, 0, 1.0F);

		assertEquals(2, layout.size());
		assertEquals(1, layout.levels(), "trails on a cycle fall back to level 1");
	}

	@Test
	void anEmptyTreeIsHarmless() {
		QuestTreeLayout layout = layout(List.of());
		layout.project(0, 0, 1.0F);

		assertEquals(0, layout.size());
		assertEquals(-1, layout.nodeAt(0, 0, 0, 100, 0, 100));
		assertEquals(0, layout.connected(0).length);
	}
}
