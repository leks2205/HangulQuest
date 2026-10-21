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

/** Break a number of blocks. */
final class BreakBlockType implements GoalType<Goal.BreakBlock> {
	@Override
	public String id() {
		return "BREAK_BLOCK";
	}

	@Override
	public Class<Goal.BreakBlock> goalClass() {
		return Goal.BreakBlock.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"), Field.count("count"),
				Field.choice("verb", false, "Picks 캐다 / 베다 / 수확하다 in the generated sentence.",
						"MINE", "CHOP", "HARVEST"));
	}

	@Override
	public Goal.BreakBlock parse(JsonObject json) {
		return new Goal.BreakBlock(JsonFields.string(json, "block", ""), JsonFields.integer(json, "count", 1),
				JsonFields.string(json, "verb", null));
	}

	@Override
	public JsonObject toJson(Goal.BreakBlock goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("count", goal.count());
		if (goal.verb() != null) {
			json.addProperty("verb", goal.verb());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.BreakBlock goal) {
		return goal.block() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.BreakBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		for (String broken : obs.brokenBlocks) {
			if (Matching.accepts(tracker, goal.block(), broken)) {
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
	public int needed(Goal.BreakBlock goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.BreakBlock goal, VerbTable table) {
		return Verb.byId(goal.verb(), table.choose(id(), goal.block(), goal.count(), Verb.BREAK));
	}

	@Override
	public String korean(Goal.BreakBlock goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts(), goal.block(), c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.BreakBlock goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.block(), obj)
				+ " " + verb.past();
	}

	@Override
	public String status(Goal.BreakBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.BreakBlock goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}
}
