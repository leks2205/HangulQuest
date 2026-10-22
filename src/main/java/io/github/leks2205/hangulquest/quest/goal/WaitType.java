package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.SentenceBuilder;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.List;
import java.util.Locale;

/**
 * Wait: "잠깐 기다려요", "10초 동안 기다려요", "밤까지 기다려요". The player stays within a few blocks of
 * where the wait began; walking away starts it again from the new spot. With {@code until} the wait also
 * ends only when that part of the day has come. A place to wait near is a condition of the step, not part of
 * the goal.
 */
final class WaitType implements GoalType<Goal.Wait> {
	private static final int DEFAULT_SECONDS = 5;
	/** How far the player may drift while waiting. */
	private static final double STAY_RADIUS = 3.0;
	/** A tick that lasts longer than this (a pause, a lag spike) counts as this much. */
	private static final long MAX_TICK_MS = 250L;

	@Override
	public String id() {
		return "WAIT";
	}

	@Override
	public Class<Goal.Wait> goalClass() {
		return Goal.Wait.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.integer("seconds", 1, 600, false),
				Field.choice("until", false, "Also wait for this part of the day.", "NIGHT", "DAY"));
	}

	@Override
	public Goal.Wait parse(JsonObject json) {
		String until = JsonFields.string(json, "until", null);
		return new Goal.Wait(JsonFields.integer(json, "seconds", DEFAULT_SECONDS),
				until == null || until.isBlank() ? null : until.toUpperCase(Locale.ROOT));
	}

	@Override
	public JsonObject toJson(Goal.Wait goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("seconds", goal.seconds());
		if (goal.until() != null) {
			json.addProperty("until", goal.until());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Wait goal) {
		return goal.seconds() + (goal.until() == null ? "" : "/" + goal.until());
	}

	private static boolean arrived(Goal.Wait goal, Observation obs) {
		if (goal.until() == null) {
			return true;
		}
		return obs.phase() == ("DAY".equals(goal.until()) ? Condition.Phase.DAY : Condition.Phase.NIGHT);
	}

	@Override
	public boolean evaluate(Goal.Wait goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		long elapsed = tracker.mark == 0L ? 0L : Math.min(MAX_TICK_MS, Math.max(0L, obs.now - tracker.mark));
		tracker.mark = obs.now;
		double x = obs.position.x;
		double z = obs.position.z;
		if (tracker.at == null || Math.hypot(x - tracker.at[0], z - tracker.at[1]) > STAY_RADIUS) {
			tracker.at = new double[] {x, z};
			tracker.dwellMs = 0;
			return false;
		}
		tracker.dwellMs += elapsed;
		return tracker.dwellMs >= goal.seconds() * 1000L && arrived(goal, obs);
	}

	@Override
	public long dwellNeededMs(Goal.Wait goal) {
		return goal.seconds() * 1000L;
	}

	@Override
	public Verb verb(Goal.Wait goal, VerbTable table) {
		return Verb.WAIT;
	}

	@Override
	public String korean(Goal.Wait goal, SentenceContext c) {
		return SentenceBuilder.phrase(how(goal, c.withCounts(), c.level()), c.verb(), c.level(), c.finale());
	}

	@Override
	public String ceremony(Goal.Wait goal, String obj, Verb verb) {
		return how(goal, true, 2) + " " + verb.past();
	}

	@Override
	public String ceremony(Goal.Wait goal, SentenceContext c) {
		return how(goal, true, c.level() + 1) + " " + c.verb().past();
	}

	/** "밤까지", "10초 동안" (from level 2 on, and not in the hint), else "잠깐". */
	private static String how(Goal.Wait goal, boolean withCounts, int level) {
		if (goal.until() != null) {
			return ("DAY".equals(goal.until()) ? "낮" : "밤") + "까지";
		}
		return withCounts && level >= 2 ? goal.seconds() + "초 동안" : "잠깐";
	}

	@Override
	public String status(Goal.Wait goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		long done = Math.min(goal.seconds(), tracker.dwellMs / 1000L);
		return done + "/" + goal.seconds() + "초"
				+ (arrived(goal, obs) ? "" : " · 아직 " + QuestStatus.phase(obs.phase()));
	}

	@Override
	public String object(Goal.Wait goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public void validate(Goal.Wait goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (goal.seconds() < 1 || goal.seconds() > 600) {
			context.problem("seconds must be 1..600");
		}
		if (goal.until() != null && !goal.until().equals("NIGHT") && !goal.until().equals("DAY")) {
			context.problem("until must be NIGHT or DAY, not " + goal.until());
		}
	}
}
