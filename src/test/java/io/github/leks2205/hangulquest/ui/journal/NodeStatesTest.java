package io.github.leks2205.hangulquest.ui.journal;

import io.github.leks2205.hangulquest.quest.Questline;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-trail state of the tree, and when it has to be recomputed. */
class NodeStatesTest {
	private final List<Questline> trails = QuestTreeLayoutTest.sample();
	private final QuestTreeLayout layout = QuestTreeLayoutTest.layout(trails);

	private NodeStates build(Map<String, Long> completed, int discovered, Questline active) {
		return NodeStates.build(layout, completed, discovered, 1, active == null ? null : active.id(),
				trail -> !trail.id().equals("d"), id -> id.equals("b"), active);
	}

	@Test
	void doneUnlockedAndTrackedComeFromTheEngine() {
		NodeStates states = build(Map.of("a", 100L), 5, null);

		assertTrue(states.has(layout.indexOf("a"), NodeStates.DONE));
		assertFalse(states.has(layout.indexOf("b"), NodeStates.DONE));
		assertTrue(states.has(layout.indexOf("b"), NodeStates.UNLOCKED));
		assertFalse(states.has(layout.indexOf("d"), NodeStates.UNLOCKED));
		assertTrue(states.has(layout.indexOf("b"), NodeStates.TRACKED));
		assertFalse(states.has(layout.indexOf("c"), NodeStates.TRACKED));
	}

	@Test
	void theLastFinishedTrailSetsTheRecommendationWhenNothingIsActive() {
		Map<String, Long> completed = new LinkedHashMap<>();
		completed.put("e", 50L);
		completed.put("a", 100L);
		NodeStates states = build(completed, 5, null);

		assertEquals(layout.indexOf("a"), states.current(), "a was finished last");
		// c is open, not done, same level and the same topic (돌) as a; b has another topic.
		assertTrue(states.has(layout.indexOf("c"), NodeStates.RECOMMENDED));
		assertFalse(states.has(layout.indexOf("b"), NodeStates.RECOMMENDED));
		assertFalse(states.has(layout.indexOf("a"), NodeStates.RECOMMENDED), "never the trail itself");
		assertFalse(states.has(layout.indexOf("d"), NodeStates.RECOMMENDED), "d is locked");
	}

	@Test
	void theActiveTrailBeatsTheLastFinishedOne() {
		NodeStates states = build(Map.of("a", 100L), 5, trails.get(1));

		assertEquals(layout.indexOf("b"), states.current());
		assertFalse(states.has(layout.indexOf("c"), NodeStates.RECOMMENDED), "c has another topic than b");
	}

	@Test
	void aFinishedTrailIsNotRecommendedAgain() {
		NodeStates states = build(Map.of("a", 100L, "c", 90L), 5, null);

		assertFalse(states.has(layout.indexOf("c"), NodeStates.RECOMMENDED));
	}

	@Test
	void noProgressRecommendsNothing() {
		NodeStates states = build(null, 0, null);

		assertEquals(-1, states.current());
		for (int i = 0; i < layout.size(); i++) {
			assertFalse(states.has(i, NodeStates.RECOMMENDED));
			assertFalse(states.has(i, NodeStates.DONE));
		}
	}

	@Test
	void theStatesAreKeptWhileProgressIsUnchanged() {
		Map<String, Long> completed = new LinkedHashMap<>(Map.of("a", 100L));
		NodeStates states = build(completed, 5, null);

		assertTrue(states.matches(layout, completed, 1, 5, 1, null, null));
	}

	@Test
	void aFinishedTrailInvalidatesTheStates() {
		Map<String, Long> completed = new LinkedHashMap<>(Map.of("a", 100L));
		NodeStates states = build(completed, 5, null);

		completed.put("b", 200L);

		assertFalse(states.matches(layout, completed, 2, 5, 1, null, null));
	}

	@Test
	void aResetInvalidatesTheStatesEvenWithTheSameCount() {
		Map<String, Long> completed = new LinkedHashMap<>(Map.of("a", 100L));
		NodeStates states = build(completed, 5, null);

		// Reset replaces the whole map; one trail finished again later makes the size 1 once more.
		Map<String, Long> afterReset = new LinkedHashMap<>(Map.of("e", 300L));

		assertFalse(states.matches(layout, afterReset, 1, 5, 1, null, null));
	}

	@Test
	void newWordsInvalidateTheStatesBecauseTheyUnlockTrails() {
		Map<String, Long> completed = new LinkedHashMap<>();
		NodeStates states = build(completed, 5, null);

		assertFalse(states.matches(layout, completed, 0, 6, 1, null, null));
	}

	@Test
	void theQuestLogAndTheFocusInvalidateTheStates() {
		Map<String, Long> completed = new LinkedHashMap<>();
		NodeStates states = build(completed, 5, trails.get(1));

		assertFalse(states.matches(layout, completed, 0, 5, 2, "b", trails.get(1)), "another quest in the log");
		assertFalse(states.matches(layout, completed, 0, 5, 1, "b", trails.get(2)), "another focused trail");
	}

	@Test
	void anotherLayoutInvalidatesTheStates() {
		Map<String, Long> completed = new LinkedHashMap<>();
		NodeStates states = build(completed, 5, null);
		QuestTreeLayout reloaded = QuestTreeLayoutTest.layout(trails);

		assertFalse(states.matches(reloaded, completed, 0, 5, 1, null, null));
	}
}
