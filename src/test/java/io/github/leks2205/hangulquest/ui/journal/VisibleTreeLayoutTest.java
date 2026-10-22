package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.quest.TrailVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tree lays out only what is shown, and counts its levels among those trails. */
class VisibleTreeLayoutTest {
	private static final List<Questline> TRAILS = List.of(
			QuestTreeLayoutTest.trail("a", "돌", 1),
			QuestTreeLayoutTest.trail("b", "돌", 2, "a"),
			QuestTreeLayoutTest.trail("c", "돌", 3, "b"),
			QuestTreeLayoutTest.trail("d", "돌", 4, "c"),
			QuestTreeLayoutTest.trail("e", "물", 5, "c"));

	@Test
	void aLayoutOfTheShownTrailsStartsAtTheTopOfWhatIsShown() {
		// a and b are done, c can be started, d and e wait behind it.
		List<Questline> shown = TRAILS.subList(2, 5);

		QuestTreeLayout layout = QuestTreeLayout.build(shown, TrailGraph.of(shown));

		assertEquals(3, layout.size());
		assertEquals(2, layout.levels(), "c on the first row, d and e on the second");
		assertEquals(1, layout.depth(layout.indexOf("c")));
		assertEquals(-1, layout.indexOf("a"), "finished trails are not drawn");
		assertEquals(layout.indexOf("c"), layout.primaryParent(layout.indexOf("d")));
	}

	@Test
	void theFullTreeStillHasEveryLevel() {
		QuestTreeLayout layout = QuestTreeLayout.build(TRAILS, TrailGraph.of(TRAILS));

		assertEquals(4, layout.levels());
		assertEquals(5, layout.size());
	}

	@Test
	void greyedTrailsCarryTheirOwnFlag() {
		List<Questline> shown = TRAILS.subList(2, 5);
		QuestTreeLayout layout = QuestTreeLayout.build(shown, TrailGraph.of(shown));
		Map<String, TrailVisibility.State> states = Map.of("c", TrailVisibility.State.AVAILABLE,
				"d", TrailVisibility.State.NEXT, "e", TrailVisibility.State.NEXT);

		NodeStates nodes = NodeStates.build(layout, Map.of(), 0, 0, null, trail -> trail.id().equals("c"),
				id -> false, null, id -> states.get(id) == TrailVisibility.State.NEXT);

		assertFalse(nodes.has(layout.indexOf("c"), NodeStates.NEXT));
		assertTrue(nodes.has(layout.indexOf("d"), NodeStates.NEXT));
		assertTrue(nodes.has(layout.indexOf("e"), NodeStates.NEXT));
		assertTrue(nodes.has(layout.indexOf("c"), NodeStates.UNLOCKED));
	}
}
