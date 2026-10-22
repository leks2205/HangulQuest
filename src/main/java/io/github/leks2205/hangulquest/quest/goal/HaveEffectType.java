package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.KoGrammar;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Map;

/**
 * Have a status effect on the player: "공중 부양 효과를 받아요". The player's active effects are read every tick. The
 * names are those of the vanilla Korean list (effect.minecraft.*); a test keeps them in step.
 */
final class HaveEffectType implements GoalType<Goal.HaveEffect> {
	/** Effect id to its vanilla Korean name. */
	static final Map<String, String> EFFECTS = Map.of("minecraft:levitation", "공중 부양");

	@Override
	public String id() {
		return "HAVE_EFFECT";
	}

	@Override
	public Class<Goal.HaveEffect> goalClass() {
		return Goal.HaveEffect.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("effect"));
	}

	@Override
	public Goal.HaveEffect parse(JsonObject json) {
		return new Goal.HaveEffect(JsonFields.string(json, "effect", ""));
	}

	@Override
	public JsonObject toJson(Goal.HaveEffect goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("effect", goal.effect());
		return json;
	}

	@Override
	public String keyPart(Goal.HaveEffect goal) {
		return goal.effect();
	}

	@Override
	public boolean evaluate(Goal.HaveEffect goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		return obs.effects.contains(goal.effect());
	}

	@Override
	public Verb verb(Goal.HaveEffect goal, VerbTable table) {
		return Verb.RECEIVE;
	}

	private static String name(Goal.HaveEffect goal) {
		return EFFECTS.getOrDefault(goal.effect(), "");
	}

	@Override
	public String korean(Goal.HaveEffect goal, SentenceContext c) {
		return SentenceBuilder.phrase(KoGrammar.eul(name(goal) + " 효과"), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.HaveEffect goal, String obj, Verb verb) {
		return KoGrammar.eul(name(goal) + " 효과") + " " + verb.past();
	}

	@Override
	public List<String> grammarIds(Goal.HaveEffect goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(SentenceBuilder.baseGrammar(c.verb(), c.level(), ObjectKind.NONE));
		ids.add("L1.eul_reul"); // 효과를
		return ids;
	}

	@Override
	public String status(Goal.HaveEffect goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return name(goal) + " 효과 필요";
	}

	@Override
	public String object(Goal.HaveEffect goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public String taskSignature(Goal.HaveEffect goal) {
		return id() + ":" + goal.effect() + ":" + Verb.RECEIVE.name();
	}

	@Override
	public void validate(Goal.HaveEffect goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (!EFFECTS.containsKey(goal.effect())) {
			context.problem("unknown effect " + goal.effect() + " (known: " + EFFECTS.keySet() + ")");
		}
	}
}
