package io.github.leks2205.hangulquest.learn;

import io.github.leks2205.hangulquest.util.Tr;

import io.github.leks2205.hangulquest.data.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Small, purely cosmetic achievement set (journal badges only, no gameplay effects). */
public final class Achievements {
	public record Achievement(String id, String ko, String de, String icon, Predicate<LearningService> test) {
	}

	public static final List<Achievement> ALL = List.of(
			new Achievement("first_word", "첫 단어", Tr.t("Erstes Wort"), "minecraft:book",
					service -> service.discovered() >= 1),
			new Achievement("words_10", "단어 열 개", Tr.t("10 Wörter"), "minecraft:paper",
					service -> service.discovered() >= 10),
			new Achievement("words_25", "단어 스물다섯 개", Tr.t("25 Wörter"), "minecraft:paper",
					service -> service.discovered() >= 25),
			new Achievement("words_50", "단어 쉰 개", Tr.t("50 Wörter"), "minecraft:book",
					service -> service.discovered() >= 50),
			new Achievement("words_100", "단어 백 개", Tr.t("100 Wörter"), "minecraft:bookshelf",
					service -> service.discovered() >= 100),
			new Achievement("mastered_10", "열 개를 알았어요", Tr.t("10 Wörter beherrscht"), "minecraft:gold_ingot",
					service -> service.mastered() >= 10),
			new Achievement("reviews_100", "복습 백 번", Tr.t("100 Wiederholungen"), "minecraft:clock",
					service -> service.totalReviews() >= 100),
			new Achievement("streak_3", "사흘 연속", Tr.t("3 Tage in Folge"), "minecraft:torch",
					service -> service.streak().current() >= 3),
			new Achievement("streak_7", "일주일 연속", Tr.t("7 Tage in Folge"), "minecraft:campfire",
					service -> service.streak().current() >= 7),
			new Achievement("streak_30", "한 달 연속", Tr.t("30 Tage in Folge"), "minecraft:beacon",
					service -> service.streak().current() >= 30),
			new Achievement("animals_10", "동물 친구", Tr.t("10 Tiere"), "minecraft:bone",
					service -> service.discoveredIn(Category.ANIMALS) >= 10),
			new Achievement("blocks_25", "블록 스물다섯 개", Tr.t("25 Blöcke"), "minecraft:cobblestone",
					service -> service.discoveredIn(Category.BLOCKS) >= 25),
			new Achievement("first_trail", "첫 번째 길", Tr.t("Erste Questlinie"), "minecraft:map",
					service -> service.completedTrails() >= 1),
			new Achievement("trails_3", "세 개의 길", Tr.t("Drei Questlinien"), "minecraft:filled_map",
					service -> service.completedTrails() >= 3),
			new Achievement("sidequest_5", "돌아온 길", Tr.t("5 Nebenquests"), "minecraft:compass",
					service -> service.sidequestsCompleted() >= 5)
	);

	private Achievements() {
	}

	public static List<Achievement> evaluate(LearningService service) {
		List<Achievement> unlocked = new ArrayList<>();
		for (Achievement achievement : ALL) {
			if (service.data().achievements.containsKey(achievement.id())) {
				continue;
			}
			if (achievement.test().test(service)) {
				service.data().achievements.put(achievement.id(), System.currentTimeMillis());
				unlocked.add(achievement);
			}
		}
		if (!unlocked.isEmpty()) {
			service.markDirty();
		}
		return unlocked;
	}
}
