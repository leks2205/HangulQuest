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

/** Hold a number of an item that was not in the inventory when the step began. */
final class AcquireItemType implements GoalType<Goal.AcquireItem> {
	@Override
	public String id() {
		return "ACQUIRE_ITEM";
	}

	@Override
	public Class<Goal.AcquireItem> goalClass() {
		return Goal.AcquireItem.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.count("count"));
	}

	@Override
	public Goal.AcquireItem parse(JsonObject json) {
		return new Goal.AcquireItem(JsonFields.string(json, "item", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.AcquireItem goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.AcquireItem goal) {
		return goal.item() + "/" + goal.count();
	}

	@Override
	public void beforeTick(Goal.AcquireItem goal, Observation obs, GoalEvaluator.Tracker tracker) {
		// The baseline is taken even while conditions fail, so items picked up meanwhile count later.
		GoalEvaluator.baselineAcquired(tracker, Matching.heldCount(obs, goal.item(), tracker),
				obs.inventorySettling, obs.screenOpen);
	}

	@Override
	public boolean evaluate(Goal.AcquireItem goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		if (!obs.inventorySettling) {
			GoalEvaluator.trackAcquired(tracker, Matching.heldCount(obs, goal.item(), tracker));
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.AcquireItem goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.AcquireItem goal, VerbTable table) {
		return table.choose(id(), goal.item(), goal.count(), Verb.COLLECT);
	}

	@Override
	public String korean(Goal.AcquireItem goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts(), goal.item(), c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.AcquireItem goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.item(), obj)
				+ " " + verb.past();
	}

	@Override
	public String status(Goal.AcquireItem goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		// Only items gained since the step began count (GoalEvaluator.baselineAcquired).
		return word + " " + Math.min(tracker.count, Math.max(1, goal.count())) + "/" + Math.max(1, goal.count());
	}

	@Override
	public String object(Goal.AcquireItem goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}
}
