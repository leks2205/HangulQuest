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
import java.util.Locale;

/** Clear sky, rain or thunder. */
final class WeatherType implements ConditionType<Condition.WeatherState> {
	@Override
	public int rank() {
		return 1;
	}

	@Override
	public String id() {
		return "WEATHER";
	}

	@Override
	public Class<Condition.WeatherState> conditionClass() {
		return Condition.WeatherState.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("weather", true, null, "CLEAR", "RAIN", "THUNDER"));
	}

	@Override
	public Condition.WeatherState parse(JsonObject json) {
		return new Condition.WeatherState(Condition.Weather.valueOf(
				JsonFields.string(json, "weather", "CLEAR").toUpperCase(Locale.ROOT)));
	}

	@Override
	public JsonObject toJson(Condition.WeatherState condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("weather", condition.weather().name());
		return json;
	}

	@Override
	public boolean met(Condition.WeatherState condition, Observation obs) {
		return switch (condition.weather()) {
			case CLEAR -> !obs.rain && !obs.thunder;
			case RAIN -> obs.rain;
			case THUNDER -> obs.thunder;
		};
	}

	@Override
	public String prefix(Condition.WeatherState condition, SentenceBuilder.Namer namer) {
		return switch (condition.weather()) {
			case CLEAR -> "맑을 때 ";
			case RAIN -> "비가 올 때 ";
			case THUNDER -> "천둥이 칠 때 ";
		};
	}

	@Override
	public List<String> words(Condition.WeatherState condition, SentenceBuilder.Namer namer) {
		return switch (condition.weather()) {
			case CLEAR -> List.of("맑은", "맑을", "맑아");
			case RAIN -> List.of("비가", "비를", "비는", "비 오", "빗속", "비에");
			case THUNDER -> List.of("천둥", "번개");
		};
	}

	@Override
	public String unmetText(Condition.WeatherState condition, Observation obs, Catalog catalog) {
		return "날씨가 맞지 않아요 · " + QuestStatus.weather(condition.weather());
	}
}
