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
import io.github.leks2205.hangulquest.quest.spatial.Pattern;
import io.github.leks2205.hangulquest.quest.spatial.Placements;
import io.github.leks2205.hangulquest.quest.spatial.Relation;
import io.github.leks2205.hangulquest.quest.spatial.Relations;
import io.github.leks2205.hangulquest.quest.spatial.SpatialRules;
import io.github.leks2205.hangulquest.quest.spatial.SpatialSentences;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.Arrays;
import java.util.List;

/** Place a block in a relation to a structure the player built: 조약돌 기둥 위에 횃불을 놓아요. */
final class PlaceOnPatternType implements GoalType<Goal.PlaceOnPattern> {
	@Override
	public String id() {
		return "PLACE_ON_PATTERN";
	}

	@Override
	public Class<Goal.PlaceOnPattern> goalClass() {
		return Goal.PlaceOnPattern.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"),
				Field.choice("relation", true, "Where the block goes, relative to the structure.",
						Arrays.stream(Relation.values()).map(Enum::name).toArray(String[]::new)),
				Field.choice("pattern", true, "The shape of the structure.",
						Arrays.stream(Pattern.values()).map(Enum::name).toArray(String[]::new)),
				Field.resource("material"));
	}

	@Override
	public Goal.PlaceOnPattern parse(JsonObject json) {
		return new Goal.PlaceOnPattern(JsonFields.string(json, "block", ""),
				Relation.byId(JsonFields.string(json, "relation", ""), Relation.ON_TOP_OF),
				Pattern.byId(JsonFields.string(json, "pattern", ""), Pattern.PILLAR),
				JsonFields.string(json, "material", ""));
	}

	@Override
	public JsonObject toJson(Goal.PlaceOnPattern goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		json.addProperty("relation", goal.relation().name());
		json.addProperty("pattern", goal.pattern().name());
		json.addProperty("material", goal.material());
		return json;
	}

	@Override
	public String keyPart(Goal.PlaceOnPattern goal) {
		return goal.block() + "/" + goal.relation() + "/" + goal.pattern() + "/" + goal.material();
	}

	private static Anchor anchor(Goal.PlaceOnPattern goal) {
		return new Anchor.Built(goal.pattern(), goal.material());
	}

	@Override
	public boolean evaluate(Goal.PlaceOnPattern goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		Placements.evaluate(goal.block(), goal.relation(), anchor(goal), obs, tracker, nightLatch);
		return tracker.count >= 1;
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	@Override
	public Verb verb(Goal.PlaceOnPattern goal, VerbTable table) {
		return table.choose(id(), goal.block(), 1, Verb.PLACE);
	}

	@Override
	public String korean(Goal.PlaceOnPattern goal, SentenceContext c) {
		return SpatialSentences.placeOnPattern(goal, c.obj(), materialName(goal, c.namer()), c.verb(), c.level(),
				c.finale(), false).ko();
	}

	@Override
	public String ceremony(Goal.PlaceOnPattern goal, String obj, Verb verb) {
		return SpatialSentences.placeOnPattern(goal, obj, materialName(goal, SentenceBuilder.Namer.NONE), verb, 1,
				false, true).ko();
	}

	@Override
	public String ceremony(Goal.PlaceOnPattern goal, SentenceContext c) {
		return SpatialSentences.placeOnPattern(goal, c.obj(), materialName(goal, c.namer()), c.verb(), 1, false,
				true).ko();
	}

	@Override
	public List<String> grammarIds(Goal.PlaceOnPattern goal, SentenceContext c) {
		return SpatialSentences.grammarPoints(goal, List.of(), c.level(), c.withCounts(), false);
	}

	private static String materialName(Goal.PlaceOnPattern goal, SentenceBuilder.Namer namer) {
		String name = namer.block(BlockSpec.representative(goal.material()));
		return name == null ? "" : name;
	}

	@Override
	public String status(Goal.PlaceOnPattern goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return Placements.status(goal.relation(), tracker, obs, 1, false);
	}

	@Override
	public String object(Goal.PlaceOnPattern goal) {
		return BlockSpec.representative(goal.block());
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public String taskSignature(Goal.PlaceOnPattern goal) {
		return id() + ":" + goal.block() + ":" + goal.relation() + ":" + anchor(goal).key() + ":"
				+ verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.PlaceOnPattern goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!BlockSpec.valid(goal.material())) {
			context.problem("bad material " + goal.material());
			return;
		}
		if (!Relations.supported(goal.relation(), anchor(goal))) {
			context.problem(goal.relation() + " does not fit a " + goal.pattern());
		}
		SpatialRules.checkRelationText(goal, goal.relation(), context);
	}
}
