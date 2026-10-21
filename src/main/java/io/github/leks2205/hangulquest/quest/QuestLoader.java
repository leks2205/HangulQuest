package io.github.leks2205.hangulquest.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads questlines from the jar (index.json lists the files). */
public final class QuestLoader {
	public static final String BASE = "/assets/hangulquest/data/questlines/";

	private QuestLoader() {
	}

	public static List<Questline> loadAll() {
		List<Questline> trails = new ArrayList<>();
		for (String file : index()) {
			try (InputStream stream = QuestLoader.class.getResourceAsStream(BASE + file)) {
				if (stream == null) {
					continue;
				}
				try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
					JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
					Questline trail = parse(object);
					if (trail != null) {
						trails.add(trail);
					}
				}
			} catch (Exception broken) {
				// A broken trail must never take the client down; validation logs it later.
				logWarn("questline " + file + " is unreadable: " + broken);
			}
		}
		return trails;
	}

	public static List<String> index() {
		List<String> files = new ArrayList<>();
		try (InputStream stream = QuestLoader.class.getResourceAsStream(BASE + "index.json")) {
			if (stream == null) {
				return files;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
				for (JsonElement element : array) {
					files.add(element.getAsString());
				}
			}
		} catch (Exception ignored) {
			// empty list
		}
		return files;
	}

	public static Questline parse(JsonObject root) {
		String id = string(root, "id", null);
		if (id == null || id.isBlank()) {
			return null;
		}
		List<QuestStep> steps = new ArrayList<>();
		if (root.has("steps")) {
			for (JsonElement element : root.getAsJsonArray("steps")) {
				QuestStep step = parseStep(element.getAsJsonObject());
				if (step != null) {
					steps.add(step);
				}
			}
		}
		QuestStep finale = root.has("finale") ? parseFinale(root.getAsJsonObject("finale")) : null;
		JsonObject finaleWrapper = root.has("finale") ? root.getAsJsonObject("finale") : null;
		String finaleKo = finaleWrapper != null ? string(finaleWrapper, "textKo", null) : null;
		String finaleDe = finaleWrapper != null ? string(finaleWrapper, "textDe", null) : null;

		JsonObject requiresObject = root.has("requires") ? root.getAsJsonObject("requires") : null;
		Questline.Requires requires = requiresObject == null ? Questline.Requires.none()
				: new Questline.Requires(integer(requiresObject, "minWordsKnown", 0),
				string(requiresObject, "dimension", null),
				strings(requiresObject, "completed"));

		JsonObject reward = root.has("reward") ? root.getAsJsonObject("reward") : null;
		String badge = reward != null ? string(reward, "badge", id) : id;

		Questline trail = new Questline(id,
				string(root, "titleKo", id),
				string(root, "titleDe", id),
				string(root, "topic", string(root, "titleDe", id)),
				integer(root, "tier", 1),
				strings(root, "concepts"),
				requires,
				steps,
				finale,
				finaleKo == null ? "여기까지 왔어요. 수고했어요!" : finaleKo,
				finaleDe == null ? "Du hast das Ziel erreicht." : finaleDe,
				badge,
				integer(root, "order", 1000),
				integer(root, "koreanLevel", integer(root, "tier", 1)),
				false);
		String finaleEn = string(root, "textEn", null);
		if (finaleEn == null && finaleDe == null) {
			finaleEn = "You reached the goal.";
		}
		return trail.withEnglish(string(root, "titleEn", null), finaleEn);
	}

	private static QuestStep parseStep(JsonObject object) {
		String id = string(object, "id", null);
		JsonObject goalObject = object.has("goal") ? object.getAsJsonObject("goal") : null;
		if (id == null || goalObject == null) {
			return null;
		}
		Goal goal = parseGoal(goalObject);
		if (goal == null) {
			return null;
		}
		List<Condition> conditions = parseConditions(object);
		JsonObject clue = object.has("clue") ? object.getAsJsonObject("clue") : null;
		JsonObject hint = object.has("hint") ? object.getAsJsonObject("hint") : null;
		QuestStep.Bonus bonus = null;
		if (object.has("bonus")) {
			JsonObject bonusObject = object.getAsJsonObject("bonus");
			if ("NEAR_STRUCTURE".equals(string(bonusObject, "type", ""))) {
				bonus = new QuestStep.Bonus(string(bonusObject, "structure", ""),
						number(bonusObject, "radius", 48));
			}
		}
		return new QuestStep(id, strings(object, "teach"), goal, conditions,
				clue == null ? null : string(clue, "ko", null),
				clue == null ? null : string(clue, "de", null),
				clue == null ? null : string(clue, "en", null),
				hint == null ? null : string(hint, "ko", null),
				hint == null ? null : string(hint, "de", null),
				hint == null ? null : string(hint, "en", null),
				bonus);
	}

	private static QuestStep parseFinale(JsonObject object) {
		if (object == null) {
			return null;
		}
		JsonObject step = object.has("step") ? object.getAsJsonObject("step") : object;
		QuestStep parsed = parseStep(step);
		if (parsed == null) {
			return null;
		}
		if (!"finale".equals(parsed.id())) {
			parsed = new QuestStep("finale", parsed.teach(), parsed.goal(), parsed.conditions(),
					parsed.clueKo(), parsed.clueDe(), parsed.clueEn(),
					parsed.hintKo(), parsed.hintDe(), parsed.hintEn(), parsed.bonus());
		}
		return parsed;
	}

	public static Goal parseGoal(JsonObject object) {
		String type = string(object, "type", "").toUpperCase(java.util.Locale.ROOT);
		return switch (type) {
			case "BREAK_BLOCK" -> new Goal.BreakBlock(string(object, "block", ""), integer(object, "count", 1),
					string(object, "verb", null));
			case "PLACE_BLOCK" -> new Goal.PlaceBlock(string(object, "block", ""), integer(object, "count", 1));
			case "ACQUIRE_ITEM" -> new Goal.AcquireItem(string(object, "item", ""), integer(object, "count", 1));
			case "CONSUME_ITEM" -> new Goal.ConsumeItem(string(object, "item", ""), integer(object, "count", 1));
			case "LOOK_AT_BLOCK" -> new Goal.LookAtBlock(string(object, "block", ""),
					integer(object, "dwellMs", 600));
			case "LOOK_AT_ENTITY" -> new Goal.LookAtEntity(string(object, "entity", ""),
					integer(object, "dwellMs", 800));
			case "DEFEAT_ENTITY" -> new Goal.DefeatEntity(string(object, "entity", ""), integer(object, "count", 1));
			case "REACH_BIOME" -> new Goal.ReachBiome(string(object, "biome", ""),
					integer(object, "dwellMs", 1000));
			case "REACH_DIMENSION" -> new Goal.ReachDimension(string(object, "dimension", ""));
			case "REACH_HEIGHT" -> new Goal.ReachHeight(integer(object, "minY", 0), integer(object, "maxY", 320));
			case "USE_BLOCK" -> new Goal.UseBlock(string(object, "block", ""));
			case "INTERACT_ENTITY" -> new Goal.InteractEntity(string(object, "entity", ""));
			default -> null;
		};
	}

	public static Condition parseCondition(JsonObject object) {
		String type = string(object, "type", "").toUpperCase(java.util.Locale.ROOT);
		return switch (type) {
			case "TIME" -> new Condition.Time(Condition.Phase.valueOf(
					string(object, "phase", "DAY").toUpperCase(java.util.Locale.ROOT)));
			case "WEATHER" -> new Condition.WeatherState(Condition.Weather.valueOf(
					string(object, "weather", "CLEAR").toUpperCase(java.util.Locale.ROOT)));
			case "Y_RANGE" -> new Condition.YRange(integer(object, "minY", 0), integer(object, "maxY", 320));
			case "NEAR_ENTITY" -> new Condition.NearEntity(string(object, "entity", ""),
					number(object, "radius", 16));
			case "NEAR_BLOCK" -> new Condition.NearBlock(string(object, "block", ""),
					number(object, "radius", 16));
			case "IN_DIMENSION" -> new Condition.InDimension(string(object, "dimension", ""));
			case "IN_BIOME" -> new Condition.InBiome(string(object, "biome", ""));
			default -> null;
		};
	}

	private static List<Condition> parseConditions(JsonObject step) {
		List<Condition> conditions = new ArrayList<>();
		if (step.has("conditions")) {
			for (JsonElement element : step.getAsJsonArray("conditions")) {
				Condition condition = parseCondition(element.getAsJsonObject());
				if (condition != null) {
					conditions.add(condition);
				}
			}
		}
		return conditions;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull()
				? object.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		try {
			return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
		} catch (RuntimeException broken) {
			return fallback;
		}
	}

	private static double number(JsonObject object, String key, double fallback) {
		try {
			return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
		} catch (RuntimeException broken) {
			return fallback;
		}
	}

	private static List<String> strings(JsonObject object, String key) {
		List<String> values = new ArrayList<>();
		if (object != null && object.has(key)) {
			for (JsonElement element : object.getAsJsonArray(key)) {
				values.add(element.getAsString());
			}
		}
		return values;
	}

	private static void logWarn(String message) {
		org.slf4j.LoggerFactory.getLogger("hangulquest").warn(message);
	}
}
