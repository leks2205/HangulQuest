package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.quest.PickerFixtures.Recorder;
import io.github.leks2205.hangulquest.sense.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.LOG;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.custom;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.obs;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.trail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The situational slot: what fits where the player is, and when it may change. */
class SituationalSlotTest {
	private static final long HOLD = SituationalSlot.HOLD_MS;

	@TempDir
	Path dir;

	private LearningService learn;
	private Recorder hud;
	private QuestEngine engine;
	private long now = 1_000;

	@BeforeEach
	void setUp() {
		learn = PickerFixtures.learner(dir.resolve("run"));
		hud = new Recorder();
		// Late orders and odd topics keep these out of the regular slots; they only fit a place.
		Questline cave = custom("cave", 500, 1, "기타", 0, List.of(),
				new Goal.BreakBlock("minecraft:deepslate", 3, null), List.of(new Condition.YRange(0, 40)));
		Questline snow = custom("snow", 510, 1, "기타", 0, List.of(),
				new Goal.ReachBiome("minecraft:snowy_plains", 1000), List.of());
		Questline sea = custom("sea", 520, 1, "기타", 0, List.of(),
				new Goal.LookAtEntity("minecraft:cod", 800), List.of());
		Questline village = custom("village", 530, 1, "기타", 0, List.of(),
				new Goal.UseBlock("minecraft:bell"), List.of());
		engine = new QuestEngine(List.of(cave, snow, sea, village, trail("wood", 10, "나무")),
				PickerFixtures.catalog(), learn, LOG);
		engine.setAutoPick(() -> true);
	}

	private Observation in(Set<Situation.Tag> where) {
		Observation obs = obs(now);
		if (where.contains(Situation.Tag.CAVE)) {
			obs.underground = true;
			obs.y = 30;
		}
		if (where.contains(Situation.Tag.SNOW)) {
			obs.biome = "minecraft:snowy_plains";
		}
		if (where.contains(Situation.Tag.SEA)) {
			obs.biomeTags.add("minecraft:is_ocean");
		}
		if (where.contains(Situation.Tag.VILLAGE)) {
			obs.nearbyEntities.put("minecraft:villager", 1);
		}
		if (where.contains(Situation.Tag.DESERT)) {
			obs.biome = "minecraft:desert";
		}
		return obs;
	}

	/** Stays in a place for {@code seconds}, one tick a second. */
	private void stay(Set<Situation.Tag> where, int seconds) {
		for (int i = 0; i < seconds; i++) {
			now += 1_000;
			engine.tick(in(where), hud);
		}
	}

	private static Set<Situation.Tag> only(Situation.Tag tag) {
		return EnumSet.of(tag);
	}

	@Test
	void aContextOnlyCountsOnceItHasHeldForTwentySeconds() {
		stay(only(Situation.Tag.CAVE), 19);
		assertNull(engine.situationalId(), "19 seconds are not enough");

		stay(only(Situation.Tag.CAVE), 2);

		assertEquals("cave", engine.situationalId());
		assertEquals(List.of("cave"), hud.situational);
	}

	@Test
	void theSlotChangesWithoutAWindow() {
		stay(only(Situation.Tag.CAVE), 25);
		List<String> windowsBefore = List.copyOf(hud.notices);
		int announcedBefore = hud.autoTracked.size();

		stay(only(Situation.Tag.SNOW), 25);

		assertEquals("snow", engine.situationalId());
		assertEquals(windowsBefore, hud.notices, "no window for a situational change");
		assertEquals(announcedBefore, hud.autoTracked.size());
		assertFalse(engine.isTracked("cave"), "the unstarted old one gave the slot up");
		assertTrue(engine.isAutoTracked("snow"));
	}

	@Test
	void walkingInAndOutOfPlacesDoesNotSwapTheTrail() {
		stay(only(Situation.Tag.CAVE), 25);
		assertEquals("cave", engine.situationalId());

		for (int round = 0; round < 6; round++) { // ten seconds each, never twenty
			stay(only(round % 2 == 0 ? Situation.Tag.SNOW : Situation.Tag.SEA), 10);
		}

		assertEquals("cave", engine.situationalId());
		assertEquals(List.of("cave"), hud.situational);
	}

	@Test
	void aTrailThePlayerWorkedOnIsNeverSwappedAway() {
		stay(only(Situation.Tag.CAVE), 25);
		Observation mining = in(only(Situation.Tag.CAVE));
		mining.now = ++now;
		mining.brokenBlocks.add("minecraft:deepslate");
		engine.tick(mining, hud);
		assertTrue(engine.progressOf("cave") > 0);

		stay(only(Situation.Tag.SNOW), 120);

		assertEquals("cave", engine.situationalId(), "it stays until it is done or the player takes it out");
		assertFalse(engine.isTracked("snow"));
	}

	@Test
	void whenNothingFitsTheSlotKeepsItsTrail() {
		stay(only(Situation.Tag.CAVE), 25);

		stay(EnumSet.noneOf(Situation.Tag.class), 60); // out in the open: no context
		assertEquals("cave", engine.situationalId());

		stay(only(Situation.Tag.DESERT), 60); // a context nothing is made for
		assertEquals("cave", engine.situationalId());
	}

	@Test
	void whenNothingFitsAndTheSlotIsEmptyItStaysEmpty() {
		stay(only(Situation.Tag.DESERT), 60);

		assertNull(engine.situationalId());
		assertEquals(List.of(), hud.situational);
	}

	@Test
	void everyContextOfTheTableFindsItsTrail() {
		assertFits(Situation.Tag.CAVE, "cave");
		assertFits(Situation.Tag.SNOW, "snow");
		assertFits(Situation.Tag.SEA, "sea");
		assertFits(Situation.Tag.VILLAGE, "village");
	}

	private void assertFits(Situation.Tag tag, String expected) {
		Questline best = engine.picker().bestFor(EnumSet.of(tag), null, obs(now));
		assertEquals(expected, best == null ? null : best.id(), tag.name());
	}

	@Test
	void theSlotIsTheFourthOfTheLogAndTheRegularOnesStayThree() {
		engine.autoStart(obs(now), hud);
		stay(only(Situation.Tag.CAVE), 25);

		assertEquals("cave", engine.situationalId());
		assertTrue(engine.trackedCount() <= QuestEngine.MAX_TRACKED);
		long regular = engine.trackedViews().stream().filter(view -> !view.situational()).count();
		assertTrue(regular <= 3);
	}

	@Test
	void aFinishedSituationalTrailFreesTheSlotForTheContextThatHolds() {
		stay(only(Situation.Tag.CAVE), 25);
		Observation mining = in(only(Situation.Tag.CAVE));
		for (int i = 0; i < 3; i++) {
			mining = in(only(Situation.Tag.CAVE));
			mining.now = ++now;
			mining.brokenBlocks.add("minecraft:deepslate");
			engine.tick(mining, hud);
		}
		Observation torch = in(only(Situation.Tag.CAVE));
		torch.now = ++now;
		torch.placedBlocks.add("minecraft:torch");
		engine.tick(torch, hud);
		assertTrue(learn.data().quests.completed.containsKey("cave"));
		assertNull(engine.situationalId());

		stay(only(Situation.Tag.SEA), 25);
		assertEquals("sea", engine.situationalId());
	}

	@Test
	void thePlayerTakingItOutKeepsItOutOfTheSlot() {
		stay(only(Situation.Tag.CAVE), 25);
		engine.untrack("cave");
		assertNull(engine.situationalId());

		stay(only(Situation.Tag.CAVE), 60);

		assertNull(engine.situationalId(), "the dismissed trail does not come straight back");
	}

	@Test
	void situationalTrailIsSavedAndComesBackAfterARestart() {
		stay(only(Situation.Tag.CAVE), 25);
		learn.store().flush();

		QuestEngine restarted = new QuestEngine(engine.trails(), PickerFixtures.catalog(),
				PickerFixtures.learner(dir.resolve("run")), LOG);

		assertEquals("cave", restarted.situationalId());
		assertTrue(restarted.isAutoTracked("cave"));
	}

	@Test
	void withTheSettingOffTheSlotStaysEmpty() {
		engine.setAutoPick(() -> false);

		stay(only(Situation.Tag.CAVE), 60);

		assertNull(engine.situationalId());
	}
}
