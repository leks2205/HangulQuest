package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.Directions;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Locale;

/**
 * The player looks along a compass direction (within 45 degrees): "북쪽을 보고 횃불을 놓으세요". Read from the yaw of
 * the player; the goal is checked while the player faces that way, and the condition says nothing about the place.
 */
final class FacingType implements ConditionType<Condition.Facing> {
	@Override
	public String id() {
		return "FACING";
	}

	@Override
	public Class<Condition.Facing> conditionClass() {
		return Condition.Facing.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("direction", true, "A compass direction; north is -Z.",
				Directions.ALL.toArray(String[]::new)));
	}

	@Override
	public Condition.Facing parse(JsonObject json) {
		return new Condition.Facing(JsonFields.string(json, "direction", "NORTH").toUpperCase(Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Condition.Facing condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("direction", condition.direction());
		return json;
	}

	@Override
	public boolean met(Condition.Facing condition, Observation obs) {
		return Directions.faces(condition.direction(), obs.yaw);
	}

	@Override
	public String prefix(Condition.Facing condition, SentenceBuilder.Namer namer) {
		return Directions.ko(condition.direction()) + "을 보고 ";
	}

	/** 북쪽을 보고 stands right in front of the action, after the place and what is held. */
	@Override
	public int rank() {
		return 4;
	}

	@Override
	public List<String> words(Condition.Facing condition, SentenceBuilder.Namer namer) {
		String way = Directions.ko(condition.direction());
		return List.of(way + "을", way + "으로", way + "에서");
	}

	@Override
	public String unmetText(Condition.Facing condition, Observation obs, Catalog catalog) {
		return Directions.ko(condition.direction()) + "을 봐요";
	}

	@Override
	public List<String> grammarIds(Condition.Facing condition) {
		return List.of("L1.eul_reul", "L2.go_sequence");
	}

	@Override
	public void validate(Condition.Facing condition, io.github.leks2205.hangulquest.quest.goal.ValidationContext context) {
		if (!Directions.valid(condition.direction())) {
			context.problem("direction must be one of " + Directions.ALL + ", not " + condition.direction());
		}
	}
}
