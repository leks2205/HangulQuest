package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** The player is in water. */
final class InWaterType implements ConditionType<Condition.InWater> {
	@Override
	public String id() {
		return "IN_WATER";
	}

	@Override
	public Class<Condition.InWater> conditionClass() {
		return Condition.InWater.class;
	}

	@Override
	public List<Field> fields() {
		return List.of();
	}

	@Override
	public Condition.InWater parse(JsonObject json) {
		return new Condition.InWater();
	}

	@Override
	public JsonObject toJson(Condition.InWater condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		return json;
	}

	@Override
	public boolean met(Condition.InWater condition, Observation obs) {
		return obs.inWater;
	}

	@Override
	public String prefix(Condition.InWater condition, SentenceBuilder.Namer namer) {
		return "물속에서 ";
	}

	@Override
	public List<String> words(Condition.InWater condition, SentenceBuilder.Namer namer) {
		return List.of("물속", "물 속", "물에서", "수영");
	}

	@Override
	public String unmetText(Condition.InWater condition, Observation obs, Catalog catalog) {
		return "물속 필요";
	}

	@Override
	public List<String> grammarIds(Condition.InWater condition) {
		return List.of("L2.eseo_activity");
	}
}
