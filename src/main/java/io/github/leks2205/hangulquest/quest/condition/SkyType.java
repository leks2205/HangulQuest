package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Locale;

/** Open sky above the player, or a roof within reach. */
final class SkyType implements ConditionType<Condition.Sky> {
	@Override
	public String id() {
		return "SKY";
	}

	@Override
	public Class<Condition.Sky> conditionClass() {
		return Condition.Sky.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("state", true, null, "OPEN", "ROOFED"));
	}

	@Override
	public Condition.Sky parse(JsonObject json) {
		try {
			return new Condition.Sky(Condition.SkyState.valueOf(
					JsonFields.string(json, "state", "OPEN").toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException unknown) {
			return new Condition.Sky(Condition.SkyState.OPEN);
		}
	}

	@Override
	public JsonObject toJson(Condition.Sky condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("state", condition.state().name());
		return json;
	}

	@Override
	public boolean met(Condition.Sky condition, Observation obs) {
		return condition.state() == Condition.SkyState.OPEN ? obs.canSeeSky : obs.roofed;
	}

	@Override
	public String prefix(Condition.Sky condition, SentenceBuilder.Namer namer) {
		return condition.state() == Condition.SkyState.OPEN ? "하늘이 보이는 곳에서 " : "지붕 아래에서 ";
	}

	@Override
	public String unmetText(Condition.Sky condition, Observation obs, Catalog catalog) {
		return condition.state() == Condition.SkyState.OPEN ? "하늘이 보이는 곳 필요" : "지붕 아래 필요";
	}

	@Override
	public List<String> grammarIds(Condition.Sky condition) {
		return condition.state() == Condition.SkyState.OPEN
				? List.of("L2.eseo_activity", "L3.neun_attr") : List.of("L2.eseo_activity");
	}
}
