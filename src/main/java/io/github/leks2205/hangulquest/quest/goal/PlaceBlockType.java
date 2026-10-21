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

/** Place a number of blocks. */
final class PlaceBlockType implements GoalType<Goal.PlaceBlock> {
	@Override
	public String id() {
		return "PLACE_BLOCK";
	}

	@Override
	public Class<Goal.PlaceBlock> goalClass() {
		return Goal.PlaceBlock.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"), Field.count("count"));
	}

	@Override
	public Goal.PlaceBlock parse(JsonObject json) {
		return new Goal.PlaceBlock(JsonFields.string(json, "block", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.PlaceBlock goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.PlaceBlock goal) {
		return goal.block() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.PlaceBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		List<String> placements = nightLatch ? obs.placedBlocksAtNight : obs.placedBlocks;
		for (String placed : placements) {
			if (Matching.accepts(tracker, goal.block(), placed)) {
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
	public int needed(Goal.PlaceBlock goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.PlaceBlock goal, VerbTable table) {
		return table.choose(id(), goal.block(), goal.count(), Verb.PLACE);
	}

	@Override
	public String korean(Goal.PlaceBlock goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts(), goal.block(), c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.PlaceBlock goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true, goal.block(), obj)
				+ " " + verb.past();
	}

	@Override
	public String status(Goal.PlaceBlock goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.count(tracker.count, goal.count());
	}

	@Override
	public String object(Goal.PlaceBlock goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}
}
