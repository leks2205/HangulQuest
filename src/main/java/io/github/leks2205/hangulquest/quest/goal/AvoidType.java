package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
import io.github.leks2205.hangulquest.quest.Condition;
import io.github.leks2205.hangulquest.quest.Field;
import io.github.leks2205.hangulquest.quest.Goal;
import io.github.leks2205.hangulquest.quest.GoalEvaluator;
import io.github.leks2205.hangulquest.quest.JsonFields;
import io.github.leks2205.hangulquest.quest.QuestStatus;
import io.github.leks2205.hangulquest.quest.Verb;
import io.github.leks2205.hangulquest.quest.VerbTable;
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Do not do something for a while: "밤에 침대에서 자지 마세요". The step is done when the window has run
 * out without the action. If the action happens the window starts over, quietly: no penalty, only
 * the status line "다시 해 봐요". The window only runs while the optional {@code during} condition holds.
 */
final class AvoidType implements GoalType<Goal.Avoid> {
	/** Shown after a restart, for a few seconds. */
	static final String RETRY = "다시 해 봐요";
	private static final String RETRY_MARK = "retry";
	private static final long RETRY_SHOWN_MS = 5_000L;
	private static final int DEFAULT_WINDOW = 60;

	@Override
	public String id() {
		return "AVOID";
	}

	@Override
	public Class<Goal.Avoid> goalClass() {
		return Goal.Avoid.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.goal("action", true, "The action that must not happen."),
				Field.integer("windowSeconds", 1, 3600, false),
				Field.condition("during", false, "The window only runs, and the action only counts, while this holds."));
	}

	@Override
	public Goal.Avoid parse(JsonObject json) {
		Goal action = json.has("action") && json.get("action").isJsonObject()
				? GoalTypes.parse(json.getAsJsonObject("action")) : null;
		Condition during = json.has("during") && json.get("during").isJsonObject()
				? ConditionTypes.parse(json.getAsJsonObject("during")) : null;
		return new Goal.Avoid(action, JsonFields.integer(json, "windowSeconds", DEFAULT_WINDOW), during);
	}

	@Override
	public JsonObject toJson(Goal.Avoid goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		if (goal.action() != null) {
			json.add("action", GoalTypes.toJson(goal.action()));
		}
		json.addProperty("windowSeconds", goal.windowSeconds());
		if (goal.during() != null) {
			json.add("during", ConditionTypes.toJson(goal.during()));
		}
		return json;
	}

	@Override
	public String keyPart(Goal.Avoid goal) {
		return (goal.action() == null ? "" : GoalEvaluator.key(goal.action())) + "/" + goal.windowSeconds()
				+ (goal.during() == null ? "" : "@" + goal.during());
	}

	/**
	 * Counts {@code dwellMs} up while the window runs; {@code count} is how often it restarted. The
	 * action is watched by a sub-goal tracker that starts over after every restart.
	 */
	@Override
	public boolean evaluate(Goal.Avoid goal, Observation obs, GoalEvaluator.Tracker tracker, boolean nightLatch) {
		if (goal.action() == null) {
			return false;
		}
		boolean running = goal.during() == null || GoalEvaluator.conditionMet(goal.during(), obs);
		if (!running) {
			return false;
		}
		GoalEvaluator.Tracker watcher = Clauses.sub(tracker, 0, 1);
		if (GoalEvaluator.tick(goal.action(), List.of(), obs, watcher)) {
			tracker.count++;
			tracker.dwellMs = 0;
			tracker.target = RETRY_MARK; // transient: a saved game that is reloaded does not show the notice
			watcher.clear();
			return false;
		}
		tracker.dwellMs += GoalEvaluator.TICK_MS;
		return tracker.dwellMs >= Math.max(1, goal.windowSeconds()) * 1000L;
	}

	@Override
	public long dwellNeededMs(Goal.Avoid goal) {
		return Math.max(1, goal.windowSeconds()) * 1000L;
	}

	@Override
	public Verb verb(Goal.Avoid goal, VerbTable table) {
		return goal.action() == null ? Verb.USE : GoalTypes.of(goal.action()).verb(goal.action(), table);
	}

	@Override
	public String korean(Goal.Avoid goal, SentenceContext c) {
		return duringPhrase(goal, c) + Clauses.negative(goal.action(), c) + ".";
	}

	@Override
	public String ceremony(Goal.Avoid goal, String obj, Verb verb) {
		return "";
	}

	@Override
	public String ceremony(Goal.Avoid goal, SentenceContext c) {
		return duringPhrase(goal, c) + Clauses.pastNegative(goal.action(), c);
	}

	@Override
	public List<String> grammarIds(Goal.Avoid goal, SentenceContext c) {
		List<String> ids = new ArrayList<>();
		ids.add("L3.jima");
		if (goal.action() != null) {
			// The action keeps its object marker, but its ending is replaced by 지 마세요.
			Goal action = goal.action();
			List<String> actionIds = GoalTypes.of(action).grammarIds(action, Clauses.contextFor(action, c, 1));
			ids.addAll(actionIds.subList(Math.min(1, actionIds.size()), actionIds.size()));
		}
		if (goal.during() != null) {
			ids.addAll(ConditionTypes.of(goal.during()).grammarIds(goal.during(), c.level()));
		}
		return ids;
	}

	@Override
	public List<String> decidingForms(Goal.Avoid goal) {
		return List.of("L3.jima");
	}

	@Override
	public String status(Goal.Avoid goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		if (RETRY_MARK.equals(tracker.target) && tracker.dwellMs < RETRY_SHOWN_MS) {
			return RETRY;
		}
		if (goal.during() != null && !GoalEvaluator.conditionMet(goal.during(), obs)) {
			return ConditionTypes.of(goal.during()).unmetText(goal.during(), obs, catalog);
		}
		return QuestStatus.look(tracker.dwellMs, dwellNeededMs(goal));
	}

	@Override
	public String object(Goal.Avoid goal) {
		return null;
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.NONE;
	}

	@Override
	public String taskSignature(Goal.Avoid goal) {
		Goal action = goal.action();
		return id() + ":" + (action == null ? "" : GoalTypes.of(action).taskSignature(action))
				+ (goal.during() == null ? "" : "@" + goal.during());
	}

	@Override
	public List<Mention> mentions(Goal.Avoid goal) {
		Goal action = goal.action();
		return action == null ? List.of() : GoalTypes.of(action).mentions(action);
	}

	@Override
	public boolean covers(Goal.Avoid goal, Concept concept) {
		return goal.action() != null && GoalTypes.of(goal.action()).covers(goal.action(), concept);
	}

	@Override
	public void validate(Goal.Avoid goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if (context.koreanLevel() > 0 && context.koreanLevel() < SequenceType.MIN_LEVEL) {
			context.problem("AVOID needs koreanLevel " + SequenceType.MIN_LEVEL + " or higher (-지 마세요)");
		}
		if (goal.windowSeconds() < 1 || goal.windowSeconds() > 3600) {
			context.problem("windowSeconds must be 1..3600");
		}
		if (Clauses.usable(goal.action())) {
			Clauses.validatePart(goal.action(), context, "action");
		} else {
			context.problem("the action of AVOID must be a simple goal");
		}
		if (goal.during() != null) {
			ConditionTypes.of(goal.during()).validate(goal.during(), context);
		}
		Clauses.checkClueDoesNotTeach(context, true);
	}

	/** "밤에 ": the window condition spoken in front, or "". */
	private static String duringPhrase(Goal.Avoid goal, SentenceContext c) {
		return goal.during() == null ? "" : ConditionTypes.of(goal.during()).prefix(goal.during(), c.namer(), c.level());
	}
}
