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

/** The block the player looks at is within some blocks of the player. */
final class NearBlockType implements ConditionType<Condition.NearBlock> {
	@Override
	public String id() {
		return "NEAR_BLOCK";
	}

	@Override
	public Class<Condition.NearBlock> conditionClass() {
		return Condition.NearBlock.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"), Field.integer("radius", 1, 64, false));
	}

	@Override
	public Condition.NearBlock parse(JsonObject json) {
		return new Condition.NearBlock(JsonFields.string(json, "block", ""), JsonFields.number(json, "radius", 16));
	}

	@Override
	public JsonObject toJson(Condition.NearBlock condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", condition.block());
		Radius.write(json, condition.radius());
		return json;
	}

	@Override
	public boolean met(Condition.NearBlock condition, Observation obs) {
		String block = condition.block();
		if (condition.radius() < 0 || block == null || !block.equals(obs.lookedBlock)) {
			return false;
		}
		if (obs.lookedBlockPos == null) {
			return true;
		}
		return obs.position.distanceToSqr(obs.lookedBlockPos.getX() + 0.5,
				obs.lookedBlockPos.getY() + 0.5, obs.lookedBlockPos.getZ() + 0.5)
				<= condition.radius() * condition.radius();
	}

	@Override
	public List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(Condition.NearBlock condition) {
		return List.of(new io.github.leks2205.hangulquest.quest.goal.GoalType.Mention(io.github.leks2205.hangulquest.quest.goal.ObjectKind.BLOCK, condition.block()));
	}

	@Override
	public String prefix(Condition.NearBlock condition, SentenceBuilder.Namer namer) {
		return SentenceBuilder.nearName(namer.block(condition.block()), "가까이에서 ");
	}

	@Override
	public String placeNoun(Condition.NearBlock condition, SentenceBuilder.Namer namer) {
		String name = namer.block(condition.block());
		return name == null || name.isBlank() ? null : name + " 근처";
	}

	@Override
	public String unmetText(Condition.NearBlock condition, Observation obs, Catalog catalog) {
		return "가까이에 " + QuestStatus.block(condition.block(), catalog) + " 필요";
	}
}
