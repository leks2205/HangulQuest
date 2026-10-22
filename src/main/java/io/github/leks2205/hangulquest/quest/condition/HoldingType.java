package io.github.leks2205.hangulquest.quest.condition;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.goal.Hands;
import io.github.leks2205.hangulquest.quest.goal.ValidationContext;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** An item is in a hand: "횃불을 들고 빵을 먹어요". */
final class HoldingType implements ConditionType<Condition.Holding> {
	@Override
	public int rank() {
		return 3;
	}

	@Override
	public String id() {
		return "HOLDING";
	}

	@Override
	public Class<Condition.Holding> conditionClass() {
		return Condition.Holding.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"));
	}

	@Override
	public Condition.Holding parse(JsonObject json) {
		return new Condition.Holding(JsonFields.string(json, "item", ""));
	}

	@Override
	public JsonObject toJson(Condition.Holding condition) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", condition.item());
		return json;
	}

	@Override
	public boolean met(Condition.Holding condition, Observation obs) {
		return Hands.holds(obs, condition.item());
	}

	@Override
	public String prefix(Condition.Holding condition, SentenceBuilder.Namer namer) {
		String name = namer.item(condition.item());
		return name == null || name.isBlank() ? "" : KoGrammar.eul(name) + " 들고 ";
	}

	@Override
	public List<io.github.leks2205.hangulquest.quest.goal.GoalType.Mention> mentions(Condition.Holding condition) {
		return List.of(new io.github.leks2205.hangulquest.quest.goal.GoalType.Mention(io.github.leks2205.hangulquest.quest.goal.ObjectKind.ITEM, condition.item()));
	}

	@Override
	public String unmetText(Condition.Holding condition, Observation obs, Catalog catalog) {
		return "손에 " + QuestStatus.block(condition.item(), catalog) + " 필요";
	}

	@Override
	public List<String> grammarIds(Condition.Holding condition) {
		return List.of("L2.go_state");
	}

	@Override
	public void validate(Condition.Holding condition, ValidationContext context) {
		if (ValidationContext.badId(condition.item())) {
			context.problem("bad item " + condition.item());
		}
	}
}
