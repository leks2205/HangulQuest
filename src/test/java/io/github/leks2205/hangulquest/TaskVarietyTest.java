package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.quest.Goal;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Task-variety ratchet ("recycle words, not tasks"). The metrics of {@link VarietyMetrics} may not get
 * worse than content/metrics-baseline.json; the roadmap targets are only printed. tools/variety.py
 * measures the same numbers from steps.json, and one test here checks that both agree. Demo trails
 * are measured in the report and exempt from the ratchet, like in VerbVarietyTest.
 */
class TaskVarietyTest {
	@TempDir
	Path scratch;

	// ---------------------------------------------------------------- the shipped content

	@Test
	void noVarietyMetricIsWorseThanTheBaseline() {
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		Map<String, Double> actual = VarietyMetrics.compute(trails, false);
		JsonObject baseline = MetricsBaseline.load();
		System.out.println("task variety (ratchet scope, gap to the roadmap targets):\n"
				+ MetricsBaseline.gapReport(actual, baseline, "variety."));
		List<String> worse = MetricsBaseline.regressions(actual, baseline, "variety.");
		assertTrue(worse.isEmpty(), "task variety got worse (fix the content, or on purpose: "
				+ "python3 tools/variety.py --update-baseline):\n" + String.join("\n", worse));
	}

	@Test
	void pythonAndJavaMeasureTheSameNumbers() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		ContentFacts.write(trails, ContentFacts.DUMP);
		assertAgree(trails, ContentFacts.DUMP);
	}

	private static void assertAgree(List<ContentFacts.Trail> trails, Path dump) {
		QuestScaffoldTest.Run run = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(), "--json");
		assertEquals(0, run.exit(), run.output());
		JsonObject python = JsonParser.parseString(run.output()).getAsJsonObject();
		Map<String, Double> java = VarietyMetrics.compute(trails, false);
		assertEquals(java.keySet(), python.keySet(), "the same metrics on both sides");
		for (var entry : java.entrySet()) {
			assertEquals(entry.getValue(), python.get(entry.getKey()).getAsDouble(), 1e-9,
					entry.getKey() + ": Java and tools/variety.py disagree");
		}
	}

	// -------------------------------------------------------------- synthetic content

	private static ContentFacts.Step step(String goalType, String object, String verb, boolean bare, String... mechanics) {
		return step(goalType, object, verb, bare, false, mechanics);
	}

	private static ContentFacts.Step step(String goalType, String object, String verb, boolean bare,
										  boolean formDependent, String... mechanics) {
		String shape = goalType + ":" + verb;
		String signature = goalType + ":" + object + ":" + verb;
		return new ContentFacts.Step("s", goalType, signature, shape, verb, object, "", List.of(),
				List.of(mechanics), bare, formDependent, List.of("L1.eoyo"), List.of(), List.of(), "x", "x");
	}

	private static ContentFacts.Trail trail(String id, int level, int order, boolean demo, List<String> requires,
											ContentFacts.Step... steps) {
		return new ContentFacts.Trail(id, level, order, demo, requires, "t", "f", List.of(steps));
	}

	private static ContentFacts.Step place(String object) {
		return step("PLACE_BLOCK", object, "PLACE", true, "goal:PLACE_BLOCK");
	}

	private static double metric(List<ContentFacts.Trail> trails, String key) {
		return VarietyMetrics.compute(trails, false).get("variety." + key);
	}

	@Test
	void duplicateSignaturesCountTheCopies() {
		var same = trail("a", 1, 1, false, List.of(), place("minecraft:torch"), place("minecraft:torch"),
				place("minecraft:torch"), place("minecraft:stone"));
		assertEquals(2.0, metric(List.of(same), "duplicateSignatures"));
		var distinct = trail("a", 1, 1, false, List.of(), place("minecraft:torch"), place("minecraft:stone"));
		assertEquals(0.0, metric(List.of(distinct), "duplicateSignatures"));
		// a repeat in another trail counts too: signatures are compared across all content
		var other = trail("b", 1, 2, false, List.of(), place("minecraft:torch"));
		assertEquals(1.0, metric(List.of(distinct, other), "duplicateSignatures"));
	}

	@Test
	void stepsThatOnlySwapTheObjectAreCountedPerTrail() {
		var swaps = trail("a", 1, 1, false, List.of(), place("minecraft:torch"), place("minecraft:stone"),
				place("minecraft:dirt"));
		assertEquals(2.0, metric(List.of(swaps), "objectOnlyRepeats"));
		var varied = trail("a", 1, 1, false, List.of(), place("minecraft:torch"),
				step("BREAK_BLOCK", "minecraft:stone", "MINE", true, "goal:BREAK_BLOCK"));
		assertEquals(0.0, metric(List.of(varied), "objectOnlyRepeats"));
		// the same shape in two different trails is not a repeat inside a trail
		var one = trail("a", 1, 1, false, List.of(), place("minecraft:torch"));
		var two = trail("b", 1, 2, false, List.of(), place("minecraft:stone"));
		assertEquals(0.0, metric(List.of(one, two), "objectOnlyRepeats"));
	}

	@Test
	void aVerbObjectPairThatComesBackTooOftenIsFound() {
		List<ContentFacts.Trail> trails = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			trails.add(trail("t" + i, 1, i, false, List.of(), place("minecraft:torch")));
		}
		assertEquals(5.0, metric(trails, "verbObjectMax"));
		assertEquals(1.0, metric(trails, "verbObjectPairsOver3"));
		assertEquals(3.0, metric(trails.subList(0, 3), "verbObjectMax"));
		assertEquals(0.0, metric(trails.subList(0, 3), "verbObjectPairsOver3"), "three is the limit, not over it");
	}

	@Test
	void theThingsASequenceAsksForCountForThePairLimitToo() {
		Goal.Sequence eatAndRide = new Goal.Sequence(List.of(new Goal.Give("minecraft:apple", "minecraft:horse", null, 1),
				new Goal.Mount("minecraft:horse", 1500)), true, "AND");
		assertEquals(List.of("GIVE minecraft:apple", "RIDE minecraft:horse"), ContentFacts.pairs(eatAndRide));
		assertEquals(List.of("PLACE minecraft:torch"), ContentFacts.pairs(new Goal.PlaceBlock("minecraft:torch", 1)));
		assertEquals(List.of(), ContentFacts.pairs(new Goal.Wait(10, null)));
		// four sequences that end in the same ride are over the limit, though no step has an object of its own
		List<ContentFacts.Trail> trails = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ContentFacts.Step sequence = new ContentFacts.Step("s", "SEQUENCE", "SEQUENCE:" + i, "SEQUENCE", "RIDE", null,
					"", List.of(), List.of("goal:SEQUENCE"), false, false, List.of("L1.eoyo"), List.of(), List.of(), "x",
					"x", ContentFacts.pairs(new Goal.Sequence(List.of(new Goal.Give("minecraft:apple" + i,
					"minecraft:horse", null, 1), new Goal.Mount("minecraft:horse", 1500)), true, "AND")));
			trails.add(trail("t" + i, 2, i, false, List.of(), sequence));
		}
		assertEquals(4.0, metric(trails, "verbObjectMax"));
		assertEquals(1.0, metric(trails, "verbObjectPairsOver3"));
	}

	@Test
	void aTrailNeedsAMechanicTheLastFiveDidNotHave() {
		List<ContentFacts.Trail> trails = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			trails.add(trail("t" + i, 1, i, false, List.of(), place("minecraft:block" + i)));
		}
		assertEquals(1.0 / 6, metric(trails, "noveltyShare"), 1e-9, "only the first trail is new");
		// a relation nobody has used yet makes a trail novel; it stops being novel while it is inside the window
		ContentFacts.Step rel = step("PLACE_REL", "minecraft:torch", "PLACE", false, "goal:PLACE_REL", "rel:NEXT_TO");
		List<ContentFacts.Trail> window = new ArrayList<>();
		window.add(trail("t0", 1, 0, false, List.of(), rel));
		for (int i = 1; i <= 5; i++) {
			window.add(trail("t" + i, 1, i, false, List.of(), place("minecraft:block" + i)));
		}
		window.add(trail("t6", 1, 6, false, List.of(), rel));
		window.add(trail("t7", 1, 7, false, List.of(), place("minecraft:block7")));
		assertEquals(3.0 / 8, metric(window, "noveltyShare"), 1e-9,
				"t0 (first), t1 (PLACE_BLOCK is new) and t6 (PLACE_REL left the window of five)");
		// the window is five trails: the same mechanic five trails later is still inside it
		List<ContentFacts.Trail> inside = new ArrayList<>();
		inside.add(trail("t0", 1, 0, false, List.of(), rel));
		for (int i = 1; i <= 4; i++) {
			inside.add(trail("t" + i, 1, i, false, List.of(), place("minecraft:block" + i)));
		}
		inside.add(trail("t5", 1, 5, false, List.of(), rel));
		assertEquals(2.0 / 6, metric(inside, "noveltyShare"), 1e-9, "t0 and t1 only: t5 is five trails after t0");
		// a trail that brings nothing new but the object word is not novel
		var swap = List.of(trail("a", 1, 1, false, List.of(), place("minecraft:a")),
				trail("b", 1, 2, false, List.of(), place("minecraft:b")));
		assertEquals(0.5, metric(swap, "noveltyShare"));
	}

	@Test
	void theBoredomIndexIsTheShareOfNeighboursThatOnlySwapTheObject() {
		var boring = trail("a", 1, 1, false, List.of(), place("minecraft:a"), place("minecraft:b"),
				place("minecraft:c"), place("minecraft:d"));
		assertEquals(1.0, metric(List.of(boring), "boredomIndex"));
		var lively = trail("a", 1, 1, false, List.of(), place("minecraft:a"),
				step("BREAK_BLOCK", "minecraft:b", "MINE", true, "goal:BREAK_BLOCK"), place("minecraft:c"),
				step("BREAK_BLOCK", "minecraft:d", "MINE", true, "goal:BREAK_BLOCK"));
		assertEquals(0.0, metric(List.of(lively), "boredomIndex"));
		// the learner path crosses trail borders: the last step of one and the first of the next are neighbours
		var one = trail("a", 1, 1, false, List.of(), place("minecraft:a"));
		var two = trail("b", 1, 2, false, List.of(), place("minecraft:b"));
		assertEquals(1.0, metric(List.of(one, two), "boredomIndex"));
	}

	@Test
	void bareStepsAndGoalTypesAreCountedPerLevel() {
		var rel = step("PLACE_REL", "minecraft:torch", "PLACE", false, "goal:PLACE_REL");
		var level1 = trail("a", 1, 1, false, List.of(), place("minecraft:a"), rel, place("minecraft:b"),
				step("CRAFT", "minecraft:stick", "CRAFT", false, "goal:CRAFT"));
		var level2 = trail("b", 2, 1, false, List.of(), place("minecraft:c"), place("minecraft:d"));
		Map<String, Double> values = VarietyMetrics.compute(List.of(level1, level2), false);
		assertEquals(0.5, values.get("variety.bareShare.1"));
		assertEquals(0.5, values.get("variety.topTypeShare.1"));
		assertEquals(3.0, values.get("variety.distinctTypes.1"));
		assertEquals(1.0, values.get("variety.bareShare.2"));
		assertEquals(1.0, values.get("variety.topTypeShare.2"));
		assertEquals(1.0, values.get("variety.distinctTypes.2"));
		assertFalse(values.containsKey("variety.bareShare.3"), "a level without content has no numbers");
	}

	@Test
	void formDependentStepsAreCountedPerLevel() {
		var rel = step("PLACE_REL", "minecraft:torch", "PLACE", false, true, "goal:PLACE_REL");
		var avoid = step("AVOID", "minecraft:bed", "SLEEP", false, true, "goal:AVOID");
		var level1 = trail("a", 1, 1, false, List.of(), place("minecraft:a"), rel, place("minecraft:b"), place("minecraft:c"));
		var level2 = trail("b", 2, 1, false, List.of(), rel, avoid, place("minecraft:d"));
		Map<String, Double> values = VarietyMetrics.compute(List.of(level1, level2), false);
		assertEquals(0.25, values.get("variety.formDependentShare.1"));
		assertEquals(2.0 / 3, values.get("variety.formDependentShare.2"), 1e-9);
		assertFalse(values.containsKey("variety.formDependentShare.3"));
	}

	@Test
	void theFormDependentShareWantsThirtyPercentFromLevelTwoOn() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		var rel = step("PLACE_REL", "minecraft:torch", "PLACE", false, true, "goal:PLACE_REL");
		var content = List.of(trail("a", 1, 1, false, List.of(), place("minecraft:a")),
				trail("b", 2, 1, false, List.of(), rel, place("minecraft:b")));
		Path dump = scratch.resolve("forms-steps.json");
		ContentFacts.write(content, dump);
		Path baseline = scratch.resolve("forms-baseline.json");
		QuestScaffoldTest.Run update = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(),
				"--baseline", baseline.toString(), "--update-baseline");
		assertEquals(0, update.exit(), update.output());
		JsonObject metrics = JsonParser.parseString(Files.readString(baseline)).getAsJsonObject().getAsJsonObject("metrics");
		assertEquals(0.0, metrics.getAsJsonObject("variety.formDependentShare.1").get("value").getAsDouble());
		assertEquals(0.0, metrics.getAsJsonObject("variety.formDependentShare.1").get("target").getAsDouble(),
				"reported at level 1, never demanded");
		assertEquals("higher", metrics.getAsJsonObject("variety.formDependentShare.2").get("better").getAsString());
		assertEquals(0.30, metrics.getAsJsonObject("variety.formDependentShare.2").get("target").getAsDouble());
		assertEquals(0.5, metrics.getAsJsonObject("variety.formDependentShare.2").get("value").getAsDouble());
	}

	@Test
	void demoTrailsAreMeasuredButExemptFromTheRatchet() {
		var real = trail("a", 1, 1, false, List.of(), place("minecraft:a"));
		var demo = trail("x_demo", 1, 2, true, List.of(), place("minecraft:a"));
		assertEquals(0.0, VarietyMetrics.compute(List.of(real, demo), false).get("variety.duplicateSignatures"));
		assertEquals(1.0, VarietyMetrics.compute(List.of(real, demo), true).get("variety.duplicateSignatures"));
		assertTrue(ContentFacts.isDemo("lighthouse_demo"));
		assertFalse(ContentFacts.isDemo("lighthouse"));
	}

	@Test
	void theLearnerPathFollowsPrerequisitesThenLevelThenOrder() {
		var root = trail("root", 2, 5, false, List.of(), place("minecraft:a"));
		var easyChild = trail("child", 1, 1, false, List.of("root"), place("minecraft:b"));
		var easyRoot = trail("lone", 1, 9, false, List.of(), place("minecraft:c"));
		var late = trail("late", 3, 1, false, List.of(), place("minecraft:d"));
		List<String> ids = ContentFacts.learnerPath(List.of(late, easyChild, root, easyRoot), false).stream()
				.map(ContentFacts.Trail::id).toList();
		assertEquals(List.of("lone", "root", "child", "late"), ids,
				"lone (level 1) first; child waits for root; child (level 1) before late (level 3)");
		var cycleA = trail("a", 1, 1, false, List.of("b"), place("minecraft:a"));
		var cycleB = trail("b", 1, 2, false, List.of("a"), place("minecraft:b"));
		assertEquals(2, ContentFacts.learnerPath(List.of(cycleA, cycleB), false).size(), "a cycle must not lose trails");
	}

	// ---------------------------------------------------------------- the ratchet itself

	private static JsonObject baseline(String key, double value, String better) {
		return baseline(key, value, better, "lower".equals(better) ? 0 : 1);
	}

	private static JsonObject baseline(String key, double value, String better, double target) {
		return JsonParser.parseString("{\"metrics\":{\"" + key + "\":{\"value\":" + value + ",\"better\":\""
				+ better + "\",\"target\":" + target + "}}}").getAsJsonObject();
	}

	@Test
	void aMetricThatMeetsItsTargetPassesWhateverTheBaselineSays() {
		// the (verb, object) maximum was 2 when it was recorded; the roadmap allows 3
		JsonObject pairs = baseline("variety.verbObjectMax", 2, "lower", 3);
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.verbObjectMax", 3.0), pairs, "variety.").size(),
				"a third occurrence is inside the target");
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.verbObjectMax", 4.0), pairs, "variety.").size(),
				"a fourth is worse than the baseline and past the target");
		JsonObject share = baseline("variety.noveltyShare", 0.9, "higher", 0.8);
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.noveltyShare", 0.85), share, "variety.").size());
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.noveltyShare", 0.7), share, "variety.").size());
		JsonObject noTarget = JsonParser.parseString(
				"{\"metrics\":{\"variety.x\":{\"value\":2,\"better\":\"lower\"}}}").getAsJsonObject();
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.x", 3.0), noTarget, "variety.").size(),
				"without a target the baseline value is the only bar");
	}

	@Test
	void theRatchetFailsOnAWorseValueAndAllowsAnImprovement() {
		JsonObject lower = baseline("variety.duplicateSignatures", 5, "lower");
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.duplicateSignatures", 6.0), lower, "variety.").size());
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.duplicateSignatures", 5.0), lower, "variety.").size());
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.duplicateSignatures", 2.0), lower, "variety.").size());
		JsonObject higher = baseline("variety.noveltyShare", 0.376206, "higher");
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.noveltyShare", 0.3761), higher, "variety.").size());
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.noveltyShare", 0.3762061), higher, "variety.").size(),
				"the stored value has six decimals: rounding is not a regression");
		assertEquals(0, MetricsBaseline.regressions(Map.of("variety.noveltyShare", 0.9), higher, "variety.").size());
	}

	@Test
	void theRatchetNoticesMissingAndVanishedMetrics() {
		JsonObject baseline = baseline("variety.a", 1, "lower");
		assertEquals(1, MetricsBaseline.regressions(Map.of("variety.a", 1.0, "variety.b", 0.0), baseline, "variety.").size(),
				"a metric the baseline does not know");
		assertEquals(1, MetricsBaseline.regressions(Map.of(), baseline, "variety.").size(), "a metric that vanished");
		assertEquals(0, MetricsBaseline.regressions(Map.of(), baseline, "coverage.").size(), "another section is not ours");
	}

	@Test
	void theBaselineCoversEveryMeasuredMetric() {
		Set<String> measured = VarietyMetrics.compute(ContentFacts.shipped(scratch), false).keySet();
		Set<String> recorded = new java.util.TreeSet<>(MetricsBaseline.metrics(MetricsBaseline.load()).keySet());
		recorded.removeIf(key -> !key.startsWith("variety."));
		assertEquals(new java.util.TreeSet<>(measured), recorded);
	}

	// ----------------------------------------------------------- the Python tool on bad content

	@Test
	void pythonAgreesOnSyntheticContentAndItsCheckCanFail() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		var one = trail("a", 1, 1, false, List.of(), place("minecraft:torch"), place("minecraft:torch"),
				place("minecraft:stone"), step("PLACE_REL", "minecraft:torch", "PLACE", false, "goal:PLACE_REL", "rel:UNDER"));
		var two = trail("b", 2, 1, false, List.of("a"), place("minecraft:torch"), place("minecraft:dirt"));
		var three = trail("x_demo", 2, 2, true, List.of(), place("minecraft:torch"));
		List<ContentFacts.Trail> trails = List.of(one, two, three);
		Path dump = scratch.resolve("steps.json");
		ContentFacts.write(trails, dump);
		assertAgree(trails, dump);

		// a baseline that the synthetic content breaks: no duplicates allowed
		Path baseline = scratch.resolve("baseline.json");
		Files.writeString(baseline, "{\"metrics\":{"
				+ "\"variety.duplicateSignatures\":{\"value\":0,\"better\":\"lower\",\"target\":0}}}");
		QuestScaffoldTest.Run check = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(),
				"--baseline", baseline.toString(), "--check");
		assertEquals(1, check.exit(), check.output());
		assertTrue(check.output().contains("duplicateSignatures got worse"), check.output());
	}

	@Test
	void theToolPassesAThirdOccurrenceBecauseTheTargetAllowsIt() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		List<ContentFacts.Trail> trails = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			trails.add(trail("t" + i, 1, i, false, List.of(), place("minecraft:torch")));
		}
		Path dump = scratch.resolve("steps.json");
		ContentFacts.write(trails, dump);
		assertAgree(trails, dump);
		Path allowed = scratch.resolve("allowed.json");
		Files.writeString(allowed, baselineFor(trails, 3));
		QuestScaffoldTest.Run pass = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(),
				"--baseline", allowed.toString(), "--check");
		assertEquals(0, pass.exit(), pass.output());
		Path strict = scratch.resolve("strict.json");
		Files.writeString(strict, baselineFor(trails, 2));
		QuestScaffoldTest.Run fail = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(),
				"--baseline", strict.toString(), "--check");
		assertEquals(1, fail.exit(), fail.output());
		assertTrue(fail.output().contains("verbObjectMax got worse"), fail.output());
	}

	/** A baseline at the current values, except the (verb, object) maximum, which was 2 and has this target. */
	private static String baselineFor(List<ContentFacts.Trail> trails, int pairTarget) {
		StringBuilder out = new StringBuilder("{\"metrics\":{");
		boolean first = true;
		for (var entry : VarietyMetrics.compute(trails, false).entrySet()) {
			boolean pairs = entry.getKey().equals("variety.verbObjectMax");
			out.append(first ? "" : ",").append('"').append(entry.getKey()).append("\":{\"value\":")
					.append(pairs ? 2 : entry.getValue()).append(",\"better\":\"lower\",\"target\":")
					.append(pairs ? pairTarget : 1000).append('}');
			first = false;
		}
		return out.append("}}").toString();
	}

	@Test
	void updatingTheBaselineWritesTheCurrentValuesAndTheTargets() throws IOException {
		assumeTrue(QuestScaffoldTest.python("-c", "pass").exit() == 0, "python3 is not available");
		List<ContentFacts.Trail> trails = ContentFacts.shipped(scratch);
		Path dump = scratch.resolve("steps.json");
		ContentFacts.write(trails, dump);
		Path baseline = scratch.resolve("baseline.json");
		QuestScaffoldTest.Run update = QuestScaffoldTest.python("tools/variety.py", "--steps", dump.toString(),
				"--baseline", baseline.toString(), "--update-baseline");
		assertEquals(0, update.exit(), update.output());
		JsonObject written = JsonParser.parseString(Files.readString(baseline)).getAsJsonObject();
		Map<String, Double> java = VarietyMetrics.compute(trails, false);
		assertEquals(0, MetricsBaseline.regressions(java, written, "variety.").size());
		assertEquals(0.30, written.getAsJsonObject("metrics").getAsJsonObject("variety.bareShare.1").get("target").getAsDouble());
		assertEquals(0.25, written.getAsJsonObject("metrics").getAsJsonObject("variety.topTypeShare.3").get("target").getAsDouble());
		// the shipped baseline equals what the tool would write now, up to improvements
		assertTrue(MetricsBaseline.regressions(java, MetricsBaseline.load(), "variety.").isEmpty());
	}
}
