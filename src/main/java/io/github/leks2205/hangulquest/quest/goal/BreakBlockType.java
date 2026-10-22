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
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
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
				Field.choice("verb", false, "Picks the verb of the generated sentence: any verb the verb table "
						+ "(verbs.json) uses for breaking, e.g. 캐다 / 베다 / 파다 / 꺾다 / 수확하다.", verbNames()));
	}

	/** The verbs of the BREAK_BLOCK rules in verbs.json; the three classic ones when the table is missing. */
	static String[] verbNames() {
		List<String> names = VerbTable.shared().verbsFor("BREAK_BLOCK").stream().map(Verb::name).toList();
		return names.isEmpty() ? new String[]{"MINE", "CHOP", "HARVEST"} : names.toArray(new String[0]);
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
		// a tag chooses the verb by the block that stands for it (꽃 -> 꺾다)
		return Verb.byId(goal.verb(), table.choose(id(), BlockSpec.representative(goal.block()), goal.count(),
				Verb.BREAK));
	}

	@Override
	public String korean(Goal.BreakBlock goal, SentenceContext c) {
		return SentenceBuilder.phrase(SentenceBuilder.counted(SentenceBuilder.objectPhrase(c.obj(), c.verb()),
				goal.count(), c.withCounts(), BlockSpec.representative(goal.block()), c.obj()), c.verb(), c.level(),
				c.finale());
	}

	@Override
	public String ceremony(Goal.BreakBlock goal, String obj, Verb verb) {
		return SentenceBuilder.counted(SentenceBuilder.objectPhrase(obj, verb), goal.count(), true,
				BlockSpec.representative(goal.block()), obj)
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

	/** Any flower, any sapling: a known tag is a block spec here. */
	@Override
	public boolean tagObject() {
		return true;
	}

	@Override
	public void validate(Goal.BreakBlock goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.verb() != null && !VerbTable.shared().verbsFor(id()).isEmpty()
				&& VerbTable.shared().verbsFor(id()).stream().noneMatch(v -> v.name().equalsIgnoreCase(goal.verb()))) {
			context.problem("the verb " + goal.verb() + " does not fit breaking a block; the verb table knows "
					+ VerbTable.shared().verbsFor(id()).stream().map(Verb::name).toList());
		}
	}
}
