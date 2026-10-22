package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authored clue wins also on a step with conditions: it is shown as written, with the condition
 * phrases it does not contain put in front, so every condition is still spoken.
 */
class AuthoredClueConditionsTest {
	private static final Condition NIGHT = new Condition.Time(Condition.Phase.NIGHT);
	private static final Condition OPEN_SKY = new Condition.Sky(Condition.SkyState.OPEN);

	@TempDir
	Path temp;

	private Catalog catalog;
	private QuestTextService text;

	@BeforeEach
	void setUp() {
		catalog = Catalog.ofConcepts(ConceptLoader.load());
		ProgressStore store = new ProgressStore(temp, LoggerFactory.getLogger("authored-clue-test"));
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.grammar().unlockAll();
		text = new QuestTextService(catalog, learn);
	}

	private static QuestStep step(String clueKo, Condition... conditions) {
		return new QuestStep("s1", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1), List.of(conditions),
				clueKo, "Platziere eine Fackel.", "Place a torch.", null, null, null, null);
	}

	@Test
	void anAuthoredClueIsKeptOnAStepWithConditions() {
		QuestStep step = step("밤에 조용히 횃불을 놓아요.", NIGHT);
		assertEquals("밤에 조용히 횃불을 놓아요.", text.clueKo(step, 1));
		// the generated sentence would be a different one
		assertEquals("밤에 횃불을 놓아요.", SentenceBuilder.normal(step.goal(), text.firstWord(step), step.conditions(), 1,
				false, text.namer()));
	}

	@Test
	void aConditionTheAuthoredClueLeavesOutIsPutInFront() {
		QuestStep step = step("조용히 횃불을 놓아요.", NIGHT);
		assertEquals("밤에 조용히 횃불을 놓아요.", text.clueKo(step, 1));
		QuestStep two = step("횃불을 놓아요.", NIGHT, OPEN_SKY);
		assertEquals("밤에 밖에서 횃불을 놓아요.", text.clueKo(two, 1));
		// only the missing one: the clue already says 밤에
		QuestStep half = step("밤에 횃불을 놓아요.", NIGHT, OPEN_SKY);
		assertEquals("밖에서 밤에 횃불을 놓아요.", text.clueKo(half, 1));
	}

	@Test
	void everyConditionIsSpokenWhatEverTheAuthoredText() {
		for (String clue : List.of("횃불을 놓아요.", "밤에 횃불을 놓아요.", "횃불을 조용히 놓아요.")) {
			QuestStep step = step(clue, NIGHT, OPEN_SKY);
			String said = text.clueKo(step, 5);
			assertTrue(said.contains(clue), said);
			assertTrue(said.contains("밤에"), said);
			assertTrue(said.contains("하늘이 보이는 곳에서"), said);
			assertTrue(SentenceBuilder.missingPhrases(said, step.conditions(), text.firstWord(step), text.namer(), 5)
					.isEmpty(), said);
		}
	}

	@Test
	void theConditionWordingFollowsTheLevelOfTheClue() {
		QuestStep step = step("횃불을 놓아요.", OPEN_SKY);
		assertEquals("밖에서 횃불을 놓아요.", text.clueKo(step, 1));
		assertEquals("밖에서 횃불을 놓아요.", text.clueKo(step, 3));
		assertEquals("하늘이 보이는 곳에서 횃불을 놓아요.", text.clueKo(step, 5));
		// a clue that already says the long form needs nothing at level 5
		QuestStep long_ = step("하늘이 보이는 곳에서 횃불을 놓아요.", OPEN_SKY);
		assertEquals("하늘이 보이는 곳에서 횃불을 놓아요.", text.clueKo(long_, 5));
	}

	@Test
	void aStepWithoutAuthoredTextStillGetsTheGeneratedClue() {
		QuestStep step = step(null, NIGHT);
		assertEquals("밤에 횃불을 놓아요.", text.clueKo(step, 1));
		assertFalse(text.grammarIds(step, 1).isEmpty());
		// an authored clue carries no generated grammar points
		assertTrue(text.grammarIds(step("밤에 횃불을 놓아요.", NIGHT), 1).isEmpty());
	}

	@Test
	void anAuthoredClueWithoutConditionsIsReturnedUntouched() {
		QuestStep step = step("횃불을 놓아요.");
		assertEquals("횃불을 놓아요.", text.clueKo(step, 1));
		assertEquals("횃불을 놓아요.", text.clueKo(step, 5));
	}
}
