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
import io.github.leks2205.hangulquest.quest.spatial.StepRef;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
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
				Field.anchor("anchor", true, "The block, family or tag the relation is measured against, or @stepId "
						+ "for the block placed in that earlier step of the trail (trail level 5 or higher)."),
				Field.count("count"),
				Field.optionalResource("anchor2", "With BETWEEN: the second block, so the Korean says A와/과 B 사이에 "
						+ "instead of 두 A 사이에."));
	}

	@Override
	public Goal.PlaceRel parse(JsonObject json) {
		String second = JsonFields.string(json, "anchor2", null);
		return new Goal.PlaceRel(JsonFields.string(json, "block", ""),
				Relation.byId(JsonFields.string(json, "relation", ""), Relation.ON_TOP_OF),
				JsonFields.string(json, "anchor", ""), JsonFields.integer(json, "count", 1),
				second == null || second.isBlank() ? null : second);
	}

	@Override
	public JsonObject toJson(Goal.PlaceRel goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("relation", goal.relation().name());
		json.addProperty("anchor", goal.anchor());
		json.addProperty("count", goal.count());
		if (goal.anchor2() != null) {
			json.addProperty("anchor2", goal.anchor2());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.PlaceRel goal) {
		return goal.block() + "/" + goal.relation() + "/" + goal.anchor()
				+ (goal.anchor2() == null ? "" : "+" + goal.anchor2()) + "/" + goal.count();
	}

	@Override
	public boolean evaluate(Goal.PlaceRel goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		Placements.evaluate(goal.block(), goal.relation(), anchorOf(goal, tracker), obs, tracker, nightLatch);
		return tracker.count >= Math.max(1, goal.count());
	}

	/**
	 * What the placement is measured against. An {@code @stepId} anchor is the spot remembered from that
	 * step; if the memory is gone (an old save), any block of the kind that step placed will do.
	 */
	private static Anchor anchorOf(Goal.PlaceRel goal, GoalEvaluator.Tracker tracker) {
		if (StepRef.isRef(goal.anchor())) {
			String step = StepRef.stepId(goal.anchor());
			int[] at = tracker.placed.get(step);
			return at != null ? new Anchor.Placed(step, at[0], at[1], at[2])
					: new Anchor.Block(tracker.refBlocks.getOrDefault(step, ""));
		}
		Anchor.Block first = new Anchor.Block(tracker.accepted.union(goal.anchor()));
		return goal.anchor2() == null ? first
				: new Anchor.Pair(first, new Anchor.Block(tracker.accepted.union(goal.anchor2())));
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
	public List<String> decidingForms(Goal.PlaceRel goal) {
		String id = goal.relation().grammarId();
		return id == null ? List.of() : List.of(id);
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

	/** The word may teach the block or an anchor; "the block from step 2" is no id the word could name. */
	@Override
	public List<Mention> mentions(Goal.PlaceRel goal) {
		List<Mention> all = new ArrayList<>(List.of(new Mention(ObjectKind.BLOCK, goal.block())));
		if (!StepRef.isRef(goal.anchor())) {
			all.add(new Mention(ObjectKind.BLOCK, goal.anchor()));
		}
		if (goal.anchor2() != null) {
			all.add(new Mention(ObjectKind.BLOCK, goal.anchor2()));
		}
		return all;
	}

	@Override
	public String taskSignature(Goal.PlaceRel goal) {
		// "A와 B 사이" and "B와 A 사이" are one task: the pair is sorted
		String anchors = goal.anchor2() == null ? goal.anchor()
				: goal.anchor().compareTo(goal.anchor2()) <= 0 ? goal.anchor() + "+" + goal.anchor2()
				: goal.anchor2() + "+" + goal.anchor();
		return id() + ":" + goal.block() + ":" + goal.relation() + ":" + anchors + ":"
				+ verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.PlaceRel goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.count() < 1) {
			context.problem("count must be at least 1");
		}
		if (goal.anchor2() != null && StepRef.isRef(goal.anchor())) {
			context.problem("BETWEEN needs two blocks, not a block from an earlier step");
			return;
		}
		if (StepRef.isRef(goal.anchor())) {
			if (!validateRef(goal, context)) {
				return;
			}
		} else {
			if (!BlockSpec.valid(goal.anchor())) {
				context.problem("bad anchor " + goal.anchor());
				return;
			}
			if (goal.anchor2() == null && !Relations.supported(goal.relation(), new Anchor.Block(goal.anchor()))) {
				context.problem(goal.relation() + " does not fit the anchor " + goal.anchor()
						+ (goal.relation().needsFacing() ? " (it has no clear front)" : ""));
			}
			nameOrProblem(goal.anchor(), context);
		}
		if (goal.anchor2() != null) {
			validateSecond(goal, context);
		}
		SpatialRules.checkRelationText(goal, goal.relation(), context);
	}

	private static void nameOrProblem(String spec, ValidationContext context) {
		String name = SpatialSentences.blockName(spec, SpatialRules.namer(context.catalog()));
		if (name == null || name.isBlank()) {
			context.problem("the anchor " + spec + " has no Korean name");
		}
	}

	/** BETWEEN with two anchors: both are blocks, they differ, and nothing else takes a second one. */
	private static void validateSecond(Goal.PlaceRel goal, ValidationContext context) {
		if (goal.relation() != Relation.BETWEEN) {
			context.problem("anchor2 only goes with BETWEEN (" + goal.relation() + " has one anchor)");
			return;
		}
		if (StepRef.isRef(goal.anchor2())) {
			context.problem("BETWEEN needs two blocks, not a block from an earlier step");
			return;
		}
		if (!BlockSpec.valid(goal.anchor2())) {
			context.problem("bad anchor2 " + goal.anchor2());
			return;
		}
		if (goal.anchor2().equals(goal.anchor())) {
			context.problem("anchor2 is the same as anchor: leave it out for \"two of them\"");
		}
		nameOrProblem(goal.anchor2(), context);
	}

	/** An {@code @stepId} anchor: a block-placing step earlier in the same trail, and a relation that makes sense for a spot. */
	private static boolean validateRef(Goal.PlaceRel goal, ValidationContext context) {
		if (!StepRef.wellFormed(goal.anchor())) {
			context.problem("bad step reference " + goal.anchor() + " (write @ and a step id)");
			return false;
		}
		String step = StepRef.stepId(goal.anchor());
		if (!context.step().placedBefore().containsKey(step)) {
			context.problem("the anchor " + goal.anchor() + " needs an earlier step '" + step
					+ "' of this trail whose goal places a block");
			return false;
		}
		if (!Relations.supported(goal.relation(), new Anchor.Placed(step, 0, 0, 0))) {
			context.problem(goal.relation() + " does not fit a block from an earlier step");
		}
		if (context.koreanLevel() > 0 && context.koreanLevel() < StepRef.MIN_LEVEL) {
			context.problem("a block from an earlier step ('아까 놓은') needs koreanLevel " + StepRef.MIN_LEVEL
					+ " or higher (-(으)ㄴ before a noun)");
		}
		return true;
	}
}
