package io.github.leks2205.hangulquest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every shipped story can open for the canonical learner at its unlock point: 98% of its running words known
 * (glossed words count) and its minWords reached. The simulation is the learner of tools/coverage.py, joined with the
 * words each story needs by tools/story_coverage.py; this test writes both dumps and runs the tool.
 */
class StoryOpenabilityTest {
	@TempDir
	Path scratch;

	@Test
	void everyStoryOpensOnItsCanonicalPath() throws Exception {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		ContentFacts.write(ContentFacts.shipped(scratch), ContentFacts.DUMP);
		VerbFormsDumpTest.write();
		StoryCoverageDumpTest.write();
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/story_coverage.py", "--check-open");
		assertEquals(0, run.exit(), "a story stays closed at its unlock point (gloss up to three words, teach them on a "
				+ "later trail, or change the text):\n" + run.output());
	}
}
