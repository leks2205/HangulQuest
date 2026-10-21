package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressStore;
import io.github.leks2205.hangulquest.learn.ReviewLog;
import io.github.leks2205.hangulquest.learn.ReviewMode;
import io.github.leks2205.hangulquest.learn.WordProgress;
import io.github.leks2205.hangulquest.learn.fsrs.Fsrs;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reverse step ("find the 돌") shows the Korean only: no translation, no word card, no sidequest. It
 * is only allowed on goals the player can recognise by looking or acting, and finishing it unaided is a
 * recall like any other quest step.
 */
class ReverseStepTest {
	private static final Logger LOG = LoggerFactory.getLogger("reverse-step-test");
	private static final String STONE = "concept:stone";
	private static final long FIRST = 1_000L;
	private static final long NOW = 700_000_000L;

	@TempDir
	Path tempDir;

	private Catalog catalog;
	private LearningService learn;
	private QuestEngine engine;
	private final Recorder recorder = new Recorder();

	private static Concept concept(String id, String ko, String de) {
		Concept concept = new Concept();
		concept.id = id;
		concept.ko = ko;
		concept.de = de;
		concept.category = "BLOCKS";
		concept.tier = 1;
		concept.items = List.of("minecraft:" + id);
		concept.descKo = "땅에서 나와요.";
		return concept;
	}

	private static QuestStep stoneStep(String id, boolean reverse) {
		return new QuestStep(id, List.of("stone"), new Goal.BreakBlock("minecraft:stone", 1, null), List.of(),
				"돌을 캐요.", "Baue Stein ab.", "Mine the stone.", "돌을 캐요.", "Baue Stein ab.", "Mine the stone.", null)
				.withReverse(reverse);
	}

	private static Questline trail(String id, QuestStep... steps) {
		QuestStep finale = new QuestStep("finale", List.of(), new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
				null, null, null, null, null);
		return new Questline(id, "길", "Pfad", 1, List.of("stone"), Questline.Requires.none(), List.of(steps),
				finale, "끝났어요.", "Fertig.", id + "-badge", 10, 1, false);
	}

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		ProgressStore store = new ProgressStore(tempDir, LOG);
		catalog = Catalog.ofConcepts(List.of(concept("stone", "돌", "Stein"), concept("torch", "횃불", "Fackel")));
		learn = new LearningService(store.data(), store, catalog);
	}

	private void engineFor(Questline... trails) {
		engine = new QuestEngine(List.of(trails), catalog, learn, LOG);
	}

	private void start(String id) {
		Observation start = new Observation();
		start.now = NOW - 1000;
		start.dimension = "minecraft:overworld";
		engine.startTrail(id, start, recorder);
	}

	private static Observation breaking(long now) {
		Observation obs = new Observation();
		obs.dimension = "minecraft:overworld";
		obs.now = now;
		obs.brokenBlocks.add("minecraft:stone");
		return obs;
	}

	private WordProgress stone() {
		return learn.data().words.get(STONE);
	}

	private void makeStoneDue() {
		learn.answer(STONE, true, FIRST);
		learn.answer(STONE, true, FIRST + 61_000L);
		assertTrue(stone().fsrs.due < NOW);
	}

	private ReviewLog.Event lastEvent() {
		learn.reviewLog().flush();
		List<ReviewLog.Event> events = ReviewLog.readAll(tempDir);
		return events.get(events.size() - 1);
	}

	// ------------------------------------------------------------------ loading

	@Test
	void theFlagIsReadFromJsonAndDefaultsToOff() {
		String steps = "[{\"id\":\"a\",\"teach\":[\"stone\"],\"goal\":{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\"},"
				+ "\"reverse\":true},{\"id\":\"b\",\"teach\":[\"stone\"],\"goal\":{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\"}},"
				+ "{\"id\":\"c\",\"teach\":[\"stone\"],\"goal\":{\"type\":\"BREAK_BLOCK\",\"block\":\"minecraft:stone\"},\"reverse\":false}]";
		JsonObject root = JsonParser.parseString("{\"id\":\"rev-test\",\"steps\":" + steps + "}").getAsJsonObject();

		List<QuestStep> parsed = QuestLoader.parse(root).allSteps();

		assertTrue(parsed.get(0).reverse());
		assertFalse(parsed.get(1).reverse());
		assertFalse(parsed.get(2).reverse());
	}

	@Test
	void theFlagSurvivesTheCopiesTheLoaderMakes() {
		QuestStep step = stoneStep("s1", true);
		assertTrue(step.withEnglish("a", "b").reverse());
		assertTrue(step.withTextKey("hangulquest.trail.x.step.s1").reverse());
		assertTrue(step.withId("s2").reverse());
	}

	@Test
	void theSchemaListsTheFlag() throws Exception {
		JsonObject schema = JsonParser.parseString(
				Files.readString(Path.of("docs/questline.schema.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		JsonObject reverse = schema.getAsJsonObject("definitions").getAsJsonObject("step")
				.getAsJsonObject("properties").getAsJsonObject("reverse");
		assertEquals("boolean", reverse.get("type").getAsString());
	}

	// ------------------------------------------------------------------ validation

	private static List<String> problemsOf(Catalog full, Goal goal, boolean reverse) {
		QuestStep step = new QuestStep("s1", List.of("stone"), goal, List.of(), null, null, null, null, null)
				.withReverse(reverse);
		QuestStep finale = new QuestStep("finale", List.of("stone"), new Goal.PlaceBlock("minecraft:torch", 1),
				List.of(), null, null, null, null, null);
		Questline trail = new Questline("rev", "길", "Pfad", 1, List.of("stone"), Questline.Requires.none(),
				List.of(step), finale, "끝났어요.", "Fertig.", "rev-badge", 10, 1, false);
		return QuestValidator.validate(List.of(trail), full, LOG).problems();
	}

	@Test
	void theValidatorAllowsTheFlagOnlyOnGoalsThatAreRecognisedByLookingOrActing() {
		assertEquals(Set.of("LOOK_AT_BLOCK", "LOOK_AT_ENTITY", "BREAK_BLOCK", "ACQUIRE_ITEM", "PLACE_REL"),
				QuestValidator.REVERSE_GOALS);
		Catalog full = Catalog.ofConcepts(ConceptLoader.load());
		List<Goal> allowed = List.of(
				new Goal.BreakBlock("minecraft:stone", 1, null),
				new Goal.LookAtBlock("minecraft:stone", 1000),
				new Goal.LookAtEntity("minecraft:cow", 1000),
				new Goal.AcquireItem("minecraft:cobblestone", 1),
				new Goal.PlaceRel("minecraft:torch", io.github.leks2205.hangulquest.quest.spatial.Relation.NEXT_TO,
						"minecraft:stone", 1));
		for (Goal goal : allowed) {
			assertEquals(problemsOf(full, goal, false), problemsOf(full, goal, true),
					"the flag adds nothing on " + goal);
		}
	}

	@Test
	void theValidatorRejectsTheFlagOnOtherGoalTypes() {
		Catalog full = Catalog.ofConcepts(ConceptLoader.load());
		List<Goal> rejected = List.of(
				new Goal.PlaceBlock("minecraft:torch", 1),
				new Goal.UseBlock("minecraft:oak_door"),
				new Goal.ReachDimension("minecraft:the_nether"),
				new Goal.DefeatEntity("minecraft:zombie", 1),
				new Goal.ReachHeight(60, 80));
		for (Goal goal : rejected) {
			List<String> without = problemsOf(full, goal, false);
			List<String> with = problemsOf(full, goal, true);
			assertEquals(without.size() + 1, with.size(), goal.toString());
			assertTrue(with.stream().anyMatch(problem -> problem.contains("reverse does not fit")), with.toString());
		}
	}

	// ------------------------------------------------------------------ what the log shows

	@Test
	void aReverseStepHidesItsTranslationEvenInEnglish() {
		engineFor(trail("t", stoneStep("s1", true)));
		start("t");

		QuestEngine.TrackView view = engine.trackedViews().get(0);

		assertTrue(view.reverse());
		assertEquals("돌을 캐요.", view.clueKo());
		assertEquals("", view.clueDe(), "the translation of a reverse step stays hidden");
		assertEquals("", engine.clueTranslation(engine.currentStep()));
	}

	@Test
	void anOrdinaryStepStillShowsItsTranslation() {
		engineFor(trail("t", stoneStep("s1", false)));
		start("t");

		QuestEngine.TrackView view = engine.trackedViews().get(0);

		assertFalse(view.reverse());
		assertEquals("Mine the stone.", view.clueDe());
	}

	@Test
	void theNextClueTranslationIsHiddenWhenTheNextStepIsReverse() {
		engineFor(trail("t", stoneStep("s1", false), stoneStep("s2", true)));
		start("t");

		engine.tick(breaking(NOW), recorder);

		assertEquals(1, recorder.steps);
		assertEquals("돌을 캐요.", recorder.nextClueKo);
		assertEquals("", recorder.nextClueDe);
	}

	@Test
	void theHintOfAReverseStepShowsNoWordAndStartsNoSidequest() {
		engineFor(trail("t", stoneStep("s1", true)));
		start("t");
		Observation obs = new Observation();
		obs.now = NOW;

		QuestEngine.HintView hint = engine.hint(obs, recorder);

		assertNull(hint.word(), "no picture of the word");
		assertFalse(hint.sidequestStarted());
		assertFalse(engine.sidequestActive());
		assertEquals(0, recorder.wordCards);
		assertEquals("돌을 캐요.", hint.simplifiedKo());
	}

	@Test
	void theHintOfAnOrdinaryStepStillShowsTheWord() {
		engineFor(trail("t", stoneStep("s1", false)));
		start("t");
		Observation obs = new Observation();
		obs.now = NOW;

		QuestEngine.HintView hint = engine.hint(obs, recorder);

		assertEquals("돌", hint.word().ko);
		assertTrue(hint.sidequestStarted());
		assertEquals(1, recorder.wordCards);
	}

	// ------------------------------------------------------------------ grading

	@Test
	void aReverseStepDoneUnaidedIsAProductiveRecall() {
		makeStoneDue();
		engineFor(trail("t", stoneStep("s1", true)));
		start("t");

		engine.tick(breaking(NOW), recorder);

		ReviewLog.Event event = lastEvent();
		assertEquals(ReviewMode.QUEST_UNAIDED, event.mode());
		assertTrue(event.mode().recall());
		assertEquals(Fsrs.GOOD, event.grade());
		assertEquals("t/s1/reverse", event.ctx());
	}

	@Test
	void anOrdinaryStepKeepsItsPrimaryRole() {
		makeStoneDue();
		engineFor(trail("t", stoneStep("s1", false)));
		start("t");

		engine.tick(breaking(NOW), recorder);

		assertEquals("t/s1/primary", lastEvent().ctx());
	}

	@Test
	void aHintedReverseStepIsHardAndNotRecall() {
		makeStoneDue();
		engineFor(trail("t", stoneStep("s1", true)));
		start("t");
		Observation hintObs = new Observation();
		hintObs.now = NOW;
		engine.hint(hintObs, recorder);

		engine.tick(breaking(NOW + 100), recorder);

		ReviewLog.Event event = lastEvent();
		assertEquals(ReviewMode.QUEST_HINTED, event.mode());
		assertFalse(event.mode().recall());
		assertEquals(Fsrs.HARD, event.grade());
	}

	@Test
	void aReverseStepOnAWordThatIsNotDueIsOnlyAnEncounter() {
		engineFor(trail("t", stoneStep("s1", true)));
		start("t");

		engine.tick(breaking(NOW), recorder);

		assertEquals(ReviewMode.EXPOSURE, lastEvent().mode());
	}

	private static final class Recorder implements QuestEngine.Feedback {
		int steps;
		int wordCards;
		String nextClueKo;
		String nextClueDe;
		final List<String> banners = new ArrayList<>();

		@Override
		public void banner(String ko, String de, boolean success) {
			banners.add(ko + "|" + de);
		}

		@Override
		public void wordCard(Word word) {
			wordCards++;
		}

		@Override
		public void sound(boolean success) {
		}

		@Override
		public void stepCompleted(String praiseKo, String nextKo, String nextDe) {
			steps++;
			nextClueKo = nextKo;
			nextClueDe = nextDe;
		}
	}
}
