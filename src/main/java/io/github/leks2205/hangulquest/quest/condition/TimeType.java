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

/** The time of day. */
final class TimeType implements ConditionType<Condition.Time> {
	@Override
	public String id() {
		return "TIME";
	}

	@Override
	public Class<Condition.Time> conditionClass() {
		return Condition.Time.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("phase", true, null, "DAY", "NIGHT", "DAWN", "DUSK"));
	}

	@Override
	public Condition.Time parse(JsonObject json) {
		return new Condition.Time(Condition.Phase.valueOf(
				JsonFields.string(json, "phase", "DAY").toUpperCase(Locale.ROOT)));
	}

	@Override
	public JsonObject toJson(Condition.Time condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("phase", condition.phase().name());
		return json;
	}

	@Override
	public boolean met(Condition.Time condition, Observation obs) {
		return obs.phase() == condition.phase();
	}

	@Override
	public String prefix(Condition.Time condition, SentenceBuilder.Namer namer) {
		return switch (condition.phase()) {
			case NIGHT -> "밤에 ";
			case DAY -> "낮에 ";
			case DAWN -> "새벽에 ";
			case DUSK -> "저녁에 ";
		};
	}

	@Override
	public List<String> words(Condition.Time condition, SentenceBuilder.Namer namer) {
		return switch (condition.phase()) {
			case NIGHT -> List.of("밤에", "밤이", "밤마다", "밤 동안", "한밤", "밤중");
			case DAY -> List.of("낮에", "낮이", "낮마다", "낮 동안", "한낮");
			case DAWN -> List.of("새벽");
			case DUSK -> List.of("저녁에", "저녁이", "해 질 때", "해가 질");
		};
	}

	@Override
	public String unmetText(Condition.Time condition, Observation obs, Catalog catalog) {
		return "지금 " + QuestStatus.phase(obs.phase()) + " · " + QuestStatus.phase(condition.phase()) + " 필요";
	}

	@Override
	public boolean timeOfDay() {
		return true;
	}

	@Override
	public boolean requiresNight(Condition.Time condition) {
		return condition.phase() == Condition.Phase.NIGHT;
	}
}
