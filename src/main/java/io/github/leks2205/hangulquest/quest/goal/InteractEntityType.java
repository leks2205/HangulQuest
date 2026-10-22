package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;

/** Interact with an entity: feed it, talk to it, trade with it. */
final class InteractEntityType implements GoalType<Goal.InteractEntity> {
	@Override
	public String id() {
		return "INTERACT_ENTITY";
	}

	@Override
	public Class<Goal.InteractEntity> goalClass() {
		return Goal.InteractEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"));
	}

	@Override
	public Goal.InteractEntity parse(JsonObject json) {
		return new Goal.InteractEntity(JsonFields.string(json, "entity", ""));
	}

	@Override
	public JsonObject toJson(Goal.InteractEntity goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		return json;
	}

	@Override
	public String keyPart(Goal.InteractEntity goal) {
		return goal.entity();
	}

	@Override
	public boolean evaluate(Goal.InteractEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		for (String interacted : obs.interactedEntities) {
			if (Matching.sameEntity(tracker, goal.entity(), interacted)) {
				tracker.count++;
			}
		}
		return tracker.count >= 1;
	}

	@Override
	public Verb verb(Goal.InteractEntity goal, VerbTable table) {
		return table.choose(id(), goal.entity(), 1, Verb.FEED);
	}

	@Override
	public String korean(Goal.InteractEntity goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.InteractEntity goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.prefix() + verb.past();
	}

	@Override
	public String status(Goal.InteractEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return "";
	}

	@Override
	public String object(Goal.InteractEntity goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}
}
