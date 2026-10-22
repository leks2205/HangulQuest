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

/** Generator wave 5: the sentences that need the step's text service (the whole step, not just the goal). */
class GeneratorWaveFiveClueTest {
	@TempDir
	Path temp;

	private QuestTextService text;

	@BeforeEach
	void setUp() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		ProgressStore store = new ProgressStore(temp, LoggerFactory.getLogger("wave5-test"));
		LearningService learn = new LearningService(store.data(), store, catalog);
		learn.grammar().unlockAll();
		text = new QuestTextService(catalog, learn);
	}

	// ----------------------------------------------- 4. the HOLDING phrase uses the taught word

	private static final Condition BOAT_IN_HAND = new Condition.Holding("minecraft:oak_boat");

	private QuestStep boatStep(String clueKo) {
		return new QuestStep("s1", List.of("torch", "boat"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(BOAT_IN_HAND), clueKo, "Platziere eine Fackel.", "Place a torch.", null, null, null, null);
	}

	@Test
	void i4_theHoldingPhraseOfAGeneratedClueSaysTheTaughtWord() {
		QuestStep step = boatStep(null);
		assertEquals("배를 들고 횃불을 놓아요.", text.hintKo(step, 1));
		// both nouns are new to the player: the clue used to fall back on the registry name of the boat
		assertEquals("배를 들고 횃불을 놓아요.", text.clueKo(step, 1));
	}

	@Test
	void i4_theHoldingPhraseOfAnAuthoredClueSaysTheTaughtWordToo() {
		assertEquals("배를 들고 횃불을 놓아요.", text.clueKo(boatStep("횃불을 놓아요."), 1));
		assertEquals("배를 들고 횃불을 놓아요.", text.hintKo(boatStep("횃불을 놓아요."), 1));
	}

	@Test
	void i4_aHeldItemNoTaughtWordNamesKeepsItsName() {
		QuestStep step = new QuestStep("s1", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(BOAT_IN_HAND), null, "Platziere eine Fackel.", "Place a torch.", null, null, null, null);
		assertEquals("참나무 보트를 들고 횃불을 놓아요.", text.clueKo(step, 1));
	}

}
