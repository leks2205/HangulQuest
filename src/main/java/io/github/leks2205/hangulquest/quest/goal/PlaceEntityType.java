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

/**
 * Set down a creature-like object: an end crystal on obsidian or bedrock. The sensor reports one that appeared
 * next to the player while the item was in the hand, so a crystal that was always there does not count.
 */
final class PlaceEntityType implements GoalType<Goal.PlaceEntity> {
	@Override
	public String id() {
		return "PLACE_ENTITY";
	}

	@Override
	public Class<Goal.PlaceEntity> goalClass() {
		return Goal.PlaceEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.integer("count", 1, 64, false));
	}

	@Override
	public Goal.PlaceEntity parse(JsonObject json) {
		return new Goal.PlaceEntity(JsonFields.string(json, "entity", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.PlaceEntity goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.PlaceEntity goal) {
		return goal.entity() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.PlaceEntity goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		for (String placed : obs.placedEntities) {
			if (placed.equals(goal.entity())) {
				tracker.count++;
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.PlaceEntity goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.PlaceEntity goal, VerbTable table) {
		return Verb.PLACE;
	}

	@Override
	public String korean(Goal.PlaceEntity goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts(), goal.entity(), c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.PlaceEntity goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.entity(), obj)
				+ " " + verb.past();
	}

	@Override
	public String status(Goal.PlaceEntity goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.PlaceEntity goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}

	@Override
	public void validate(Goal.PlaceEntity goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!"minecraft:end_crystal".equals(goal.entity())) {
			context.problem("only an end crystal can be seen being set down, not " + goal.entity());
		}
		if (goal.count() < 1 || goal.count() > 64) {
			context.problem("count must be 1..64");
		}
	}
}
