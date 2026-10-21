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

/** Eat or drink a number of an item. */
final class ConsumeItemType implements GoalType<Goal.ConsumeItem> {
	@Override
	public String id() {
		return "CONSUME_ITEM";
	}

	@Override
	public Class<Goal.ConsumeItem> goalClass() {
		return Goal.ConsumeItem.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"), Field.count("count"));
	}

	@Override
	public Goal.ConsumeItem parse(JsonObject json) {
		return new Goal.ConsumeItem(JsonFields.string(json, "item", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.ConsumeItem goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.ConsumeItem goal) {
		return goal.item() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.ConsumeItem goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		List<String> eaten = nightLatch ? obs.consumedItemsAtNight : obs.consumedItems;
		for (String consumed : eaten) {
			if (consumed.equals(goal.item())) {
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
	public int needed(Goal.ConsumeItem goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.ConsumeItem goal, VerbTable table) {
		return table.choose(id(), goal.item(), goal.count(), Verb.EAT);
	}

	@Override
	public String korean(Goal.ConsumeItem goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.ConsumeItem goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true)
				+ " " + verb.past();
	}

	@Override
	public String status(Goal.ConsumeItem goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.ConsumeItem goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}
}
