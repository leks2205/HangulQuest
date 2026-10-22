package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.grammar.GrammarBook;
import io.github.leks2205.hangulquest.quest.grammar.Ladder;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder at runtime: the player's grammar level gates the text, a step teaches the level it was
 * written at, and the rule card of a new construction shows once.
 */
class GrammarLadderTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("grammar-ladder-test");

	@TempDir
	Path dir;

	private ProgressStore store;
	private Catalog catalog;
	private LearningService learn;
	private QuestEngine engine;

	private static Concept concept(String id, String ko, String en) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = en;
		concept.en = en;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		return concept;
	}

	/** A trail of {@code steps} generated stone steps and a torch finale, written for this Korean level. */
	private static Questline trail(String id, int koreanLevel, int steps) {
		List<QuestStep> list = new ArrayList<>();
		for (int i = 1; i <= steps; i++) {
			list.add(new QuestStep("s" + i, List.of("stone"), new Goal.BreakBlock("minecraft:stone", 1, null),
					List.of(), null, null, null, null, null));
		}
		QuestStep finale = new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		return new Questline(id, id, id, 1, List.of("stone", "torch"), Questline.Requires.none(), list, finale,
				"끝났어요.", "Fertig.", id + "-badge", 10, koreanLevel, false);
	}

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		store = new ProgressStore(dir.resolve("p"), LOG);
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "stone"), concept("torch", "횃불", "torch")));
		learn = new LearningService(store.data(), store, catalog);
	}

	@AfterEach
	void tearDown() {
		Lang.set("EN");
	}

	private QuestEngine engineFor(Questline... trails) {
		engine = new QuestEngine(List.of(trails), catalog, learn, LOG);
		return engine;
	}

	private static Observation at(long now) {
		Observation obs = new Observation();
		obs.now = now;
		obs.dimension = "minecraft:overworld";
		return obs;
	}

	private static final class Quiet implements QuestEngine.Feedback {
		@Override
		public void banner(String ko, String de, boolean success) {
		}

		@Override
		public void wordCard(Word word) {
		}

		@Override
		public void sound(boolean success) {
		}
	}

	/** Breaks a stone: completes the current stone step. */
	private void breakStone(long now) {
		Observation obs = at(now);
		obs.brokenBlocks.add("minecraft:stone");
		engine.tick(obs, new Quiet());
	}

	// ------------------------------------------------------------------ gating

	@Test
	void aNewPlayerReadsLevelOneEvenOnAHardTrail() {
		engineFor(trail("hard", 4, 3)).startTrail("hard", at(1_000), new Quiet());
		QuestStep step = engine.currentStep();
		assertEquals("돌을 캐요.", engine.clueKo(step));
		assertEquals("돌을 캐요.", engine.hintKo(step));
	}

	@Test
	void theTextMayUseTheLevelTheyHaveMetPlusOneNewOne() {
		engineFor(trail("hard", 6, 3)).startTrail("hard", at(1_000), new Quiet());
		QuestStep step = engine.currentStep();
		String[] expected = {null, "돌을 캐세요.", "돌을 캐세요.", "돌을 캐야 해요.", "돌을 캐야 해요.", "돌을 캐야 해요.",
				"돌을 캐야 해요."};
		// grammar level g: the text of level g + 1
		for (int g = 1; g <= 6; g++) {
			for (int i = 0; i < Ladder.shared().gateSteps(); i++) {
				learn.grammar().ladderStep(g);
			}
			assertEquals(g, learn.grammarLevel(), "grammar level");
			assertEquals(expected[g], engine.clueKo(step), "after the steps of level " + g);
		}
	}

	@Test
	void twoStepsOfALevelDoNotOpenTheNext() {
		engineFor(trail("hard", 4, 3)).startTrail("hard", at(1_000), new Quiet());
		learn.grammar().ladderStep(1);
		learn.grammar().ladderStep(1);
		assertEquals(0, learn.grammarLevel());
		assertEquals("돌을 캐요.", engine.clueKo(engine.currentStep()));
	}

	@Test
	void aTrailNeverReadsAboveItsOwnLevel() {
		engineFor(trail("easy", 2, 3)).startTrail("easy", at(1_000), new Quiet());
		learn.grammar().unlockAll();
		assertEquals("돌을 캐세요.", engine.clueKo(engine.currentStep()));
	}

	@Test
	void doingStepsRaisesTheLevelAndTheNextStepReadsHigher() {
		engineFor(trail("climb", 4, 3)).startTrail("climb", at(1_000), new Quiet());
		for (int i = 0; i < 3; i++) {
			assertEquals("돌을 캐요.", engine.clueKo(engine.currentStep()), "step " + (i + 1) + " is level 1");
			breakStone(2_000 + i * 100L);
		}
		assertEquals(1, learn.grammarLevel());
		assertEquals(3, learn.grammar().stepsAtLevel(1));
		assertEquals(3, learn.grammar().steps("L1.eoyo"));
		assertEquals(3, learn.grammar().steps("L1.obj_eul"), "the alias L1.eul_reul counts for L1.obj_eul");
		assertEquals("finale", engine.currentStep().id());
		assertEquals("횃불을 놓으세요.", engine.clueKo(engine.currentStep()), "the new construction: -(으)세요");
	}

	@Test
	void anAuthoredClueIsLeftAlone() {
		QuestStep authored = new QuestStep("s1", List.of("stone"), new Goal.BreakBlock("minecraft:stone", 1, null),
				List.of(), "돌을 캐야만 해요.", "Baue Stein ab.", null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("a", "a", "a", 1, List.of("stone"), Questline.Requires.none(),
				List.of(authored), finale, "끝", "Ende", "a", 10, 4, false);
		engineFor(trail).startTrail("a", at(1_000), new Quiet());
		assertEquals("돌을 캐야만 해요.", engine.clueKo(engine.currentStep()));
		breakStone(2_000);
		assertEquals(0, learn.grammar().stepsAtLevel(1), "an authored clue teaches no ladder level");
	}

	// ------------------------------------------------------------------ rule card

	@Test
	void theRuleCardShowsOnceAndThenTheNextNewForm() {
		engineFor(trail("cards", 1, 3)).startTrail("cards", at(1_000), new Quiet());
		QuestEngine.RuleCardView first = engine.pendingRuleCard();
		assertNotNull(first);
		assertEquals("L1.eoyo", first.id());
		assertEquals("-아/어요", first.form());
		assertEquals(List.of("횃불을 놓아요.", "빵을 먹어요."), first.examplesKo());
		assertEquals(2, first.rule().size());
		assertTrue(first.rule().get(0).startsWith("Polite everyday ending"), first.rule().get(0));

		engine.ruleCardShown(first.id());
		assertNull(engine.pendingRuleCard(), "one card per step");
		assertNull(engine.pendingRuleCard(), "and not again for the same step");

		breakStone(2_000);
		QuestEngine.RuleCardView second = engine.pendingRuleCard();
		assertNotNull(second, "the next step brings the next new form");
		assertEquals("L1.obj_eul", second.id());
		engine.ruleCardShown(second.id());

		breakStone(2_100);
		assertNull(engine.pendingRuleCard(), "both forms have been met; the third step shows nothing");
	}

	@Test
	void theRuleCardComesInTheInterfaceLanguage() {
		Lang.set("DE");
		engineFor(trail("cards", 1, 2)).startTrail("cards", at(1_000), new Quiet());
		QuestEngine.RuleCardView card = engine.pendingRuleCard();
		assertNotNull(card);
		assertTrue(card.rule().get(0).startsWith("Höfliche Alltagsendung"), card.rule().get(0));
		assertEquals(List.of("횃불을 놓아요.", "빵을 먹어요."), card.examplesKo(), "the Korean examples stay Korean");
	}

	@Test
	void aMetFormStaysMetAfterARestart() {
		engineFor(trail("cards", 1, 2)).startTrail("cards", at(1_000), new Quiet());
		QuestEngine.RuleCardView card = engine.pendingRuleCard();
		engine.ruleCardShown(card.id());
		breakStone(2_000);
		learn.reviewLog().flush();

		// a new session on the same profile: nothing but the review log remembers
		LearningService again = new LearningService(store.data(), store, catalog);
		assertTrue(again.grammarMet("L1.eoyo"));
		assertEquals(1, again.grammar().steps("L1.eoyo"));
		assertEquals(1, again.grammar().stepsAtLevel(1));
		QuestEngine reopened = new QuestEngine(List.of(trail("cards", 1, 2)), catalog, again, LOG);
		reopened.startTrail("cards", at(5_000), new Quiet());
		QuestEngine.RuleCardView next = reopened.pendingRuleCard();
		assertNotNull(next);
		assertEquals("L1.obj_eul", next.id(), "L1.eoyo is not shown a second time");
	}

	@Test
	void resettingProgressStartsTheGrammarOver() {
		engineFor(trail("cards", 1, 2)).startTrail("cards", at(1_000), new Quiet());
		engine.ruleCardShown(engine.pendingRuleCard().id());
		assertTrue(learn.grammarMet("L1.eoyo"));
		assertTrue(learn.reset());
		assertFalse(learn.grammarMet("L1.eoyo"));
		learn.reviewLog().flush();
		LearningService again = new LearningService(store.data(), store, catalog);
		assertFalse(again.grammarMet("L1.eoyo"), "the reset is in the log, so the next start agrees");
	}

	// ------------------------------------------------------------------ the ids the content emits

	/** Ids a goal type emits that grammar.json has no construction for: no card, no count. */
	private static final Set<String> NO_CONSTRUCTION = Set.of();

	@Test
	void everyGrammarIdTheContentEmitsIsAConstructionOrAnAliasOfOne() {
		Set<String> unresolved = new TreeSet<>();
		GrammarBook book = GrammarBook.shared();
		SentenceBuilder.Namer namer = KoreanFixtures.registryNamer();
		for (Questline trail : QuestLoader.loadAll()) {
			for (QuestStep step : trail.allSteps()) {
				for (int level = 1; level <= 6; level++) {
					for (String id : SentenceBuilder.grammar(step.goal(), null, step.conditions(), level,
							"finale".equals(step.id()), namer)) {
						if (!book.has(Ladder.shared().resolve(id))) {
							unresolved.add(id);
						}
					}
				}
			}
		}
		assertEquals(NO_CONSTRUCTION.stream().sorted().toList(), List.copyOf(unresolved),
				"ids with no construction in grammar.json; add an alias to ladder.json or a construction");
	}
}
