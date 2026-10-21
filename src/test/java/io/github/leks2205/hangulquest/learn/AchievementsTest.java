package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.util.I18n;
import io.github.leks2205.hangulquest.util.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementsTest {
	/** Saved progress files refer to these; they must stay. */
	private static final List<String> SAVED_IDS = List.of("first_word", "words_10", "words_25", "words_50", "words_100",
			"mastered_10", "reviews_100", "streak_3", "streak_7", "streak_30", "animals_10", "blocks_25", "first_trail",
			"trails_3", "sidequest_5");

	@TempDir
	Path dir;

	private LearningService learn;

	@BeforeEach
	void setUp() {
		Lang.set("EN");
		Concept concept = new Concept();
		concept.id = "stone";
		concept.ko = "돌";
		concept.de = "Stein";
		concept.items = List.of("minecraft:stone");
		ProgressStore store = new ProgressStore(dir, LoggerFactory.getLogger("achievements-test"));
		learn = new LearningService(store.data(), store, Catalog.ofConcepts(List.of(concept)));
	}

	@Test
	void everySavedAchievementIdStillExists() {
		Set<String> ids = Achievements.ALL.stream().map(Achievements.Achievement::id).collect(Collectors.toSet());
		assertTrue(ids.containsAll(SAVED_IDS), "missing: " + SAVED_IDS.stream().filter(id -> !ids.contains(id)).toList());
		assertEquals(ids.size(), Achievements.ALL.size(), "ids are unique");
	}

	@Test
	void theLabelsAreFactsAndNeverMentionLosing() {
		for (Achievements.Achievement achievement : Achievements.ALL) {
			for (Lang lang : List.of(Lang.EN, Lang.DE)) {
				String label = I18n.t(lang, achievement.key()).toLowerCase();
				assertFalse(label.isBlank(), achievement.id());
				for (String banned : List.of("lose", "lost", "broken", "verlor", "gebrochen", "streak lost")) {
					assertFalse(label.contains(banned), achievement.id() + ": " + label);
				}
			}
		}
	}

	@Test
	void aSavedUnlockStaysUnlockedWhenTheStreakIsGone() {
		learn.data().achievements.put("streak_7", 123L);

		Achievements.evaluate(learn);

		assertEquals(123L, learn.data().achievements.get("streak_7"), "nothing is ever taken away or rewritten");
	}

	@Test
	void theWeeklyGoalBadgeStatesAFactAfterFourStudyDays() {
		LocalDate today = Streak.studyDay(LocalDateTime.now());
		for (int back : new int[] {0, 1, 3}) {
			learn.data().days.put(today.minusDays(back).toString(), new DayCounts(5, 4));
		}
		assertFalse(Achievements.evaluate(learn).stream().anyMatch(a -> a.id().equals("weekly_goal")));

		learn.data().days.put(today.minusDays(5).toString(), new DayCounts(5, 4));

		assertTrue(Achievements.evaluate(learn).stream().anyMatch(a -> a.id().equals("weekly_goal")));
	}

	@Test
	void theReviewCountBadgesFollowTheTotals() {
		learn.data().totals.reviews = 299;
		Achievements.evaluate(learn);
		assertTrue(learn.data().achievements.containsKey("reviews_100"));
		assertFalse(learn.data().achievements.containsKey("reviews_300"));
		learn.data().totals.reviews = 300;
		Achievements.evaluate(learn);
		assertTrue(learn.data().achievements.containsKey("reviews_300"));
	}

	@Test
	void aStreakBadgeCountsDaysBridgedByAFreeze() {
		LocalDate today = Streak.studyDay(LocalDateTime.now());
		// Three study days with one missed day in between: a streak of three with a freeze.
		learn.data().days.put(today.toString(), new DayCounts(1, 1));
		learn.data().days.put(today.minusDays(2).toString(), new DayCounts(1, 1));
		learn.data().days.put(today.minusDays(3).toString(), new DayCounts(1, 1));

		assertTrue(Achievements.evaluate(learn).stream().anyMatch(a -> a.id().equals("streak_3")));
	}
}
