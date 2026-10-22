package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Locale;

/**
 * Go a distance in a compass direction: "북쪽으로 가요", from level 2 on "북쪽으로 이십 블록 가세요". The distance is
 * what the player has moved along the direction, from the change of position between ticks; a step backwards takes
 * from it, and a jump of more than eight blocks between two ticks (a teleport, a portal) is not walking. The verb is
 * 가다, because the goal measures position only: sprinting, swimming and riding count as well.
 */
final class WalkType implements GoalType<Goal.Walk> {
	private static final int DEFAULT_BLOCKS = 20;
	private static final double JUMP = 8.0;

	@Override
	public String id() {
		return "WALK";
	}

	@Override
	public Class<Goal.Walk> goalClass() {
		return Goal.Walk.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.choice("direction", true, "A compass direction; north is -Z.",
						Directions.ALL.toArray(String[]::new)),
				Field.integer("blocks", 2, 1000, false));
	}

	@Override
	public Goal.Walk parse(JsonObject json) {
		return new Goal.Walk(JsonFields.string(json, "direction", "NORTH").toUpperCase(Locale.ROOT),
				JsonFields.integer(json, "blocks", DEFAULT_BLOCKS));
	}

	@Override
	public JsonObject toJson(Goal.Walk goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("direction", goal.direction());
		json.addProperty("blocks", goal.blocks());
		return json;
	}

	@Override
	public String keyPart(Goal.Walk goal) {
		return goal.direction() + ":" + goal.blocks();
	}

	@Override
	public boolean evaluate(Goal.Walk goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		double x = obs.position.x;
		double z = obs.position.z;
		if (tracker.at != null) {
			double dx = x - tracker.at[0];
			double dz = z - tracker.at[1];
			if (Math.hypot(dx, dz) < JUMP) {
				tracker.distance = Math.max(0.0, tracker.distance + Directions.along(goal.direction(), dx, dz));
			}
		}
		tracker.at = new double[] {x, z};
		tracker.count = (int) tracker.distance;
		return tracker.distance >= goal.blocks();
	}

	@Override
	public boolean countable() {
		return true;
	}

	@Override
	public int needed(Goal.Walk goal) {
		return goal.blocks();
	}

	@Override
	public Verb verb(Goal.Walk goal, VerbTable table) {
		return Verb.GO;
	}

	@Override
	public String korean(Goal.Walk goal, SentenceContext c) {
		return SentenceBuilder.phrase(head(goal, c.withCounts() && c.level() >= 2), c.verb(), c.level(), c.finale());
	}

	/** "북쪽으로" at the first level and in the hint, "북쪽으로 이십 블록" after. */
	private static String head(Goal.Walk goal, boolean withDistance) {
		String way = KoGrammar.euro(Directions.ko(goal.direction()));
		return withDistance ? way + " " + KoGrammar.blocks(goal.blocks()) : way;
	}

	@Override
	public String ceremony(Goal.Walk goal, String obj, Verb verb) {
		return head(goal, true) + " " + verb.past();
	}

	@Override
	public List<String> grammarIds(Goal.Walk goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>();
		ids.add(io.github.leks2205.hangulquest.quest.grammar.Ladder.shared().rung(c.level()).grammar());
		ids.add("L2.euro_direction");
		if (c.level() >= 2) {
			ids.add("L2.num_sino");
		}
		return ids;
	}

	@Override
	public String status(Goal.Walk goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count((int) tracker.distance, goal.blocks());
	}

	@Override
	public String object(Goal.Walk goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	/** The direction is the handle: two walks that differ in it are two tasks. */
	@Override
	public String taskSignature(Goal.Walk goal) {
		return id() + ":" + goal.direction() + ":" + verb(goal, VerbTable.shared()).name();
	}

	@Override
	public void validate(Goal.Walk goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!Directions.valid(goal.direction())) {
			context.problem("direction must be one of " + Directions.ALL + ", not " + goal.direction());
		}
		if (goal.blocks() < 2 || goal.blocks() > 1000) {
			context.problem("blocks must be 2..1000");
		}
	}
}
