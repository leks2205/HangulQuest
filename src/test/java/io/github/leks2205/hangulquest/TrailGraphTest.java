package io.github.leks2205.hangulquest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.QuestEngine;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.QuestValidator;
import io.github.leks2205.hangulquest.quest.Questline;
import io.github.leks2205.hangulquest.quest.TrailGraph;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The memoised prerequisite graph: same depths as before, linear cost, no trouble with cycles. */
class TrailGraphTest {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("trail-graph-test");

	@TempDir
	Path tempDir;

	private static Questline trail(String id, String... parents) {
		QuestStep step = new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		return new Questline(id, id, id, 1, List.of(), new Questline.Requires(0, null, List.of(parents)),
				List.of(), step, "끝", "Ende", id, 100, 1, false);
	}

	/** The recursive algorithm the engine used before the graph, kept as the oracle. */
	private static int oldDepth(Questline trail, Map<String, Questline> all, Set<String> seen, int[] calls) {
		calls[0]++;
		if (!seen.add(trail.id())) {
			return 1;
		}
		int depth = 1;
		for (String parentId : trail.requires().completed()) {
			Questline parent = all.get(parentId);
			if (parent != null) {
				depth = Math.max(depth, 1 + oldDepth(parent, all, seen, calls));
			}
		}
		seen.remove(trail.id());
		return depth;
	}

	@Test
	void depthsMatchTheOldAlgorithmOnTheShippedContent() {
		List<Questline> trails = QuestLoader.loadAll();
		assertEquals(421, trails.size());
		Map<String, Questline> all = new HashMap<>();
		trails.forEach(trail -> all.put(trail.id(), trail));
		TrailGraph graph = TrailGraph.of(trails);
		assertFalse(graph.hasCycle());
		int[] calls = {0};
		int max = 1;
		for (Questline trail : trails) {
			int expected = oldDepth(trail, all, new HashSet<>(), calls);
			assertEquals(expected, graph.depth(trail.id()), "depth of " + trail.id());
			max = Math.max(max, expected);
		}
		assertEquals(max, graph.maxDepth());
		assertTrue(max >= 5, "expected a deep tree");
		// The old walk was exponential on this DAG; the graph evaluates each trail once.
		assertTrue(calls[0] > 20 * graph.evaluations(),
				"the old algorithm made " + calls[0] + " calls, the graph " + graph.evaluations());
	}

	@Test
	void buildingEvaluatesEachTrailOnce() {
		List<Questline> trails = QuestLoader.loadAll();
		TrailGraph graph = TrailGraph.of(trails);
		assertEquals(421, graph.evaluations());
		assertTrue(graph.evaluations() < 2000);
		assertEquals(421, graph.topologicalOrder().size());
	}

	@Test
	void topologicalOrderPutsParentsFirst() {
		TrailGraph graph = TrailGraph.of(QuestLoader.loadAll());
		Set<String> seen = new HashSet<>();
		for (Questline trail : graph.topologicalOrder()) {
			for (String parent : graph.parents(trail.id())) {
				assertTrue(seen.contains(parent), parent + " must come before " + trail.id());
			}
			seen.add(trail.id());
		}
	}

	@Test
	void parentsChildrenAndLookup() {
		Questline a = trail("a");
		Questline b = trail("b", "a", "ghost");
		Questline c = trail("c", "a", "b");
		TrailGraph graph = TrailGraph.of(List.of(c, b, a));
		assertSame(a, graph.byId("a"));
		assertNull(graph.byId("ghost"));
		assertNull(graph.byId(null));
		assertEquals(List.of("a"), graph.parents("b"), "unknown parents are ignored");
		assertEquals(List.of("a", "b"), graph.parents("c"));
		assertEquals(Set.of("b", "c"), Set.copyOf(graph.children("a").stream().map(Questline::id).toList()));
		assertEquals(1, graph.depth("a"));
		assertEquals(2, graph.depth("b"));
		assertEquals(3, graph.depth("c"));
		assertEquals(1, graph.depth("ghost"));
	}

	@Test
	void theFirstTrailWinsADuplicatedId() {
		Questline first = trail("dup");
		Questline second = trail("dup", "x");
		assertSame(first, TrailGraph.of(List.of(first, second)).byId("dup"));
	}

	@Test
	void cyclesAreReportedWithoutOverflowingTheStack() {
		Questline a = trail("a", "b");
		Questline b = trail("b", "a");
		Questline below = trail("below", "a");
		Questline root = trail("root");
		Questline self = trail("self", "self");
		TrailGraph graph = TrailGraph.of(List.of(a, b, below, root, self));
		assertTrue(graph.hasCycle());
		assertEquals(Set.of("a", "b", "self"), graph.cyclic());
		assertEquals(1, graph.depth("a"));
		assertEquals(1, graph.depth("below"));
		assertEquals(1, graph.depth("root"));
		assertEquals(List.of("root"), graph.topologicalOrder().stream().map(Questline::id).toList());
	}

	@Test
	void aVeryDeepChainDoesNotOverflowTheStack() {
		List<Questline> chain = new ArrayList<>();
		chain.add(trail("n0"));
		for (int i = 1; i < 20_000; i++) {
			chain.add(trail("n" + i, "n" + (i - 1)));
		}
		TrailGraph graph = TrailGraph.of(chain);
		assertEquals(20_000, graph.depth("n19999"));
		assertEquals(20_000, graph.maxDepth());
		assertEquals(20_000, graph.evaluations());
	}

	@Test
	void theValidatorDropsTrailsOnACycle() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		QuestStep step = new QuestStep("s1", List.of(), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		QuestStep finale = new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		Questline fine = new Questline("fine", "길", "Pfad", 1, List.of(), Questline.Requires.none(),
				List.of(step), finale, "끝", "Ende", "fine", 1, 1, false);
		Questline loopA = new Questline("loopA", "길", "Pfad", 1, List.of(),
				new Questline.Requires(0, null, List.of("loopB")), List.of(step), finale,
				"끝", "Ende", "loopA", 2, 1, false);
		Questline loopB = new Questline("loopB", "길", "Pfad", 1, List.of(),
				new Questline.Requires(0, null, List.of("loopA")), List.of(step), finale,
				"끝", "Ende", "loopB", 3, 1, false);
		QuestValidator.Result result = QuestValidator.validate(List.of(fine, loopA, loopB), catalog, LOG);
		assertEquals(List.of("fine"), result.trails().stream().map(Questline::id).toList());
		assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("loopA")
				&& problem.contains("cycle")), "problems: " + result.problems());
	}

	// ------------------------------------------------------------------ the engine

	private QuestEngine engineFor(List<Questline> trails, String dir) {
		ProgressStore store = new ProgressStore(tempDir.resolve(dir), LOG);
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		LearningService learn = new LearningService(store.data(), store, catalog);
		return new QuestEngine(trails, catalog, learn, LOG);
	}

	@Test
	void theEngineBuildsTheGraphOncePerTrailList() {
		List<Questline> trails = QuestLoader.loadAll();
		QuestEngine engine = engineFor(trails, "once");
		TrailGraph first = engine.graph();
		assertEquals(421, first.evaluations());
		for (Questline trail : trails) {
			assertSame(trail, engine.byId(trail.id()));
		}
		assertSame(first, engine.graph(), "lookups must not rebuild the graph");

		engine.replaceTrails(trails.subList(0, 100));
		assertNotSame(first, engine.graph());
		assertNull(engine.byId(trails.get(200).id()));
		assertSame(trails.get(5), engine.byId(trails.get(5).id()));

		engine.addTrail(trail("extra"));
		assertEquals(101, engine.trails().size());
		assertSame(engine.trails().get(100), engine.byId("extra"));
	}

	@Test
	void replacingTheTrailsKeepsTheAdaptiveTrail() {
		QuestEngine engine = engineFor(List.of(trail("a")), "adaptive");
		engine.addTrail(trail("adaptive"));
		engine.replaceTrails(List.of(trail("b")));
		assertTrue(engine.byId("adaptive") != null);
		assertEquals(2, engine.trails().size());
	}

	@Test
	void anEngineWithACycleStillWorks() {
		QuestEngine engine = engineFor(List.of(trail("a", "b"), trail("b", "a"), trail("c")), "cycle");
		assertTrue(engine.graph().hasCycle());
		assertEquals(1, engine.graph().depth("a"));
		assertSame(engine.byId("c"), engine.graph().byId("c"));
	}

	@Test
	void advancingAfterACompletionNeverReevaluatesTheGraph() throws Exception {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		List<Questline> trails = QuestValidator.validate(QuestLoader.loadAll(), catalog, LOG).trails();
		assertEquals(421, trails.size());
		QuestEngine engine = engineFor(trails, "advance");
		TrailGraph graph = engine.graph();
		Method advance = QuestEngine.class.getDeclaredMethod("advanceAfterCompletion",
				String.class, Observation.class, QuestEngine.Feedback.class);
		advance.setAccessible(true);
		QuestEngine.Feedback quiet = new QuestEngine.Feedback() {
			@Override
			public void banner(String ko, String de, boolean success) {
			}

			@Override
			public void wordCard(Word word) {
			}

			@Override
			public void sound(boolean success) {
			}
		};
		Observation obs = new Observation();
		obs.now = 1_000L;
		obs.dimension = "minecraft:overworld";
		// Every root in turn, so the same-level search runs over many different start depths.
		long start = System.nanoTime();
		int passes = 0;
		for (Questline root : trails) {
			if (root.requires().completed().isEmpty() && passes < 20) {
				advance.invoke(engine, root.id(), obs, quiet);
				passes++;
			}
		}
		long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
		assertTrue(passes > 0);
		assertSame(graph, engine.graph());
		assertEquals(421, engine.graph().evaluations(), "no depth was recomputed");
		// The old recursive walk needed about 2.3 million calls per pass; allow a very generous bound.
		assertTrue(elapsedMs < 1_500, passes + " passes took " + elapsedMs + " ms");
	}
}
