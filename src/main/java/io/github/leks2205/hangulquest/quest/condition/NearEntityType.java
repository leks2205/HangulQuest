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

/** An entity of a kind within some blocks of the player. */
final class NearEntityType implements ConditionType<Condition.NearEntity> {
	@Override
	public String id() {
		return "NEAR_ENTITY";
	}

	@Override
	public Class<Condition.NearEntity> conditionClass() {
		return Condition.NearEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.integer("radius", 1, 64, false));
	}

	@Override
	public Condition.NearEntity parse(JsonObject json) {
		return new Condition.NearEntity(JsonFields.string(json, "entity", ""), JsonFields.number(json, "radius", 16));
	}

	@Override
	public JsonObject toJson(Condition.NearEntity condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", condition.entity());
		Radius.write(json, condition.radius());
		return json;
	}

	/**
	 * The nearest entity of that type must be within {@code radius} of the player. When the sensor
	 * did not record a distance (hand-built observations), presence in the scan is enough.
	 */
	@Override
	public boolean met(Condition.NearEntity condition, Observation obs) {
		if (condition.radius() < 0 || obs.nearbyEntities.getOrDefault(condition.entity(), 0) <= 0) {
			return false;
		}
		Double distance = obs.nearestEntityDistance.get(condition.entity());
		return distance == null || distance <= condition.radius();
	}

	@Override
	public List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(Condition.NearEntity condition) {
		return List.of(new io.github.leks2205.hangulquest.quest.goal.GoalType.Mention(io.github.leks2205.hangulquest.quest.goal.ObjectKind.ENTITY, condition.entity()));
	}

	@Override
	public String prefix(Condition.NearEntity condition, SentenceBuilder.Namer namer) {
		return SentenceBuilder.nearName(namer.entity(condition.entity()), "가까이에서 ");
	}

	@Override
	public String placeNoun(Condition.NearEntity condition, SentenceBuilder.Namer namer) {
		String name = namer.entity(condition.entity());
		return name == null || name.isBlank() ? null : name + " 근처";
	}

	@Override
	public String unmetText(Condition.NearEntity condition, Observation obs, Catalog catalog) {
		return "가까이에 " + QuestStatus.entity(condition.entity(), catalog) + " 필요";
	}
}
