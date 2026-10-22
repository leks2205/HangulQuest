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

/** The dimension the player is in. */
final class InDimensionType implements ConditionType<Condition.InDimension> {
	@Override
	public String id() {
		return "IN_DIMENSION";
	}

	@Override
	public Class<Condition.InDimension> conditionClass() {
		return Condition.InDimension.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("dimension"));
	}

	@Override
	public Condition.InDimension parse(JsonObject json) {
		return new Condition.InDimension(JsonFields.string(json, "dimension", ""));
	}

	@Override
	public JsonObject toJson(Condition.InDimension condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("dimension", condition.dimension());
		return json;
	}

	@Override
	public boolean met(Condition.InDimension condition, Observation obs) {
		return condition.dimension().equals(obs.dimension);
	}

	@Override
	public String prefix(Condition.InDimension condition, SentenceBuilder.Namer namer) {
		String name = SentenceBuilder.dimension(condition.dimension());
		return name == null ? "다른 차원에서 " : name + "에서 ";
	}

	@Override
	public String placeNoun(Condition.InDimension condition, SentenceBuilder.Namer namer) {
		return SentenceBuilder.dimension(condition.dimension());
	}

	@Override
	public String unmetText(Condition.InDimension condition, Observation obs, Catalog catalog) {
		return "지금 " + QuestStatus.dimension(obs.dimension) + " · "
				+ QuestStatus.dimension(condition.dimension()) + " 필요";
	}
}
