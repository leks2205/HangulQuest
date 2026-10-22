package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
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
 * Sit in a boat or a minecart (앉아요), or ride a horse, a pig or another mount (타요). Unlike
 * INTERACT_ENTITY, which sees the click on the animal, this watches the vehicle state of the player: the
 * client knows what the player is riding. Every boat is one boat, every cart one cart.
 */
final class MountType implements GoalType<Goal.Mount> {
	private static final int DEFAULT_DWELL_MS = 1500;

	@Override
	public String id() {
		return "MOUNT";
	}

	@Override
	public Class<Goal.Mount> goalClass() {
		return Goal.Mount.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("entity"), Field.integer("dwellMs", 500, 30000, false));
	}

	@Override
	public Goal.Mount parse(JsonObject json) {
		return new Goal.Mount(JsonFields.string(json, "entity", ""), JsonFields.integer(json, "dwellMs", DEFAULT_DWELL_MS));
	}

	@Override
	public JsonObject toJson(Goal.Mount goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("entity", goal.entity());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.Mount goal) {
		return goal.entity() + "/" + goal.dwellMs();
	}

	@Override
	public boolean evaluate(Goal.Mount goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		boolean on = obs.vehicle != null && Matching.sameVehicle(goal.entity(), obs.vehicle);
		return Matching.dwell(tracker, "ride", on ? "ride" : null, goal.dwellMs());
	}

	@Override
	public long dwellNeededMs(Goal.Mount goal) {
		return goal.dwellMs();
	}

	/** A seat (boat, cart) is sat in; an animal is ridden. */
	@Override
	public Verb verb(Goal.Mount goal, VerbTable table) {
		return Matching.vehicleFamily(goal.entity()).isEmpty() ? Verb.RIDE : Verb.SIT;
	}

	@Override
	public List<Verb> alternativeVerbs(Goal.Mount goal) {
		return Matching.vehicleFamily(goal.entity()).isEmpty() ? List.of() : List.of(Verb.RIDE);
	}

	@Override
	public String korean(Goal.Mount goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.Mount goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String status(Goal.Mount goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.look(tracker.dwellMs, goal.dwellMs());
	}

	@Override
	public String object(Goal.Mount goal) {
		return goal.entity();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ENTITY;
	}

	/** The word for a boat covers every boat: the concept lists one, the family the rest. */
	@Override
	public boolean covers(Goal.Mount goal, Concept concept) {
		return GoalType.super.covers(goal, concept)
				|| concept.entity != null && Matching.sameVehicle(goal.entity(), concept.entity);
	}

	@Override
	public void validate(Goal.Mount goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.dwellMs() < 500) {
			context.problem("dwellMs must be at least 500");
		}
	}
}
