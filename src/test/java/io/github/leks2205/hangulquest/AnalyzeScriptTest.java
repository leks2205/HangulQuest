package io.github.leks2205.hangulquest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * tools/research/analyze.py is checked against synthetic logs with known answers by its own Python
 * unittest file, run from here so CI covers it. Needs python3; without it the test is skipped (CI has it).
 */
class AnalyzeScriptTest {
	@BeforeAll
	static void needsPython() {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
	}

	@Test
	void theAnalysisScriptPassesItsUnitTests() {
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/research/test_analyze.py");
		assertEquals(0, run.exit(), run.output());
	}

	@Test
	void thePseudowordGeneratorAcceptsTheShippedList() {
		// the vetting rules exist twice (Java, Python); the generator must agree with the shipped file
		String words = String.join(" ", io.github.leks2205.hangulquest.learn.research.Pseudowords.load().stream()
				.map(io.github.leks2205.hangulquest.learn.research.Pseudowords.Item::ko).toList());
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/research/make_pseudowords.py", "--print", "--words", words);
		assertEquals(0, run.exit(), run.output());
	}
}
