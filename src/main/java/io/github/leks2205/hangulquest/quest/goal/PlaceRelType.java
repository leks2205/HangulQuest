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
import io.github.leks2205.hangulquest.quest.spatial.Anchor;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Placements;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.Relations;
import io.github.leks2205.hangulquest.quest.spatial.SpatialRules;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.Arrays;
import java.util.List;

/** Place a block in a relation to an anchor block: 문 옆에 횃불을 놓아요. */
final class PlaceRelType implements GoalType<Goal.PlaceRel> {
	@Override
	public String id() {
		return "PLACE_REL";
	}

	@Override
	public Class<Goal.PlaceRel> goalClass() {
		return Goal.PlaceRel.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"),
				Field.choice("relation", true, "Where the block goes, relative to the anchor.",
						Arrays.stream(Relation.values()).map(Enum::name).toArray(String[]::new)),
				Field.resource("anchor"), Field.count("count"));
	}

	@Override
	public Goal.PlaceRel parse(JsonObject json) {
		return new Goal.PlaceRel(JsonFields.string(json, "block", ""),
				Relation.byId(JsonFields.string(json, "relation", ""), Relation.ON_TOP_OF),
				JsonFields.string(json, "anchor", ""), JsonFields.integer(json, "count", 1));
	}

	@Override
	public JsonObject toJson(Goal.PlaceRel goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("relation", goal.relation().name());
		json.addProperty("anchor", goal.anchor());
		json.addProperty("count", goal.count());
		return json;
	}

	@Override
	public String keyPart(Goal.PlaceRel goal) {
		return goal.block() + "/" + goal.relation() + "/" + goal.anchor() + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.PlaceRel goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		Placements.evaluate(goal.block(), goal.relation(), new Anchor.Block(goal.anchor()), obs, tracker,
				nightLatch);
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
	public int needed(Goal.PlaceRel goal) {
		return goal.count();
	}

	@Override
	public Verb verb(Goal.PlaceRel goal, VerbTable table) {
		return table.choose(id(), goal.block(), goal.count(), Verb.PLACE);
	}

	@Override
	public String korean(Goal.PlaceRel goal, SentenceContext c) {
		return SpatialSentences.placeRel(goal, c.obj(), c.verb(), c.level(), c.withCounts(), c.finale(), false,
				c.namer()).ko();
	}

	@Override
	public String ceremony(Goal.PlaceRel goal, String obj, Verb verb) {
		return SpatialSentences.placeRel(goal, obj, verb, 1, true, false, true, SentenceBuilder.Namer.NONE).ko();
	}

	@Override
	public String ceremony(Goal.PlaceRel goal, SentenceContext c) {
		return SpatialSentences.placeRel(goal, c.obj(), c.verb(), 1, true, false, true, c.namer()).ko();
	}

	@Override
	public List<String> grammarIds(Goal.PlaceRel goal, SentenceContext c) {
		return SpatialSentences.grammarPoints(goal, List.of(), c.level(), c.withCounts(), false);
	}

	@Override
	public String status(Goal.PlaceRel goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return Placements.status(goal.relation(), tracker, obs, goal.count(), true);
	}

	@Override
	public String object(Goal.PlaceRel goal) {
		return BlockSpec.representative(goal.block());
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public String taskSignature(Goal.PlaceRel goal) {
		return id() + ":" + goal.block() + ":" + goal.relation() + ":" + goal.anchor() + ":"
				+ verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.PlaceRel goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!BlockSpec.valid(goal.anchor())) {
			context.problem("bad anchor " + goal.anchor());
			return;
		}
		if (!Relations.supported(goal.relation(), new Anchor.Block(goal.anchor()))) {
			context.problem(goal.relation() + " does not fit the anchor " + goal.anchor()
					+ (goal.relation().needsFacing() ? " (it has no clear front)" : ""));
		}
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
		String name = SpatialRules.namer(context.catalog()).block(BlockSpec.representative(goal.anchor()));
		if (name == null || name.isBlank()) {
			context.problem("the anchor " + goal.anchor() + " has no Korean name");
		}
		SpatialRules.checkRelationText(goal, goal.relation(), context);
	}
}
