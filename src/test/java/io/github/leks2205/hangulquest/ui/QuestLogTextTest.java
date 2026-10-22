package io.github.leks2205.hangulquest.ui;

import io.github.leks2205.hangulquest.quest.QuestEngine.TrackView;
import io.github.leks2205.hangulquest.util.I18n;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The strings of a quest-log row. */
class QuestLogTextTest {
	private static TrackView view(boolean countable, int progress, int needed, String status, int step, int steps) {
		return new TrackView("stone", "돌 길", "Steinpfad", "Stone path", "돌", 1,
				"회색 돌을 세 개 캐요.", "", status, progress, needed, countable, false, step, steps);
	}

	@Test
	void theCounterIsOneBased() {
		assertEquals("1/4", QuestLogText.counter(view(false, 0, 1, "", 0, 4)));
		assertEquals("4/4", QuestLogText.counter(view(false, 0, 1, "", 3, 4)));
	}

	@Test
	void aCountingGoalShowsItsCountBehindTheClue() {
		assertEquals(" 2/3", QuestLogText.countSuffix(view(true, 2, 3, "", 0, 4)));
	}

	@Test
	void aGoalThatIsNotACountShowsNoCount() {
		assertEquals("", QuestLogText.countSuffix(view(false, 0, 3, "", 0, 4)));
		assertEquals("", QuestLogText.countSuffix(view(true, 0, 1, "", 0, 4)), "one item needs no 0/1");
	}

	@Test
	void onlyConditionMessagesBecomeAStatus() {
		assertEquals("밤 필요", QuestLogText.conditionStatus(view(true, 0, 3, "밤 필요", 0, 4)));
		assertEquals("", QuestLogText.conditionStatus(view(true, 0, 3, "0/3", 0, 4)), "counts are inline already");
		assertEquals("", QuestLogText.conditionStatus(view(true, 0, 3, " ", 0, 4)));
		assertEquals("", QuestLogText.conditionStatus(view(true, 0, 3, null, 0, 4)));
	}

	@Test
	void theStatusJoinsTheLineOnlyWhenItFits() {
		assertTrue(QuestLogText.statusFits(100, 6, 30, 136));
		assertFalse(QuestLogText.statusFits(100, 6, 30, 135));
	}

	@Test
	void theSituationalRowCarriesAMarkerAndOthersDoNot() {
		TrackView plain = view(false, 0, 1, "", 0, 4);
		TrackView here = new TrackView(plain.id(), plain.titleKo(), plain.titleDe(), plain.titleEn(), plain.topicKey(),
				plain.koreanLevel(), plain.clueKo(), plain.clueDe(), "", 0, 1, false, false, 0, 4, false, true);

		assertEquals("", QuestLogText.situationalMarker(plain));
		assertEquals(I18n.t("hangulquest.ui.hud.situational"), QuestLogText.situationalMarker(here));
		assertFalse(QuestLogText.situationalMarker(here).isBlank(), "the translation is not empty");
		// 여기 = "here", the translation shown beside it (상황 meant "situation" and did not match)
		assertEquals("여기", QuestLogText.SITUATIONAL_KO);
	}
}
