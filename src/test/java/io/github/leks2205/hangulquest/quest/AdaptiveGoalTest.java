package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The daily review asks for the thing a word names, never for the picture that illustrates it. */
class AdaptiveGoalTest {
	private static String target(Goal goal) {
		return switch (goal) {
			case Goal.AcquireItem g -> g.item();
			case Goal.ConsumeItem g -> g.item();
			case Goal.BreakBlock g -> g.block();
			default -> null;
		};
	}

	@Test
	void reviewGoalsUseReferentsNotIcons() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		int checked = 0;
		for (Word word : catalog.all()) {
			Goal goal = AdaptiveTrailGenerator.goalFor(word);
			String item = goal == null ? null : target(goal);
			if (item == null) {
				continue;
			}
			checked++;
			assertTrue(word.referents.contains(item), word.id + " asks for " + item + ", which it does not name");
		}
		assertTrue(checked > 100, "only " + checked + " item goals checked");
	}

	@Test
	void aVerbIllustratedByAnItemGetsNoItemGoal() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		Word eat = catalog.byConceptId("eat").orElseThrow();
		assertEquals("minecraft:apple", eat.iconItem);
		assertNull(AdaptiveTrailGenerator.goalFor(eat), "먹다 must not become 'get an apple'");
	}
}
