package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wave 4, item 6: a second noun of a step stands in the clue when that keeps the clue to one unknown word, and
 * stays in the hint when it does not.
 */
class ClueWordsTest {
	@TempDir
	Path temp;

	private Catalog catalog;
	private LearningService learn;
	private QuestTextService text;

	@BeforeEach
	void setUp() {
		catalog = Catalog.ofConcepts(ConceptLoader.load());
		ProgressStore store = new ProgressStore(temp, LoggerFactory.getLogger("clue-words-test"));
		learn = new LearningService(store.data(), store, catalog);
		learn.grammar().unlockAll();
		text = new QuestTextService(catalog, learn);
	}

	private static final Goal DOOR_BY_PLANKS = new Goal.PlaceRel("minecraft:birch_door", Relation.NEXT_TO,
			"minecraft:spruce_planks", 1);

	/** A trail whose first step teaches the planks and whose second step teaches the door and the planks. */
	private QuestStep second(boolean planksFirst) {
		QuestStep first = new QuestStep("s1", List.of(planksFirst ? "planks" : "stone"),
				new Goal.PlaceBlock("minecraft:spruce_planks", 1), List.of(), null, null, null, null, null);
		QuestStep second = new QuestStep("s2", List.of("door", "planks"), DOOR_BY_PLANKS, List.of(), null, null, null,
				null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("t", "t", "t", 1, List.of("door", "planks"), Questline.Requires.none(),
				List.of(first, second), finale, "끝", "Ende", "t", 10, 1, false);
		return trail.steps().get(1); // the trail's own copy knows the steps before it
	}

	@Test
	void i6_aNounTheTrailAlreadyTaughtStandsInTheClue() {
		QuestStep step = second(true);
		assertEquals("판자 옆에 문을 달아요.", text.clueKo(step, 1));
	}

	@Test
	void i6_aNewSecondNounStaysInTheHint() {
		QuestStep step = second(false);
		// the door is the one unknown word; the planks are new too, so the clue keeps their registry name
		assertEquals("가문비나무 판자 옆에 문을 달아요.", text.clueKo(step, 1));
		assertEquals("판자 옆에 문을 달아요.", text.hintKo(step, 1));
	}

	@Test
	void i6_aNounThePlayerKnowsStandsInTheClueToo() {
		QuestStep step = second(false);
		assertEquals("가문비나무 판자 옆에 문을 달아요.", text.clueKo(step, 1));
		learn.markKnown(catalog.byConceptId("planks").orElseThrow().id, true, 1_000L);
		assertEquals("판자 옆에 문을 달아요.", text.clueKo(step, 1), "what the player learns changes the clue");
	}

	@Test
	void i6_aStepThatTeachesSeveralNounsNeedsNoAuthoredHint() {
		QuestStep step = second(false);
		// the generated hint names both nouns; nothing asks for an explicit one
		String hint = text.hintKo(step, 1);
		org.junit.jupiter.api.Assertions.assertTrue(hint.contains("문") && hint.contains("판자"), hint);
	}
}
