package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Word recycling ratchet ("recycle words, not tasks"). For every core word (curriculum band 초급 or
 * NIKL grade A, not Minecraft-only) the canonical learner counts encounters, roles and returns within
 * 14 simulated days (tools/coverage.py). The medians and shares may not get worse than
 * content/metrics-baseline.json; the target (10 encounters in 3 roles) is printed, not demanded.
 * The under-exposed words go to {@code build/reports/content/recycling-gaps.csv} for the content briefs.
 */
class RecyclingTest {
	@TempDir
	Path scratch;

	// ------------------------------------------------------------------------ the shipped content

	@Test
	void recyclingIsNotWorseThanTheBaseline() throws IOException {
		CoverageTest.requirePython();
		Map<String, Double> actual = CoverageTest.shipped(scratch);
		JsonObject baseline = MetricsBaseline.load();
		System.out.println("word recycling (gap to the roadmap targets):\n"
				+ MetricsBaseline.gapReport(actual, baseline, "coverage.recycling."));
		List<String> worse = MetricsBaseline.regressions(actual, baseline, "coverage.recycling.");
		assertTrue(worse.isEmpty(), "word recycling got worse (fix the content, or on purpose: "
				+ "python3 tools/variety.py --update-baseline):\n" + String.join("\n", worse));
	}

	@Test
	void theGapListNamesTheUnderExposedCoreWordsWorstFirst() throws IOException {
		CoverageTest.requirePython();
		CoverageTest.shipped(scratch);
		Path csv = ContentFacts.DUMP.resolveSibling("recycling-gaps.csv");
		List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
		assertTrue(lines.get(0).startsWith("word,concepts,kind,"), lines.get(0));
		assertTrue(lines.size() > 10, "expected many under-exposed words in the classic tree, got " + (lines.size() - 1));
		int previous = -1;
		for (String line : lines.subList(1, lines.size())) {
			String[] cells = line.split(",", -1);
			int encounters = Integer.parseInt(cells[6]);
			assertTrue(encounters >= previous, "worst first: " + line);
			previous = encounters;
			assertFalse(cells[12].isBlank(), "a listed word lacks something: " + line);
		}
	}

	// ---------------------------------------------------------------------------- synthetic content

	/**
	 * 32 trails, two per simulated day. 돌 is in all of them in three roles; 횃불 in two; 문 in the first and in trail
	 * {@code doorAgain}; 삽 in none; 상자 (not a core word) in one.
	 */
	private static List<ContentFacts.Trail> corpus(int doorAgain) {
		var stone = CoverageTest.target("object", "minecraft:stone");
		var torch = CoverageTest.target("object", "minecraft:torch");
		List<ContentFacts.Trail> trails = new ArrayList<>();
		for (int i = 0; i < 32; i++) {
			String id = "t" + String.format("%02d", i);
			trails.add(switch (i) {
				case 0 -> CoverageTest.trail(id, i, "문 옆에 돌을 놓아요.", "", stone,
						CoverageTest.target("anchor", "minecraft:oak_door"));
				case 2 -> CoverageTest.trail(id, i, "돌로 횃불을 만들어요.", "", torch,
						CoverageTest.target("tool", "minecraft:stone"));
				case 3 -> CoverageTest.trail(id, i, "돌이 있어요.", "");
				case 5 -> CoverageTest.trail(id, i, "횃불을 놓아요.", "", torch);
				case 7 -> CoverageTest.trail(id, i, "상자를 놓아요.", "");
				default -> i == doorAgain ? CoverageTest.trail(id, i, "문을 놓아요.", "")
						: CoverageTest.trail(id, i, "돌을 놓아요.", "", stone);
			});
		}
		return trails;
	}

	private static Map<String, String[]> rows(Path csv) throws IOException {
		Map<String, String[]> out = new HashMap<>();
		List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
		for (String line : lines.subList(1, lines.size())) {
			String[] cells = line.split(",", -1);
			out.put(cells[0], cells);
		}
		return out;
	}

	private static final int ENCOUNTERS = 6;
	private static final int CONTEXTS = 7;
	private static final int RETRIEVALS = 9;
	private static final int LACKS = 12;

	@Test
	void aWordMetOftenInThreeRolesIsNotListedAndTheOthersSayWhatTheyLack() throws IOException {
		CoverageTest.requirePython();
		Path csv = scratch.resolve("gaps.csv");
		CoverageTest.run(scratch, corpus(28), csv, "--json");
		Map<String, String[]> rows = rows(csv);

		assertFalse(rows.containsKey("돌"), "32 encounters in the roles object, tool and mention are enough");
		assertFalse(rows.containsKey("상자"), "not a core word");
		assertFalse(rows.containsKey("놓다"), "not a core word");

		String[] torch = rows.get("횃불");
		assertEquals("2", torch[ENCOUNTERS]);
		assertEquals("1", torch[CONTEXTS], "both times as the object");
		assertTrue(torch[LACKS].contains("encounters 2/10") && torch[LACKS].contains("roles 1/3"), torch[LACKS]);

		String[] shovel = rows.get("삽");
		assertEquals("0", shovel[ENCOUNTERS], "a core word nobody uses is the worst gap");
		assertTrue(shovel[LACKS].contains("encounters 0/10"));
	}

	@Test
	void aReturnCountsOnlyWithinFourteenDaysOfTheFirstEncounter() throws IOException {
		CoverageTest.requirePython();
		// two trails per day: trail 28 is on day 14, trail 30 on day 15
		Path inside = scratch.resolve("inside.csv");
		CoverageTest.run(scratch, corpus(28), inside, "--json");
		assertEquals("1", rows(inside).get("문")[RETRIEVALS], "day 14 is still inside the window");

		Path outside = scratch.resolve("outside.csv");
		CoverageTest.run(scratch, corpus(30), outside, "--json");
		assertEquals("0", rows(outside).get("문")[RETRIEVALS], "day 15 is too late to count as a retrieval");
	}

	@Test
	void theMetricsSummariseTheCoreWords() throws IOException {
		CoverageTest.requirePython();
		Map<String, Double> m = CoverageTest.run(scratch, corpus(28), scratch.resolve("gaps.csv"), "--json").metrics();
		// core words: 돌 (32 encounters), 횃불 (2), 문 (2), 삽 (0)
		assertEquals(2.0, m.get("coverage.recycling.medianEncounters"));
		assertEquals(0.75, m.get("coverage.recycling.seenShare"));
		assertEquals(0.25, m.get("coverage.recycling.targetMetShare"), "only 돌 has 10 encounters in 3 roles");
		assertEquals(1.0, m.get("coverage.recycling.medianRetrievals"), "retrievals 14, 1, 1, 0");
	}

	@Test
	void theCheckFailsWhenRecyclingGetsWorse() throws IOException {
		CoverageTest.requirePython();
		Path baseline = scratch.resolve("baseline.json");
		Files.writeString(baseline, "{\"params\":" + MetricsBaseline.load().getAsJsonObject("params") + ",\"metrics\":{"
				+ "\"coverage.recycling.medianEncounters\":{\"value\":10,\"better\":\"higher\",\"target\":10},"
				+ "\"coverage.recycling.medianRetrievals\":{\"value\":0,\"better\":\"higher\",\"target\":3},"
				+ "\"coverage.recycling.seenShare\":{\"value\":1,\"better\":\"higher\",\"target\":1},"
				+ "\"coverage.recycling.targetMetShare\":{\"value\":0,\"better\":\"higher\",\"target\":1}}}");
		CoverageTest.Result result = CoverageTest.run(scratch, corpus(28), scratch.resolve("gaps.csv"), "--check",
				"--baseline", baseline.toString());
		assertEquals(1, result.exit(), result.output());
		assertTrue(result.output().contains("medianEncounters got worse"), result.output());
		assertTrue(result.output().contains("seenShare got worse"), result.output());
		assertFalse(result.output().contains("medianRetrievals got worse"), "an equal or better value is allowed");
	}

	@Test
	void theCheckRefusesABaselineMadeForAnotherLearner() throws IOException {
		CoverageTest.requirePython();
		Path baseline = scratch.resolve("baseline.json");
		Files.writeString(baseline, "{\"params\":{\"knownAfter\":99},\"metrics\":{}}");
		CoverageTest.Result result = CoverageTest.run(scratch, corpus(28), scratch.resolve("gaps.csv"), "--check",
				"--baseline", baseline.toString());
		assertEquals(1, result.exit(), result.output());
		assertTrue(result.output().contains("parameters differ"), result.output());
	}
}
