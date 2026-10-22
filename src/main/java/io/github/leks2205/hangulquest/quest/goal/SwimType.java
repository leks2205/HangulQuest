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

/**
 * Swim a distance: "물에서 수영해요", from level 2 on "물에서 20블록 수영해요". The distance is what the
 * player covers across the ground while the sensor says the player is in water; a jump of more than eight
 * blocks between two ticks (a teleport) is not swimming.
 */
final class SwimType implements GoalType<Goal.Swim> {
	private static final int DEFAULT_BLOCKS = 20;
	private static final double JUMP = 8.0;
	private static final String WATER = "minecraft:water";

	@Override
	public String id() {
		return "SWIM";
	}

	@Override
	public Class<Goal.Swim> goalClass() {
		return Goal.Swim.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("blocks", 1, 1000, false));
	}

	@Override
	public Goal.Swim parse(JsonObject json) {
		return new Goal.Swim(JsonFields.integer(json, "blocks", DEFAULT_BLOCKS));
	}

	@Override
	public JsonObject toJson(Goal.Swim goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("blocks", goal.blocks());
		return json;
	}

	@Override
	public String keyPart(Goal.Swim goal) {
		return String.valueOf(goal.blocks());
	}

	@Override
	public boolean evaluate(Goal.Swim goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		if (!obs.inWater) {
			tracker.at = null;
			return tracker.distance >= goal.blocks();
		}
		double x = obs.position.x;
		double z = obs.position.z;
		if (tracker.at != null) {
			double step = Math.hypot(x - tracker.at[0], z - tracker.at[1]);
			if (step < JUMP) {
				tracker.distance += step;
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
	public int needed(Goal.Swim goal) {
		return goal.blocks();
	}

	@Override
	public Verb verb(Goal.Swim goal, VerbTable table) {
		return Verb.SWIM;
	}

	@Override
	public String korean(Goal.Swim goal, SentenceContext c) {
		String sea = sea(c);
		String water = SentenceBuilder.objectPhrase(sea != null ? sea : water(c), Verb.SWIM); // 바다에서, not 바다의 물에서
		String far = c.withCounts() && c.level() >= 2 ? " " + KoGrammar.blocks(goal.blocks()) : "";
		return SentenceBuilder.phrase(water + far, c.verb(), c.level(), c.finale());
	}

	/** Sea and river are the places one swims in: the sentence says 바다에서 and the condition's own phrase is left out. */
	@Override
	public boolean says(Goal.Swim goal, io.github.leks2205.hangulquest.quest.Condition condition) {
		return condition instanceof io.github.leks2205.hangulquest.quest.Condition.InBiome biome
				&& SEAS.contains(biome.biome());
	}

	private static final java.util.Set<String> SEAS = java.util.Set.of("#minecraft:is_ocean", "#minecraft:is_river");

	/** The sea or river the step asks for, else null. */
	private static String sea(SentenceContext c) {
		for (var condition : c.conditions()) {
			if (condition instanceof io.github.leks2205.hangulquest.quest.Condition.InBiome biome
					&& SEAS.contains(biome.biome())) {
				return SentenceBuilder.biomeName(biome.biome(), c.namer());
			}
		}
		return null;
	}

	@Override
	public String ceremony(Goal.Swim goal, SentenceContext c) {
		String sea = sea(c);
		return SentenceBuilder.objectPhrase(sea != null ? sea : "물", c.verb()) + " " + c.verb().past();
	}

	@Override
	public String ceremony(Goal.Swim goal, String obj, Verb verb) {
		return SentenceBuilder.objectPhrase("물", verb) + " " + verb.past();
	}

	@Override
	public List<String> grammarIds(Goal.Swim goal, SentenceContext c) {
		List<String> ids = new java.util.ArrayList<>(GoalType.super.grammarIds(goal, c));
		ids.add("L2.eseo_activity");
		return ids;
	}

	private static String water(SentenceContext c) {
		String name = c.name(ObjectKind.BLOCK, WATER);
		return name.isEmpty() ? "물" : name;
	}

	@Override
	public String status(Goal.Swim goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		return QuestStatus.count((int) tracker.distance, goal.blocks());
	}

	@Override
	public String object(Goal.Swim goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public void validate(Goal.Swim goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.blocks() < 1 || goal.blocks() > 1000) {
			context.problem("blocks must be 1..1000");
		}
	}
}
