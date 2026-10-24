package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.learn.LearningService;
import io.github.leks2205.hangulquest.learn.ProgressData;
import io.github.leks2205.hangulquest.sense.GiveFact;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.villager.Personality;
import io.github.leks2205.hangulquest.villager.Region;
import io.github.leks2205.hangulquest.villager.Request;
import io.github.leks2205.hangulquest.villager.RequestSet;
import io.github.leks2205.hangulquest.villager.Requests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.leks2205.hangulquest.quest.PickerFixtures.LOG;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.obs;
import static io.github.leks2205.hangulquest.quest.PickerFixtures.trail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Villager requests in the quest log: they share its slots, push out only the game's own picks, and advance only there. */
class RequestSlotsTest {
	private static final String A = new UUID(1, 1).toString();
	private static final String B = new UUID(2, 2).toString();
	private static final String C = new UUID(3, 3).toString();
	private static final String D = new UUID(4, 4).toString();

	@TempDir
	Path dir;

	private LearningService learn;
	private QuestEngine engine;
	private Requests requests;
	private int dirty;

	static Request give(String id, String item, int count) {
		Map<Personality, Map<Request.Part, Request.Line>> lines = new EnumMap<>(Personality.class);
		return new Request(id, 1, Request.Kind.GIVE, List.of(Region.PLAINS), List.of(), List.of(),
				new Goal.Give(item, "minecraft:villager", "DROP_NEAR", count), null, 0, List.of("concept:bread"),
				new Request.Line("네, 좋아요!", null), new Request.Line("아니요.", null), lines);
	}

	@BeforeEach
	void setUp() {
		learn = PickerFixtures.learner(dir.resolve("run"));
		engine = new QuestEngine(List.of(trail("wood", 10, "나무"), trail("food", 20, "음식"), trail("pig", 30, "동물"),
				trail("stone", 40, "돌"), trail("fish", 50, "물고기")), PickerFixtures.catalog(), learn, LOG);
		RequestSet set = RequestSet.of(List.of(give("req_a", "minecraft:bread", 1), give("req_b", "minecraft:wheat", 3),
				give("req_c", "minecraft:carrot", 1), give("req_d", "minecraft:potato", 1)));
		requests = new Requests(learn.data(), set, () -> dirty++, id -> null);
		requests.attach(new Requests.Log() {
			@Override
			public boolean makeRoom(long now) {
				return engine.makeRoomForExtra(now);
			}

			@Override
			public String clue(QuestStep step, int koreanLevel) {
				return engine.clueOf(step, koreanLevel);
			}

			@Override
			public String status(QuestStep step, GoalEvaluator.Tracker tracker) {
				return engine.statusOf(step, tracker);
			}
		});
		engine.setExtra(requests);
	}

	private ProgressData.OpenRequest accept(String request, String villager) {
		return requests.accept(requests.set().get(request), villager, "민준", 1_000L);
	}

	private static Observation gave(long now, String item, int count, String villager) {
		Observation obs = obs(now);
		obs.addGave(new GiveFact(item, count, "minecraft:villager", UUID.fromString(villager), GiveFact.Method.DROP_NEAR));
		return obs;
	}

	@Test
	void anAcceptedRequestIsARowOfTheQuestLogWithTheVillagersName() {
		ProgressData.OpenRequest open = accept("req_a", A);
		assertTrue(open.tracked);
		List<QuestEngine.TrackView> views = engine.trackedViews();
		assertEquals(1, views.size());
		assertEquals(Requests.ROW_PREFIX + A, views.get(0).id());
		assertEquals("민준의 부탁", views.get(0).titleKo());
		assertTrue(views.get(0).clueKo().contains("떨어뜨려요"), views.get(0).clueKo());
		assertTrue(views.get(0).titleEn().contains("민준"));
	}

	@Test
	void aRequestTakesASlotSoTheLogHoldsFewerTrails() {
		accept("req_a", A);
		assertTrue(engine.setTracked("wood", true));
		assertTrue(engine.setTracked("food", true));
		assertTrue(engine.setTracked("pig", true));
		assertFalse(engine.autoTrack("stone", false, 2_000L), "four slots: three trails and the request");
		assertEquals(4, engine.trackedViews().size());
	}

	@Test
	void aFullLogLosesItsOldestAutoPickButNeverATrailThePlayerChose() {
		assertTrue(engine.setTracked("wood", true));
		assertTrue(engine.autoTrack("food", false, 1_000L));
		assertTrue(engine.autoTrack("pig", false, 1_100L));
		assertTrue(engine.autoTrack("stone", true, 1_200L));
		ProgressData.OpenRequest open = accept("req_a", A);
		assertTrue(open.tracked);
		assertFalse(engine.isTracked("food"), "the oldest of the game's picks made room");
		assertTrue(engine.isTracked("wood"));
		assertTrue(engine.isTracked("pig"));
		assertTrue(engine.hasProgress("food"), "it keeps its state, it is only out of the log");
	}

	@Test
	void whenThePlayerChoseEveryTrailTheRequestWaitsUntracked() {
		for (String trail : List.of("wood", "food", "pig", "stone")) {
			assertTrue(engine.setTracked(trail, true));
		}
		ProgressData.OpenRequest open = accept("req_a", A);
		assertNotNull(open);
		assertFalse(open.tracked);
		assertEquals(4, engine.trackedViews().size(), "no request row");
		assertFalse(requests.setTracked(A, true, 2_000L), "no room until the player makes some");
		engine.setTracked("stone", false);
		assertTrue(requests.setTracked(A, true, 3_000L));
		assertTrue(learn.data().requests.open.get(0).tracked);
	}

	@Test
	void anUntrackedRequestDoesNotAdvance() {
		accept("req_a", A);
		requests.setTracked(A, false, 1_000L);
		requests.tick(gave(2_000L, "minecraft:bread", 1, A), null);
		assertFalse(requests.openFor(A).done);
		requests.setTracked(A, true, 3_000L);
		List<String> done = new ArrayList<>();
		requests.tick(gave(4_000L, "minecraft:bread", 1, A), (open, request) -> done.add(request.id()));
		assertTrue(requests.openFor(A).done);
		assertEquals(List.of("req_a"), done);
		assertEquals("민준에게 말해요", engine.trackedViews().get(0).status());
	}

	@Test
	void onlyAGiftToTheVillagerWhoAskedCounts() {
		accept("req_b", A);
		requests.tick(gave(2_000L, "minecraft:wheat", 2, B), null);
		assertEquals(0, requests.openFor(A).progress, "another villager took it");
		requests.tick(gave(3_000L, "minecraft:wheat", 2, A), null);
		assertEquals(2, requests.openFor(A).progress);
		assertFalse(requests.openFor(A).done);
		requests.tick(gave(4_000L, "minecraft:wheat", 1, A), null);
		assertTrue(requests.openFor(A).done);
	}

	@Test
	void oneRequestPerVillagerThreeInAllAndHandingInCountsIt() {
		assertNotNull(accept("req_a", A));
		assertNull(accept("req_b", A), "one per villager");
		assertNotNull(accept("req_b", B));
		assertNotNull(accept("req_c", C));
		assertNull(accept("req_d", D), "three at once");
		assertFalse(requests.canAccept(D));
		assertTrue(requests.abandon(B));
		assertTrue(requests.canAccept(D));
		assertEquals(2, engine.trackedViews().size());

		requests.tick(gave(2_000L, "minecraft:bread", 1, A), null);
		assertTrue(requests.ready(requests.openFor(A), null, 0));
		assertEquals("req_a", requests.handIn(A).id());
		assertNull(requests.openFor(A));
		assertEquals(1, requests.finishedCount("req_a"));
		assertTrue(dirty > 0);
	}

	@Test
	void theOfferPrefersRequestsNotDoneYetAndStaysTheSameAllDay() {
		UUID villager = UUID.fromString(A);
		Request first = requests.choose(Personality.LAZY, Region.PLAINS, null, 1, villager, 5);
		assertEquals(first, requests.choose(Personality.LAZY, Region.PLAINS, null, 1, villager, 5));
		learn.data().requests.finished.put("req_a", 1);
		learn.data().requests.finished.put("req_b", 1);
		learn.data().requests.finished.put("req_c", 1);
		assertEquals("req_d", requests.choose(Personality.LAZY, Region.PLAINS, null, 1, villager, 5).id());
		accept("req_d", B);
		assertTrue(!requests.choose(Personality.LAZY, Region.PLAINS, null, 1, villager, 5).id().equals("req_d"),
				"a request open elsewhere is not offered twice");
		assertNull(requests.choose(Personality.LAZY, Region.DESERT, null, 1, villager, 5));
	}

	@Test
	void openRequestsSurviveASaveAndReload() {
		accept("req_b", A);
		requests.tick(gave(2_000L, "minecraft:wheat", 2, A), null);
		learn.store().markDirty();
		learn.store().flush();
		LearningService reloaded = PickerFixtures.learner(dir.resolve("run"));
		ProgressData.OpenRequest open = reloaded.data().requests.open.get(0);
		assertEquals("req_b", open.id);
		assertEquals(A, open.villager);
		assertEquals("민준", open.name);
		assertEquals(2, open.progress);
		assertTrue(open.tracked);
		Requests again = new Requests(reloaded.data(), requests.set(), () -> {
		}, id -> null);
		again.tick(gave(3_000L, "minecraft:wheat", 1, A), null);
		assertTrue(again.openFor(A).done, "the saved count carries on");
	}
}
