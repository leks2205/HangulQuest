package io.github.leks2205.hangulquest.quest.grammar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ladder.json decides the ending of each level; every name in it must resolve to real grammar. */
class LadderTest {
	private static final Ladder LADDER = Ladder.shared();
	private static final GrammarBook BOOK = GrammarBook.shared();

	@Test
	void theLevelsClimbFromPoliteToInstructionToObligation() {
		List<String> endings = List.of("ending:eoyo", "ending:seyo", "ending:seyo", "ending:eoya_haeyo",
				"ending:eoya_haeyo", "ending:eoya_haeyo");
		for (int level = 1; level <= 6; level++) {
			assertEquals(endings.get(level - 1), LADDER.rung(level).ending(), "level " + level);
		}
		// never the old resigned, double-negative or literary endings as an instruction
		for (int level = 1; level <= 6; level++) {
			assertFalse(LADDER.rung(level).ending().contains("su_bakke"), "level " + level);
		}
	}

	@Test
	void onlyLevelSixHasAForcedConclusionEnding() {
		for (int level = 1; level <= 5; level++) {
			assertNull(LADDER.rung(level).forcedEnding(), "level " + level);
			assertEquals(LADDER.rung(level).ending(), LADDER.rung(level).endingFor(true));
		}
		assertEquals("nominal:su_bakke", LADDER.rung(6).endingFor(true));
		assertEquals("ending:eoya_haeyo", LADDER.rung(6).endingFor(false));
		assertEquals("L6.su_bakke_eopda", LADDER.rung(6).grammarFor(true));
	}

	@Test
	void everyNameInTheLadderIsReal() {
		for (int level = 1; level <= 6; level++) {
			Ladder.Rung rung = LADDER.rung(level);
			assertTrue(Endings.has(rung.ending()), rung.ending());
			assertTrue(rung.forcedEnding() == null || Endings.has(rung.forcedEnding()), String.valueOf(rung.forcedEnding()));
			assertNotNull(BOOK.get(rung.grammar()), rung.grammar() + " is not in grammar.json");
			assertTrue(rung.forcedGrammar() == null || BOOK.has(rung.forcedGrammar()), String.valueOf(rung.forcedGrammar()));
		}
		LADDER.aliases().forEach((emitted, construction) -> {
			assertTrue(BOOK.has(construction), emitted + " points at " + construction + ", not in grammar.json");
			assertFalse(BOOK.has(emitted), emitted + " is a construction itself; an alias would hide it");
		});
	}

	@Test
	void levelsOutsideTheLadderAreClamped() {
		assertEquals(LADDER.rung(1), LADDER.rung(-3));
		assertEquals(LADDER.rung(6), LADDER.rung(9));
	}

	@Test
	void theGateIsThreeStepsAndComesFromTheData() {
		assertEquals(3, LADDER.gateSteps());
		Ladder custom = Ladder.parse(withGate(5));
		assertEquals(5, custom.gateSteps());
	}

	@Test
	void anEndingThatEndingsDoesNotKnowIsRejected() {
		String broken = withGate(3).replace("ending:seyo", "ending:no_such_form");
		assertThrows(IllegalStateException.class, () -> Ladder.parse(broken));
	}

	@Test
	void aliasesResolveAndPlainIdsStay() {
		assertEquals("L1.obj_eul", LADDER.resolve("L1.eul_reul"));
		assertEquals("L3.jima_seyo", LADDER.resolve("L3.jima"));
		assertEquals("L2.seyo", LADDER.resolve("L2.seyo"));
		assertEquals(List.of("L1.eoyo", "L1.obj_eul"), BOOK.resolve(List.of("L1.eoyo", "L1.eul_reul", "L1.obj_eul",
				"L3.neun_attr"), LADDER));
	}

	private static String withGate(int gate) {
		StringBuilder levels = new StringBuilder();
		String[] endings = {"eoyo", "seyo", "seyo", "eoya_haeyo", "eoya_haeyo", "eoya_haeyo"};
		for (int level = 1; level <= 6; level++) {
			levels.append(level > 1 ? "," : "").append('"').append(level).append("\":{\"ending\":\"ending:")
					.append(endings[level - 1]).append("\",\"grammar\":\"L1.eoyo\"}");
		}
		return "{\"gateSteps\":" + gate + ",\"levels\":{" + levels + "}}";
	}
}
