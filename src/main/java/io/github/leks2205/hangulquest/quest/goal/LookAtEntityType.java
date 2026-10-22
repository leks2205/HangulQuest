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

/** Keep the crosshair on an entity for a moment. */
final class LookAtEntityType implements GoalType<Goal.LookAtEntity> {
	@Override
	public String id() {
		return "LOOK_AT_ENTITY";
	}

	@Override
	public Class<Goal.LookAtEntity> goalClass() {
		return Goal.LookAtEntity.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.integer("dwellMs", 100, 10000, true));
	}

	@Override
	public Goal.LookAtEntity parse(JsonObject json) {
		return new Goal.LookAtEntity(JsonFields.string(json, "entity", ""), JsonFields.integer(json, "dwellMs", 800));
	}

	@Override
	public JsonObject toJson(Goal.LookAtEntity goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.LookAtEntity goal) {
		return goal.entity() + "/" + goal.dwellMs();
	}

	@Override
	public boolean evaluate(Goal.LookAtEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		String seen = Matching.sameEntity(tracker, goal.entity(), obs.lookedEntity) ? goal.entity() : null;
		return Matching.dwell(tracker, goal.entity(), seen, goal.dwellMs());
	}

	@Override
	public boolean needsFarEntityLook() {
		return true;
	}

	@Override
	public long dwellNeededMs(Goal.LookAtEntity goal) {
		return goal.dwellMs();
	}

	@Override
	public Verb verb(Goal.LookAtEntity goal, VerbTable table) {
		return Verb.WATCH;
	}

	@Override
	public String korean(Goal.LookAtEntity goal, SentenceContext c) {
		return SentenceBuilder.phrase(KoGrammar.eul(c.obj()) + " " + SentenceBuilder.secondsPhrase(goal.dwellMs()),
				c.verb(), c.level(), c.finale());
	}

	@Override
	public List<String> grammarIds(Goal.LookAtEntity goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(GoalType.super.grammarIds(goal, c));
		if (c.level() >= 2) {
			ids.add("L2.num_sino"); // 삼 초
		}
		return ids;
	}

	@Override
	public String ceremony(Goal.LookAtEntity goal, String obj, Verb verb) {
		return KoGrammar.eul(obj) + " " + verb.past();
	}

	@Override
	public String status(Goal.LookAtEntity goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.look(tracker.dwellMs, goal.dwellMs());
	}

	@Override
	public String object(Goal.LookAtEntity goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}
}
