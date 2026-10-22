package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** The player's height. */
final class YRangeType implements ConditionType<Condition.YRange> {
	@Override
	public String id() {
		return "Y_RANGE";
	}

	@Override
	public Class<Condition.YRange> conditionClass() {
		return Condition.YRange.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("minY", -256, 512, true), Field.integer("maxY", -256, 512, true));
	}

	@Override
	public Condition.YRange parse(JsonObject json) {
		return new Condition.YRange(JsonFields.integer(json, "minY", 0), JsonFields.integer(json, "maxY", 320));
	}

	@Override
	public JsonObject toJson(Condition.YRange condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("minY", condition.minY());
		json.addProperty("maxY", condition.maxY());
		return json;
	}

	@Override
	public boolean met(Condition.YRange condition, Observation obs) {
		return obs.y >= condition.minY() && obs.y <= condition.maxY();
	}

	@Override
	public String prefix(Condition.YRange condition, SentenceBuilder.Namer namer) {
		String where = yRange(condition);
		return where.isEmpty() ? "" : where + ' ';
	}

	@Override
	public String placeNoun(Condition.YRange condition, SentenceBuilder.Namer namer) {
		String where = yRange(condition);
		return where.endsWith("에서") && !where.equals("땅속에서") ? where.substring(0, where.length() - 2) : null;
	}

	private static String yRange(Condition.YRange y) {
		if (y.minY() <= 0 && y.maxY() >= 320) {
			return "";
		}
		if (y.maxY() <= 0) {
			return "높이 " + y.maxY() + " 이하에서";
		}
		if (y.minY() <= 0) {
			return "땅속에서";
		}
		return "높이 " + y.minY() + "블록 위에서";
	}

	@Override
	public String unmetText(Condition.YRange condition, Observation obs, Catalog catalog) {
		return QuestStatus.yText(obs.y, condition.minY(), condition.maxY());
	}
}
