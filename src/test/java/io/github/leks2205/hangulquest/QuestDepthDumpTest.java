package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.Topik;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * tools/quest_tree.py has its own copy of the depth and TOPIK band logic. This writes what the mod
 * computes ({@code {"id": {"depth": n, "level": n}}}) and has the script compare against it, so the
 * generated quest-tree report can never describe a different tree than the one the game builds.
 */
class QuestDepthDumpTest {
	static final Path DUMP = Path.of("build/reports/quest-depths.json");

	@Test
	void questTreePyAgreesWithTrailGraph() throws IOException {
		List<Questline> trails = QuestLoader.loadAll();
		TrailGraph graph = TrailGraph.of(trails);
		StringBuilder json = new StringBuilder("{\n");
		List<Questline> sorted = trails.stream().sorted(java.util.Comparator.comparing(Questline::id)).toList();
		for (int i = 0; i < sorted.size(); i++) {
			int depth = graph.depth(sorted.get(i).id());
			json.append("  \"").append(sorted.get(i).id()).append("\": {\"depth\": ").append(depth)
					.append(", \"level\": ").append(Topik.levelForDepth(depth)).append('}')
					.append(i + 1 < sorted.size() ? ",\n" : "\n");
		}
		json.append("}\n");
		Files.createDirectories(DUMP.getParent());
		Files.writeString(DUMP, json.toString(), StandardCharsets.UTF_8);

		QuestScaffoldTest.Run python = QuestScaffoldTest.python("-c", "pass");
		assumeTrue(python.exit() == 0, "python3 is not available");
		QuestScaffoldTest.Run check = QuestScaffoldTest.python("tools/quest_tree.py", "--check-depths", DUMP.toString());
		assertEquals(0, check.exit(), check.output());
		assertTrue(check.output().contains(trails.size() + " trails"), check.output());
	}

	@Test
	void theCheckCatchesADifferentDepth() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		List<Questline> trails = QuestLoader.loadAll();
		TrailGraph graph = TrailGraph.of(trails);
		Questline first = trails.get(0);
		int depth = graph.depth(first.id());
		Path wrong = Files.createTempFile("quest-depths", ".json");
		try {
			StringBuilder json = new StringBuilder("{");
			for (Questline trail : trails) {
				int d = graph.depth(trail.id()) + (trail == first ? 1 : 0);
				json.append("\"").append(trail.id()).append("\": {\"depth\": ").append(d)
						.append(", \"level\": ").append(Topik.levelForDepth(d)).append("},");
			}
			json.setLength(json.length() - 1);
			json.append("}");
			Files.writeString(wrong, json.toString());
			QuestScaffoldTest.Run check = QuestScaffoldTest.python("tools/quest_tree.py", "--check-depths", wrong.toString());
			assertEquals(1, check.exit(), "a different depth must fail: depth was " + depth);
			assertTrue(check.output().contains(first.id()), check.output());
		} finally {
			Files.deleteIfExists(wrong);
		}
	}
}
