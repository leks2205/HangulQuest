package de.zirks.hangulquest;

import de.zirks.hangulquest.quest.KoGrammar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoGrammarTest {
	@Test
	void batchimDetectsFinalConsonant() {
		assertTrue(KoGrammar.hasBatchim("돌"));
		assertTrue(KoGrammar.hasBatchim("숲"));
		assertTrue(KoGrammar.hasBatchim("집"));
		assertFalse(KoGrammar.hasBatchim("나무"));
		assertFalse(KoGrammar.hasBatchim("사과"));
		assertFalse(KoGrammar.hasBatchim("좀비"));
		assertFalse(KoGrammar.hasBatchim(""));
	}

	@Test
	void particlesFollowBatchim() {
		assertEquals("돌을", KoGrammar.eul("돌"));
		assertEquals("나무를", KoGrammar.eul("나무"));
		assertEquals("좀비가", KoGrammar.iGa("좀비"));
		assertEquals("숲이", KoGrammar.iGa("숲"));
		assertEquals("돌은", KoGrammar.eunNeun("돌"));
		assertEquals("판자는", KoGrammar.eunNeun("판자"));
		assertEquals("주민과", KoGrammar.waGwa("주민"));
		assertEquals("나무와", KoGrammar.waGwa("나무"));
	}

	@Test
	void copulaFollowsBatchim() {
		assertEquals("숲이에요", KoGrammar.ieyo("숲"));
		assertEquals("나무예요", KoGrammar.ieyo("나무"));
	}
}
