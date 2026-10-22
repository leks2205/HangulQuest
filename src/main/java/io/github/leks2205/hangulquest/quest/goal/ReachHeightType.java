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

/** Stand at a height between two Y levels. */
final class ReachHeightType implements GoalType<Goal.ReachHeight> {
	@Override
	public String id() {
		return "REACH_HEIGHT";
	}

	@Override
	public Class<Goal.ReachHeight> goalClass() {
		return Goal.ReachHeight.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("minY", -256, 512, true), Field.integer("maxY", -256, 512, true));
	}

	@Override
	public Goal.ReachHeight parse(JsonObject json) {
		return new Goal.ReachHeight(JsonFields.integer(json, "minY", 0), JsonFields.integer(json, "maxY", 320));
	}

	@Override
	public JsonObject toJson(Goal.ReachHeight goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("minY", goal.minY());
		json.addProperty("maxY", goal.maxY());
		return json;
	}

	@Override
	public String keyPart(Goal.ReachHeight goal) {
		return goal.minY() + "-" + goal.maxY();
	}

	@Override
	public boolean evaluate(Goal.ReachHeight goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		return obs.y >= goal.minY() && obs.y <= goal.maxY();
	}

	@Override
	public Verb verb(Goal.ReachHeight goal, VerbTable table) {
		return descends(goal) ? Verb.DESCEND : Verb.CLIMB;
	}

	/** Sea level; a goal whose ceiling lies below it asks for a descent. */
	static final int SEA_LEVEL = 63;

	/** A goal with a ceiling below sea level and a floor at or below the ground: go down, not up. */
	static boolean descends(Goal.ReachHeight goal) {
		return goal.minY() <= 0 && goal.maxY() < SEA_LEVEL;
	}

	@Override
	public String korean(Goal.ReachHeight goal, SentenceContext c) {
		if (descends(goal)) {
			return SentenceBuilder.phrase(down(goal, c.level()), c.verb(), c.level(), c.finale());
		}
		if (goal.minY() > 0) {
			// Level 1 has no numbers for a height: climb a hill or a mountain.
			String where = c.level() <= 1 ? summit(goal.minY(), c.conditions()) : "높이 " + KoGrammar.blocks(goal.minY()) + " 이상으로";
			return SentenceBuilder.phrase(where, c.verb(), c.level(), c.finale());
		}
		return SentenceBuilder.phrase("땅 위로", Verb.CLIMB, c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.ReachHeight goal, SentenceContext c) {
		if (descends(goal)) {
			return down(goal, c.level()) + " " + Verb.DESCEND.past();
		}
		return goal.minY() > 0 ? summit(goal.minY(), c.conditions()) + " " + Verb.CLIMB.past()
				: ceremony(goal, c.obj(), c.verb());
	}

	@Override
	public String ceremony(Goal.ReachHeight goal, String obj, Verb verb) {
		if (descends(goal)) {
			return down(goal, 2) + " " + Verb.DESCEND.past();
		}
		if (goal.minY() > 0) {
			return summit(goal.minY()) + " " + Verb.CLIMB.past();
		}
		return "땅 위로 " + Verb.CLIMB.past();
	}

	/** "아래로" on the first level, the height from level 2 on ("높이 0 이하로"). */
	private static String down(Goal.ReachHeight goal, int level) {
		return level <= 1 ? "아래로" : "높이 " + (goal.maxY() <= 0 ? goal.maxY() + "" : KoGrammar.blocks(goal.maxY())) + " 이하로";
	}

	/**
	 * The summit as the place of the step allows: a biome or another dimension in the conditions already names
	 * where the climb happens, so the hill or the mountain is dropped ("높은 곳으로"); the Nether has no 산.
	 */
	static String summit(int minY, java.util.List<io.github.leks2205.hangulquest.quest.Condition> conditions) {
		boolean placed = conditions != null && conditions.stream().anyMatch(condition ->
				condition instanceof io.github.leks2205.hangulquest.quest.Condition.InBiome
						|| condition instanceof io.github.leks2205.hangulquest.quest.Condition.InDimension dimension
						&& !"minecraft:overworld".equals(dimension.dimension()));
		return placed ? "높은 곳으로" : summit(minY);
	}

	/** "언덕 위로" above sea level, "산 위로" from the height of real mountains (Y 100 and up). */
	static String summit(int minY) {
		return (minY >= 100 ? "산" : "언덕") + " 위로";
	}

	@Override
	public String status(Goal.ReachHeight goal, Observation obs, GoalEvaluator.Tracker tracker,
						 Catalog catalog, String word) {
		return QuestStatus.yText(obs.y, goal.minY(), goal.maxY());
	}

	@Override
	public String object(Goal.ReachHeight goal) {
		return null;
	}

	/**
	 * A height has no object, so the band is its handle: a descent, the ground, a hill (Y 63 to 79), and the
	 * heights 80, 90 and 100 and up (a mountain). Two climbs of different bands are two tasks.
	 */
	@Override
	public String taskSignature(Goal.ReachHeight goal) {
		return id() + "::" + verb(goal, VerbTable.shared()).name() + ":" + band(goal);
	}

	static String band(Goal.ReachHeight goal) {
		if (descends(goal)) {
			return "down";
		}
		int y = goal.minY();
		if (y <= 0) {
			return "ground";
		}
		return y >= 100 ? "h" + (y / 50 * 50) : y >= 90 ? "90" : y >= 80 ? "80" : "hill";
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}
}
