package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** Stand at a height between two Y levels. */
final class ReachHeightType implements GoalType<Goal.ReachHeight> {
	@Override
	public String id() {
		return "REACH_HEIGHT";
	}

	@Override
	public Class<Goal.ReachHeight> goalClass() {
		return Goal.ReachHeight.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("minY", -256, 512, true), Field.integer("maxY", -256, 512, true));
	}

	@Override
	public Goal.ReachHeight parse(JsonObject json) {
		return new Goal.ReachHeight(JsonFields.integer(json, "minY", 0), JsonFields.integer(json, "maxY", 320));
	}

	@Override
	public JsonObject toJson(Goal.ReachHeight goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("minY", goal.minY());
		json.addProperty("maxY", goal.maxY());
		return json;
	}

	@Override
	public String keyPart(Goal.ReachHeight goal) {
		return goal.minY() + "-" + goal.maxY();
	}

	@Override
	public boolean evaluate(Goal.ReachHeight goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		return obs.y >= goal.minY() && obs.y <= goal.maxY();
	}

	@Override
	public Verb verb(Goal.ReachHeight goal, VerbTable table) {
		return goal.minY() > 0 || goal.maxY() > 0 ? Verb.CLIMB : Verb.DESCEND;
	}

	@Override
	public String korean(Goal.ReachHeight goal, SentenceContext c) {
		if (goal.minY() > 0) {
			return SentenceBuilder.phrase("높이 " + goal.minY() + "블록 위로", c.verb(), c.level(), c.finale());
		}
		if (goal.maxY() <= 0) {
			return SentenceBuilder.phrase("높이 " + goal.maxY() + " 이하로", c.verb(), c.level(), c.finale());
		}
		if (goal.minY() <= 0) {
			return SentenceBuilder.phrase("땅 위로", Verb.CLIMB, c.level(), c.finale());
		}
		return SentenceBuilder.phrase("땅속으로", Verb.DESCEND, c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.ReachHeight goal, String obj, Verb verb) {
		if (goal.minY() > 0) {
			return "높이 " + goal.minY() + "블록 위로 " + Verb.CLIMB.past();
		}
		if (goal.maxY() <= 0) {
			return "높이 " + goal.maxY() + " 이하로 " + Verb.DESCEND.past();
		}
		return goal.minY() <= 0 ? "땅 위로 " + Verb.CLIMB.past() : "땅속으로 " + Verb.DESCEND.past();
	}

	@Override
	public String status(Goal.ReachHeight goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.yText(obs.y, goal.minY(), goal.maxY());
	}

	@Override
	public String object(Goal.ReachHeight goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}
}
