package io.github.leks2205.hangulquest.quest.goal;

import com.google.gson.JsonObject;
import io.github.leks2205.hangulquest.data.Catalog;
import io.github.leks2205.hangulquest.data.Concept;
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
import io.github.leks2205.hangulquest.quest.condition.ConditionTypes;
import io.github.leks2205.hangulquest.sense.Observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold an item while doing something else: "횃불을 들고 숲으로 가요" (the inner goal only counts while the
 * item is in a hand) or "횃불을 들고 밤에 걸어요" (a condition: walk with the item for some seconds while
 * it holds). Teaches the held-state -고.
 */
final class HoldWhileType implements GoalType<Goal.HoldWhile> {
	private static final int DEFAULT_SECONDS = 10;

	@Override
	public String id() {
		return "HOLD_WHILE";
	}

	@Override
	public Class<Goal.HoldWhile> goalClass() {
		return Goal.HoldWhile.class;
	}

	@Override
	public List<Field> fields() {
		return List.of(Field.resource("item"),
				Field.goalOrCondition("inner", "A goal that only counts while the item is held, or a condition "
						+ "that must hold while walking with it."),
				Field.integer("seconds", 1, 600, false));
	}

	@Override
	public Goal.HoldWhile parse(JsonObject json) {
		Goal innerGoal = null;
		Condition innerCondition = null;
		if (json.has("inner") && json.get("inner").isJsonObject()) {
			JsonObject inner = json.getAsJsonObject("inner");
			innerGoal = GoalTypes.parse(inner);
			if (innerGoal == null) {
				innerCondition = ConditionTypes.parse(inner);
			}
		}
		return new Goal.HoldWhile(JsonFields.string(json, "item", ""), innerGoal, innerCondition,
				JsonFields.integer(json, "seconds", DEFAULT_SECONDS));
	}

	@Override
	public JsonObject toJson(Goal.HoldWhile goal) {
		JsonObject json = new JsonObject();
		json.addProperty("type", id());
		json.addProperty("item", goal.item());
		if (goal.innerGoal() != null) {
			json.add("inner", GoalTypes.toJson(goal.innerGoal()));
		} else if (goal.innerCondition() != null) {
			json.add("inner", ConditionTypes.toJson(goal.innerCondition()));
			json.addProperty("seconds", goal.seconds());
		}
		return json;
	}

	@Override
	public String keyPart(Goal.HoldWhile goal) {
		String inner = goal.innerGoal() != null ? GoalEvaluator.key(goal.innerGoal())
				: String.valueOf(goal.innerCondition()) + "/" + goal.seconds();
		return goal.item() + "+" + inner;
	}

	@Override
	public boolean evaluate(Goal.HoldWhile goal, Observation obs, GoalEvaluator.Tracker tracker,
							boolean nightLatch) {
		boolean holding = Hands.holds(obs, goal.item());
		if (goal.innerGoal() != null) {
			GoalEvaluator.Tracker sub = Clauses.sub(tracker, 0, 1);
			if (!holding) {
				// Counts stay, but a look or a stay has to start again.
				sub.dwellMs = 0;
				sub.target = null;
				return false;
			}
			boolean done = GoalEvaluator.tick(goal.innerGoal(), List.of(), obs, sub);
			tracker.count = Math.max(tracker.count, sub.count);
			tracker.assisted |= sub.assisted;
			return done;
		}
		if (goal.innerCondition() == null) {
			return false;
		}
		// Walking with the item while the condition holds; the seconds add up.
		if (holding && !obs.idle && GoalEvaluator.conditionMet(goal.innerCondition(), obs)) {
			tracker.dwellMs += GoalEvaluator.TICK_MS;
		}
		return tracker.dwellMs >= Math.max(1, goal.seconds()) * 1000L;
	}

	@Override
	public long dwellNeededMs(Goal.HoldWhile goal) {
		return goal.innerGoal() == null ? Math.max(1, goal.seconds()) * 1000L : 0L;
	}

	@Override
	public Verb verb(Goal.HoldWhile goal, VerbTable table) {
		Goal inner = goal.innerGoal();
		return inner == null ? Verb.WALK : GoalTypes.of(inner).verb(inner, table);
	}

	/** From this level the item is held "while" the rest is done: 들고 있는 동안 (-는 동안), not 들고 (-고). */
	static final int WHILE_LEVEL = 5;

	@Override
	public String korean(Goal.HoldWhile goal, SentenceContext c) {
		String held = KoGrammar.eul(heldName(goal, c)) + (whileHolding(goal, c.level()) ? " 들고 있는 동안" : " 들고");
		if (goal.innerGoal() != null) {
			String inner = Clauses.sentence(goal.innerGoal(), c, c.level());
			// "들고 있는 동안 … 일 초 동안 바라봐요" would say 동안 twice: the look keeps its length, the sentence does not
			return held + " " + (whileHolding(goal, c.level()) ? withoutSeconds(goal.innerGoal(), inner) : inner);
		}
		return SentenceBuilder.phrase(held + conditionPhrase(goal, c), Verb.WALK, c.level(), c.finale());
	}

	/** The look without its "일 초 동안": 들고 있는 동안 already is the 동안 of the sentence. */
	private static String withoutSeconds(Goal inner, String sentence) {
		long dwell = switch (inner) {
			case Goal.LookAtBlock look -> look.dwellMs();
			case Goal.LookAtEntity look -> look.dwellMs();
			default -> 0L;
		};
		return dwell > 0 ? sentence.replace(" " + SentenceBuilder.secondsPhrase(dwell), "") : sentence;
	}

	@Override
	public String ceremony(Goal.HoldWhile goal, String obj, Verb verb) {
		return KoGrammar.eul(obj) + " 들고 " + verb.past();
	}

	@Override
	public String ceremony(Goal.HoldWhile goal, SentenceContext c) {
		String held = KoGrammar.eul(heldName(goal, c)) + " 들고";
		if (goal.innerGoal() != null) {
			return held + " " + Clauses.ceremony(goal.innerGoal(), c);
		}
		return held + conditionPhrase(goal, c) + " " + Verb.WALK.past();
	}

	@Override
	public List<String> grammarIds(Goal.HoldWhile goal, SentenceContext c) {
		List<String> ids = new ArrayList<>();
		ids.add("L2.go_state");
		if (whileHolding(goal, c.level())) {
			ids.add("L5.neun_dongan");
		}
		if (goal.innerGoal() != null) {
			Goal inner = goal.innerGoal();
			ids.addAll(GoalTypes.of(inner).grammarIds(inner, Clauses.contextFor(inner, c, c.level())));
		} else {
			ids.addAll(SentenceBuilder.baseGrammar(Verb.WALK, c.level(), ObjectKind.NONE));
			if (goal.innerCondition() != null) {
				ids.addAll(ConditionTypes.of(goal.innerCondition()).grammarIds(goal.innerCondition(), c.level()));
			}
		}
		return ids;
	}

	/** The item stays in the hand for as long as the rest goes on; -는 동안 says so, the plain -고 does not. */
	@Override
	public List<String> decidingForms(Goal.HoldWhile goal, int level) {
		return whileHolding(goal, level) ? List.of("L5.neun_dongan") : List.of();
	}

	/** Holding while doing something (an inner goal) is -는 동안; walking at night with it in the hand stays 들고. */
	private static boolean whileHolding(Goal.HoldWhile goal, int level) {
		return level >= WHILE_LEVEL && goal.innerGoal() != null;
	}

	@Override
	public String status(Goal.HoldWhile goal, Observation obs, GoalEvaluator.Tracker tracker, Catalog catalog,
						 String word) {
		if (!Hands.holds(obs, goal.item())) {
			return "손에 " + QuestStatus.block(goal.item(), catalog) + " 필요";
		}
		if (goal.innerGoal() != null) {
			Goal inner = goal.innerGoal();
			return GoalTypes.of(inner).status(inner, obs, Clauses.sub(tracker, 0, 1), catalog, word);
		}
		Condition condition = goal.innerCondition();
		if (condition != null && !GoalEvaluator.conditionMet(condition, obs)) {
			return ConditionTypes.of(condition).unmetText(condition, obs, catalog);
		}
		if (obs.idle) {
			return "걸어요";
		}
		return QuestStatus.look(tracker.dwellMs, Math.max(1, goal.seconds()) * 1000L);
	}

	@Override
	public String object(Goal.HoldWhile goal) {
		return goal.item();
	}

	@Override
	public ObjectKind objectKind() {
		return ObjectKind.ITEM;
	}

	@Override
	public String taskSignature(Goal.HoldWhile goal) {
		Goal inner = goal.innerGoal();
		String what = inner != null ? GoalTypes.of(inner).taskSignature(inner)
				: goal.innerCondition() == null ? "" : ConditionTypes.of(goal.innerCondition()).id();
		return id() + ":" + goal.item() + ":" + Verb.HOLD.name() + "+" + what;
	}

	/** The word may teach what is held or what the inner goal or condition points at. */
	@Override
	public List<Mention> mentions(Goal.HoldWhile goal) {
		List<Mention> all = new ArrayList<>(List.of(new Mention(ObjectKind.ITEM, goal.item())));
		Goal inner = goal.innerGoal();
		if (inner != null) {
			all.addAll(GoalTypes.of(inner).mentions(inner));
		}
		if (goal.innerCondition() != null) {
			all.addAll(ConditionTypes.of(goal.innerCondition()).mentions(goal.innerCondition()));
		}
		return all;
	}

	@Override
	public boolean covers(Goal.HoldWhile goal, Concept concept) {
		Goal inner = goal.innerGoal();
		return GoalType.super.covers(goal, concept) || inner != null && GoalTypes.of(inner).covers(inner, concept);
	}

	@Override
	public void validate(Goal.HoldWhile goal, ValidationContext context) {
		GoalType.super.validate(goal, context);
		if ((goal.innerGoal() == null) == (goal.innerCondition() == null)) {
			context.problem("HOLD_WHILE needs exactly one inner goal or condition");
		}
		if (goal.innerGoal() != null) {
			if (Clauses.usable(goal.innerGoal())) {
				Clauses.validatePart(goal.innerGoal(), context, "inner");
			} else {
				context.problem("the inner goal of HOLD_WHILE must be a simple goal");
			}
		}
		if (goal.innerCondition() != null) {
			ConditionTypes.of(goal.innerCondition()).validate(goal.innerCondition(), context);
		}
		if (goal.seconds() < 1 || goal.seconds() > 600) {
			context.problem("seconds must be 1..600");
		}
	}

	private static String heldName(Goal.HoldWhile goal, SentenceContext c) {
		String name = c.nameOfObject(ObjectKind.ITEM, goal.item());
		return name.isEmpty() ? c.obj() : name;
	}

	/** " 밤에": the condition spoken in front of the walking, or "". */
	private static String conditionPhrase(Goal.HoldWhile goal, SentenceContext c) {
		if (goal.innerCondition() == null) {
			return "";
		}
		String prefix = ConditionTypes.of(goal.innerCondition()).prefix(goal.innerCondition(), c.namer(), c.level()).trim();
		return prefix.isEmpty() ? "" : " " + prefix;
	}
}
