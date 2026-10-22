package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.PickerFixtures.Recorder;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.LOG;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.breaking;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.finish;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.obs;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.trail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The automatic quest log: the start pick, the follow-up, the player's choices, the cap and the setting. */
class QuestPickerTest {
	@TempDir
	Path dir;

	private LearningService learn;
	private Recorder hud;

	@BeforeEach
	void setUp() {
		learn = PickerFixtures.learner(dir.resolve("run"));
		hud = new Recorder();
	}

	private QuestEngine engine(Questline... trails) {
		QuestEngine engine = new QuestEngine(List.of(trails), PickerFixtures.catalog(), learn, LOG);
		engine.setAutoPick(() -> true);
		return engine;
	}

	private static Set<String> ids(QuestEngine engine) {
		return new TreeSet<>(engine.trackedViews().stream().map(QuestEngine.TrackView::id).toList());
	}

	/** Five roots: wood, food, a second food trail that beats the animal one on score, animals, a level-3 one. */
	private QuestEngine fiveRoots() {
		return engine(trail("wood", 10, "나무"), trail("food", 20, "음식"), trail("food2", 30, "음식"),
				trail("pig", 40, "동물"), trail("tower", 50, 3, "산", List.of()));
	}

	// ------------------------------------------------------------ the start pick

	@Test
	void aNewLogGetsThreeTrailsOfDifferentTopics() {
		QuestEngine engine = fiveRoots();

		engine.autoStart(obs(10_000), hud);

		assertEquals(Set.of("wood", "food", "pig"), ids(engine),
				"food2 scores higher than pig alone, but it repeats the topic of food");
		assertEquals(List.of(List.of("wood", "food", "pig")), hud.autoTracked);
		assertEquals(List.of(QuestEngine.Feedback.AutoReason.START), hud.reasons);
		for (String id : ids(engine)) {
			assertTrue(engine.isAutoTracked(id), id + " was picked by the game");
		}
		assertEquals("wood", engine.focusedId(), "the best one is focused");
	}

	@Test
	void theStartPickSkipsWhatIsTooHardOutOfReachOrLocked() {
		Questline nether = new Questline("nether", "nether", "nether-de", "불", 1, List.of("stone"),
				new Questline.Requires(0, null, List.of()),
				List.of(new QuestStep("s1", List.of("stone"), new Goal.ReachBiome("minecraft:nether_wastes", 1000),
						List.of(), null, null, null, null, null)),
				new QuestStep("finale", List.of("torch"), new Goal.PlaceBlock("minecraft:torch", 1), List.of(),
						null, null, null, null, null),
				"끝", "Ende", "nether", 5, 1, false);
		QuestEngine engine = engine(nether, trail("wood", 10, "나무"),
				trail("hard", 6, 3, "음식", List.of()),
				trail("later", 7, 1, "물", List.of("wood")),
				PickerFixtures.custom("wordy", 8, 1, "밤", 50, List.of(), new Goal.BreakBlock("minecraft:wordy", 1, null), List.of()));

		engine.autoStart(obs(10_000), hud);

		assertEquals(Set.of("wood"), ids(engine));
	}

	@Test
	void aTrailThatAsksForAFewMoreWordsStillCountsButOneThatAsksForMuchMoreDoesNot() {
		QuestEngine engine = engine(
				PickerFixtures.custom("near", 10, 1, "나무", NextQuestPolicy.Weights.WORD_SLACK, List.of(),
						new Goal.BreakBlock("minecraft:near", 1, null), List.of()),
				PickerFixtures.custom("far", 11, 1, "돌", NextQuestPolicy.Weights.WORD_SLACK + 1, List.of(),
						new Goal.BreakBlock("minecraft:far", 1, null), List.of()));

		engine.autoStart(obs(10_000), hud);

		assertEquals(Set.of("near"), ids(engine));
	}

	@Test
	void theAdaptiveTrailIsNeverPickedAndDoesNotTakeASlot() {
		Questline review = trail("wood", 10, "나무").withAdaptive(true);
		QuestEngine engine = engine(review, trail("a", 20, "돌"), trail("b", 30, "음식"), trail("c", 40, "동물"),
				trail("d", 50, "물"));

		engine.autoStart(obs(10_000), hud);
		assertFalse(engine.isTracked("wood"), "the review trail is only tracked by the player");
		assertEquals(3, engine.trackedCount());

		assertTrue(engine.setTracked("wood", true));
		assertEquals(4, engine.trackedCount(), "it sits next to the three regular slots");
		engine.tick(obs(20_000), hud);
		assertEquals(4, engine.trackedCount());
		assertTrue(engine.isTracked("a") && engine.isTracked("b") && engine.isTracked("c"), "nothing was dropped for it");
	}

	@Test
	void theSettingOffTracksNothingByItself() {
		QuestEngine engine = fiveRoots();
		engine.setAutoPick(() -> false);

		engine.autoStart(obs(10_000), hud);
		for (int i = 1; i < 30; i++) {
			engine.tick(obs(10_000 + i * 1_000L), hud);
		}

		assertEquals(0, engine.trackedCount());
		assertTrue(hud.autoTracked.isEmpty());
	}

	@Test
	void withTheSettingOffAFinishedTrailOnlyOffersTheNextOnes() {
		QuestEngine engine = engine(trail("t", 5, "나무"), trail("c1", 100, 1, "돌", List.of("t")),
				trail("c2", 101, 1, "음식", List.of("t")));
		engine.setAutoPick(() -> false);
		engine.startTrail("t", obs(1_000), hud);

		finish(engine, "t", 2_000, hud);

		assertEquals(1, hud.choices.size(), "the popup of old");
		assertEquals(0, engine.trackedCount(), "nothing is tracked for the player");
		assertTrue(hud.autoTracked.isEmpty());
	}

	// ---------------------------------------------------------------- follow-up

	@Test
	void whenAnAutoPickedTrailIsFinishedItsFollowUpIsTrackedAtOnce() {
		QuestEngine engine = engine(trail("wood", 10, "나무"), trail("wood2", 15, 1, "나무", List.of("wood")),
				trail("food", 20, "음식"), trail("pig", 40, "동물"), trail("water", 50, "물"));
		engine.autoStart(obs(10_000), hud);
		assertEquals(Set.of("wood", "food", "pig"), ids(engine));
		hud.autoTracked.clear();
		hud.reasons.clear();

		finish(engine, "wood", 20_000, hud);

		assertEquals(Set.of("wood2", "food", "pig"), ids(engine), "the follow-up took the freed slot");
		assertEquals(List.of(List.of("wood2")), hud.autoTracked);
		assertEquals(List.of(QuestEngine.Feedback.AutoReason.FOLLOW_UP), hud.reasons);
		assertEquals("wood2", engine.focusedId(), "the finished trail was the focused one: its follow-up takes over");
		assertTrue(engine.isAutoTracked("wood2"));
		assertTrue(hud.choices.isEmpty(), "no popup in the automatic mode");
	}

	@Test
	void aTrailTheOwnerChoseGetsAFollowUpToo() {
		QuestEngine engine = engine(trail("a", 10, "나무"), trail("a2", 400, 1, "물", List.of("a")),
				trail("b", 20, "돌"), trail("c", 30, "음식"));
		assertTrue(engine.startTrail("a", obs(1_000), hud));
		assertFalse(engine.isAutoTracked("a"));

		finish(engine, "a", 2_000, hud);

		assertEquals(Set.of("a2", "b", "c"), ids(engine));
		int last = hud.autoTracked.size() - 1;
		assertEquals(List.of("a2"), hud.autoTracked.get(last), "the follow-up comes first although it scores lowest on its own");
		assertEquals(QuestEngine.Feedback.AutoReason.FOLLOW_UP, hud.reasons.get(last));
	}

	@Test
	void withoutAFollowUpTheSamePolicyPicks() {
		QuestEngine engine = engine(trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"),
				trail("d", 40, "동물"), trail("e", 50, "물"));
		engine.autoStart(obs(10_000), hud);
		assertEquals(Set.of("a", "b", "c"), ids(engine));

		finish(engine, "b", 20_000, hud);

		assertEquals(Set.of("a", "c", "d"), ids(engine), "the best of the rest takes the slot");
		assertEquals(QuestEngine.Feedback.AutoReason.FOLLOW_UP, hud.reasons.get(hud.reasons.size() - 1));
	}

	@Test
	void aFollowUpThatIsStillLockedIsNotTracked() {
		QuestEngine engine = engine(trail("a", 10, "나무"),
				trail("a2", 15, 1, "나무", List.of("a", "other")), trail("other", 900, 4, "음식", List.of()),
				trail("b", 20, "돌"), trail("c", 30, "음식"));
		engine.autoStart(obs(10_000), hud);

		finish(engine, "a", 20_000, hud);

		assertFalse(engine.isTracked("a2"), "it still waits for another trail");
		assertEquals(2, engine.trackedCount(), "and nothing else is good enough to take the slot");
	}

	// ------------------------------------------------------------ the owner's choices

	@Test
	void aTrailTakenOutOfTheLogStaysOutAndTheLogIsNotRefilledForAWhile() {
		QuestEngine engine = fiveRoots();
		engine.autoStart(obs(10_000), hud);
		hud.autoTracked.clear();

		engine.untrack("food");
		long now = 11_000;
		for (int i = 0; i < 600; i++) { // ten minutes of play
			now += 1_000;
			engine.tick(obs(now), hud);
		}

		assertEquals(Set.of("wood", "pig"), ids(engine), "the slot stays empty");
		assertTrue(hud.autoTracked.isEmpty());

		for (int i = 0; i < 1_300; i++) { // past thirty minutes of play in all
			now += 1_000;
			engine.tick(obs(now), hud);
		}
		assertEquals(Set.of("wood", "food", "pig"), ids(engine), "after the pause the slot is filled again");
	}

	@Test
	void theNextFinishedTrailEndsThePause() {
		QuestEngine engine = engine(trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"),
				trail("d", 40, "동물"), trail("e", 50, "물"));
		engine.autoStart(obs(10_000), hud);
		engine.untrack("c");

		finish(engine, "a", 20_000, hud);

		assertNotNull(learn.data().quests.completed.get("a"));
		assertTrue(learn.data().quests.dismissed == null || learn.data().quests.dismissed.isEmpty(),
				"the next finished trail ends the pause");
		assertEquals(3, engine.trackedCount(), "the log is filled at once");
	}

	@Test
	void aDismissedTrailIsLeftOutOfTheFollowUpPickWhileThePauseLasts() {
		QuestEngine engine = fiveRoots();
		engine.autoStart(obs(10_000), hud);
		assertTrue(engine.isTracked("pig"));
		engine.untrack("pig");

		assertTrue(engine.picker().choose(obs(11_000), null, 3).stream().noneMatch(trail -> trail.id().equals("pig")));
		assertTrue(learn.data().quests.dismissed.containsKey("pig"));
	}

	@Test
	void whatThePlayerTracksNeverGetsReplacedByThePicker() {
		QuestEngine engine = engine(trail("m1", 400, "물"), trail("m2", 410, "밤"), trail("m3", 420, "산"),
				trail("m4", 430, "마을"), trail("a", 10, "나무"), trail("b", 20, "돌"));
		for (String id : List.of("m1", "m2", "m3", "m4")) {
			assertTrue(engine.startTrail(id, obs(1_000), hud));
		}

		engine.autoStart(obs(10_000), hud);
		for (int i = 1; i < 20; i++) {
			engine.tick(obs(10_000 + i * 5_000L), hud);
		}

		assertEquals(Set.of("m1", "m2", "m3", "m4"), ids(engine), "a full log of the player's own is left alone");
		for (String id : ids(engine)) {
			assertFalse(engine.isAutoTracked(id));
		}
	}

	@Test
	void manuallyTrackingTakesTheSlotOfAnUnstartedAutoPickAndNeverOneWithProgress() {
		QuestEngine engine = engine(trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"),
				trail("d", 40, "동물"), trail("e", 50, "물"));
		engine.autoStart(obs(10_000), hud);
		assertEquals(Set.of("a", "b", "c"), ids(engine));
		engine.tick(breaking(11_000, "minecraft:a"), hud); // a: first block broken
		assertTrue(engine.setTracked("d", true));
		assertTrue(engine.setTracked("e", true));

		assertEquals(4, engine.trackedCount(), "the cap holds");
		assertTrue(engine.isTracked("a"), "a has progress and stays");
		assertTrue(engine.isTracked("d") && engine.isTracked("e"));
		assertFalse(engine.isAutoTracked("d"), "a trail the player checks is theirs");
	}

	@Test
	void theLogNeverHoldsMoreThanFourAndDeclinesWhenEveryOneHasProgress() {
		QuestEngine engine = engine(trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"),
				trail("d", 40, "동물"), trail("e", 50, "물"), trail("f", 60, "밤"));
		engine.setAutoPick(() -> false);
		for (String id : List.of("a", "b", "c", "d")) {
			engine.startTrail(id, obs(1_000), hud);
			engine.tick(breaking(2_000, "minecraft:" + id), hud);
		}
		assertEquals(QuestEngine.MAX_TRACKED, engine.trackedCount());
		assertEquals(4, QuestEngine.MAX_TRACKED);

		assertFalse(engine.setTracked("e", true));
		assertFalse(engine.startTrail("f", obs(3_000), hud));

		assertEquals(4, engine.trackedCount());
		engine.setAutoPick(() -> true);
		engine.autoStart(obs(4_000), hud);
		assertEquals(4, engine.trackedCount());
	}

	// ---------------------------------------------------------- saves and old saves

	@Test
	void anOldSaveKeepsItsTrackedTrailsExactlyAndOnlyEmptySlotsAreFilled() {
		List<Questline> trails = List.of(trail("old1", 400, "물"), trail("old2", 410, "밤"),
				trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"));
		QuestEngine before = new QuestEngine(trails, PickerFixtures.catalog(), learn, LOG);
		before.startTrail("old1", obs(1_000), hud);
		before.startTrail("old2", obs(1_000), hud);
		assertNull(learn.data().quests.situational);
		learn.data().quests.auto = null; // a save from before the picker
		learn.data().quests.dismissed = null;

		QuestEngine after = new QuestEngine(trails, PickerFixtures.catalog(), learn, LOG);
		after.setAutoPick(() -> true);
		assertEquals(Set.of("old1", "old2"), ids(after));
		assertFalse(after.isAutoTracked("old1"), "no flag in the file means the player chose it");

		after.autoStart(obs(10_000), hud);

		assertTrue(ids(after).containsAll(Set.of("old1", "old2")), "nothing the player tracked was touched");
		assertEquals(3, after.trackedCount(), "only the one empty regular slot was filled");
		assertEquals(Set.of("a"), new TreeSet<>(hud.autoTracked.get(0)));
	}

	@Test
	void aLogFromBeforeTheCapOfFourIsNotTrimmed() {
		List<Questline> trails = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			trails.add(trail("t" + i, 10 + i, "물"));
		}
		QuestEngine before = new QuestEngine(trails, PickerFixtures.catalog(), learn, LOG);
		for (int i = 0; i < 4; i++) {
			before.startTrail("t" + i, obs(1_000), hud);
		}
		// an older build allowed six: write them the way it did
		learn.data().quests.tracked = new ArrayList<>(List.of("t0", "t1", "t2", "t3", "t4", "t5"));
		for (String id : List.of("t4", "t5")) {
			ProgressDataHelper.saved(learn, id);
		}

		QuestEngine after = new QuestEngine(trails, PickerFixtures.catalog(), learn, LOG);
		after.setAutoPick(() -> true);
		after.autoStart(obs(10_000), hud);

		assertEquals(6, after.trackedCount(), "all six are still there");
		assertTrue(hud.autoTracked.isEmpty(), "and nothing is added");
	}

	@Test
	void theAutoFlagsTheSituationalTrailAndThePausesSurviveARestart() {
		List<Questline> trails = List.of(trail("a", 10, "나무"), trail("b", 20, "돌"), trail("c", 30, "음식"),
				trail("d", 40, "동물"));
		QuestEngine first = new QuestEngine(trails, PickerFixtures.catalog(), learn, LOG);
		first.setAutoPick(() -> true);
		first.autoStart(obs(10_000), hud);
		first.startTrail("d", obs(11_000), hud); // the fourth slot
		first.untrack("b");
		learn.store().flush();

		LearningService reloaded = PickerFixtures.learner(dir.resolve("run"));
		QuestEngine second = new QuestEngine(trails, PickerFixtures.catalog(), reloaded, LOG);

		assertEquals(ids(first), ids(second));
		for (String id : ids(first)) {
			assertEquals(first.isAutoTracked(id), second.isAutoTracked(id), id);
		}
		assertTrue(reloaded.data().quests.dismissed.containsKey("b"));
		assertFalse(second.isAutoTracked("d"));
	}

	/** Puts a started-but-unchecked trail into the save the way the engine would. */
	private static final class ProgressDataHelper {
		static void saved(LearningService learn, String id) {
			io.github.leks2205.hangulquest.learn.ProgressData.Active snapshot = new io.github.leks2205.hangulquest.learn.ProgressData.Active();
			snapshot.trailId = id;
			snapshot.stepId = "s1";
			snapshot.startedAt = 1_000;
			learn.data().quests.saved.put(id, snapshot);
		}
	}

	// -------------------------------------------------------------------- views

	@Test
	void theLogRowOfTheSituationalSlotIsMarked() {
		Questline cave = PickerFixtures.custom("cave", 500, 1, "기타", 0, List.of(),
				new Goal.BreakBlock("minecraft:deepslate", 3, null), List.of(new Condition.YRange(0, 40)));
		QuestEngine engine = engine(cave, trail("a", 10, "나무"));
		Observation underground = obs(1_000);
		underground.underground = true;
		underground.y = 30;
		for (int i = 0; i <= 25; i++) {
			underground.now = 1_000 + i * 1_000L;
			engine.tick(underground, hud);
		}

		assertEquals("cave", engine.situationalId());
		for (QuestEngine.TrackView view : engine.trackedViews()) {
			assertEquals(view.id().equals("cave"), view.situational(), view.id());
		}
	}
}
