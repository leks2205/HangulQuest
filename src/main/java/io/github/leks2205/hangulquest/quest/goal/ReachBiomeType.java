package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
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

/** Stand in a biome (or a biome tag) for a moment. */
final class ReachBiomeType implements GoalType<Goal.ReachBiome> {
	@Override
	public String id() {
		return "REACH_BIOME";
	}

	@Override
	public Class<Goal.ReachBiome> goalClass() {
		return Goal.ReachBiome.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("biome"), Field.integer("dwellMs", 100, 10000, true));
	}

	@Override
	public Goal.ReachBiome parse(JsonObject json) {
		return new Goal.ReachBiome(JsonFields.string(json, "biome", ""), JsonFields.integer(json, "dwellMs", 1000));
	}

	@Override
	public JsonObject toJson(Goal.ReachBiome goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("biome", goal.biome());
		json.addProperty("dwellMs", goal.dwellMs());
		return json;
	}

	@Override
	public String keyPart(Goal.ReachBiome goal) {
		return goal.biome() + "/" + goal.dwellMs();
	}

	@Override
	public boolean evaluate(Goal.ReachBiome goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		boolean matches = obs.biomeIs(goal.biome());
		if (!matches && nightLatch) {
			matches = biomeAtNightMatches(obs, goal.biome());
		}
		return Matching.dwell(tracker, goal.biome(), matches ? goal.biome() : null, goal.dwellMs());
	}

	private static boolean biomeAtNightMatches(Observation obs, String biomeOrTag) {
		if (biomeOrTag == null || obs.biomeAtNight == null) {
			return false;
		}
		if (biomeOrTag.startsWith("#")) {
			return obs.biomeTagsAtNight.contains(biomeOrTag.substring(1));
		}
		return biomeOrTag.equals(obs.biomeAtNight);
	}

	@Override
	public boolean nightLatchable() {
		return true;
	}

	@Override
	public Verb verb(Goal.ReachBiome goal, VerbTable table) {
		return table.choose(id(), goal.biome(), 1, Verb.GO); // a cave is entered (들어가요), not gone to
	}

	/** A sea or a river is swum in when the step also asks for the water: 바다에서 수영해요, not 물속에서 바다로 가요. */
	@Override
	public Verb verb(Goal.ReachBiome goal, VerbTable table, List<Condition> conditions) {
		boolean inWater = conditions.stream().anyMatch(condition -> condition instanceof Condition.InWater);
		return inWater && water(goal.biome()) ? Verb.SWIM : verb(goal, table);
	}

	/** The sentence of a swim in a water biome already says that the player is in the water. */
	@Override
	public boolean says(Goal.ReachBiome goal, Condition condition) {
		return condition instanceof Condition.InWater && water(goal.biome());
	}

	/** Oceans and rivers, by tag or by id. */
	static boolean water(String biome) {
		if (biome == null || biome.isBlank()) {
			return false;
		}
		String path = biome.substring(biome.indexOf(':') + 1);
		return path.equals("is_ocean") || path.equals("is_river") || path.endsWith("ocean") || path.endsWith("river");
	}

	@Override
	public String korean(Goal.ReachBiome goal, SentenceContext c) {
		if (c.verb() == Verb.SWIM) {
			return SentenceBuilder.phrase(SentenceBuilder.objectPhrase(c.obj(), c.verb()), c.verb(), c.level(),
					c.finale());
		}
		return SentenceBuilder.phrase(KoGrammar.euro(c.obj()), c.verb(), c.level(), c.finale());
	}

	@Override
	public List<String> grammarIds(Goal.ReachBiome goal, SentenceContext c) {
		if (c.verb() == Verb.SWIM) {
			return List.of(io.github.leks2205.hangulquest.quest.grammar.Ladder.shared().rung(c.level()).grammar(),
					"L2.eseo_activity");
		}
		return GoalType.super.grammarIds(goal, c);
	}

	@Override
	public String ceremony(Goal.ReachBiome goal, String obj, Verb verb) {
		if (verb == Verb.SWIM) {
			return SentenceBuilder.objectPhrase(obj, verb) + " " + verb.past();
		}
		if (verb == Verb.ENTER) {
			return KoGrammar.euro(obj) + " " + verb.past();
		}
		return obj + "에 " + Verb.ARRIVE.past();
	}

	@Override
	public String status(Goal.ReachBiome goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return "현재: " + QuestStatus.biome(obs.biome, catalog) + " · 목표: "
				+ QuestStatus.biome(goal.biome(), catalog);
	}

	@Override
	public String object(Goal.ReachBiome goal) {
		return goal.biome();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.BIOME;
	}

	@Override
	public void validate(Goal.ReachBiome goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.biome() == null || goal.biome().isBlank()) {
			context.problem("no biome");
		}
	}
}
