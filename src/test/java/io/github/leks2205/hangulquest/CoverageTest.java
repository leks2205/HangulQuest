package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sentence coverage ratchet: a canonical learner walks the trail graph (tools/coverage.py) and a word
 * is known after a few encounters. Per sentence, the unknown words and the hint coverage may not get
 * worse than content/metrics-baseline.json; the roadmap targets (at most one unknown word per
 * sentence, hints that use only known words) are printed, not demanded. The simulation lives in
 * Python; the synthetic tests here feed it content that has to fail.
 */
class CoverageTest {
	@TempDir
	Path scratch;

	// ---------------------------------------------------------------- helpers shared with RecyclingTest

	/** A one-step trail with the given clue, hint and targets. */
	static ContentFacts.Trail trail(String id, int order, String clue, String hint, ContentFacts.Target... targets) {
		return trail(id, order, clue, hint, "", targets);
	}

	static ContentFacts.Trail trail(String id, int order, String clue, String hint, String finale,
									ContentFacts.Target... targets) {
		ContentFacts.Step step = new ContentFacts.Step("s1", "PLACE_BLOCK", "PLACE_BLOCK:x:PLACE", "PLACE_BLOCK:PLACE",
				"PLACE", "x", "", List.of(), List.of("goal:PLACE_BLOCK"), true, false, List.of("L1.eoyo"), List.of(),
				List.of(targets), clue, hint);
		return new ContentFacts.Trail(id, 1, order, false, List.of(), "", finale, List.of(step));
	}

	/** A one-step trail whose step teaches the given concept ids; title and finale are free text. */
	static ContentFacts.Trail teaching(String id, int order, String title, String clue, String hint, String finale,
									   List<String> teach) {
		ContentFacts.Step step = new ContentFacts.Step("s1", "PLACE_BLOCK", "PLACE_BLOCK:x:PLACE", "PLACE_BLOCK:PLACE",
				"PLACE", "x", "", List.of(), List.of("goal:PLACE_BLOCK"), true, false, List.of("L1.eoyo"), teach,
				List.of(), clue, hint);
		return new ContentFacts.Trail(id, 1, order, false, List.of(), title, finale, List.of(step));
	}

	static ContentFacts.Target target(String role, String id) {
		return new ContentFacts.Target(role, id);
	}

	/** The words of a tiny lexicon: stone and torch are core, the chest is not. */
	static String lexicon() {
		return """
				[{"id":"stone","ko":"돌","core":true,"ids":["minecraft:stone"]},
				 {"id":"torch","ko":"횃불","core":true,"ids":["minecraft:torch"]},
				 {"id":"door","ko":"문","core":true,"ids":["minecraft:oak_door"]},
				 {"id":"shovel","ko":"삽","core":true,"ids":["minecraft:iron_shovel"]},
				 {"id":"chest","ko":"상자","core":false,"ids":["minecraft:chest"]},
				 {"id":"place","ko":"놓다","core":false,"verb":true},
				 {"id":"craft","ko":"만들다","core":false,"verb":true}]""";
	}

	record Result(int exit, String output, Map<String, Double> metrics) {
	}

	/** Runs tools/coverage.py on the trails with the tiny lexicon; the baseline is optional. */
	static Result run(Path dir, List<ContentFacts.Trail> trails, Path csv, String... more) throws IOException {
		Path steps = dir.resolve("steps.json");
		ContentFacts.write(trails, steps);
		Path lexicon = dir.resolve("lexicon.json");
		Files.writeString(lexicon, lexicon(), StandardCharsets.UTF_8);
		List<String> args = new ArrayList<>(List.of("tools/coverage.py", "--steps", steps.toString(), "--lexicon",
				lexicon.toString(), "--csv", csv.toString()));
		args.addAll(List.of(more));
		QuestScaffoldTest.Run run = QuestScaffoldTest.python(args.toArray(new String[0]));
		return new Result(run.exit(), run.output(), args.contains("--json") ? parse(run.output()) : Map.of());
	}

	static Map<String, Double> parse(String json) {
		Map<String, Double> out = new LinkedHashMap<>();
		JsonObject object = JsonParser.parseString(json).getAsJsonObject();
		object.entrySet().forEach(entry -> out.put(entry.getKey(), entry.getValue().getAsDouble()));
		return out;
	}

	static void requirePython() {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
	}

	/** The metrics of the shipped content, from tools/coverage.py with the shipped lexicon. */
	static Map<String, Double> shipped(Path scratch) throws IOException {
		ContentFacts.write(ContentFacts.shipped(scratch), ContentFacts.DUMP);
		try {
			VerbFormsDumpTest.write(); // the tool reads the verb forms next to the steps
		} catch (Exception broken) {
			throw new IllegalStateException(broken);
		}
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/coverage.py", "--steps", ContentFacts.DUMP.toString(),
				"--csv", ContentFacts.DUMP.resolveSibling("recycling-gaps.csv").toString(), "--json");
		assertEquals(0, run.exit(), run.output());
		return parse(run.output());
	}

	// ------------------------------------------------------------------------ the shipped content

	@Test
	void sentencesAreNotLessCoveredThanTheBaseline() throws IOException {
		requirePython();
		Map<String, Double> actual = shipped(scratch);
		JsonObject baseline = MetricsBaseline.load();
		System.out.println("sentence coverage (gap to the roadmap targets):\n"
				+ MetricsBaseline.gapReport(actual, baseline, "coverage.sentences."));
		List<String> worse = MetricsBaseline.regressions(actual, baseline, "coverage.sentences.");
		assertTrue(worse.isEmpty(), "sentence coverage got worse (fix the content, or on purpose: "
				+ "python3 tools/variety.py --update-baseline):\n" + String.join("\n", worse));
	}

	@Test
	void theSimulatorParametersAreTheBaselinesOnes() {
		// the numbers mean something only for the learner they were measured with
		JsonObject params = MetricsBaseline.load().getAsJsonObject("params");
		assertEquals(3, params.get("knownAfter").getAsInt());
		assertTrue(params.has("trailsPerDay") && params.has("retrievalDays"));
	}

	// ---------------------------------------------------------------------------- synthetic content

	@Test
	void aSentenceWithTwoUnknownWordsIsFoundUntilTheWordsAreKnown() throws IOException {
		requirePython();
		// three words nobody has met: 돌, 횃불, 놓다
		List<ContentFacts.Trail> one = List.of(trail("a", 1, "돌과 횃불을 놓아요.", ""));
		Map<String, Double> m = run(scratch, one, scratch.resolve("gaps.csv"), "--json").metrics();
		assertEquals(1.0, m.get("coverage.sentences.unknownOverOneShare"));
		assertEquals(3.0, m.get("coverage.sentences.meanUnknown"));

		// the same sentence four times: the learner knows the words after three encounters, so the fourth is clean
		List<ContentFacts.Trail> four = new ArrayList<>();
		for (int i = 1; i <= 4; i++) {
			four.add(trail("t" + i, i, "돌과 횃불을 놓아요.", ""));
		}
		m = run(scratch, four, scratch.resolve("gaps.csv"), "--json").metrics();
		assertEquals(0.75, m.get("coverage.sentences.unknownOverOneShare"));
		assertEquals(9.0 / 4, m.get("coverage.sentences.meanUnknown"));
	}

	@Test
	void oneNewWordPerSentenceIsFine() throws IOException {
		requirePython();
		Map<String, Double> m = run(scratch, List.of(trail("a", 1, "놓아요.", "")), scratch.resolve("gaps.csv"),
				"--json").metrics();
		assertEquals(0.0, m.get("coverage.sentences.unknownOverOneShare"), "one unknown word is the limit, not over it");
		assertEquals(1.0, m.get("coverage.sentences.meanUnknown"));
		// a word needs three encounters: 돌 and 놓다 are both still unknown in all of the first three sentences
		List<ContentFacts.Trail> three = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			three.add(trail("t" + i, i, "돌을 놓아요.", ""));
		}
		m = run(scratch, three, scratch.resolve("gaps.csv"), "--json").metrics();
		assertEquals(1.0, m.get("coverage.sentences.unknownOverOneShare"));
	}

	@Test
	void aHintMayOnlyUseWordsTheLearnerKnowsOrJustRead() throws IOException {
		requirePython();
		// the hint brings 횃불, which neither the clue nor an earlier step introduced
		List<ContentFacts.Trail> bad = List.of(trail("a", 1, "돌을 놓아요.", "횃불을 놓아요."));
		Map<String, Double> m = run(scratch, bad, scratch.resolve("gaps.csv"), "--json").metrics();
		assertEquals(0.0, m.get("coverage.sentences.hintFullyKnownShare"));
		assertEquals(0.5, m.get("coverage.sentences.hintWordCoverage"), "횃불 is missing, 놓다 is in the clue");

		List<ContentFacts.Trail> good = List.of(trail("a", 1, "돌을 놓아요.", "돌을 놓아요."));
		m = run(scratch, good, scratch.resolve("gaps.csv"), "--json").metrics();
		assertEquals(1.0, m.get("coverage.sentences.hintFullyKnownShare"));
		assertEquals(1.0, m.get("coverage.sentences.hintWordCoverage"));
	}

	@Test
	void theCheckFailsWhenCoverageIsWorseThanTheBaseline() throws IOException {
		requirePython();
		List<ContentFacts.Trail> bad = List.of(trail("a", 1, "돌과 횃불을 놓아요.", "문을 만들어요."));
		Path baseline = scratch.resolve("baseline.json");
		Files.writeString(baseline, "{\"params\":" + MetricsBaseline.load().getAsJsonObject("params") + ",\"metrics\":{"
				+ "\"coverage.sentences.unknownOverOneShare\":{\"value\":0,\"better\":\"lower\",\"target\":0},"
				+ "\"coverage.sentences.meanUnknown\":{\"value\":5,\"better\":\"lower\",\"target\":1},"
				+ "\"coverage.sentences.hintFullyKnownShare\":{\"value\":0,\"better\":\"higher\",\"target\":1},"
				+ "\"coverage.sentences.hintWordCoverage\":{\"value\":0,\"better\":\"higher\",\"target\":1}}}");
		Result result = run(scratch, bad, scratch.resolve("gaps.csv"), "--check", "--baseline", baseline.toString());
		assertEquals(1, result.exit(), result.output());
		assertTrue(result.output().contains("unknownOverOneShare got worse"), result.output());
	}

	@Test
	void theCheckPassesWorseCoverageThatStillMeetsTheTarget() throws IOException {
		requirePython();
		// one unknown word per sentence is the target; the baseline was better (0.5), the content is at 1
		List<ContentFacts.Trail> trails = List.of(trail("a", 1, "놓아요.", ""));
		Path baseline = scratch.resolve("baseline.json");
		String metrics = "\"coverage.sentences.unknownOverOneShare\":{\"value\":0,\"better\":\"lower\",\"target\":0},"
				+ "\"coverage.sentences.hintFullyKnownShare\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.sentences.hintWordCoverage\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.recycling.medianEncounters\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.recycling.medianRetrievals\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.recycling.seenShare\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.recycling.targetMetShare\":{\"value\":0,\"better\":\"higher\",\"target\":0},"
				+ "\"coverage.sentences.meanUnknown\":{\"value\":0.5,\"better\":\"lower\",\"target\":%s}";
		String params = MetricsBaseline.load().getAsJsonObject("params").toString();
		Files.writeString(baseline, "{\"params\":" + params + ",\"metrics\":{" + metrics.formatted("1") + "}}");
		Result met = run(scratch, trails, scratch.resolve("gaps.csv"), "--check", "--baseline", baseline.toString());
		assertEquals(0, met.exit(), met.output());
		Files.writeString(baseline, "{\"params\":" + params + ",\"metrics\":{" + metrics.formatted("0.5") + "}}");
		Result missed = run(scratch, trails, scratch.resolve("gaps.csv"), "--check", "--baseline", baseline.toString());
		assertEquals(1, missed.exit(), missed.output());
		assertTrue(missed.output().contains("meanUnknown got worse"), missed.output());
	}

	// ---------------------------------------------------------------------------- teach entries (wave 5)

	/** Encounters of a word, from the gap list (the core words with something missing). */
	private int encounters(String word, ContentFacts.Trail... trails) throws IOException {
		Path csv = scratch.resolve("gaps.csv");
		run(scratch, List.of(trails), csv, "--json");
		List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
		for (String line : lines.subList(1, lines.size())) {
			String[] cells = line.split(",", -1);
			if (cells[0].equals(word)) {
				return Integer.parseInt(cells[6]);
			}
		}
		return -1;
	}

	@Test
	void aTeachEntryIsNoEncounterWhenTheWordIsInNoSentenceOfTheStep() throws IOException {
		requirePython();
		// 돌 is taught, but neither the clue, the hint, the title nor the finale says it
		assertEquals(0, encounters("돌", teaching("a", 1, "문의 길", "문을 놓아요.", "문을 놓아요.", "문이 서 있어요.",
				List.of("stone"))), "a teach entry alone is no encounter");
	}

	@Test
	void aTeachEntryCountsWhenTheHintTheTitleOrTheFinaleCarriesTheWord() throws IOException {
		requirePython();
		assertEquals(1, encounters("돌", teaching("a", 1, "문의 길", "문을 놓아요.", "돌 옆에 문을 놓아요.", "",
				List.of("stone"))), "the hint carries the taught word");
		assertEquals(1, encounters("돌", teaching("a", 1, "돌의 길", "문을 놓아요.", "", "", List.of("stone"))),
				"the title carries the taught word");
		assertEquals(1, encounters("돌", teaching("a", 1, "문의 길", "문을 놓아요.", "", "돌이 있어요.",
				List.of("stone"))), "the finale carries the taught word");
		assertEquals(1, encounters("돌", teaching("a", 1, "문의 길", "돌을 놓아요.", "", "", List.of("stone"))),
				"the clue carries it once; the teach entry does not count a second time");
	}

	// ------------------------------------------------------------------------- ㄹ verbs (wave 5)

	private static String tokens(Path dir, String text) throws IOException {
		Path lexicon = dir.resolve("lexicon.json");
		Files.writeString(lexicon, """
				[{"id":"dig","ko":"파다","verb":true},{"id":"sell","ko":"팔다","verb":true},
				 {"id":"buy","ko":"사다","verb":true},{"id":"live","ko":"살다","verb":true},
				 {"id":"make","ko":"만들다","verb":true},{"id":"play","ko":"놀다","verb":true}]""", StandardCharsets.UTF_8);
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/coverage.py", "--lexicon", lexicon.toString(),
				"--tokens", text);
		assertEquals(0, run.exit(), run.output());
		return run.output().strip().replace("\n", " ");
	}

	@Test
	void anLStemVerbIsNotReadAsTheVerbWithoutTheL() throws IOException {
		requirePython();
		assertEquals("팔다", tokens(scratch, "팔아요"), "팔다, not 파다");
		assertEquals("팔다", tokens(scratch, "팔아"));
		assertEquals("파다", tokens(scratch, "파요"));
		assertEquals("살다", tokens(scratch, "살아요"), "살다, not 사다");
		assertEquals("사다", tokens(scratch, "사요"), "사다, not 살다");
		assertEquals("사다", tokens(scratch, "샀어요"));
		assertEquals("만들다", tokens(scratch, "만들어요"));
		assertEquals("만들다", tokens(scratch, "만듭니다"), "the ㄹ drops in front of ㅂ");
		assertEquals("놀다", tokens(scratch, "놀아요"));
		assertEquals("놀다", tokens(scratch, "놀았어요"));
	}

	@Test
	void theCheckPassesWhenNothingIsWorse() throws IOException {
		requirePython();
		List<ContentFacts.Trail> trails = List.of(trail("a", 1, "돌과 횃불을 놓아요.", "돌을 놓아요."));
		Path baseline = scratch.resolve("baseline.json");
		Result updated = run(scratch, trails, scratch.resolve("gaps.csv"), "--update-baseline", "--baseline",
				baseline.toString());
		assertEquals(0, updated.exit(), updated.output());
		Result check = run(scratch, trails, scratch.resolve("gaps.csv"), "--check", "--baseline", baseline.toString());
		assertEquals(0, check.exit(), check.output());
	}
}
