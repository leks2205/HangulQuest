package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.spatial.BlockSpec;
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.PatternMatcher;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.sense.Observation;
import io.github.leks2205.hangulquest.sense.PlacementFact;

import java.util.Arrays;
import java.util.List;

/** Build a small structure out of one material: 돌로 작은 피라미드를 쌓아요. */
final class BuildPatternType implements GoalType<Goal.BuildPattern> {
	@Override
	public String id() {
		return "BUILD_PATTERN";
	}

	@Override
	public Class<Goal.BuildPattern> goalClass() {
		return Goal.BuildPattern.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(
				Field.choice("pattern", true, "The shape to build.",
						Arrays.stream(Pattern.values()).map(Enum::name).toArray(String[]::new)),
				Field.resource("material"),
				Field.integer("size", 2, 4, false));
	}

	@Override
	public Goal.BuildPattern parse(JsonObject json) {
		Pattern pattern = Pattern.byId(JsonFields.string(json, "pattern", ""), Pattern.PILLAR);
		return new Goal.BuildPattern(pattern, JsonFields.string(json, "material", ""),
				JsonFields.integer(json, "size", pattern.defaultSize()));
	}

	@Override
	public JsonObject toJson(Goal.BuildPattern goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("pattern", goal.pattern().name());
		json.addProperty("material", goal.material());
		json.addProperty("size", goal.size());
		return json;
	}

	@Override
	public String keyPart(Goal.BuildPattern goal) {
		return goal.pattern() + "/" + goal.material() + "/" + goal.size();
	}

	@Override
	public boolean evaluate(Goal.BuildPattern goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		for (PlacementFact fact : obs.placements) {
			if (nightLatch && !fact.night()) {
				continue;
			}
			String material = tracker.accepted.union(goal.material());
			tracker.progress = Math.max(tracker.progress,
					PatternMatcher.progress(fact.around(), goal.pattern(), goal.size(), material));
			if (!PatternMatcher.builtWith(fact.around(), goal.pattern(), goal.size(), material).isEmpty()) {
				tracker.count++;
			}
		}
		return tracker.count >= 1;
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	/** A row is laid out (놓다), a cross and stairs are made (만들다); everything else is piled up (쌓다). */
	@Override
	public Verb verb(Goal.BuildPattern goal, VerbTable table) {
		if (goal.pattern() == Pattern.ROW) {
			return Verb.PLACE;
		}
		if (goal.pattern() == Pattern.CROSS || goal.pattern() == Pattern.STAIRS) {
			return Verb.CRAFT;
		}
		return table.choose(id(), goal.material(), 1, Verb.STACK);
	}

	@Override
	public String korean(Goal.BuildPattern goal, SentenceContext c) {
		return SpatialSentences.buildPattern(goal, c.obj(), c.verb(), c.level(), c.withCounts(), c.finale(),
				false).ko();
	}

	@Override
	public String ceremony(Goal.BuildPattern goal, String obj, Verb verb) {
		return SpatialSentences.buildPattern(goal, obj, verb, 1, true, false, true).ko();
	}

	@Override
	public List<String> grammarIds(Goal.BuildPattern goal, SentenceContext c) {
		return SpatialSentences.grammarPoints(goal, List.of(), c.level(), c.withCounts(), false);
	}

	@Override
	public String status(Goal.BuildPattern goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		int total = goal.pattern().cells(goal.size(), 0).size();
		return QuestStatus.count(tracker.count >= 1 ? total : Math.min(tracker.progress, total), total);
	}

	@Override
	public String object(Goal.BuildPattern goal) {
		return BlockSpec.representative(goal.material());
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public String taskSignature(Goal.BuildPattern goal) {
		return id() + ":" + goal.pattern() + "/" + goal.size() + ":" + goal.material() + ":"
				+ verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.BuildPattern goal, ValidationContext context) {
		if (!BlockSpec.valid(goal.material())) {
			context.problem("bad material " + goal.material());
			return;
		}
		GoalType.super.validate(goal, context);
		if (!goal.pattern().validSize(goal.size())) {
			context.problem(goal.pattern() + " cannot be size " + goal.size() + " (allowed "
					+ goal.pattern().minSize() + ".." + goal.pattern().maxSize() + ")");
		}
	}
}
