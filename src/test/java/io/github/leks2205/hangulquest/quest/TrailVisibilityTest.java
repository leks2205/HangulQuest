package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.quest.TrailVisibility.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which trails the journal draws: available and active ones, their direct follow-ups greyed, the rest hidden. */
class TrailVisibilityTest {
	@TempDir
	Path dir;

	private static Questline trail(String id, int words, String... parents) {
		QuestStep finale = new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		return new Questline(id, id, id, 1, List.of(), new Questline.Requires(words, null, List.of(parents)),
				List.of(), finale, "끝", "Ende", id, 100, 1, false);
	}

	/**
	 * <pre>
	 *   a -> c -> d -> f
	 *   b -/      e (needs b and c)
	 *   g (a root that wants 5 words)   h (a root that wants 40)   i (a third open root)
	 * </pre>
	 */
	private static TrailGraph graph() {
		return TrailGraph.of(List.of(trail("a", 0), trail("b", 0), trail("c", 0, "a", "b"), trail("d", 0, "c"),
				trail("e", 0, "b", "c"), trail("f", 0, "d"), trail("g", 5), trail("h", 40), trail("i", 0)));
	}

	private static Map<String, State> compute(Set<String> completed, Set<String> tracked, int words) {
		return compute(graph(), completed, tracked, words);
	}

	private static Map<String, State> compute(TrailGraph graph, Set<String> completed, Set<String> tracked, int words) {
		Predicate<Questline> unlocked = trail -> words >= trail.requires().minWordsKnown()
				&& completed.containsAll(trail.requires().completed());
		return TrailVisibility.of(graph, completed::contains, tracked::contains, unlocked);
	}

	@Test
	void aNewPlayerSeesTheRootsAndWhatWaitsDirectlyBehindThem() {
		Map<String, State> states = compute(Set.of(), Set.of(), 0);

		assertEquals(State.AVAILABLE, states.get("a"));
		assertEquals(State.AVAILABLE, states.get("b"));
		assertEquals(State.NEXT, states.get("c"), "behind two available roots");
		assertEquals(State.HIDDEN, states.get("d"), "two steps away");
		assertEquals(State.HIDDEN, states.get("e"), "its parent c is only greyed");
		assertEquals(State.HIDDEN, states.get("f"));
		assertEquals(State.HIDDEN, states.get("g"), "a root that only waits for words is no follow-up of anything");
		assertEquals(State.HIDDEN, states.get("h"));
	}

	@Test
	void finishingATrailMovesTheWindowOneStepAlong() {
		Map<String, State> states = compute(Set.of("a", "b"), Set.of(), 0);

		assertEquals(State.COMPLETED, states.get("a"));
		assertEquals(State.AVAILABLE, states.get("c"));
		assertEquals(State.NEXT, states.get("d"));
		assertEquals(State.NEXT, states.get("e"), "its parents b is done and c is available");
		assertEquals(State.HIDDEN, states.get("f"));
	}

	@Test
	void aTrailInTheLogIsActiveWhateverItsPrerequisitesSay() {
		Map<String, State> states = compute(Set.of(), Set.of("d"), 0);

		assertEquals(State.ACTIVE, states.get("d"));
		assertEquals(State.NEXT, states.get("f"), "the follow-up of an active trail is greyed");
		assertEquals(State.NEXT, states.get("c"));
	}

	@Test
	void aRootThatWaitsForWordsStaysHiddenUntilTheWordsAreThere() {
		assertEquals(State.HIDDEN, compute(Set.of(), Set.of(), 0).get("g"));
		assertEquals(State.HIDDEN, compute(Set.of(), Set.of(), 4).get("g"), "even one word short");
		assertEquals(State.AVAILABLE, compute(Set.of(), Set.of(), 5).get("g"));
		assertEquals(State.HIDDEN, compute(Set.of(), Set.of(), 30).get("h"));
	}

	@Test
	void aTrailWhoseParentsAreDoneButLacksWordsIsHiddenToo() {
		TrailGraph graph = TrailGraph.of(List.of(trail("a", 0), trail("b", 0), trail("c", 0), trail("d", 0), trail("late", 50, "a")));

		Map<String, State> states = compute(graph, Set.of("a"), Set.of(), 0);

		assertEquals(State.HIDDEN, states.get("late"));
		assertEquals(State.AVAILABLE, compute(graph, Set.of("a"), Set.of(), 50).get("late"));
	}

	@Test
	void fewerThanThreeToDoShowsTheNearestWordGatedRootsGreyed() {
		TrailGraph graph = TrailGraph.of(List.of(trail("a", 0), trail("far", 90), trail("mid", 20), trail("near", 5),
				trail("nearer", 3)));

		Map<String, State> states = compute(graph, Set.of(), Set.of(), 0);

		assertEquals(State.AVAILABLE, states.get("a"));
		assertEquals(State.NEXT, states.get("nearer"), "the closest ones first");
		assertEquals(State.NEXT, states.get("near"));
		assertEquals(State.HIDDEN, states.get("mid"), "one available plus two greyed is three");
		assertEquals(State.HIDDEN, states.get("far"));
	}

	@Test
	void trailsInTheLogCountTowardTheThree() {
		TrailGraph graph = TrailGraph.of(List.of(trail("a", 0), trail("b", 0), trail("w1", 5), trail("w2", 9)));

		Map<String, State> states = compute(graph, Set.of(), Set.of("a"), 0);

		assertEquals(State.ACTIVE, states.get("a"));
		assertEquals(State.AVAILABLE, states.get("b"));
		assertEquals(State.NEXT, states.get("w1"), "two to do, so one greyed root makes three");
		assertEquals(State.HIDDEN, states.get("w2"));
	}

	@Test
	void threeAvailableTrailsNeedNoWordGatedHelp() {
		TrailGraph graph = TrailGraph.of(List.of(trail("a", 0), trail("b", 0), trail("c", 0), trail("w", 1)));

		assertEquals(State.HIDDEN, compute(graph, Set.of(), Set.of(), 0).get("w"));
	}

	@Test
	void greyedFollowUpsDoNotCountAsAvailable() {
		// one root, a long chain behind it: still fewer than three to do, so the word-gated root shows
		TrailGraph graph = TrailGraph.of(List.of(trail("a", 0), trail("b", 0, "a"), trail("c", 0, "a"), trail("w", 2)));

		Map<String, State> states = compute(graph, Set.of(), Set.of(), 0);

		assertEquals(State.NEXT, states.get("b"));
		assertEquals(State.NEXT, states.get("w"));
	}

	@Test
	void onlyAvailableActiveAndNextAreDrawn() {
		assertTrue(TrailVisibility.drawn(State.AVAILABLE));
		assertTrue(TrailVisibility.drawn(State.ACTIVE));
		assertTrue(TrailVisibility.drawn(State.NEXT));
		assertEquals(false, TrailVisibility.drawn(State.HIDDEN));
		assertEquals(false, TrailVisibility.drawn(State.COMPLETED));
	}

	// ---------------------------------------------------------------- real content

	@Test
	void aFreshProfileOnTheShippedTrailsShowsRootsAndTheirDirectChildren() {
		Catalog catalog = Catalog.ofConcepts(ConceptLoader.load());
		ProgressStore store = new ProgressStore(dir, LOG);
		LearningService learn = new LearningService(store.data(), store, catalog);
		QuestEngine engine = new QuestEngine(QuestLoader.loadAll(), catalog, learn, LOG);
		TrailGraph graph = engine.graph();

		Map<String, State> states = engine.visibility();

		List<String> available = new ArrayList<>();
		List<String> greyed = new ArrayList<>();
		for (Questline trail : engine.trails()) {
			State state = states.get(trail.id());
			switch (state) {
				case AVAILABLE, ACTIVE -> {
					available.add(trail.id());
					assertTrue(graph.parents(trail.id()).isEmpty(), trail.id() + " is available but has parents");
				}
				case NEXT -> greyed.add(trail.id());
				case HIDDEN -> {
				}
				default -> throw new AssertionError(trail.id() + " is " + state + " on a fresh profile");
			}
		}
		for (String id : greyed) {
			boolean child = graph.parents(id).stream()
					.anyMatch(parent -> states.get(parent) == State.AVAILABLE || states.get(parent) == State.ACTIVE);
			boolean wordGatedRoot = graph.parents(id).isEmpty() && available.size() < TrailVisibility.MIN_SHOWN;
			assertTrue(child || wordGatedRoot, id + " is greyed but follows nothing that can be started");
		}
		assertTrue(available.contains("steinpfad"), "the first trail can be started: " + available);
		for (Questline trail : engine.trails()) {
			if (graph.depth(trail.id()) >= 3) {
				assertEquals(State.HIDDEN, states.get(trail.id()), trail.id() + " is two steps away");
			}
			if (states.get(trail.id()) == State.HIDDEN && graph.parents(trail.id()).isEmpty()) {
				assertTrue(trail.requires().minWordsKnown() > 0 || available.size() >= TrailVisibility.MIN_SHOWN,
						trail.id() + " is a root that can be started, so it is not hidden");
			}
		}
		long shown = states.values().stream().filter(TrailVisibility::drawn).count();
		assertTrue(shown < engine.trails().size() / 10, "far less than the whole tree: " + shown);
		System.out.println("visibility on a fresh profile: " + available.size() + " available, "
				+ states.values().stream().filter(s -> s == State.NEXT).count() + " greyed, " + shown + " of "
				+ engine.trails().size() + " drawn");
	}
}
