package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** Be in a dimension. */
final class ReachDimensionType implements GoalType<Goal.ReachDimension> {
	@Override
	public String id() {
		return "REACH_DIMENSION";
	}

	@Override
	public Class<Goal.ReachDimension> goalClass() {
		return Goal.ReachDimension.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("dimension"));
	}

	@Override
	public Goal.ReachDimension parse(JsonObject json) {
		return new Goal.ReachDimension(JsonFields.string(json, "dimension", ""));
	}

	@Override
	public JsonObject toJson(Goal.ReachDimension goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("dimension", goal.dimension());
		return json;
	}

	@Override
	public String keyPart(Goal.ReachDimension goal) {
		return goal.dimension();
	}

	@Override
	public boolean evaluate(Goal.ReachDimension goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		return goal.dimension().equals(obs.dimension);
	}

	@Override
	public Verb verb(Goal.ReachDimension goal, VerbTable table) {
		return Verb.GO;
	}

	@Override
	public String korean(Goal.ReachDimension goal, SentenceContext c) {
		return SentenceBuilder.phrase(KoGrammar.euro(SentenceBuilder.dimensionName(goal.dimension(), c.obj())),
				c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.ReachDimension goal, String obj, Verb verb) {
		return SentenceBuilder.dimensionName(goal.dimension(), obj) + "에 " + Verb.ARRIVE.past();
	}

	@Override
	public String status(Goal.ReachDimension goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return "현재: " + QuestStatus.dimension(obs.dimension) + " · 목표: "
				+ QuestStatus.dimension(goal.dimension());
	}

	@Override
	public String object(Goal.ReachDimension goal) {
		return goal.dimension();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.DIMENSION;
	}
}
