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
 * Lie down in a bed: "침대에 누워요". The player has to be in the bed for a moment, which the client sees
 * as the sleeping pose; unlike USE_BLOCK (자요) the step never waits for the night to pass. A bed can only
 * be entered at night or in a thunderstorm, so a step like this belongs to a night condition (the time
 * condition is excused once the server has skipped to the morning, as for 자요).
 */
final class LieDownType implements GoalType<Goal.LieDown> {
	private static final int DEFAULT_DWELL_MS = 2000;

	@Override
	public String id() {
		return "LIE_DOWN";
	}

	@Override
	public Class<Goal.LieDown> goalClass() {
		return Goal.LieDown.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"), Field.integer("dwellMs", 500, 30000, false));
	}

	@Override
	public Goal.LieDown parse(JsonObject json) {
		return new Goal.LieDown(JsonFields.string(json, "block", "minecraft:red_bed"),
				JsonFields.integer(json, "dwellMs", DEFAULT_DWELL_MS));
	}

	@Override
	public JsonObject toJson(Goal.LieDown goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.LieDown goal) {
		return goal.block() + "/" + goal.dwellMs();
	}

	@Override
	public boolean evaluate(Goal.LieDown goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		boolean inBed = obs.sleeping && obs.sleepingBed != null && Matching.matchesBlock(goal.block(), obs.sleepingBed);
		return Matching.dwell(tracker, "bed", inBed ? "bed" : null, goal.dwellMs());
	}

	@Override
	public long dwellNeededMs(Goal.LieDown goal) {
		return goal.dwellMs();
	}

	/** Being in bed is proof of the night, even after the server skipped to morning. */
	@Override
	public boolean excusesTimeCondition(Goal.LieDown goal, Observation obs) {
		return obs.sleeping && Matching.isBed(goal.block());
	}

	@Override
	public Verb verb(Goal.LieDown goal, VerbTable table) {
		return Verb.LIE;
	}

	@Override
	public String korean(Goal.LieDown goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.LieDown goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
	}

	@Override
	public String status(Goal.LieDown goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return obs.sleeping ? QuestStatus.look(tracker.dwellMs, goal.dwellMs()) : "침대에 누울 수 없어요";
	}

	@Override
	public String object(Goal.LieDown goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public void validate(Goal.LieDown goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!Matching.isBed(goal.block())) {
			context.problem("only a bed can be lain in, not " + goal.block());
		}
	}
}
