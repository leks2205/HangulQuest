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

/**
 * Go into an end gateway. The client cannot see the block being entered, but it sees the throw: a sudden jump of
 * the player's position from beside an end gateway. The sensor sets {@link Observation#gatewayUsed} on that tick.
 */
final class EnterGatewayType implements GoalType<Goal.EnterGateway> {
	static final String GATEWAY = "minecraft:end_gateway";

	@Override
	public String id() {
		return "ENTER_GATEWAY";
	}

	@Override
	public Class<Goal.EnterGateway> goalClass() {
		return Goal.EnterGateway.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("block"));
	}

	@Override
	public Goal.EnterGateway parse(JsonObject json) {
		return new Goal.EnterGateway(JsonFields.string(json, "block", GATEWAY));
	}

	@Override
	public JsonObject toJson(Goal.EnterGateway goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("block", goal.block());
		return json;
	}

	@Override
	public String keyPart(Goal.EnterGateway goal) {
		return goal.block();
	}

	@Override
	public boolean evaluate(Goal.EnterGateway goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		if (obs.gatewayUsed) {
			tracker.count++;
		}
		return tracker.count >= 1;
	}

	@Override
	public Verb verb(Goal.EnterGateway goal, VerbTable table) {
		return Verb.ENTER;
	}

	@Override
	public String korean(Goal.EnterGateway goal, SentenceContext c) {
		return SentenceBuilder.phrase(c.obj() + "에", c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.EnterGateway goal, String obj, Verb verb) {
		return obj + "에 " + verb.past();
	}

	@Override
	public List<String> grammarIds(Goal.EnterGateway goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(SentenceBuilder.baseGrammar(c.verb(), c.level(), ObjectKind.NONE));
		ids.add("L1.e_place"); // 관문에: the place the body goes to, not an object marker
		return ids;
	}

	@Override
	public String status(Goal.EnterGateway goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return "엔드 관문 옆에 서서 들어가세요";
	}

	@Override
	public String object(Goal.EnterGateway goal) {
		return goal.block();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BLOCK;
	}

	@Override
	public void validate(Goal.EnterGateway goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!GATEWAY.equals(goal.block())) {
			context.problem("only the end gateway can be entered, not " + goal.block());
		}
	}
}
