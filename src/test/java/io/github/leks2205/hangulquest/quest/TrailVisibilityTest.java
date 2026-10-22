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
	 *   g (a root that wants 5 more words)   h (a root that wants 40 more)
	 * </pre>
	 */
	private static TrailGraph graph() {
		return TrailGraph.of(List.of(trail("a", 0), trail("b", 0), trail("c", 0, "a", "b"), trail("d", 0, "c"),
				trail("e", 0, "b", "c"), trail("f", 0, "d"), trail("g", 5), trail("h", 40)));
	}

	private static Map<String, State> compute(Set<String> completed, Set<String> tracked, int words) {
		TrailGraph graph = graph();
		Predicate<Questline> unlocked = trail -> words >= trail.requires().minWordsKnown()
				&& completed.containsAll(trail.requires().completed());
		return TrailVisibility.of(graph, completed::contains, tracked::contains, unlocked, words);
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
		assertEquals(State.NEXT, states.get("g"), "five words away is close");
		assertEquals(State.HIDDEN, states.get("h"), "forty words away is not");
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
	void wordsMetMoveARootFromGreyToAvailable() {
		assertEquals(State.NEXT, compute(Set.of(), Set.of(), 0).get("g"));
		assertEquals(State.AVAILABLE, compute(Set.of(), Set.of(), 5).get("g"));
		assertEquals(State.NEXT, compute(Set.of(), Set.of(), 30).get("h"));
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
		for (Questline trail : engine.trails()) {
			State state = states.get(trail.id());
			switch (state) {
				case AVAILABLE -> {
					available.add(trail.id());
					assertTrue(graph.parents(trail.id()).isEmpty(), trail.id() + " is available but has parents");
					assertEquals(0, trail.requires().minWordsKnown(), trail.id());
				}
				case NEXT -> {
					boolean child = graph.parents(trail.id()).stream()
							.anyMatch(parent -> states.get(parent) == State.AVAILABLE);
					boolean rootSoon = graph.parents(trail.id()).isEmpty()
							&& trail.requires().minWordsKnown() <= TrailVisibility.WORD_LOOKAHEAD;
					assertTrue(child || rootSoon, trail.id() + " is greyed but neither follows an available trail nor is a root nearly open");
				}
				case HIDDEN -> {
				}
				default -> throw new AssertionError(trail.id() + " is " + state + " on a fresh profile");
			}
		}
		assertTrue(available.containsAll(List.of("steinpfad")), "the first trail can be started: " + available);
		assertTrue(available.size() <= 3, "a handful at most: " + available);
		for (Questline trail : engine.trails()) {
			if (graph.depth(trail.id()) >= 3) {
				assertEquals(State.HIDDEN, states.get(trail.id()), trail.id() + " is two steps away");
			}
		}
		long shown = states.values().stream().filter(TrailVisibility::drawn).count();
		assertTrue(shown < engine.trails().size() / 3, "far less than the whole tree: " + shown);
		System.out.println("visibility on a fresh profile: " + available.size() + " available, "
				+ states.values().stream().filter(s -> s == State.NEXT).count() + " greyed, " + shown + " of "
				+ engine.trails().size() + " drawn");
	}
}
