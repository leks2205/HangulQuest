package io.github.leks2205.hangulquest;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.data.ConceptLoader;
import io.github.leks2205.hangulquest.data.Word;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.QuestLoader;
import io.github.leks2205.hangulquest.quest.QuestStep;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.ContainerDiff;
import io.github.leks2205.hangulquest.sense.ContainerTakeFact;
import io.github.leks2205.hangulquest.sense.EntityFact;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.ToggleTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 3, A2: the verbs the player can really do, each with a goal or a condition the client can see:
 * 열다/닫다 (the door state changes), 들어가다/나가다/나오다 (the sky above flips), 내려가다 (a descent),
 * 꺼내다 (the slot tally), 듣다 (a playing jukebox or a struck note block), 눕다 (the bed pose), 기다리다 (time
 * in one place), 요리하다 (food in the furnace), 수영하다 (a swum distance, a sea with the water condition),
 * 앉다/타다 (the vehicle state) and 키우다 (a baby after a feeding). The sentences are in
 * action-sentences-gold.tsv too.
 */
class EnactableVerbsTest {
	private static final Catalog CATALOG = KoreanFixtures.catalog();
	private static final SentenceBuilder.Namer NAMER = KoreanFixtures.namer(CATALOG);
	private static final long T0 = 100_000L;

	private static Word word(String concept) {
		return CATALOG.byConceptId(concept).orElseThrow();
	}

	private static String clue(Goal goal, String concept, int level, Condition... conditions) {
		return SentenceBuilder.normal(goal, concept == null ? null : word(concept), List.of(conditions), level, false,
				NAMER);
	}

	private static String clue(Goal goal, String concept) {
		return clue(goal, concept, 1);
	}

	/** The clue of a step that teaches {@code nouns} (first) and the verb {@code verb}. */
	private static String clueTeaching(Goal goal, String noun, String verb, Condition... conditions) {
		return SentenceBuilder.normal(goal, noun == null ? null : word(noun), word(verb), List.of(conditions), 1,
				false, NAMER);
	}

	private static String ceremony(Goal goal, String concept, Condition... conditions) {
		return SentenceBuilder.ceremony(goal, concept == null ? null : word(concept), List.of(conditions),
				KoreanFixtures.registryNamer());
	}

	private static List<String> problems(Goal goal, String... teach) {
		List<String> problems = new ArrayList<>();
		QuestStep step = new QuestStep("s1", List.of(teach), goal, List.of(), null, null, "Do it.", null, null, null,
				null);
		GoalTypes.of(goal).validate(goal, new ValidationContext("t/s1", step, CATALOG, problems));
		return problems;
	}

	private static Observation at(long now) {
		Observation obs = new Observation();
		obs.now = now;
		return obs;
	}

	private static boolean tick(Goal goal, GoalEvaluator.Tracker tracker, Observation obs, Condition... conditions) {
		return GoalEvaluator.tick(goal, List.of(conditions), obs, tracker);
	}

	private static Goal parse(String json) {
		return QuestLoader.parseGoal(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
	}

	// ---------------------------------------------------------------------- 열다 / 닫다

	@Test
	void aDoorIsOpenedOrClosedAndTheSentenceSaysWhich() {
		Goal open = new Goal.UseBlock("minecraft:oak_door", "OPEN");
		Goal close = new Goal.UseBlock("minecraft:oak_door", "CLOSE");
		assertEquals("문을 열어요.", clue(open, "door"));
		assertEquals("문을 닫아요.", clue(close, "door"));
		assertEquals("문을 열었어요.", ceremony(open, "door"));
		assertEquals("문을 닫았어요.", ceremony(close, "door"));
		assertEquals(Verb.CLOSE, SentenceBuilder.verbFor(close));
		assertEquals(Verb.OPEN, SentenceBuilder.verbFor(open));
		// the two are two tasks
		assertFalse(GoalTypes.of(open).taskSignature(open).equals(GoalTypes.of(close).taskSignature(close)));
		// a plain use keeps the old key, so saved progress of old trails stays valid
		assertEquals("UseBlock:minecraft:oak_door", GoalEvaluator.key(new Goal.UseBlock("minecraft:oak_door")));
		assertEquals("UseBlock:minecraft:oak_door/CLOSE", GoalEvaluator.key(close));
	}

	@Test
	void theDoorMustReallyChangeItsState() {
		Goal close = new Goal.UseBlock("minecraft:oak_door", "CLOSE");
		Goal open = new Goal.UseBlock("minecraft:oak_door", "OPEN");
		Observation clicked = at(T0);
		clicked.usedBlocks.add("minecraft:oak_door"); // the click alone: the door may not have moved
		assertFalse(tick(close, new GoalEvaluator.Tracker(), clicked));
		assertFalse(tick(open, new GoalEvaluator.Tracker(), clicked));

		Observation opened = at(T0);
		opened.addOpened("minecraft:spruce_door"); // any door of the family
		assertTrue(tick(open, new GoalEvaluator.Tracker(), opened));
		assertFalse(tick(close, new GoalEvaluator.Tracker(), opened), "opening is not closing");

		Observation closed = at(T0);
		closed.addClosed("minecraft:oak_door");
		assertTrue(tick(close, new GoalEvaluator.Tracker(), closed));
		assertFalse(tick(open, new GoalEvaluator.Tracker(), closed));
	}

	@Test
	void onlyDoorsTrapdoorsAndGatesOpenByHand() {
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:oak_door", "OPEN"), "door"));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:oak_trapdoor", "CLOSE")));
		assertEquals(List.of(), problems(new Goal.UseBlock("minecraft:birch_fence_gate", "OPEN")));
		assertFalse(problems(new Goal.UseBlock("minecraft:iron_door", "OPEN")).isEmpty(), "iron doors need redstone");
		assertFalse(problems(new Goal.UseBlock("minecraft:chest", "OPEN")).isEmpty());
		assertFalse(problems(new Goal.UseBlock("minecraft:oak_door", "SHUT")).isEmpty());
	}

	@Test
	void useBlockStaysTheSameInJson() {
		Goal plain = parse("{\"type\":\"USE_BLOCK\",\"block\":\"minecraft:oak_door\"}");
		assertEquals(new Goal.UseBlock("minecraft:oak_door"), plain);
		assertFalse(GoalTypes.toJson(plain).has("change"), "an old file stays an old file");
		Goal close = parse("{\"type\":\"USE_BLOCK\",\"block\":\"minecraft:oak_door\",\"change\":\"close\"}");
		assertEquals(new Goal.UseBlock("minecraft:oak_door", "CLOSE"), close);
		assertEquals(close, GoalTypes.parse(GoalTypes.toJson(close)));
	}

	@Test
	void aClickIsAToggleOnlyWhenTheStateChanges() {
		ToggleTracker toggles = new ToggleTracker();
		BlockPos door = new BlockPos(3, 64, 3);
		Map<BlockPos, Boolean> world = new HashMap<>();
		world.put(door, false);
		List<String> opened = new ArrayList<>();
		List<String> closed = new ArrayList<>();
		toggles.clicked(door, "minecraft:oak_door", false, T0);
		toggles.clicked(door, "minecraft:oak_door", false, T0); // the off hand: the same click
		toggles.poll(world::get, T0 + 50, opened::add, closed::add);
		assertTrue(opened.isEmpty() && closed.isEmpty(), "nothing moved yet");
		world.put(door, true);
		toggles.poll(world::get, T0 + 100, opened::add, closed::add);
		assertEquals(List.of("minecraft:oak_door"), opened);
		assertTrue(closed.isEmpty());
		assertTrue(toggles.idle());

		// closing: the state before the click was open
		toggles.clicked(door, "minecraft:oak_door", true, T0 + 2000);
		world.put(door, false);
		toggles.poll(world::get, T0 + 2050, opened::add, closed::add);
		assertEquals(List.of("minecraft:oak_door"), closed);
		assertEquals(1, opened.size(), "not counted twice");
	}

	@Test
	void aClickThatChangesNothingTimesOutAndABlockWithoutAnOpenStateIsIgnored() {
		ToggleTracker toggles = new ToggleTracker();
		BlockPos iron = new BlockPos(1, 64, 1);
		List<String> any = new ArrayList<>();
		toggles.clicked(iron, "minecraft:iron_door", false, T0);
		toggles.poll(pos -> false, T0 + ToggleTracker.WINDOW_MS - 1, any::add, any::add);
		assertFalse(toggles.idle(), "still waiting for the server");
		toggles.poll(pos -> false, T0 + ToggleTracker.WINDOW_MS + 1, any::add, any::add);
		assertTrue(toggles.idle());
		assertTrue(any.isEmpty());

		toggles.clicked(iron, "minecraft:stone", null, T0); // no open property
		assertTrue(toggles.idle());
		toggles.clicked(iron, "minecraft:oak_door", false, T0);
		toggles.poll(pos -> null, T0 + 10, any::add, any::add); // the block is gone
		assertTrue(toggles.idle());
		assertTrue(any.isEmpty());
	}

	// ------------------------------------------------------------ 들어가다 / 나가다 / 나오다

	@Test
	void goingInAndOutHasItsSentences() {
		Goal in = new Goal.Transit("IN");
		Goal out = new Goal.Transit("OUT");
		assertEquals("안으로 들어가요.", clue(in, null));
		assertEquals("집 안으로 들어가요.", clue(in, "house"));
		assertEquals("밖으로 나가요.", clue(out, null));
		assertEquals("집 밖으로 나가요.", clue(out, "house"));
		assertEquals("집 안으로 들어갔어요.", ceremony(in, "house"));
		assertEquals("집에서 나왔어요.", ceremony(out, "house"));
		assertEquals("밖으로 나갔어요.", ceremony(out, null));
		// a word that is no roofed place is not spoken as one
		assertEquals("안으로 들어가요.", clue(in, "tree"));
		assertEquals("안으로 들어가요.", clue(in, "stone"));
		assertEquals("동굴 안으로 들어가요.", clue(in, "cave"));
		assertEquals(Verb.ENTER, SentenceBuilder.verbFor(in));
		assertEquals(Verb.LEAVE, SentenceBuilder.verbFor(out));
	}

	@Test
	void teachingComeOutMakesTheClueSayIt() {
		Goal out = new Goal.Transit("OUT");
		assertEquals("집에서 나와요.", clueTeaching(out, "house", "come_out"));
		assertEquals("밖으로 나와요.", clueTeaching(out, null, "come_out"));
		assertEquals("집 밖으로 나가요.", clueTeaching(out, "house", "go_out"));
		assertEquals(List.of(), problems(out, "come_out"));
		assertEquals(List.of(), problems(out, "go_out"));
		assertEquals(List.of(), problems(new Goal.Transit("IN"), "enter"));
	}

	private static Observation sky(long now, boolean open) {
		Observation obs = at(now);
		obs.canSeeSky = open;
		obs.roofed = !open;
		return obs;
	}

	@Test
	void walkingUnderARoofCountsAfterAMomentAndOnlyAfterBeingOutside() {
		Goal in = new Goal.Transit("IN");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (long now = T0; now <= T0 + 2000; now += 100) {
			assertFalse(tick(in, tracker, sky(now, true)), "outside is where it starts");
		}
		// a step through the doorway and back out is no passage
		assertFalse(tick(in, tracker, sky(T0 + 2100, false)));
		assertFalse(tick(in, tracker, sky(T0 + 2400, false)));
		assertFalse(tick(in, tracker, sky(T0 + 2500, true)));
		for (long now = T0 + 2600; now <= T0 + 3500; now += 100) {
			assertFalse(tick(in, tracker, sky(now, true)));
		}
		// inside for a second: now it counts
		assertFalse(tick(in, tracker, sky(T0 + 3600, false)));
		assertFalse(tick(in, tracker, sky(T0 + 4500, false)));
		assertTrue(tick(in, tracker, sky(T0 + 4600, false)));
	}

	@Test
	void standingInsideWhenTheStepBeginsIsNotAWayIn() {
		Goal in = new Goal.Transit("IN");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (long now = T0; now <= T0 + 5000; now += 100) {
			assertFalse(tick(in, tracker, sky(now, false)));
		}
		// going out and in again is
		for (long now = T0 + 5100; now <= T0 + 6200; now += 100) {
			assertFalse(tick(in, tracker, sky(now, true)));
		}
		boolean done = false;
		for (long now = T0 + 6300; now <= T0 + 7500 && !done; now += 100) {
			done = tick(in, tracker, sky(now, false));
		}
		assertTrue(done);
	}

	@Test
	void goingOutNeedsTheSkyToOpen() {
		Goal out = new Goal.Transit("OUT");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (long now = T0; now <= T0 + 1500; now += 100) {
			assertFalse(tick(out, tracker, sky(now, false)));
		}
		boolean done = false;
		for (long now = T0 + 1600; now <= T0 + 3000 && !done; now += 100) {
			done = tick(out, tracker, sky(now, true));
		}
		assertTrue(done);
		GoalEvaluator.Tracker other = new GoalEvaluator.Tracker();
		for (long now = T0; now <= T0 + 3000; now += 100) {
			assertFalse(tick(out, other, sky(now, true)), "already outside: nothing to leave");
		}
	}

	// -------------------------------------------------------------------------- 내려가다

	@Test
	void goingDownIsTheOppositeOfClimbing() {
		Goal down = new Goal.ReachHeight(-64, 40);
		assertEquals("아래로 내려가요.", clue(down, "go_down"));
		assertEquals(Verb.DESCEND, SentenceBuilder.verbFor(down));
		assertEquals("높이 40 이하로 내려가세요.", clue(down, "go_down", 2));
		assertEquals("아래로 내려갔어요.", ceremony(down, "go_down"));
		// a goal above sea level still climbs, the open goal too
		assertEquals(Verb.CLIMB, SentenceBuilder.verbFor(new Goal.ReachHeight(80, 320)));
		assertEquals("땅 위로 올라가요.", clue(new Goal.ReachHeight(0, 320), "climb"));
		Observation low = at(T0);
		low.y = 30;
		assertTrue(tick(down, new GoalEvaluator.Tracker(), low));
		Observation high = at(T0);
		high.y = 70;
		assertFalse(tick(down, new GoalEvaluator.Tracker(), high));
		assertEquals(List.of(), problems(down, "go_down"));
		assertEquals(List.of(), problems(down, "climb"), "any word fits a height");
	}

	// ---------------------------------------------------------------------------- 꺼내다

	@Test
	void takingSomethingOutOfAChestHasItsSentence() {
		Goal take = new Goal.TakeOut("minecraft:apple", "minecraft:chest", 1);
		assertEquals("상자에서 사과를 꺼내요.", clue(take, null));
		assertEquals("상자에서 사과를 꺼내요.", clue(take, "apple"));
		assertEquals("상자에서 사과를 꺼냈어요.", ceremony(take, "apple"));
		assertEquals("상자에서 빵을 세 개 꺼내요.", clue(new Goal.TakeOut("minecraft:bread", "minecraft:chest", 3), null));
		assertEquals("상자에서 빵을 꺼내요.", SentenceBuilder.hint(new Goal.TakeOut("minecraft:bread", "minecraft:chest", 3),
				null, List.of(), NAMER));
		assertEquals(Verb.TAKE_OUT, SentenceBuilder.verbFor(take));
		assertEquals(List.of(), problems(take, "apple"));
		assertEquals(List.of(), problems(take, "chest"));
		assertEquals(List.of(), problems(take, "take_out"));
		assertFalse(problems(take, "stone").isEmpty());
		assertFalse(problems(new Goal.TakeOut("minecraft:apple", "minecraft:chest", 0), "apple").isEmpty());
	}

	@Test
	void takingItemsOutOfTheRightContainerCounts() {
		Goal take = new Goal.TakeOut("minecraft:apple", "minecraft:chest", 3);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation first = at(T0);
		first.addTakenOut(new ContainerTakeFact("minecraft:apple", 2, "minecraft:chest", BlockPos.ZERO));
		assertFalse(tick(take, tracker, first));
		Observation other = at(T0 + 50);
		other.addTakenOut(new ContainerTakeFact("minecraft:apple", 1, "minecraft:barrel", BlockPos.ZERO));
		other.addTakenOut(new ContainerTakeFact("minecraft:bread", 5, "minecraft:chest", BlockPos.ZERO));
		assertFalse(tick(take, tracker, other), "another container, another item");
		assertEquals(2, tracker.count);
		Observation last = at(T0 + 100);
		last.addTakenOut(new ContainerTakeFact("minecraft:apple", 1, "minecraft:trapped_chest", BlockPos.ZERO));
		assertTrue(tick(take, tracker, last), "a trapped chest is a chest");
	}

	private static Map<String, Integer> tally(Object... pairs) {
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	@Test
	void theSlotTallySeesWhatTheContainerLostAndThePlayerGained() {
		ContainerDiff diff = new ContainerDiff();
		diff.open(tally("minecraft:apple", 5), tally());
		diff.update(tally("minecraft:apple", 3), tally("minecraft:apple", 2));
		assertEquals(List.of(new ContainerDiff.Put("minecraft:apple", 2)), diff.takes());
		assertTrue(diff.takes() == diff.takes());
		diff.update(tally("minecraft:apple", 3), tally("minecraft:apple", 2));
		assertTrue(diff.takes().isEmpty(), "the next tick has nothing new");

		// a hopper empties the chest: the player gained nothing
		ContainerDiff hopper = new ContainerDiff();
		hopper.open(tally("minecraft:apple", 5), tally());
		hopper.update(tally("minecraft:apple", 1), tally());
		assertTrue(hopper.takes().isEmpty());

		// putting an apple in and taking the same one back is not a take
		ContainerDiff back = new ContainerDiff();
		back.open(tally(), tally("minecraft:apple", 1));
		back.update(tally("minecraft:apple", 1), tally());
		assertTrue(back.takes().isEmpty());
		back.update(tally(), tally("minecraft:apple", 1));
		assertTrue(back.takes().isEmpty(), "the player only took back what was just put in");
		// but a second take of something else is
		ContainerDiff more = new ContainerDiff();
		more.open(tally("minecraft:bread", 2), tally());
		more.update(tally("minecraft:bread", 1), tally("minecraft:bread", 1));
		assertEquals(List.of(new ContainerDiff.Put("minecraft:bread", 1)), more.takes());
	}

	// ------------------------------------------------------------------------------ 듣다

	@Test
	void listeningHasASentenceForAJukeboxAndANoteBlock() {
		Goal jukebox = new Goal.Hear("minecraft:jukebox", 10, 3000);
		Goal note = new Goal.Hear("minecraft:note_block", 10, 3000);
		assertEquals("주크박스 근처에서 노래를 들어요.", clue(jukebox, null));
		assertEquals("주크박스 근처에서 노래를 들었어요.", ceremony(jukebox, null));
		assertEquals("소리 블록의 소리를 들어요.", clue(note, null));
		assertEquals("소리 블록의 소리를 들었어요.", ceremony(note, null));
		assertEquals(Verb.HEAR, SentenceBuilder.verbFor(jukebox));
		assertEquals(List.of(), problems(jukebox, "jukebox"));
		assertEquals(List.of(), problems(jukebox, "hear"));
		assertEquals(List.of(), problems(note, "note_block"));
		assertFalse(problems(new Goal.Hear("minecraft:bell", 10, 3000), "bell").isEmpty());
		assertFalse(problems(new Goal.Hear("minecraft:jukebox", 40, 3000), "jukebox").isEmpty());
	}

	@Test
	void aPlayingJukeboxIsHeardFromNearEnoughForAMoment() {
		Goal jukebox = new Goal.Hear("minecraft:jukebox", 10, 3000);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = at(T0);
		assertFalse(tick(jukebox, tracker, obs));
		assertTrue(obs.musicScanWanted, "the sensor scans for a jukebox only while a step listens");
		boolean done = false;
		for (int i = 1; i <= 80 && !done; i++) {
			Observation near = at(T0 + i * 50L);
			near.jukeboxDistance = 6.5;
			done = tick(jukebox, tracker, near);
			assertFalse(done && i < 55, "three seconds are three seconds");
		}
		assertTrue(done);

		GoalEvaluator.Tracker far = new GoalEvaluator.Tracker();
		for (int i = 1; i <= 200; i++) {
			Observation away = at(T0 + i * 50L);
			away.jukeboxDistance = 18.0;
			assertFalse(tick(jukebox, far, away));
			Observation none = at(T0 + i * 50L);
			none.jukeboxDistance = -1;
			assertFalse(tick(jukebox, far, none));
		}
	}

	@Test
	void aStruckNoteBlockIsHeardAtOnce() {
		Goal note = new Goal.Hear("minecraft:note_block", 10, 3000);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		Observation obs = at(T0);
		assertFalse(tick(note, tracker, obs));
		assertFalse(obs.musicScanWanted, "no scan for a note block");
		Observation struck = at(T0 + 50);
		struck.usedBlocks.add("minecraft:note_block");
		assertTrue(tick(note, tracker, struck));
	}

	// ------------------------------------------------------------------------------ 눕다

	@Test
	void lyingDownIsInBedWithoutWaitingForTheNight() {
		Goal lie = new Goal.LieDown("minecraft:red_bed", 2000);
		assertEquals("침대에 누워요.", clue(lie, "bed"));
		assertEquals("침대에 누웠어요.", ceremony(lie, "bed"));
		assertEquals(Verb.LIE, SentenceBuilder.verbFor(lie));
		assertEquals(List.of(), problems(lie, "bed"));
		assertEquals(List.of(), problems(lie, "lie_down"));
		assertFalse(problems(new Goal.LieDown("minecraft:stone", 2000)).isEmpty());

		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i <= 60 && !done; i++) {
			Observation obs = at(T0 + i * 50L);
			obs.sleeping = true;
			obs.sleepingBed = "minecraft:white_bed";
			done = tick(lie, tracker, obs);
			assertFalse(done && i < 40, "two seconds in bed");
		}
		assertTrue(done);
		GoalEvaluator.Tracker standing = new GoalEvaluator.Tracker();
		for (int i = 0; i <= 100; i++) {
			assertFalse(tick(lie, standing, at(T0 + i * 50L)));
		}
	}

	@Test
	void theNightIsExcusedOnceTheServerHasSkippedToTheMorning() {
		Goal lie = new Goal.LieDown("minecraft:red_bed", 500);
		Condition night = new Condition.Time(Condition.Phase.NIGHT);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i <= 40 && !done; i++) {
			Observation obs = at(T0 + i * 50L);
			obs.clockTime = 1000L; // morning already
			obs.sleeping = true;
			obs.sleepingBed = "minecraft:red_bed";
			done = tick(lie, tracker, obs, night);
		}
		assertTrue(done);
		GoalEvaluator.Tracker awake = new GoalEvaluator.Tracker();
		for (int i = 0; i <= 40; i++) {
			Observation obs = at(T0 + i * 50L);
			obs.clockTime = 1000L;
			assertFalse(tick(lie, awake, obs, night));
		}
	}

	// ---------------------------------------------------------------------------- 기다리다

	@Test
	void waitingHasItsSentences() {
		Goal brief = new Goal.Wait(5, null);
		Goal ten = new Goal.Wait(10, null);
		Goal night = new Goal.Wait(5, "NIGHT");
		assertEquals("잠깐 기다려요.", clue(brief, null));
		assertEquals("10초 동안 기다리세요.", clue(ten, null, 2));
		assertEquals("10초 동안 기다렸어요.", ceremony(ten, null));
		assertEquals("밤까지 기다려요.", clue(night, null));
		assertEquals("낮까지 기다려요.", clue(new Goal.Wait(5, "DAY"), null));
		assertEquals("밤까지 기다렸어요.", ceremony(night, null));
		assertEquals("잠깐 기다려요.", SentenceBuilder.hint(ten, null, List.of(), NAMER));
		assertEquals(Verb.WAIT, SentenceBuilder.verbFor(brief));
		assertEquals(List.of(), problems(brief, "wait"));
		assertFalse(problems(new Goal.Wait(0, null)).isEmpty());
		assertFalse(problems(new Goal.Wait(5, "DUSK")).isEmpty());
		// near something: the condition speaks it
		assertEquals("모닥불 근처에서 잠깐 기다려요.", clue(brief, null, 1, new Condition.NearBlock("minecraft:campfire", 5)));
	}

	private static Observation standing(long now, double x, double z) {
		Observation obs = at(now);
		obs.position = new Vec3(x, 64, z);
		return obs;
	}

	@Test
	void waitingMeansStayingWhereYouAre() {
		Goal wait = new Goal.Wait(2, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		int ticks = 0;
		for (int i = 0; i <= 80 && !done; i++) {
			done = tick(wait, tracker, standing(T0 + i * 50L, 10.0 + (i % 2) * 0.5, 10.0));
			ticks = i;
		}
		assertTrue(done);
		assertTrue(ticks >= 40 && ticks <= 42, "two seconds, not less: " + ticks);

		// walking away starts again
		GoalEvaluator.Tracker walker = new GoalEvaluator.Tracker();
		for (int i = 0; i <= 120; i++) {
			assertFalse(tick(wait, walker, standing(T0 + i * 50L, 10.0 + i * 0.4, 10.0)), "tick " + i);
		}
	}

	@Test
	void waitingUntilTheNightNeedsTheNight() {
		Goal wait = new Goal.Wait(1, "NIGHT");
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		for (int i = 0; i <= 100; i++) {
			Observation day = standing(T0 + i * 50L, 5, 5);
			day.clockTime = 6000L;
			assertFalse(tick(wait, tracker, day));
		}
		Observation night = standing(T0 + 101 * 50L, 5, 5);
		night.clockTime = 14000L;
		assertTrue(tick(wait, tracker, night), "the long wait is already done when the night comes");
	}

	@Test
	void aPauseDoesNotCountAsWaiting() {
		Goal wait = new Goal.Wait(5, null);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(wait, tracker, standing(T0, 1, 1)));
		assertFalse(tick(wait, tracker, standing(T0 + 60_000L, 1, 1)), "a minute with the game paused");
		assertTrue(tracker.dwellMs <= 250);
	}

	// ----------------------------------------------------------------------------- 요리하다

	@Test
	void teachingCookMakesFoodInTheFurnaceCooked() {
		Goal chicken = new Goal.Smelt("minecraft:chicken", "minecraft:cooked_chicken", 1);
		assertEquals("익히지 않은 닭고기를 구워요.", clue(chicken, null));
		assertEquals("익히지 않은 닭고기를 구워요.", clue(chicken, "chicken_meat"));
		assertEquals("닭고기를 요리해요.", clueTeaching(chicken, "chicken_meat", "cook"));
		assertEquals("닭고기를 요리했어요.", SentenceBuilder.ceremony(chicken, word("chicken_meat"), word("cook"),
				List.of(), KoreanFixtures.registryNamer()));
		// the verb alone: the product is named
		assertEquals("닭고기를 요리해요.", clueTeaching(chicken, null, "cook"));
		assertEquals(List.of(), problems(chicken, "cook"));
		// iron is not food: it stays 만들어요 in the furnace even when the step teaches cooking
		Goal iron = new Goal.Smelt("minecraft:raw_iron", "minecraft:iron_ingot", 1);
		assertEquals("화로에서 철을 만들어요.", clueTeaching(iron, null, "cook"));
		assertFalse(problems(iron, "cook").isEmpty(), "요리하다 does not describe an ingot");
	}

	@Test
	void theGeneratedCookClueGrammarHasNoPlaceParticle() {
		Goal chicken = new Goal.Smelt("minecraft:chicken", "minecraft:cooked_chicken", 1);
		List<String> ids = SentenceBuilder.grammar(chicken, word("chicken_meat"), word("cook"), List.of(), 1, false,
				NAMER);
		assertFalse(ids.contains("L2.eseo_activity"), ids.toString());
	}

	// ----------------------------------------------------------------------------- 수영하다

	@Test
	void swimmingInTheSeaReadsAsSwimmingNotAsGoingThere() {
		Goal sea = new Goal.ReachBiome("#minecraft:is_ocean", 1000);
		Condition water = new Condition.InWater();
		assertEquals("바다에서 수영해요.", clue(sea, "ocean", 1, water));
		assertEquals("바다로 가요.", clue(sea, "ocean"));
		assertEquals("바다에서 수영했어요.", ceremony(sea, "ocean", water));
		assertEquals(Verb.SWIM, SentenceBuilder.verbOf(sea, null, List.of(water)));
		// the swimmer is still in the water: the condition is spoken by the sentence, not twice
		assertFalse(clue(sea, "ocean", 1, water).contains("물속"), clue(sea, "ocean", 1, water));
		assertEquals("강에서 수영해요.", clue(new Goal.ReachBiome("#minecraft:is_river", 1000), "river", 1, water));
		// a land biome keeps the water phrase in front
		assertEquals("물속에서 숲으로 가요.", clue(new Goal.ReachBiome("minecraft:forest", 1000), "forest", 1, water));
		// and the condition is checked all the same
		Observation dry = at(T0);
		dry.biomeTags.add("minecraft:is_ocean");
		assertFalse(tick(sea, new GoalEvaluator.Tracker(), dry, water));
	}

	@Test
	void anAuthoredSwimClueDoesNotGetTheWaterPhraseInFront() {
		Goal sea = new Goal.ReachBiome("#minecraft:is_ocean", 1000);
		Condition water = new Condition.InWater();
		assertEquals("바다에서 수영해요.", SentenceBuilder.authored(sea, "바다에서 수영해요.", List.of(water), word("ocean"),
				NAMER, 1));
		// a clue that names the water in its own words is left alone too
		assertEquals("물속을 헤엄쳐요.", SentenceBuilder.authored(new Goal.Swim(10), "물속을 헤엄쳐요.", List.of(water), null,
				NAMER, 1));
		assertEquals("물속에서 바다로 가요.", SentenceBuilder.authored(new Goal.ReachBiome("minecraft:forest", 1000),
				"바다로 가요.", List.of(water), null, NAMER, 1).replace("바다로 가요.", "바다로 가요."));
	}

	@Test
	void swimmingADistanceHasItsSentence() {
		Goal swim = new Goal.Swim(20);
		assertEquals("물에서 수영해요.", clue(swim, "swim"));
		assertEquals("물에서 20블록 수영하세요.", clue(swim, "swim", 2));
		assertEquals("물에서 수영해요.", SentenceBuilder.hint(swim, word("swim"), List.of(), NAMER));
		assertEquals("물에서 수영했어요.", ceremony(swim, "swim"));
		assertEquals(Verb.SWIM, SentenceBuilder.verbFor(swim));
		assertEquals(List.of(), problems(swim, "swim"));
		assertFalse(problems(new Goal.Swim(0)).isEmpty());
	}

	@Test
	void onlyTheWaterSwamCounts() {
		Goal swim = new Goal.Swim(10);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i < 40 && !done; i++) {
			Observation obs = standing(T0 + i * 50L, i, 0);
			obs.inWater = i >= 5 && i < 30; // a few strides on the beach, then water
			done = tick(swim, tracker, obs);
			if (i < 14) {
				assertFalse(done, "tick " + i);
			}
		}
		assertTrue(done);
		// a teleport inside the water is not a swim
		GoalEvaluator.Tracker jumper = new GoalEvaluator.Tracker();
		Observation a = standing(T0, 0, 0);
		a.inWater = true;
		Observation b = standing(T0 + 50, 500, 0);
		b.inWater = true;
		assertFalse(tick(swim, jumper, a));
		assertFalse(tick(swim, jumper, b));
		assertTrue(jumper.distance < 1.0);
		// out of the water, nothing grows
		GoalEvaluator.Tracker dry = new GoalEvaluator.Tracker();
		for (int i = 0; i < 100; i++) {
			assertFalse(tick(swim, dry, standing(T0 + i * 50L, i, 0)));
		}
	}

	// ------------------------------------------------------------------------- 앉다 / 타다

	@Test
	void aBoatIsSatInAndAHorseIsRidden() {
		Goal boat = new Goal.Mount("minecraft:oak_boat", 1500);
		Goal horse = new Goal.Mount("minecraft:horse", 1500);
		assertEquals("배에 앉아요.", clue(boat, "boat"));
		assertEquals("가문비나무 보트에 앉아요.", clue(new Goal.Mount("minecraft:spruce_boat", 1500), null));
		assertEquals("말을 타요.", clue(horse, "horse"));
		assertEquals("말을 탔어요.", ceremony(horse, "horse"));
		assertEquals("배에 앉았어요.", ceremony(boat, "boat"));
		assertEquals(Verb.SIT, SentenceBuilder.verbFor(boat));
		assertEquals(Verb.SIT, SentenceBuilder.verbFor(new Goal.Mount("minecraft:minecart", 1500)));
		assertEquals(Verb.RIDE, SentenceBuilder.verbFor(horse));
		assertEquals(Verb.RIDE, SentenceBuilder.verbFor(new Goal.Mount("minecraft:pig", 1500)));
		// teaching 타다 turns the seat into a ride
		assertEquals("배를 타요.", clueTeaching(boat, "boat", "ride"));
		assertEquals("말을 타요.", clueTeaching(horse, "horse", "ride"));
		assertEquals("말을 타요.", clueTeaching(horse, "horse", "sit"), "a horse is never sat in");
	}

	@Test
	void theWordForABoatCoversEveryBoat() {
		Goal spruce = new Goal.Mount("minecraft:spruce_boat", 1500);
		assertEquals(List.of(), problems(spruce, "boat"));
		assertEquals(List.of(), problems(new Goal.Mount("minecraft:horse", 1500), "horse"));
		assertFalse(problems(new Goal.Mount("minecraft:horse", 1500), "boat").isEmpty());
		assertEquals(List.of(), problems(new Goal.Mount("minecraft:oak_boat", 1500), "sit"));
		assertEquals(List.of(), problems(new Goal.Mount("minecraft:oak_boat", 1500), "ride"));
	}

	@Test
	void theVehicleStateDecides() {
		Goal boat = new Goal.Mount("minecraft:oak_boat", 1500);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		boolean done = false;
		for (int i = 0; i <= 60 && !done; i++) {
			Observation obs = at(T0 + i * 50L);
			obs.vehicle = "minecraft:birch_boat"; // any boat is a boat
			done = tick(boat, tracker, obs);
			assertFalse(done && i < 28);
		}
		assertTrue(done);
		GoalEvaluator.Tracker onFoot = new GoalEvaluator.Tracker();
		GoalEvaluator.Tracker inCart = new GoalEvaluator.Tracker();
		for (int i = 0; i <= 100; i++) {
			assertFalse(tick(boat, onFoot, at(T0 + i * 50L)));
			Observation cart = at(T0 + i * 50L);
			cart.vehicle = "minecraft:minecart";
			assertFalse(tick(boat, inCart, cart));
		}
	}

	// ------------------------------------------------------------------------------ 키우다

	@Test
	void raisingAnimalsHasItsSentence() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		assertEquals("소를 키워요.", clue(cow, "cow"));
		assertEquals("소를 두 마리 키워요.", clue(new Goal.Breed("minecraft:cow", 2), "cow"));
		assertEquals("소를 키웠어요.", ceremony(cow, "cow"));
		assertEquals(Verb.RAISE, SentenceBuilder.verbFor(cow));
		assertEquals(List.of(), problems(cow, "cow"));
		assertEquals(List.of(), problems(cow, "raise"));
		assertFalse(problems(cow, "pig").isEmpty());
	}

	private static Observation withBaby(long now, String type, UUID id) {
		Observation obs = at(now);
		obs.addEntityPosition(new EntityFact(type, id, 1, 64, 1, 0F, 0F, true));
		return obs;
	}

	@Test
	void aBabyAfterAFeedingIsTheResultOfIt() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		UUID calf = UUID.randomUUID();
		UUID before = UUID.randomUUID();
		assertFalse(tick(cow, tracker, withBaby(T0, "minecraft:cow", before)), "a calf that was there before");
		Observation fed = at(T0 + 1000);
		fed.addGave(new GiveFact("minecraft:wheat", 1, "minecraft:cow", UUID.randomUUID(), GiveFact.Method.FEED));
		assertFalse(tick(cow, tracker, fed));
		assertFalse(tick(cow, tracker, withBaby(T0 + 2000, "minecraft:cow", before)), "still the old calf");
		assertFalse(tick(cow, tracker, withBaby(T0 + 20_000, "minecraft:pig", UUID.randomUUID())), "another kind");
		assertTrue(tick(cow, tracker, withBaby(T0 + 30_000, "minecraft:cow", calf)));
	}

	@Test
	void aBabyThatComesLongAfterTheFeedingOrWithoutOneIsNotCounted() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		GoalEvaluator.Tracker lonely = new GoalEvaluator.Tracker();
		assertFalse(tick(cow, lonely, at(T0)));
		assertFalse(tick(cow, lonely, withBaby(T0 + 1000, "minecraft:cow", UUID.randomUUID())),
				"no feeding, so the calf was simply there");
		GoalEvaluator.Tracker late = new GoalEvaluator.Tracker();
		Observation fed = at(T0);
		fed.interactedEntities.add("minecraft:cow");
		assertFalse(tick(cow, late, fed));
		assertFalse(tick(cow, late, withBaby(T0 + 300_000L, "minecraft:cow", UUID.randomUUID())));
	}

	@Test
	void creativeModeFeedsWithoutUsingUpTheFoodSoTheInteractionCounts() {
		Goal cow = new Goal.Breed("minecraft:cow", 1);
		GoalEvaluator.Tracker tracker = new GoalEvaluator.Tracker();
		assertFalse(tick(cow, tracker, at(T0)));
		Observation click = at(T0 + 100);
		click.interactedEntities.add("minecraft:cow");
		assertFalse(tick(cow, tracker, click));
		assertTrue(tick(cow, tracker, withBaby(T0 + 5000, "minecraft:cow", UUID.randomUUID())));
	}

	// ------------------------------------------------------------------ the registry and the JSON

	@Test
	void everyNewGoalReadsAndWritesItsOwnJson() {
		List<Goal> goals = List.of(new Goal.UseBlock("minecraft:oak_door", "CLOSE"),
				new Goal.TakeOut("minecraft:apple", "minecraft:chest", 2), new Goal.Transit("OUT"),
				new Goal.Hear("minecraft:jukebox", 8, 2500), new Goal.LieDown("minecraft:red_bed", 1500),
				new Goal.Wait(12, "NIGHT"), new Goal.Breed("minecraft:cow", 2), new Goal.Mount("minecraft:horse", 2000),
				new Goal.Swim(30));
		for (Goal goal : goals) {
			JsonObject json = GoalTypes.toJson(goal);
			assertEquals(goal, GoalTypes.parse(json), json.toString());
			assertNotNull(GoalTypes.of(goal).fields());
		}
	}

	@Test
	void everyNewVerbConceptCanBeTaughtOnItsGoal() {
		Map<String, Goal> goals = new HashMap<>();
		goals.put("open", new Goal.UseBlock("minecraft:oak_door", "OPEN"));
		goals.put("close", new Goal.UseBlock("minecraft:oak_door", "CLOSE"));
		goals.put("enter", new Goal.Transit("IN"));
		goals.put("go_out", new Goal.Transit("OUT"));
		goals.put("go_down", new Goal.ReachHeight(-64, 40));
		goals.put("take_out", new Goal.TakeOut("minecraft:apple", "minecraft:chest", 1));
		goals.put("hear", new Goal.Hear("minecraft:jukebox", 10, 3000));
		goals.put("lie_down", new Goal.LieDown("minecraft:red_bed", 2000));
		goals.put("wait", new Goal.Wait(5, null));
		goals.put("cook", new Goal.Smelt("minecraft:chicken", "minecraft:cooked_chicken", 1));
		goals.put("swim", new Goal.Swim(10));
		goals.put("sit", new Goal.Mount("minecraft:oak_boat", 1500));
		goals.put("ride", new Goal.Mount("minecraft:horse", 1500));
		goals.put("raise", new Goal.Breed("minecraft:cow", 1));
		Map<String, Concept> concepts = new HashMap<>();
		ConceptLoader.load().forEach(concept -> concepts.put(concept.id, concept));
		for (Map.Entry<String, Goal> entry : goals.entrySet()) {
			assertTrue(concepts.containsKey(entry.getKey()), "no concept " + entry.getKey());
			assertEquals(List.of(), problems(entry.getValue(), entry.getKey()), entry.getKey());
		}
	}
}
