package io.github.leks2205.hangulquest.quest;

import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Word;
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
		if (step.goal() instanceof Goal.UseBlock use
				&& use.block() != null && use.block().endsWith("_bed")) {
			if (obs.sleeping) {
				return "침대에서 자는 중";
			}
			if (obs.bedUseRecent) {
				return "침대를 사용했지만 잘 수 없어요";
			}
		}
		List<Condition> conditions = step.conditions();
		if (conditions != null && !conditions.isEmpty()
				&& !GoalEvaluator.conditionsMet(conditions, obs)) {
			return conditionText(conditions, obs, catalog);
		}
		return goalText(step, obs, tracker, catalog);
	}

	private static String conditionText(List<Condition> conditions, Observation obs, Catalog catalog) {
		for (Condition condition : conditions) {
			if (GoalEvaluator.conditionMet(condition, obs)) {
				continue;
			}
			return switch (condition) {
				case Condition.Time t -> "지금 " + phase(obs.phase()) + " · " + phase(t.phase()) + " 필요";
				case Condition.InBiome b -> "지금 " + biome(obs.biome, catalog) + " · "
						+ biome(b.biome(), catalog) + " 필요";
				case Condition.InDimension d -> "지금 " + dimension(obs.dimension) + " · "
						+ dimension(d.dimension()) + " 필요";
				case Condition.YRange y -> yText(obs.y, y.minY(), y.maxY());
				case Condition.NearEntity n -> "가까이에 " + entity(n.entity(), catalog) + " 필요";
				case Condition.NearBlock n -> "가까이에 " + block(n.block(), catalog) + " 필요";
				case Condition.WeatherState w -> "날씨가 맞지 않아요 · " + weather(w.weather());
			};
		}
		return "";
	}

	private static String goalText(QuestStep step, Observation obs, GoalEvaluator.Tracker tracker,
								   Catalog catalog) {
		String word = step.teach().isEmpty() ? "" : catalog.byConceptId(step.teach().get(0))
				.map(w -> w.ko).orElse("");
		return switch (step.goal()) {
			case Goal.BreakBlock g -> count(tracker.count, g.count());
			case Goal.PlaceBlock g -> count(tracker.count, g.count());
			case Goal.ConsumeItem g -> count(tracker.count, g.count());
			case Goal.DefeatEntity g -> count(tracker.count, g.count());
			// Only items gained since the step began count (GoalEvaluator.baselineAcquired).
			case Goal.AcquireItem g -> word + " " + Math.min(tracker.count, Math.max(1, g.count()))
					+ "/" + Math.max(1, g.count());
			case Goal.LookAtBlock g -> look(tracker.dwellMs, g.dwellMs());
			case Goal.LookAtEntity g -> look(tracker.dwellMs, g.dwellMs());
			case Goal.ReachHeight g -> yText(obs.y, g.minY(), g.maxY());
			case Goal.ReachBiome g -> "현재: " + biome(obs.biome, catalog) + " · 목표: "
					+ biome(g.biome(), catalog);
			case Goal.ReachDimension g -> "현재: " + dimension(obs.dimension) + " · 목표: "
					+ dimension(g.dimension());
			case Goal.UseBlock g -> "";
			case Goal.InteractEntity g -> "";
		};
	}

	private static String count(int current, int needed) {
		return current + "/" + Math.max(1, needed);
	}

	private static String look(long dwell, long needed) {
		long target = Math.max(50, needed);
		return Math.min(100L, dwell * 100L / target) + "%";
	}

	private static String yText(int y, int minY, int maxY) {
		int current = y;
		if (maxY <= 0) {
			return "Y " + current + " / " + maxY + " 이하";
		}
		if (minY <= 0) {
			return "Y " + current + " / " + minY + "~" + maxY;
		}
		return "Y " + current + " / " + minY + " 위";
	}

	private static String phase(Condition.Phase phase) {
		return switch (phase) {
			case DAY -> "낮";
			case NIGHT -> "밤";
			case DAWN -> "새벽";
			case DUSK -> "저녁";
		};
	}

	private static String weather(Condition.Weather weather) {
		return switch (weather) {
			case CLEAR -> "맑음";
			case RAIN -> "비";
			case THUNDER -> "천둥";
		};
	}

	private static String dimension(String id) {
		if (id == null) {
			return "다른 차원";
		}
		String name = SentenceBuilder.dimension(id);
		return name == null ? "다른 차원" : name;
	}

	private static String biome(String id, Catalog catalog) {
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

	private static String entity(String id, Catalog catalog) {
		return catalog.byEntityId(id).map(word -> word.ko).orElse("그것");
	}

	private static String block(String id, Catalog catalog) {
		return catalog.byItemId(id).map(word -> word.ko).orElse("그것");
	}
}
