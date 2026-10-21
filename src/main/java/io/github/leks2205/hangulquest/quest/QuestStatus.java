package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.quest.goal.GoalType;
import io.github.leks2205.hangulquest.quest.goal.GoalTypes;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** One short Korean status line for the quest tracker: why it is not done yet, or how far it is. */
public final class QuestStatus {
	private QuestStatus() {
	}

	public static String text(QuestStep step, Observation obs, GoalEvaluator.Tracker tracker,
							  Catalog catalog) {
		if (step == null || obs == null) {
			return "";
		}
		GoalType<Goal> type = GoalTypes.of(step.goal());
		String override = type.statusOverride(step.goal(), obs);
		if (override != null) {
			return override;
		}
		List<Condition> conditions = step.conditions();
		if (conditions != null && !conditions.isEmpty()
				&& !GoalEvaluator.conditionsMet(conditions, obs)) {
			return conditionText(conditions, obs, catalog);
		}
		return goalText(type, step, obs, tracker, catalog);
	}

	private static String conditionText(List<Condition> conditions, Observation obs, Catalog catalog) {
		for (Condition condition : conditions) {
			if (GoalEvaluator.conditionMet(condition, obs)) {
				continue;
			}
			return ConditionTypes.of(condition).unmetText(condition, obs, catalog);
		}
		return "";
	}

	private static String goalText(GoalType<Goal> type, QuestStep step, Observation obs, GoalEvaluator.Tracker tracker,
								   Catalog catalog) {
		String word = step.teach().isEmpty() ? "" : catalog.byConceptId(step.teach().get(0))
				.map(w -> w.ko).orElse("");
		return type.status(step.goal(), obs, tracker, catalog, word);
	}

	public static String count(int current, int needed) {
		return current + "/" + Math.max(1, needed);
	}

	public static String look(long dwell, long needed) {
		long target = Math.max(50, needed);
		return Math.min(100L, dwell * 100L / target) + "%";
	}

	public static String yText(int y, int minY, int maxY) {
		int current = y;
		if (maxY <= 0) {
			return "Y " + current + " / " + maxY + " 이하";
		}
		if (minY <= 0) {
			return "Y " + current + " / " + minY + "~" + maxY;
		}
		return "Y " + current + " / " + minY + " 위";
	}

	public static String phase(Condition.Phase phase) {
		return switch (phase) {
			case DAY -> "낮";
			case NIGHT -> "밤";
			case DAWN -> "새벽";
			case DUSK -> "저녁";
		};
	}

	public static String weather(Condition.Weather weather) {
		return switch (weather) {
			case CLEAR -> "맑음";
			case RAIN -> "비";
			case THUNDER -> "천둥";
		};
	}

	public static String dimension(String id) {
		if (id == null) {
			return "다른 차원";
		}
		String name = SentenceBuilder.dimension(id);
		return name == null ? "다른 차원" : name;
	}

	public static String biome(String id, Catalog catalog) {
		if (id == null) {
			return "알 수 없음";
		}
		if (id.startsWith("#")) {
			return switch (id) {
				case "#minecraft:is_mountain" -> "산";
				case "#minecraft:is_beach" -> "해변";
				case "#minecraft:is_ocean" -> "바다";
				case "#minecraft:is_forest" -> "숲";
				case "#minecraft:is_taiga" -> "타이가";
				case "#minecraft:is_jungle" -> "정글";
				case "#minecraft:is_badlands" -> "메사";
				case "#minecraft:is_savanna" -> "사바나";
				case "#minecraft:is_river" -> "강";
				default -> "그 생물 군계";
			};
		}
		return catalog.byBiomeId(id).map(word -> word.ko).orElse("그 생물 군계");
	}

	public static String entity(String id, Catalog catalog) {
		return catalog.byEntityId(id).map(word -> word.ko).orElse("그것");
	}

	public static String block(String id, Catalog catalog) {
		return catalog.byItemId(id).map(word -> word.ko).orElse("그것");
	}
}
