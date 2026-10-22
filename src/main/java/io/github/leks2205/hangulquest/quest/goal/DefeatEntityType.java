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

/** Defeat a number of entities. */
final class DefeatEntityType implements GoalType<Goal.DefeatEntity> {
	@Override
	public String id() {
		return "DEFEAT_ENTITY";
	}

	@Override
	public Class<Goal.DefeatEntity> goalClass() {
		return Goal.DefeatEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.count("count"));
	}

	@Override
	public Goal.DefeatEntity parse(JsonObject json) {
		return new Goal.DefeatEntity(JsonFields.string(json, "entity", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.DefeatEntity goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.DefeatEntity goal) {
		return goal.entity() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.DefeatEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		List<String> kills = nightLatch ? obs.defeatedEntitiesAtNight : obs.defeatedEntities;
		for (String defeated : kills) {
			if (Matching.sameEntity(tracker, goal.entity(), defeated)) {
				tracker.count++;
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.DefeatEntity goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.DefeatEntity goal, VerbTable table) {
		return Verb.DEFEAT;
	}

	@Override
	public String korean(Goal.DefeatEntity goal, SentenceContext c) {
		return SentenceBuilder.phrase(c.withCounts()
				? KoGrammar.eul(c.obj()) + " " + SentenceBuilder.counter(goal.count(), "마리")
				: KoGrammar.eul(c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.DefeatEntity goal, String obj, Verb verb) {
		return KoGrammar.eul(obj) + " "
				+ (goal.count() > 1 ? SentenceBuilder.counter(goal.count(), "마리") + " " : "") + verb.past();
	}

	@Override
	public String status(Goal.DefeatEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.DefeatEntity goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}
}
