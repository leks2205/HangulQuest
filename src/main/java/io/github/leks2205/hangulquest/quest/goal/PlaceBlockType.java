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
import io.github.leks2205.hangulquest.quest.spatial.Placements;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;

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
		if (tracker.roofedPlacement) {
			// a block under a roof: only a placement with a roof over the block itself counts
			for (PlacementFact fact : obs.placements) {
				if (fact.roofed() && (!nightLatch || fact.night()) && Matching.accepts(tracker, goal.block(), fact.block())) {
					tracker.count++;
					Placements.remember(tracker, fact);
				}
			}
			return tracker.count >= Math.max(1, goal.count());
		}
		List<String> placements = nightLatch ? obs.placedBlocksAtNight : obs.placedBlocks;
		for (String placed : placements) {
			if (Matching.accepts(tracker, goal.block(), placed)) {
				tracker.count++;
			}
		}
		for (PlacementFact fact : obs.placements) {
			if (Matching.accepts(tracker, goal.block(), fact.block())) {
				Placements.remember(tracker, fact);
			}
		}
		return tracker.count >= Math.max(1, goal.count());
	}

	/** 지붕 아래에 통을 놓아요: the roof is over the barrel, wherever the player stands. */
	@Override
	public boolean takesOver(Goal.PlaceBlock goal, io.github.leks2205.hangulquest.quest.Condition condition) {
		return condition instanceof io.github.leks2205.hangulquest.quest.Condition.Sky sky
				&& sky.state() == io.github.leks2205.hangulquest.quest.Condition.SkyState.ROOFED;
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
