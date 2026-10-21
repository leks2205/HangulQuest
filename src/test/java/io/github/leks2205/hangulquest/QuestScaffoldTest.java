package io.github.leks2205.hangulquest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Topik;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.util.TextClean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * tools/new_questline.py must scaffold a trail that passes the content rules, and the finales it picks
 * from must stay in step with the sentence generator. Needs python3; without it the tests are skipped
 * (CI has it).
 */
class QuestScaffoldTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("scaffold-test");
	private static final Path TOOLS = Path.of("tools");

	@TempDir
	Path out;
	private int titles;

	@BeforeAll
	static void needsPython() {
		assumeTrue(python("-c", "pass").exit() == 0, "python3 is not available");
	}

	record Run(int exit, String output) {
	}

	static Run python(String... args) {
		List<String> command = new ArrayList<>(List.of("python3"));
		command.addAll(List.of(args));
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(120, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return new Run(-1, "timed out\n" + output);
			}
			return new Run(process.exitValue(), output);
		} catch (IOException | InterruptedException unavailable) {
			return new Run(-1, unavailable.toString());
		}
	}

	private Run scaffold(String id, String words, String after, String... more) {
		List<String> args = new ArrayList<>(List.of(TOOLS.resolve("new_questline.py").toString(), id,
				"--words", words, "--title-ko", "시험의 " + (char) ('가' + titles++) + "길",
				"--title-de", "Testpfad " + id, "--title-en", "Test Trail " + id,
				"--out-dir", out.toString()));
		if (after != null) {
			args.addAll(List.of("--after", after));
		}
		args.addAll(List.of(more));
		return python(args.toArray(new String[0]));
	}

	private Questline load(String id) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(out.resolve(id + ".json"))).getAsJsonObject();
		return QuestLoader.parse(root);
	}

	private static String path(String id) {
		return id.substring(id.indexOf(':') + 1);
	}

	// --- the finale list ---------------------------------------------------------------------------

	private static JsonArray finales() throws IOException {
		return JsonParser.parseString(Files.readString(TOOLS.resolve("data/finales.json"))).getAsJsonArray();
	}

	/** A one-step stand-in trail around a finale entry, so the real parser and validator see it. */
	private static Questline trailAround(JsonObject entry) {
		JsonObject step = new JsonObject();
		step.addProperty("id", "finale");
		JsonArray teach = new JsonArray();
		teach.add(entry.get("concept").getAsString());
		step.add("teach", teach);
		step.add("goal", entry.getAsJsonObject("goal"));
		step.add("conditions", entry.getAsJsonArray("conditions"));
		JsonObject finale = new JsonObject();
		finale.add("step", step);
		finale.add("textKo", entry.get("textKo"));
		finale.add("textDe", entry.get("textDe"));
		finale.add("textEn", entry.get("textEn"));
		JsonObject root = new JsonObject();
		root.addProperty("id", "finale_" + entry.get("id").getAsString().replace('-', '_'));
		root.add("finale", finale);
		return QuestLoader.parse(root);
	}

	@Test
	void everyFinaleIsTheTextTheGeneratorProducesAndIsReachable() throws IOException {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		Set<String> ids = new HashSet<>();
		Set<String> texts = new HashSet<>();
		assertTrue(finales().size() >= 6, "keep a few finales so siblings can differ");
		List<String> stale = new ArrayList<>();
		for (JsonElement element : finales()) {
			JsonObject entry = element.getAsJsonObject();
			Questline trail = trailAround(entry);
			if (!KoreanFixtures.ceremony(trail).equals(trail.finaleKo())) {
				stale.add(entry.get("id").getAsString() + " -> " + KoreanFixtures.ceremony(trail));
			}
		}
		assertTrue(stale.isEmpty(), "textKo should be what SentenceBuilder.ceremony says:\n"
				+ String.join("\n", stale));
		for (JsonElement element : finales()) {
			JsonObject entry = element.getAsJsonObject();
			String id = entry.get("id").getAsString();
			assertTrue(ids.add(id), "duplicate finale id " + id);
			Questline trail = trailAround(entry);
			QuestStep step = trail.finale();

			assertTrue(texts.add(trail.finaleKo()), id + ": two finales share a text");
			assertTrue(TextClean.isHangulOnly(trail.finaleKo()), id + ": textKo is not Hangul-only");
			assertFalse(trail.finaleDe().isBlank(), id + ": no German text");
			assertFalse(trail.finaleEnRaw() == null || trail.finaleEnRaw().isBlank(), id + ": no English text");

			QuestValidator.Result result = QuestValidator.validate(List.of(trail), catalog, LOG);
			assertTrue(result.problems().isEmpty(), id + ": " + result.problems());

			// Reachable in a fresh world: no creative-only target, no structure, no rare biome, no dimension.
			String target = KoreanFixtures.targetOf(step.goal());
			assertFalse(ObtainabilityTest.unobtainable(path(target)), id + " asks for " + target);
			assertTrue(step.goal() instanceof Goal.PlaceBlock || step.goal() instanceof Goal.LookAtEntity
					|| step.goal() instanceof Goal.AcquireItem, id + ": keep finales to simple goals");

			for (Condition condition : step.conditions()) {
				if (condition instanceof Condition.Time time) {
					String phrase = switch (time.phase()) {
						case DAY -> "낮";
						case NIGHT -> "밤";
						case DAWN -> "새벽";
						case DUSK -> "저녁";
					};
					assertTrue(trail.finaleKo().contains(phrase), id + ": text omits its condition " + phrase);
				}
			}
		}
	}

	// --- scaffolding -------------------------------------------------------------------------------

	private static List<Questline> shipped() {
		return QuestLoader.loadAll();
	}

	/** Some shipped trail on level 3 of the tree, so the scaffold has to compute a depth. */
	private static Questline deepParent(TrailGraph graph, List<Questline> trails) {
		return trails.stream().filter(trail -> graph.depth(trail.id()) == 5).findFirst().orElseThrow();
	}

	@Test
	void aScaffoldedTrailPassesTheContentRules() throws IOException {
		List<Questline> trails = shipped();
		TrailGraph graph = TrailGraph.of(trails);
		Questline parent = deepParent(graph, trails);
		Run run = scaffold("scaffold_one", "stone,apple,cow", parent.id());
		assertEquals(0, run.exit(), run.output());

		Questline trail = load("scaffold_one");
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		List<Questline> all = new ArrayList<>(trails);
		all.add(trail);
		QuestValidator.Result result = QuestValidator.validate(all, catalog, LOG);
		assertTrue(result.problems().isEmpty(), "problems: " + result.problems());
		assertEquals(trails.size() + 1, result.trails().size(), "the scaffold must survive validation");

		TrailGraph withNew = TrailGraph.of(all);
		assertFalse(withNew.hasCycle());
		int depth = withNew.depth("scaffold_one");
		assertEquals(graph.depth(parent.id()) + 1, depth);
		assertEquals(Topik.levelForDepth(depth), trail.koreanLevel(), "koreanLevel follows the depth");
		assertEquals(Math.min(5, 2), trail.tier(), "tier is 1 plus the parent count");
		assertEquals(List.of(parent.id()), trail.requires().completed());
		assertFalse(trail.topic().isBlank());
		assertFalse(trail.badge().isBlank());
		assertTrue(trail.order() > 0);

		// English everywhere: the content tests demand it and the scaffold used to leave it out.
		assertFalse(trail.titleEnRaw() == null || trail.titleEnRaw().isBlank(), "no titleEn");
		assertFalse(trail.finaleEnRaw() == null || trail.finaleEnRaw().isBlank(), "no finale textEn");
		assertTrue(TextClean.isHangulOnly(trail.finaleKo()), "finale textKo: " + trail.finaleKo());
		assertTrue(trail.allSteps().size() >= 3);

		Set<String> goals = new HashSet<>();
		for (QuestStep step : trail.allSteps()) {
			assertFalse(step.clueEn() == null || step.clueEn().isBlank(), step.id() + ": no clue.en");
			assertFalse(step.hintEn() == null || step.hintEn().isBlank(), step.id() + ": no hint.en");
			assertFalse(step.clueEn().matches(".*[가-힣].*"), step.id() + ": Hangul in clue.en");
			assertTrue(goals.add(step.goal().toString()), "repeats a goal: " + step.goal());
			assertFalse(ObtainabilityTest.unobtainable(path(KoreanFixtures.targetOf(step.goal()))), step.id());

			// The generated Korean clue names the taught word, like an authored one must.
			var word = catalog.byConceptId(step.teach().get(0)).orElseThrow();
			String clue = SentenceBuilder.normal(step.goal(), word, step.conditions(), trail.koreanLevel(),
					"finale".equals(step.id()), KoreanFixtures.namer(catalog));
			assertTrue(TextClean.isHangulOnly(clue), step.id() + ": " + clue);
			assertTrue(clue.contains(word.ko), step.id() + " clue omits " + word.ko + ": " + clue);
		}
		// The finale is not the same task as any step.
		Set<String> stepTargets = new HashSet<>();
		for (QuestStep step : trail.steps()) {
			stepTargets.add(KoreanFixtures.targetOf(step.goal()));
		}
		assertFalse(stepTargets.contains(KoreanFixtures.targetOf(trail.finale().goal())));
	}

	@Test
	void aScaffoldedTrailFitsTheSchema() throws IOException {
		assumeTrue(python("-c", "import jsonschema").exit() == 0, "jsonschema is not installed");
		Run run = scaffold("scaffold_schema", "stone,apple,cow", null);
		assertEquals(0, run.exit(), run.output());
		Run schema = python(TOOLS.resolve("validate_schema.py").toString(), out.toString());
		assertEquals(0, schema.exit(), schema.output());
	}

	@Test
	void siblingsGetDifferentFinales() throws IOException {
		List<Questline> trails = shipped();
		TrailGraph graph = TrailGraph.of(trails);
		String parent = deepParent(graph, trails).id();
		assertEquals(0, scaffold("scaffold_a", "stone,apple,cow", parent).exit());
		assertEquals(0, scaffold("scaffold_b", "bread,chest,sheep", parent).exit());
		assertEquals(0, scaffold("scaffold_c", "dirt,coal,furnace", parent).exit());

		Set<String> shippedSiblings = new HashSet<>();
		for (Questline sibling : graph.children(parent)) {
			shippedSiblings.add(KoreanFixtures.targetOf(sibling.finale().goal()));
		}
		Set<String> seen = new HashSet<>();
		for (String id : List.of("scaffold_a", "scaffold_b", "scaffold_c")) {
			Questline trail = load(id);
			String target = KoreanFixtures.targetOf(trail.finale().goal());
			assertTrue(seen.add(target), id + " repeats a sibling's finale: " + target);
			assertFalse(shippedSiblings.contains(target), id + " repeats a shipped sibling's finale: " + target);
		}
		assertEquals(3, seen.size());
	}

	@Test
	void theFinaleIsNeverOneOfTheTrailsOwnTasks() throws IOException {
		// cow, chest and furnace are all finale goals: asking for them in a step must exclude them.
		assertEquals(0, scaffold("scaffold_own", "cow,sheep,chest", null).exit());
		Questline trail = load("scaffold_own");
		String target = KoreanFixtures.targetOf(trail.finale().goal());
		for (QuestStep step : trail.steps()) {
			assertNotEquals(target, KoreanFixtures.targetOf(step.goal()));
		}
	}

	@Test
	void badInputIsRefusedWithAMessage() {
		Run unknown = scaffold("scaffold_bad", "stone,no_such_word", null);
		assertNotEquals(0, unknown.exit());
		assertTrue(unknown.output().contains("unknown concepts"), unknown.output());
		assertFalse(Files.exists(out.resolve("scaffold_bad.json")));

		Run orphan = scaffold("scaffold_bad", "stone,apple,cow", "no_such_trail");
		assertNotEquals(0, orphan.exit());
		assertTrue(orphan.output().contains("unknown parent"), orphan.output());

		Run tooShort = scaffold("scaffold_bad", "stone", null);
		assertNotEquals(0, tooShort.exit());

		// An existing shipped id is not overwritten by accident.
		Run clash = scaffold("steinpfad", "stone,apple,cow", null);
		assertNotEquals(0, clash.exit());
		assertTrue(clash.output().contains("exists"), clash.output());
	}
}
