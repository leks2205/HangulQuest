package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.Questline;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grammar gate at levels 4 to 6 (generator wave 11): a trail written for level 6 reads one construction above what
 * the player has met, so the if clause (level 4) shows before the while clause (level 5), and every new
 * construction of the upper levels has its rule card in English and German.
 */
class UpperLevelGatingTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("upper-level-gating");

	@TempDir
	Path dir;

	private LearningService learn;
	private QuestEngine engine;
	private Catalog catalog;

	private static Concept concept(String id, String ko) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = id;
		concept.en = id;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		return concept;
	}

	private static Questline trail(Goal goal, List<Condition> conditions) {
		QuestStep step = new QuestStep("s1", List.of("torch"), goal, conditions, null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		return new Questline("up", "up", "up", 1, List.of("torch"), Questline.Requires.none(), List.of(step), finale,
				"끝났어요.", "Fertig.", "up-badge", 10, 6, false);
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

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		ProgressStore store = new ProgressStore(dir.resolve("p"), LOG);
		catalog = Catalog.ofConcepts(List.of(concept("torch", "횃불"), concept("stone", "돌")));
		learn = new LearningService(store.data(), store, catalog);
	}

	@AfterEach
	void tearDown() {
		Lang.set("EN");
	}

	private void start(Goal goal, List<Condition> conditions) {
		engine = new QuestEngine(List.of(trail(goal, conditions)), catalog, learn, LOG);
		Observation obs = new Observation();
		obs.now = 1_000;
		obs.dimension = "minecraft:overworld";
		engine.startTrail("up", obs, new Quiet());
	}

	/** The player has done the steps of every level up to {@code level}. */
	private void metUpTo(int level) {
		for (int g = 1; g <= level; g++) {
			for (int i = 0; i < Ladder.shared().gateSteps(); i++) {
				learn.grammar().ladderStep(g);
			}
		}
		assertEquals(level, learn.grammarLevel());
	}

	@Test
	void theIfClauseOpensAtLevelFourAndStaysAtFiveAndSix() {
		start(new Goal.PlaceBlock("minecraft:torch", 1), List.of(new Condition.Time(Condition.Phase.NIGHT)));
		String[] expected = {null, "밤에 횃불을 놓아요.", "밤에 횃불을 놓으세요.", "밤에 횃불을 놓으세요.",
				"밤이 되면 횃불을 놓아야 해요.", "밤이 되면 횃불을 놓아야 해요.", "밤이 되면 횃불을 놓아야 해요."};
		for (int g = 0; g <= 5; g++) {
			if (g > 0) {
				metUpTo(g);
			}
			assertEquals(expected[g + 1], engine.clueKo(engine.currentStep()), "grammar level " + g);
		}
		assertEquals("밤에 횃불을 놓아요.", engine.hintKo(engine.currentStep()), "the hint stays level one");
	}

	@Test
	void theWhileClauseWaitsForLevelFive() {
		start(new Goal.Wait(10, null), List.of(new Condition.Time(Condition.Phase.DUSK)));
		metUpTo(3);
		assertEquals("해가 지면 십 초 동안 기다려야 해요.", engine.clueKo(engine.currentStep()),
				"after level 3 the text is level 4: the if clause, not yet 해가 지는 동안");
		metUpTo(4);
		assertEquals("해가 지는 동안 기다려야 해요.", engine.clueKo(engine.currentStep()));
	}

	@Test
	void theHeldItemWaitsForLevelFiveToo() {
		start(new Goal.HoldWhile("minecraft:torch", new Goal.BreakBlock("minecraft:stone", 1, null), null, 10), List.of());
		metUpTo(3);
		assertEquals("횃불을 들고 돌을 캐야 해요.", engine.clueKo(engine.currentStep()));
		metUpTo(4);
		assertEquals("횃불을 들고 있는 동안 돌을 캐야 해요.", engine.clueKo(engine.currentStep()));
	}

	@Test
	void theConcessionWaitsForLevelFiveAndEvenThenTheNotStaysTheDecidingForm() {
		start(new Goal.Avoid(new Goal.PlaceBlock("minecraft:torch", 1), 60, new Condition.Time(Condition.Phase.NIGHT)),
				List.of());
		metUpTo(3);
		assertEquals("밤이 되면 횃불을 놓지 마세요.", engine.clueKo(engine.currentStep()));
		metUpTo(4);
		assertEquals("밤이 되어도 횃불을 놓지 마세요.", engine.clueKo(engine.currentStep()));
	}

	@Test
	void theNewConstructionOfTheStepShowsItsRuleCardInBothLanguages() {
		start(new Goal.PlaceBlock("minecraft:torch", 1), List.of(new Condition.Time(Condition.Phase.NIGHT)));
		metUpTo(3);
		// what the player met on the way: the ending and the object marker
		learn.grammarCardShown("L4.eoya_haeyo", 1_500);
		learn.grammarCardShown("L1.obj_eul", 1_500);
		QuestEngine.RuleCardView card = engine.pendingRuleCard();
		assertNotNull(card);
		assertEquals("L4.myeon", card.id());
		assertTrue(card.rule().get(0).startsWith("Stem + (으)면 = if / when"), card.rule().get(0));
		Lang.set("DE");
		assertTrue(engine.pendingRuleCard().rule().get(0).startsWith("Stamm + (으)면 = wenn"),
				engine.pendingRuleCard().rule().get(0));
	}

	@Test
	void everyUpperConstructionHasItsCardInEnglishAndGerman() {
		int seen = 0;
		for (GrammarBook.Construction construction : GrammarBook.shared().all()) {
			if (construction.level() < 4) {
				continue;
			}
			seen++;
			String id = construction.id();
			assertFalse(construction.ruleCard().ko().isEmpty(), id + " has no Korean example");
			assertTrue(construction.ruleCard().en().size() >= 2, id + " has no English rule");
			assertTrue(construction.ruleCard().de().size() >= 2, id + " has no German rule");
			assertFalse(construction.examples().isEmpty(), id + " has no example");
			assertFalse(construction.clozes().isEmpty(), id + " has no cloze");
			assertTrue(construction.ruleCard().en().stream().noneMatch(String::isBlank), id);
			assertTrue(construction.ruleCard().de().stream().noneMatch(String::isBlank), id);
		}
		assertTrue(seen >= 20, "only " + seen + " constructions at level 4 and up");
		for (String id : List.of("L4.myeon", "L4.jiman", "L4.eoseo", "L4.eureo_gayo", "L4.eulgeyo", "L5.neun_dongan",
				"L5.eodo", "L5.eunikka", "L5.hapsyo", "L6.eulsurok", "L6.deusi", "L6.gi_maryeon_ida",
				"L6.su_bakke_eopda", "L6.banmal")) {
			assertTrue(GrammarBook.shared().has(id), id + " is not a construction");
		}
	}
}
